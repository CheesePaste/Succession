package com.s.ecoflux.config;

public record ChunkRules(
        int consuming,
        int maxPlantCount,
        double queueFillFactor,
        IntRange evaluationIntervalDays,
        int processingIntervalTicks,
        int evaluationIntervalTicks,
        double positiveProgressStep,
        double negativeProgressStep) {
    public ChunkRules {
        if (consuming < 0) {
            throw new IllegalArgumentException("consuming must be non-negative");
        }
        if (maxPlantCount <= 0) {
            throw new IllegalArgumentException("maxPlantCount must be positive");
        }
        if (queueFillFactor < 1.0D) {
            throw new IllegalArgumentException("queueFillFactor must be at least 1.0");
        }
        if (evaluationIntervalDays == null) {
            throw new IllegalArgumentException("evaluationIntervalDays cannot be null");
        }
        if (processingIntervalTicks <= 0) {
            throw new IllegalArgumentException("processingIntervalTicks must be positive");
        }
        if (evaluationIntervalTicks < 0) {
            throw new IllegalArgumentException("evaluationIntervalTicks must be non-negative");
        }
        if (positiveProgressStep <= 0.0D) {
            throw new IllegalArgumentException("positiveProgressStep must be positive");
        }
        if (negativeProgressStep <= 0.0D) {
            throw new IllegalArgumentException("negativeProgressStep must be positive");
        }
    }

    public int queueCapacity() {
        return (int) Math.ceil(maxPlantCount * queueFillFactor);
    }

    public int resolvedEvaluationIntervalTicks(int prototypeDayTicks) {
        if (evaluationIntervalTicks > 0) {
            return evaluationIntervalTicks;
        }
        return Math.max(1, evaluationIntervalDays.min()) * prototypeDayTicks;
    }
}
