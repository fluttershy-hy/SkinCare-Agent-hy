package com.skincarecopilot.knowledge;

/** 人工核对的短条目。书籍只保存少量摘录与改写，不保存整本正文。 */
public record KnowledgeEntry(String id, String publisher, String title, String url, String section,
                             String verifiedOn, String originalExcerpt, String text,
                             String sourceType, String bookAuthor, String bookPublisher,
                             String edition, String isbn, String locator) {
    public KnowledgeEntry(String id, String publisher, String title, String url, String section,
                          String verifiedOn, String originalExcerpt, String text) {
        this(id, publisher, title, url, section, verifiedOn, originalExcerpt, text,
                "WEB", null, null, null, null, null);
    }
}
