package com.s.ecoflux.plant;

import java.util.Optional;

public record VegetationObservation(
        boolean present,
        VegetationLifecycleStage stage,
        int currentPointValue,
        boolean mature,
        boolean aging,
        Optional<VegetationTransformation> transformation,
        String detail) {
    public static VegetationObservation absent(String detail) {
        return new VegetationObservation(
                false,
                VegetationLifecycleStage.DEAD,
                0,
                false,
                false,
                Optional.empty(),
                detail);
    }
}
