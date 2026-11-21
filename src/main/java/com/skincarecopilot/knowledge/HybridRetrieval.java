package com.skincarecopilot.knowledge;

import java.util.*;

/** 中文二字词 BM25 与向量结果的倒数排名融合。 */
final class HybridRetrieval {
    private static final Map<String,String> EXPANSIONS = Map.of(
        "泛红", "潮红 红斑 血管扩张", "修复", "屏障修复 神经酰胺 积雪草 角鲨烷",
        "长痘", "痤疮 粉刺 丘疹", "过敏", "接触性皮炎 过敏原");
    static String expand(String query) {
        StringBuilder result = new StringBuilder(query);
        EXPANSIONS.forEach((key, synonyms) -> { if (query.contains(key)) result.append(' ').append(synonyms); });
        return result.toString();
    }
    static List<String> tokens(String input) {
        var result = new ArrayList<String>();
        var matcher = java.util.regex.Pattern.compile("[\\p{IsHan}]+|[a-zA-Z0-9]+").matcher(input.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() == 1 || !Character.UnicodeScript.of(word.charAt(0)).equals(Character.UnicodeScript.HAN)) result.add(word);
            else for (int i=0; i<word.length()-1; i++) result.add(word.substring(i,i+2));
        }
        return result;
    }
    static List<KnowledgeService.SearchHit> bm25(String query, List<KnowledgeChunk> corpus, int limit) {
        var terms = new HashSet<>(tokens(query));
        if (terms.isEmpty() || corpus.isEmpty()) return List.of();
        var docs = new ArrayList<Map<String,Integer>>(); var df = new HashMap<String,Integer>(); double totalLength = 0;
        for (var chunk : corpus) {
            var counts = new HashMap<String,Integer>();
            var words = tokens(chunk.text()); totalLength += words.size();
            for (String token : words) if (terms.contains(token)) counts.merge(token, 1, Integer::sum);
            counts.keySet().forEach(t -> df.merge(t,1,Integer::sum));
            counts.put("\u0000length", words.size()); docs.add(counts);
        }
        double avg = Math.max(1, totalLength / corpus.size()); var ranked = new ArrayList<KnowledgeService.SearchHit>();
        for (int i=0;i<corpus.size();i++) {
            var doc=docs.get(i); double score=0; double length=doc.get("\u0000length");
            for (String term:terms) { int tf=doc.getOrDefault(term,0); if(tf==0)continue;
                double idf=Math.log(1+(corpus.size()-df.get(term)+0.5)/(df.get(term)+0.5));
                score += idf * tf * 2.2 / (tf + 1.2 * (0.25 + 0.75 * length / avg)); }
            if(score>0) ranked.add(new KnowledgeService.SearchHit(score,corpus.get(i)));
        }
        ranked.sort(Comparator.comparingDouble(KnowledgeService.SearchHit::score).reversed());
        return ranked.stream().limit(limit).toList();
    }
    static List<KnowledgeService.SearchHit> fuse(List<KnowledgeService.SearchHit> lexical, List<KnowledgeService.SearchHit> semantic, int limit) {
        Map<String,Double> score=new HashMap<>(); Map<String,KnowledgeChunk> chunks=new HashMap<>();
        for(var list:List.of(lexical,semantic)) for(int i=0;i<list.size();i++) {var h=list.get(i);String id=h.chunk().id();chunks.put(id,h.chunk());score.merge(id,1.0/(60+i+1),Double::sum);}
        return score.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed())
                .limit(limit).map(e->new KnowledgeService.SearchHit(e.getValue(),chunks.get(e.getKey()))).toList();
    }
}
