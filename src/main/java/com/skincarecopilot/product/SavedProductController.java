package com.skincarecopilot.product;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理虚构用户确认的包装成分表，不将其标作官方配方。 */
@RestController
@RequestMapping("/api/products/saved")
public class SavedProductController {
    private final ProductIngredientStore store;

    public SavedProductController(ProductIngredientStore store) { this.store = store; }

    @GetMapping("/{userId}")
    public List<ProductIngredientStore.SavedProduct> list(@PathVariable String userId) {
        return store.list(userId);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable String userId, @RequestParam String productName) {
        return store.delete(userId, productName) ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
