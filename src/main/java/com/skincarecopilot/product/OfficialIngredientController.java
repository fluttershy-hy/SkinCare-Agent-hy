package com.skincarecopilot.product;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class OfficialIngredientController {
    private final OfficialIngredientService service;

    public OfficialIngredientController(OfficialIngredientService service) { this.service = service; }

    @PostMapping({"/web-ingredients", "/official-ingredients"})
    public ResponseEntity<?> extract(@RequestBody Request request) throws IOException, InterruptedException {
        var result = service.extract(new OfficialIngredientService.Identity(request.productName(),
                request.market(), request.version()), request.url());
        return result.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                        "error", "该网页未提供名称匹配且明确标注的完整成分表；请核对包装，不能从摘要补全。")));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({IOException.class, InterruptedException.class})
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> unavailable(Exception exception) {
        if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
        return Map.of("error", "产品网页暂不可访问，未导入成分表。请稍后重试或核对包装。");
    }

    public record Request(String productName, String market, String version, String url) {}
}
