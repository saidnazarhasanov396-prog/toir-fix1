package com.toir.service.maintenanceembedding;

import java.util.List;

/**
 * Internal contract boundary only. No HTTP adapter exists until the AI team freezes its wire contract.
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
