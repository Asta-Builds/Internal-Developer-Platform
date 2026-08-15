package com.idp.rag;

/**
 * Turns text into a vector whose cosine similarity approximates relatedness.
 *
 * <p>Deliberately an interface: Anthropic ships no embeddings API, so the default
 * implementation is local (see {@link HashingEmbeddingModel}). Swapping in a hosted
 * model — Voyage, or whatever the platform team standardises on — is a new bean plus
 * a re-index, with no change to ingestion or retrieval.
 *
 * <p>Implementations must return <em>L2-normalised</em> vectors of a fixed
 * {@link #dimensions()}, so cosine similarity reduces to a dot product.
 */
public interface EmbeddingModel {

    float[] embed(String text);

    int dimensions();

    /** Identifies the model in logs and ingestion reports. */
    String name();
}
