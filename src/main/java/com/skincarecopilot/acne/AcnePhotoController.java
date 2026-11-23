package com.skincarecopilot.acne;

import com.skincarecopilot.chat.ChatResponse;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/acne")
public class AcnePhotoController {
    private final AcnePhotoService service;
    public AcnePhotoController(AcnePhotoService service) { this.service=service; }
    @GetMapping("/status")
    public java.util.Map<String, Boolean> status() { return java.util.Map.of("configured", service.configured()); }
    @PostMapping(value="/analyze",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatResponse analyze(@RequestParam String userId, @RequestParam String sessionId, @RequestParam(required=false) String question,
                                @RequestParam MultipartFile image) throws Exception {
        if (image.isEmpty() || image.getSize()>5L*1024*1024) throw new IllegalArgumentException("请上传不超过 5 MiB 的 JPEG 或 PNG 图片。");
        return service.analyze(userId,sessionId,question,image.getBytes());
    }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Error> badRequest(RuntimeException e) { return ResponseEntity.badRequest().body(new Error(e.getMessage())); }
    @ExceptionHandler({IOException.class, InterruptedException.class})
    public ResponseEntity<Error> upstream(Exception e) { if(e instanceof InterruptedException) Thread.currentThread().interrupt(); return ResponseEntity.status(502).body(new Error("图片分析服务暂时不可用，请稍后重试。")); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Error> tooLarge() { return ResponseEntity.status(413).body(new Error("图片超过 5 MiB，请压缩后重试。")); }
    public record Error(String error) { }
}
