package com.idp.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The properties retrieval depends on: normalised output, stable hashing, and a
 * ranking that actually tracks relatedness.
 */
class HashingEmbeddingModelTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel();

    private double cosine(String a, String b) {
        float[] va = model.embed(a);
        float[] vb = model.embed(b);
        double sum = 0.0;
        for (int i = 0; i < va.length; i++) {
            sum += (double) va[i] * vb[i];
        }
        return sum;
    }

    @Test
    @DisplayName("produces L2-normalised vectors so cosine reduces to a dot product")
    void producesUnitVectors() {
        float[] vector = model.embed("Payment Gateway handles card charges and refunds");

        double magnitude = 0.0;
        for (float value : vector) {
            magnitude += (double) value * value;
        }

        assertThat(vector).hasSize(model.dimensions());
        assertThat(Math.sqrt(magnitude)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("is deterministic — the same text always embeds identically")
    void isDeterministic() {
        // Embeddings are persisted, so instability would silently corrupt the index.
        assertThat(model.embed("canary rollout")).isEqualTo(model.embed("canary rollout"));
    }

    @Test
    @DisplayName("ranks related text above unrelated text")
    void ranksRelatedTextHigher() {
        String document = "The Payment Gateway Service handles credit card charges, "
                + "refunds and 3DS verification via POST /api/v1/payments/charge.";

        double related = cosine("How do I charge a credit card?", document);
        double unrelated = cosine("Which Grafana dashboard shows pod memory?", document);

        assertThat(related).isGreaterThan(unrelated);
    }

    @Test
    @DisplayName("matches on identifiers that whole-word tokenisation would miss")
    void matchesPartialIdentifiers() {
        // Character n-grams are what let 'rollout' reach 'rolloutPercent'.
        assertThat(cosine("rollout", "rolloutPercent controls the canary")).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("returns a zero vector for empty input rather than failing")
    void handlesEmptyInput() {
        assertThat(model.embed("")).containsOnly(0f);
        assertThat(model.embed(null)).containsOnly(0f);
    }
}
