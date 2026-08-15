package com.idp.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a document into overlapping passages sized for retrieval.
 *
 * <p>Splits on paragraph boundaries first and only falls back to a hard character
 * cut inside an oversized paragraph, because a chunk that ends mid-sentence embeds
 * badly and reads worse when it is quoted back as a citation.
 */
@Component
public class DocumentChunker {

    /**
     * Target chunk size in characters. Large enough to hold a complete procedure or
     * endpoint description, small enough that one topic dominates the embedding.
     */
    static final int TARGET_CHARS = 900;

    /**
     * Carried from the tail of the previous chunk. Without it, a fact split across a
     * boundary is in neither chunk's embedding and is retrievable by neither.
     */
    static final int OVERLAP_CHARS = 150;

    /** Below this a chunk is a heading or stray line — noise in the ranking. */
    static final int MIN_CHUNK_CHARS = 40;

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        StringBuilder current = new StringBuilder();
        for (String paragraph : text.split("\\r?\\n\\s*\\r?\\n")) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                continue;
            }

            // A single paragraph over budget can't be packed — cut it directly so one
            // huge block doesn't become one huge chunk.
            if (trimmed.length() > TARGET_CHARS) {
                flush(chunks, current);
                for (String slice : hardSplit(trimmed)) {
                    addIfSubstantial(chunks, slice);
                }
                continue;
            }

            if (current.length() + trimmed.length() + 2 > TARGET_CHARS && current.length() > 0) {
                flush(chunks, current);
                current.append(overlapFrom(chunks));
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }
        flush(chunks, current);

        // A document shorter than MIN_CHUNK_CHARS still deserves to be retrievable;
        // returning nothing would silently drop it from the index.
        if (chunks.isEmpty()) {
            String stripped = text.strip();
            if (!stripped.isEmpty()) {
                chunks.add(stripped);
            }
        }
        return chunks;
    }

    private void flush(List<String> chunks, StringBuilder current) {
        addIfSubstantial(chunks, current.toString());
        current.setLength(0);
    }

    private void addIfSubstantial(List<String> chunks, String candidate) {
        String trimmed = candidate.strip();
        if (trimmed.length() >= MIN_CHUNK_CHARS) {
            chunks.add(trimmed);
        }
    }

    /** Tail of the last emitted chunk, cut back to a word boundary. */
    private String overlapFrom(List<String> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }
        String previous = chunks.get(chunks.size() - 1);
        if (previous.length() <= OVERLAP_CHARS) {
            return previous + "\n\n";
        }
        String tail = previous.substring(previous.length() - OVERLAP_CHARS);
        int wordBoundary = tail.indexOf(' ');
        return (wordBoundary > 0 ? tail.substring(wordBoundary + 1) : tail) + "\n\n";
    }

    private List<String> hardSplit(String paragraph) {
        List<String> slices = new ArrayList<>();
        int position = 0;
        while (position < paragraph.length()) {
            int end = Math.min(position + TARGET_CHARS, paragraph.length());
            // Prefer the last sentence end, then the last space, before cutting blind.
            if (end < paragraph.length()) {
                int boundary = lastBoundary(paragraph, position, end);
                if (boundary > position) {
                    end = boundary;
                }
            }
            slices.add(paragraph.substring(position, end).strip());
            // Step back by the overlap so the next slice re-reads the seam.
            position = Math.max(end - OVERLAP_CHARS, position + 1);
            if (end >= paragraph.length()) {
                break;
            }
        }
        return slices;
    }

    private int lastBoundary(String text, int from, int to) {
        for (int i = to - 1; i > from; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                return i + 1;
            }
        }
        int lastSpace = text.lastIndexOf(' ', to - 1);
        return lastSpace > from ? lastSpace : -1;
    }
}
