package com.s.ecoflux.config;

public record ChunkRules(int consuming, int maxPlantCount, double queueFillFactor, IntRange evaluationIntervalDays) {
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
    }

    public int queueCapacity() {
        return (int) Math.ceil(maxPlantCount * queueFillFactor);
    }
}
