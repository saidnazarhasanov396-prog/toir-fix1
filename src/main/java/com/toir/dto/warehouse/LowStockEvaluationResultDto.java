package com.toir.dto.warehouse;

public record LowStockEvaluationResultDto(
        int evaluatedCount,
        int openedCount,
        int updatedCount,
        int resolvedCount,
        int skippedCount
) {
    public LowStockEvaluationResultDto plus(LowStockEvaluationResultDto other) {
        if (other == null) {
            return this;
        }
        return new LowStockEvaluationResultDto(
                evaluatedCount + other.evaluatedCount(),
                openedCount + other.openedCount(),
                updatedCount + other.updatedCount(),
                resolvedCount + other.resolvedCount(),
                skippedCount + other.skippedCount()
        );
    }

    public static LowStockEvaluationResultDto empty() {
        return new LowStockEvaluationResultDto(0, 0, 0, 0, 0);
    }

    public static LowStockEvaluationResultDto evaluatedOpened() {
        return new LowStockEvaluationResultDto(1, 1, 0, 0, 0);
    }

    public static LowStockEvaluationResultDto evaluatedUpdated() {
        return new LowStockEvaluationResultDto(1, 0, 1, 0, 0);
    }

    public static LowStockEvaluationResultDto evaluatedResolved() {
        return new LowStockEvaluationResultDto(1, 0, 0, 1, 0);
    }

    public static LowStockEvaluationResultDto evaluatedHealthy() {
        return new LowStockEvaluationResultDto(1, 0, 0, 0, 0);
    }

    public static LowStockEvaluationResultDto evaluatedSkipped() {
        return new LowStockEvaluationResultDto(1, 0, 0, 0, 1);
    }
}
