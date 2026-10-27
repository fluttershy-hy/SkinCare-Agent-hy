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
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final ProductSearchService productSearch;
    private static final Pattern POSSIBLE_PRODUCT_REACTION = Pattern.compile(
            "(?:可能|怀疑).{0,12}对(.{2,80}?)(?:过敏|不耐受|有反应)");

    public ChatController(ChatService chatService, ProductSearchService productSearch) {
        this.chatService = chatService;
        this.productSearch = productSearch;
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
        // 餐食校验尚未实现时，不把明显的餐食请求交给模型生成未经校验的方案。
        if (UnsupportedMealRequest.matches(request.message())) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ChatResponse(
                    "当前版本尚未提供经过资料检索和过敏原校验的餐食建议。请先使用画像保存与查询功能；餐食功能完成后再试。"));
        }
        Matcher product = POSSIBLE_PRODUCT_REACTION.matcher(request.message());
        if (product.find()) {
            String productName = product.group(1).strip();
            if (productName.length() < 6) {
                return ResponseEntity.ok(new ChatResponse("请提供包装上的具体产品全名；仅凭品牌名无法确定配方。"
                        + "成分表也不能单独证明你对哪个成分过敏。", ChatRoute.PRODUCT_SEARCH, List.of()));
            }
            try {
                var result = productSearch.search(productName);
                StringBuilder answer = new StringBuilder(result.note());
                for (var page : result.officialPageCandidates()) {
                    answer.append("\n官网候选：").append(page.title()).append(" ").append(page.url());
                }
                return ResponseEntity.ok(new ChatResponse(answer.toString(), ChatRoute.PRODUCT_SEARCH, List.of()));
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ChatResponse("联网搜索暂不可用，请稍后重试。"));
            }
        }
        return ResponseEntity.ok(chatService.replyWithSources(
                request.userId(), request.sessionId(), request.message().trim()));
    }

    @ExceptionHandler(ModelNotConfiguredException.class)
    public ResponseEntity<ChatResponse> missingKey(ModelNotConfiguredException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ChatResponse(exception.getMessage()));
    }
}
