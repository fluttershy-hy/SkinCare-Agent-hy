package com.skincarecopilot.knowledge;

import java.util.List;
import org.springframework.stereotype.Component;

/** 向量相似不等于回答相关；先排除已知的人群或主题错配。 */
@Component
public class KnowledgeRelevanceFilter {
    public List<KnowledgeService.SearchHit> relevant(String query, List<KnowledgeService.SearchHit> hits) {
        return hits.stream().filter(hit -> relevant(query, hit.chunk().source())).toList();
    }

    boolean relevant(String query, KnowledgeEntry source) {
        String id = source.id();
        if (id.startsWith("malassezia-"))
            return containsAny(query, "马拉色菌", "脂溢性皮炎", "培养基", "脂质实验");
        if (id.equals("book-binghan-cleansing-sebum") || id.equals("miriam-cleansing"))
            return containsAny(query, "清洁", "洁面", "洗脸", "出油", "油脂");
        if (id.equals("miriam-preservative")) return containsAny(query, "防腐剂", "防腐");
        if (id.equals("book-binghan-rosacea-redness"))
            return containsAny(query, "玫瑰痤疮", "泛红", "发红");
        if (id.equals("book-skin-structure")) return containsAny(query, "表皮", "真皮", "皮肤结构");
        if (id.equals("book-skin-barrier") || id.equals("book-skin-dryness-lipids"))
            return containsAny(query, "屏障", "干燥", "保湿", "角质层", "敏感");
        if (id.equals("book-skin-acne-mechanisms")) return containsAny(query, "痤疮", "痘", "粉刺");
        if (id.equals("book-skin-sensitive-distinction") || id.equals("book-skin-sensitive-symptoms"))
            return containsAny(query, "敏感", "过敏", "刺激", "泛红", "刺痛");
        return true;
    }

    private boolean containsAny(String query, String... terms) {
        for (String term : terms) if (query.contains(term)) return true;
        return false;
    }
}
