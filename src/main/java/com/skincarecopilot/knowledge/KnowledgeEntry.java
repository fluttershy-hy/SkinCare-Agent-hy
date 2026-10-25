package com.skincarecopilot.knowledge;

/** A manually reviewed passage. text is a Chinese translation, originalExcerpt is the source wording. */
public record KnowledgeEntry(String id, String publisher, String title, String url, String section,
                             String verifiedOn, String originalExcerpt, String text) {
}
