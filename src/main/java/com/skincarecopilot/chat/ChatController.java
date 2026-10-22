package com.skincarecopilot.chat;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.skincarecopilot.profile.ProfileService;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
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
        // 餐食校验尚未实现时，不把明显的餐食请求交给模型生成未经校验的方案。
        if (UnsupportedMealRequest.matches(request.message())) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ChatResponse(
                    "当前版本尚未提供经过资料检索和过敏原校验的餐食建议。请先使用画像保存与查询功能；餐食功能完成后再试。"));
        }
        return ResponseEntity.ok(new ChatResponse(chatService.reply(request.userId(), request.message().trim())));
    }

    @ExceptionHandler(ModelNotConfiguredException.class)
    public ResponseEntity<ChatResponse> missingKey(ModelNotConfiguredException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ChatResponse(exception.getMessage()));
    }
}
