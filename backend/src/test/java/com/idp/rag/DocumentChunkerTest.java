package com.idp.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    @DisplayName("keeps a short document as a single chunk")
    void keepsShortDocumentWhole() {
        String text = "The Notification Dispatcher sends SMS, email and WhatsApp alerts "
                + "with fallback to Twilio and SendGrid.";

        assertThat(chunker.chunk(text)).containsExactly(text);
    }

    @Test
    @DisplayName("never emits a chunk far over the target size")
    void respectsTargetSize() {
        String paragraph = "Runbook step describing the failover procedure in detail. ".repeat(80);

        List<String> chunks = chunker.chunk(paragraph);

        assertThat(chunks).isNotEmpty();
        // A boundary search can overshoot slightly; a hard multiple would be a bug.
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.length()).isLessThanOrEqualTo(DocumentChunker.TARGET_CHARS));
    }

    @Test
    @DisplayName("carries overlap across a boundary so split facts stay retrievable")
    void carriesOverlapBetweenChunks() {
        String text = ("Paragraph about the payment settlement window. ".repeat(12) + "\n\n")
                + ("Paragraph about refund reconciliation and chargebacks. ".repeat(12));

        List<String> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        // The tail of chunk N must reappear at the head of chunk N+1.
        String tailOfFirst = chunks.get(0).substring(chunks.get(0).length() - 40);
        assertThat(chunks.get(1)).contains(tailOfFirst.strip().split("\\s+")[0]);
    }

    @Test
    @DisplayName("drops headings and stray lines that carry no retrievable content")
    void dropsInsubstantialFragments() {
        List<String> chunks = chunker.chunk("# Title\n\nOK\n\n" + "Real content that is long enough to matter as a passage. ".repeat(3));

        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.length()).isGreaterThanOrEqualTo(DocumentChunker.MIN_CHUNK_CHARS));
    }

    @Test
    @DisplayName("indexes a document shorter than the minimum rather than dropping it")
    void keepsVeryShortDocument() {
        assertThat(chunker.chunk("Short note.")).containsExactly("Short note.");
    }

    @Test
    @DisplayName("returns nothing for blank input")
    void handlesBlankInput() {
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("   ")).isEmpty();
    }
}
