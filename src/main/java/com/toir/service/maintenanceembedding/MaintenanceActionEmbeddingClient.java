package com.toir.service.maintenanceembedding;

import java.util.List;

/**
 * Internal contract boundary. The HTTP adapter maps the frozen texts/embeddings wire contract to this model.
 */
public interface MaintenanceActionEmbeddingClient {

    EmbeddingResult embed(EmbeddingInput input);

    enum InputMode {
        DOCUMENT,
        QUERY
    }

    record EmbeddingInput(
            String text,
            InputMode mode,
            String modelName,
            String modelRevision,
            int expectedDimension
    ) {
    }

    record EmbeddingResult(
            List<? extends Number> values,
            String modelName,
            String modelRevision,
            InputMode mode
    ) {
    }
}
