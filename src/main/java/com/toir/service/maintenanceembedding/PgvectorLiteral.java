package com.toir.service.maintenanceembedding;

import java.util.List;

final class PgvectorLiteral {

    private PgvectorLiteral() {
    }

    static String validated(List<? extends Number> values, int dimension) {
        double[] vector = EmbeddingVectorValidator.requireFiniteDimension(values, dimension);
        StringBuilder literal = new StringBuilder(vector.length * 12).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(Double.toString(vector[index]));
        }
        return literal.append(']').toString();
    }
}
