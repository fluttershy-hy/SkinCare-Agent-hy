package com.skincarecopilot.product;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductSearchController {
    private final ProductSearchService search;

    public ProductSearchController(ProductSearchService search) { this.search = search; }

    @PostMapping("/search")
    public ProductSearchService.ProductSearchResult search(@RequestBody ProductSearchRequest request)
            throws IOException, InterruptedException {
        return search.search(request.productName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Error invalid(IllegalArgumentException exception) { return new Error(exception.getMessage()); }

    @ExceptionHandler({IOException.class, InterruptedException.class})
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Error unavailable(Exception exception) { return new Error("联网搜索暂不可用，请稍后重试。"); }

    public record ProductSearchRequest(String productName) {}
    public record Error(String error) {}
}
