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
import com.skincarecopilot.product.OfficialIngredientService;
import com.skincarecopilot.product.OfficialProductCatalog;
import com.skincarecopilot.ingredient.IngredientRequest;
import com.skincarecopilot.ingredient.IngredientService;
import com.skincarecopilot.meal.MealPlanService;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final ProductSearchService productSearch;
    private final ProductIngredientStore savedProducts;
    private final IngredientService ingredients;
    private final MealPlanService meals;
    private final OfficialIngredientService officialIngredients;
    private final OfficialProductCatalog officialCatalog;
    // 产品追问沿用当前用户和会话里的产品名；不能让模型凭聊天记忆猜测是否需要联网。
    private final ConcurrentHashMap<ConversationId, RecentProduct> recentProducts = new ConcurrentHashMap<>();
    private static final Pattern POSSIBLE_PRODUCT_REACTION = Pattern.compile(
            "(?:可能|怀疑).{0,12}对(.{2,80}?)(?:过敏|不耐受|有反应)");
    private static final Pattern PRODUCT_FOLLOW_UP = Pattern.compile(
            "(?:这款|这个|该)(?:产品|面膜|面霜|乳液|精华)|(?:为什么|怎么)(?:没|没有)查到|(?:再|重新)(?:查|搜)|(?:它|这个)(?:的)?(?:成分|配方)");
    private static final Pattern RETRY_QUERY = Pattern.compile("(?:为什么|怎么)(?:没|没有)查到|(?:再|重新)(?:查|搜)");

    public ChatController(ChatService chatService, ProductSearchService productSearch,
                          ProductIngredientStore savedProducts, IngredientService ingredients,
                          MealPlanService meals, OfficialIngredientService officialIngredients,
                          OfficialProductCatalog officialCatalog) {
        this.chatService = chatService;
        this.productSearch = productSearch;
        this.savedProducts = savedProducts;
        this.ingredients = ingredients;
        this.meals = meals;
        this.officialIngredients = officialIngredients;
        this.officialCatalog = officialCatalog;
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
        ConversationId conversation = new ConversationId(request.userId(), request.sessionId());
        if (request.productName() != null && !request.productName().isBlank()) {
            recentProducts.put(conversation, new RecentProduct(request.productName().strip(), request.message()));
            return productAnswer(request, request.productName().strip());
        }
        Matcher product = POSSIBLE_PRODUCT_REACTION.matcher(request.message());
        if (product.find()) {
            String productName = product.group(1).strip();
            if (productName.length() < 6) {
                return ResponseEntity.ok(new ChatResponse("请提供包装上的具体产品全名；仅凭品牌名无法确定配方。"
                        + "成分表也不能单独证明你对哪个成分过敏。", ChatRoute.PRODUCT_SEARCH, List.of()));
            }
            recentProducts.put(conversation, new RecentProduct(productName, request.message()));
            return productAnswer(request, productName);
        }
        RecentProduct previousProduct = recentProducts.get(conversation);
        if (PRODUCT_FOLLOW_UP.matcher(request.message()).find()) {
            if (previousProduct != null) {
                if (RETRY_QUERY.matcher(request.message()).find()) {
                    // 重新搜索时沿用原问题，避免模型把“为什么没查到”误当成用户贴了成分表。
                    ChatRequest retry = new ChatRequest(request.userId(), request.sessionId(),
                            previousProduct.question(), request.bodyArea(), request.currentState(),
                            request.sensitive(), request.concern(), previousProduct.name(),
                            request.confirmedIngredientAllergies(), request.allergiesConfirmed(),
                            request.productMarket(), request.productVersion());
                    var result = productAnswer(retry, previousProduct.name());
                    var answer = result.getBody();
                    if (answer != null && result.getStatusCode().is2xxSuccessful())
                        return ResponseEntity.ok(new ChatResponse("我重新查询了“" + previousProduct.name()
                                + "”的产品页面。\n" + answer.answer(), answer.route(), answer.sources()));
                    return result;
                }
                return productAnswer(request, previousProduct.name());
            }
            return ResponseEntity.ok(new ChatResponse("我可以联网查找具体产品页面和候选成分表。"
                    + "请提供产品全名；如果你刚开启了新会话，也请再发一次产品名。"
                    + "查到的网页成分仍需与你手中包装核对。", ChatRoute.PRODUCT_SEARCH, List.of()));
        }
        var namedProduct = ProductQuestionDetector.exactProductName(request.message());
        if (namedProduct.isPresent()) {
            recentProducts.put(conversation, new RecentProduct(namedProduct.get(), request.message()));
            return productAnswer(request, namedProduct.get());
        }
        return ResponseEntity.ok(chatService.replyWithSources(
                request.userId(), request.sessionId(), request.message().trim()));
    }

    private record RecentProduct(String name, String question) {}

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
        var known = officialCatalog.find(productName);
        if (known.isPresent()) {
            var entry = known.get();
            try {
                var extracted = officialIngredients.extract(entry.identity(), entry.url());
                if (extracted.isPresent()) return officialAnswer(request, extracted.get(), true);
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            }
            return ResponseEntity.ok(new ChatResponse("已找到这款产品的品牌官网来源，但当前页面无法核对完整成分。"
                    + "请稍后重试或粘贴包装成分表；不会用旧配方代替。官网：" + entry.url(),
                    ChatRoute.PRODUCT_SEARCH, List.of()));
        }
        try {
            var result = productSearch.search(productName);
            var identity = new OfficialIngredientService.Identity(productName,
                    request.productMarket(), request.productVersion());
            for (var page : result.officialPageCandidates()) {
                try {
                    var extracted = officialIngredients.extract(identity, page.url());
                    if (extracted.isEmpty()) continue;
                    return officialAnswer(request, extracted.get(), false);
                } catch (IOException | IllegalArgumentException ignored) {
                    // 页面无法访问或未标注完整成分，继续检查其他候选。
                }
            }
            StringBuilder answer = new StringBuilder("你问的是具体产品“").append(productName)
                    .append("”。本地没有这款产品的已确认成分表；我先查网页候选。\n")
                    .append(result.note());
            for (var page : result.officialPageCandidates()) {
                answer.append("\n网页候选：").append(page.title()).append(" ").append(page.url());
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

    private ResponseEntity<ChatResponse> officialAnswer(ChatRequest request,
                                                          OfficialIngredientService.OfficialList official,
                                                          boolean catalogVerified) {
        var analysis = ingredients.analyze(new IngredientRequest(request.userId(),
                new IngredientRequest.Product(official.productName(),
                        String.join("、", official.ingredients())), null,
                null, request.concern(), request.confirmedIngredientAllergies(),
                request.allergiesConfirmed(), request.message(), request.bodyArea(),
                request.currentState(), request.sensitive()));
        var sources = new java.util.ArrayList<ChatResponse.Source>();
        String publisher = catalogVerified ? "品牌官网" : "网页候选（来源未核实）";
        sources.add(new ChatResponse.Source("product-page", publisher, official.productName(),
                "市场 " + (official.market() == null ? "未核对" : official.market())
                        + " · SKU/型号 " + (official.version() == null ? "未核对" : official.version()),
                official.sourceUrl(), official.checkedOn(), "页面标注的全成分区域"));
        analysis.sources().forEach(s -> sources.add(new ChatResponse.Source(s.url(), s.publisher(),
                s.title(), s.section(), s.url(), s.verifiedOn(), "")));
        return ResponseEntity.ok(new ChatResponse(analysis.answer()
                + "\n\n核对说明：本次参考了网页候选中的 " + official.ingredients().size()
                + " 项成分（查阅于 " + official.checkedOn()
                + "）；请以手中包装为准，SKU 不代表配方修订版本。",
                ChatRoute.PRODUCT_SEARCH, List.copyOf(sources)));
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
