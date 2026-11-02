package com.skincarecopilot.chat;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.skincarecopilot.profile.ProfileService;
import com.skincarecopilot.product.ProductSearchService;
import com.skincarecopilot.product.ProductQuestionDetector;
import com.skincarecopilot.product.ProductIngredientStore;
import com.skincarecopilot.ingredient.IngredientRequest;
import com.skincarecopilot.ingredient.IngredientService;
import com.skincarecopilot.meal.MealPlanService;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final ProductSearchService productSearch;
    private final ProductIngredientStore savedProducts;
    private final IngredientService ingredients;
    private final MealPlanService meals;
    private static final Pattern POSSIBLE_PRODUCT_REACTION = Pattern.compile(
            "(?:可能|怀疑).{0,12}对(.{2,80}?)(?:过敏|不耐受|有反应)");

    public ChatController(ChatService chatService, ProductSearchService productSearch,
                          ProductIngredientStore savedProducts, IngredientService ingredients,
                          MealPlanService meals) {
        this.chatService = chatService;
        this.productSearch = productSearch;
        this.savedProducts = savedProducts;
        this.ingredients = ingredients;
        this.meals = meals;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(new ChatResponse("消息不能为空"));
        }
        if (request.message().length() > 2000) {
            return ResponseEntity.badRequest().body(new ChatResponse("消息不能超过 2000 字符"));
        }
        try {
            ProfileService.validateUserId(request.userId());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ChatResponse(exception.getMessage()));
        }
        if (request.sessionId() == null || !request.sessionId().matches("[A-Za-z0-9_-]{1,64}")) {
            return ResponseEntity.badRequest().body(new ChatResponse("会话 ID 只能由 1～64 位字母、数字、下划线或短横线组成"));
        }
        // 餐食请求先进入结构化草稿与 Java 校验，不交给普通聊天模型自由生成。
        if (UnsupportedMealRequest.matches(request.message())) {
            return ResponseEntity.ok(meals.plan(request.userId(), request.message().strip()));
        }
        if (request.productName() != null && !request.productName().isBlank()) {
            return productAnswer(request, request.productName().strip());
        }
        Matcher product = POSSIBLE_PRODUCT_REACTION.matcher(request.message());
        if (product.find()) {
            String productName = product.group(1).strip();
            if (productName.length() < 6) {
                return ResponseEntity.ok(new ChatResponse("请提供包装上的具体产品全名；仅凭品牌名无法确定配方。"
                        + "成分表也不能单独证明你对哪个成分过敏。", ChatRoute.PRODUCT_SEARCH, List.of()));
            }
            return productAnswer(request, productName);
        }
        var namedProduct = ProductQuestionDetector.exactProductName(request.message());
        if (namedProduct.isPresent()) {
            return productAnswer(request, namedProduct.get());
        }
        return ResponseEntity.ok(chatService.replyWithSources(
                request.userId(), request.sessionId(), request.message().trim()));
    }

    private ResponseEntity<ChatResponse> productAnswer(ChatRequest request, String productName) {
        if (productName == null || productName.isBlank() || productName.length() > 120
                || productName.chars().anyMatch(Character::isISOControl)) {
            return ResponseEntity.badRequest().body(new ChatResponse("请提供不超过 120 字的具体产品全名"));
        }
        var saved = savedProducts.find(request.userId(), productName);
        if (saved.isPresent()) {
            var product = saved.get();
            var analysis = ingredients.analyze(new IngredientRequest(request.userId(),
                    new IngredientRequest.Product(product.name(), product.ingredients()), null,
                    null, request.concern(), request.confirmedIngredientAllergies(),
                    request.allergiesConfirmed(), request.message(), request.bodyArea(),
                    request.currentState(), request.sensitive()));
            var sources = analysis.sources().stream()
                    .map(s -> new ChatResponse.Source(s.url(), s.publisher(), s.title(), s.section(),
                            s.url(), s.verifiedOn(), "")).toList();
            return ResponseEntity.ok(new ChatResponse("根据你此前确认并保存的包装成分表：\n"
                    + analysis.answer(), ChatRoute.PRODUCT_SEARCH, sources));
        }
        try {
            var result = productSearch.search(productName);
            StringBuilder answer = new StringBuilder("你问的是具体产品“").append(productName)
                    .append("”。本地没有这款产品的已确认成分表；我先查官网候选页。\n")
                    .append(result.note());
            for (var page : result.officialPageCandidates()) {
                answer.append("\n官网候选：").append(page.title()).append(" ").append(page.url());
            }
            answer.append("\n搜索摘要不是完整 INCI。请粘贴包装成分表，再结合使用部位和当前状态讨论；"
                    + "不能仅凭产品名判断面部或身体是否适合。 ");
            return ResponseEntity.ok(new ChatResponse(answer.toString(), ChatRoute.PRODUCT_SEARCH, List.of()));
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ChatResponse("产品页面查询暂不可用，请提供包装上的完整成分表。"));
        }
    }

    @ExceptionHandler(ModelNotConfiguredException.class)
    public ResponseEntity<ChatResponse> missingKey(ModelNotConfiguredException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ChatResponse(exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ChatResponse> invalidProductInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ChatResponse(exception.getMessage()));
    }
}
