package com.s.ecoflux.config;

public record ClimateCondition(FloatRange temperature, FloatRange downfall) {
    public ClimateCondition {
        if (temperature == null) {
            throw new IllegalArgumentException("temperature range cannot be null");
        }
        if (downfall == null) {
            throw new IllegalArgumentException("downfall range cannot be null");
        }
    }

    public boolean matches(double temperatureValue, double downfallValue) {
        return temperature.contains(temperatureValue) && downfall.contains(downfallValue);
    }
}
