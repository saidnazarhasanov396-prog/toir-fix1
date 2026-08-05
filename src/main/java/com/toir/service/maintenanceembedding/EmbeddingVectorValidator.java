package com.toir.service.maintenanceembedding;

import java.util.List;

public final class EmbeddingVectorValidator {

    private EmbeddingVectorValidator() {
    }

    public static double[] requireFiniteDimension(List<? extends Number> values, int expectedDimension) {
        if (values == null || values.size() != expectedDimension) {
            throw new InvalidEmbeddingException("Embedding dimension does not match the configured dimension");
        }
        double[] validated = new double[expectedDimension];
        for (int index = 0; index < expectedDimension; index++) {
            Number value = values.get(index);
            if (value == null) {
                throw new InvalidEmbeddingException("Embedding contains a null value");
            }
            double numeric = value.doubleValue();
            if (!Double.isFinite(numeric)) {
                throw new InvalidEmbeddingException("Embedding contains a non-finite value");
            }
            validated[index] = numeric;
        }
        return validated;
    }

    public static final class InvalidEmbeddingException extends IllegalArgumentException {
        public InvalidEmbeddingException(String message) {
            super(message);
        }
    }
}
