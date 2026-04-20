package com.s.ecoflux.config;

public record IntRange(int min, int max) {
    public IntRange {
        if (max < min) {
            throw new IllegalArgumentException("Range max must be greater than or equal to min");
        }
    }

    public boolean contains(int value) {
        return value >= min && value <= max;
    }
}
