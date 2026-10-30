package com.skincarecopilot.ingredient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** 博主文章是科普阅读层，不进入 AAD/NICE 的事实向量索引。 */
@Service
public class CreatorArticleService {
    private final List<Article> articles;

    public CreatorArticleService(ObjectMapper json) throws IOException {
        try (var input = new ClassPathResource("knowledge/creator-articles.json").getInputStream()) {
            List<Article> loaded = json.readValue(input, new TypeReference<>() {});
            if (loaded == null || loaded.size() > 10) throw new IllegalArgumentException("博主文章目录最多 10 项");
            var ids = new HashSet<String>();
            for (Article article : loaded) {
                if (article == null || article.id() == null || !article.id().matches("[a-z0-9-]{1,80}")
                        || !ids.add(article.id()) || article.title() == null || article.title().isBlank()
                        || !"温唯".equals(article.author()) || article.commonQuestion() == null
                        || article.commonQuestion().isBlank() || article.scope() == null || article.scope().isBlank()
                        || article.keywords() == null || article.keywords().isEmpty()) {
                    throw new IllegalArgumentException("博主文章缺少来源或问题元数据");
                }
                URI url = URI.create(article.url());
                if (!"https".equals(url.getScheme()) || !"miriammibao.com".equals(url.getHost())
                        || url.getUserInfo() != null || url.getPort() != -1) {
                    throw new IllegalArgumentException("博主文章链接必须来自核实的网站");
                }
                if (LocalDate.parse(article.publishedOn()).isAfter(LocalDate.parse(article.reviewedOn()))
                        || LocalDate.parse(article.reviewedOn()).isAfter(LocalDate.now())) {
                    throw new IllegalArgumentException("文章日期无效");
                }
            }
            this.articles = List.copyOf(loaded);
        }
    }

    public List<Article> catalog() { return articles; }

    public List<Article> related(String question) {
        if (question == null) return List.of();
        return articles.stream().filter(article -> article.keywords().stream().anyMatch(question::contains)).toList();
    }

    public record Article(String id, String author, String title, String url, String publishedOn,
                          String reviewedOn, String commonQuestion, List<String> keywords, String scope) {}
}
