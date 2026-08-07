package com.toir.service.maintenanceembedding;

import java.util.List;

public final class PgvectorQueryLiteral {

    private PgvectorQueryLiteral() {
    }

    public static String validated(List<? extends Number> values, int dimension) {
        return PgvectorLiteral.validated(values, dimension);
    }
}
