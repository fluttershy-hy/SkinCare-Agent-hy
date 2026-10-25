package com.skincarecopilot.knowledge;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @GetMapping("/sources")
    public KnowledgeService.Catalog sources() {
        return knowledge.catalog();
    }

    @PostMapping("/search")
    public KnowledgeService.SearchResult search(@RequestBody SearchRequest request) {
        return knowledge.search(request.query(), request.topK() == null ? 3 : request.topK());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SearchError invalid(IllegalArgumentException exception) {
        return new SearchError(exception.getMessage());
    }

    public record SearchRequest(String query, Integer topK) {
    }

    public record SearchError(String error) {
    }
}
