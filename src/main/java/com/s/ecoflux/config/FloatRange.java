package com.s.ecoflux.config;

public record FloatRange(double min, double max) {
    public FloatRange {
        if (max < min) {
            throw new IllegalArgumentException("Range max must be greater than or equal to min");
        }
    }

    public boolean contains(double value) {
        return value >= min && value <= max;
    }
}
