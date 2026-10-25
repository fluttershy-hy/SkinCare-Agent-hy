package com.skincarecopilot.knowledge;

/** Offsets are Java UTF-16 indices into entry.text(), with an exclusive end. */
public record KnowledgeChunk(String id, String entryId, int start, int end, String text,
                             KnowledgeEntry source) {
}
