package com.idp.rag;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Local embedding model: a signed hashing vectoriser over word and character n-grams.
 *
 * <p>Chosen because it needs no external service and no API key, so retrieval works
 * in the docker-compose stack out of the box and the ingestion pipeline is
 * deterministic and testable. It captures lexical overlap — including partial and
 * morphological overlap, via character 4-grams, which matter for a bilingual corpus
 * and for code identifiers like {@code srv-payment} or {@code rolloutPercent}.
 *
 * <p><strong>Known limit:</strong> it has no notion of synonymy. "How do I charge a
 * card?" will not match a runbook that only ever says "process a payment" unless the
 * two share tokens. That is the ceiling of any lexical model, and it is the reason
 * {@link EmbeddingModel} is an interface — when the corpus grows past what lexical
 * matching serves, swap in a hosted semantic model and re-index. Retrieval,
 * ingestion, and generation are unaffected by that swap.
 */
@Component
public class HashingEmbeddingModel implements EmbeddingModel {

    /**
     * Wide enough that unrelated features rarely collide at this corpus size, small
     * enough that a full scan stays trivially fast. Changing this invalidates every
     * stored embedding — re-index after.
     */
    private static final int DIMENSIONS = 384;

    /** Below this length a token is punctuation or noise more often than signal. */
    private static final int MIN_TOKEN_LENGTH = 2;

    private static final int CHAR_NGRAM = 4;

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }

        // Raw counts first, so term frequency can be damped before projection —
        // otherwise a word repeated 40 times in a runbook drowns out everything else.
        Map<String, Integer> features = new HashMap<>();
        String[] tokens = tokenize(text);

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            features.merge(token, 1, Integer::sum);

            // Bigrams: "feature flag" should not score the same as "flag feature".
            if (i + 1 < tokens.length) {
                features.merge(token + "_" + tokens[i + 1], 1, Integer::sum);
            }

            // Character n-grams give partial credit across inflections and
            // hyphenated identifiers, where whole-token matching returns nothing.
            for (int start = 0; start + CHAR_NGRAM <= token.length(); start++) {
                features.merge("#" + token.substring(start, start + CHAR_NGRAM), 1, Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> entry : features.entrySet()) {
            int hash = hash(entry.getKey());
            int index = Math.floorMod(hash, DIMENSIONS);
            // Signed hashing: the sign bit makes collisions cancel out on average
            // instead of always inflating the bucket they land in.
            float sign = hash >= 0 ? 1f : -1f;
            // Sublinear term frequency — the 40th repeat carries far less than the 2nd.
            float weight = (float) (1.0 + Math.log(entry.getValue()));
            vector[index] += sign * weight;
        }

        return normalize(vector);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public String name() {
        return "local-hashing-v1";
    }

    private String[] tokenize(String text) {
        String[] raw = text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
        int kept = 0;
        for (String token : raw) {
            if (token.length() >= MIN_TOKEN_LENGTH) {
                // Fold platform vocabulary onto one canonical form so a French
                // question can reach an English runbook. Applied here — the single
                // path both ingestion and querying take — so the index and the query
                // can never disagree about a term's canonical form.
                raw[kept++] = DomainLexicon.canonical(token);
            }
        }
        String[] tokens = new String[kept];
        System.arraycopy(raw, 0, tokens, 0, kept);
        return tokens;
    }

    /**
     * FNV-1a: cheap, well-distributed, and — unlike {@link String#hashCode()} —
     * stable across JVM versions, which matters because these indices are persisted.
     */
    private int hash(String feature) {
        int hash = 0x811c9dc5;
        for (byte b : feature.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x01000193;
        }
        return hash;
    }

    /**
     * L2-normalises in place so cosine similarity is a plain dot product, and so a
     * long document cannot outrank a short one on magnitude alone.
     */
    private float[] normalize(float[] vector) {
        double sumOfSquares = 0.0;
        for (float value : vector) {
            sumOfSquares += (double) value * value;
        }
        if (sumOfSquares == 0.0) {
            return vector;
        }
        float norm = (float) Math.sqrt(sumOfSquares);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }
}
