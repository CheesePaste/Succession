package com.s.ecoflux.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.resources.ResourceKey;

public final class SuccessionConfigRegistry {
    private static final Comparator<SuccessionPathDefinition> PATH_ORDER = Comparator
            .comparingInt(SuccessionPathDefinition::priority)
            .reversed()
            .thenComparing(path -> path.pathId().toString());

    private static volatile List<SuccessionPathDefinition> allPaths = List.of();
    private static volatile Map<ResourceLocation, SuccessionPathDefinition> pathsById = Map.of();
    private static volatile Map<ResourceLocation, List<SuccessionPathDefinition>> pathsBySourceBiome = Map.of();

    private SuccessionConfigRegistry() {
    }

    public static synchronized void replace(Collection<SuccessionPathDefinition> definitions) {
        List<SuccessionPathDefinition> sortedPaths = new ArrayList<>(definitions);
        sortedPaths.sort(PATH_ORDER);

        Map<ResourceLocation, SuccessionPathDefinition> byId = new LinkedHashMap<>();
        Map<ResourceLocation, List<SuccessionPathDefinition>> bySourceBiome = new LinkedHashMap<>();
        for (SuccessionPathDefinition definition : sortedPaths) {
            byId.put(definition.pathId(), definition);
            for (ResourceLocation sourceBiome : definition.sourceBiomes()) {
                bySourceBiome.computeIfAbsent(sourceBiome, ignored -> new ArrayList<>()).add(definition);
            }
        }

        Map<ResourceLocation, List<SuccessionPathDefinition>> immutableBySource = new LinkedHashMap<>();
        bySourceBiome.forEach((key, value) -> immutableBySource.put(key, List.copyOf(value)));

        allPaths = List.copyOf(sortedPaths);
        pathsById = Map.copyOf(byId);
        pathsBySourceBiome = Map.copyOf(immutableBySource);
    }

    public static List<SuccessionPathDefinition> getAllPaths() {
        return allPaths;
    }

    public static Optional<SuccessionPathDefinition> getPath(ResourceLocation pathId) {
        return Optional.ofNullable(pathsById.get(pathId));
    }

    public static List<SuccessionPathDefinition> findMatches(ResourceLocation biomeId, double temperature, double downfall) {
        return pathsBySourceBiome.getOrDefault(biomeId, List.of()).stream()
                .filter(path -> path.matches(biomeId, temperature, downfall))
                .toList();
    }

    public static List<SuccessionPathDefinition> findMatches(ResourceKey<Biome> biomeKey, double temperature, double downfall) {
        return findMatches(biomeKey.location(), temperature, downfall);
    }

    public static Optional<SuccessionPathDefinition> findBestMatch(ResourceLocation biomeId, double temperature, double downfall) {
        return findMatches(biomeId, temperature, downfall).stream().findFirst();
    }

    public static Optional<SuccessionPathDefinition> findBestMatch(ResourceKey<Biome> biomeKey, double temperature, double downfall) {
        return findBestMatch(biomeKey.location(), temperature, downfall);
    }
}
