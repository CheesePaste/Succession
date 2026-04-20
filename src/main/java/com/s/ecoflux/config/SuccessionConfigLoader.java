package com.s.ecoflux.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import com.s.ecoflux.EcofluxConstants;

public final class SuccessionConfigLoader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "succession_paths";
    public static final SuccessionConfigLoader INSTANCE = new SuccessionConfigLoader();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SuccessionConfigLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonByFileId, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<SuccessionPathDefinition> parsedPaths = new ArrayList<>();
        Map<ResourceLocation, ResourceLocation> fileIdByPathId = new LinkedHashMap<>();

        jsonByFileId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> parseFile(entry, parsedPaths, fileIdByPathId));

        SuccessionConfigRegistry.replace(parsedPaths);
        EcofluxConstants.LOGGER.info("Loaded {} Ecoflux succession path definitions", parsedPaths.size());
        logSourceBiomeSummary(parsedPaths);
    }

    private void parseFile(
            Map.Entry<ResourceLocation, JsonElement> entry,
            List<SuccessionPathDefinition> parsedPaths,
            Map<ResourceLocation, ResourceLocation> fileIdByPathId) {
        ResourceLocation fileId = entry.getKey();
        try {
            JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), fileId.toString());
            SuccessionPathDefinition definition = parseDefinition(fileId, root);

            ResourceLocation existingFile = fileIdByPathId.putIfAbsent(definition.pathId(), fileId);
            if (existingFile != null) {
                throw new JsonParseException("Duplicate path_id " + definition.pathId() + " also declared in " + existingFile);
            }

            parsedPaths.add(definition);
        } catch (RuntimeException exception) {
            EcofluxConstants.LOGGER.error("Failed to parse Ecoflux succession path {}", fileId, exception);
        }
    }

    private SuccessionPathDefinition parseDefinition(ResourceLocation fileId, JsonObject root) {
        int schemaVersion = GsonHelper.getAsInt(root, "schema_version");
        if (schemaVersion != 1) {
            throw new JsonParseException("Unsupported schema_version " + schemaVersion + " in " + fileId);
        }

        return new SuccessionPathDefinition(
                parseId(root, "path_id"),
                GsonHelper.getAsInt(root, "priority", 0),
                parseIdList(GsonHelper.getAsJsonArray(root, "source_biomes"), "source_biomes"),
                parseId(root, "target_biome"),
                parseOptionalId(root, "fallback_biome"),
                parseClimate(GsonHelper.getAsJsonObject(root, "climate")),
                parseChunkRules(GsonHelper.getAsJsonObject(root, "chunk_rules")),
                parsePlants(GsonHelper.getAsJsonArray(root, "plants")));
    }

    private ClimateCondition parseClimate(JsonObject climateObject) {
        return new ClimateCondition(
                parseFloatRange(GsonHelper.getAsJsonObject(climateObject, "temperature"), "temperature"),
                parseFloatRange(GsonHelper.getAsJsonObject(climateObject, "downfall"), "downfall"));
    }

    private ChunkRules parseChunkRules(JsonObject chunkRulesObject) {
        return new ChunkRules(
                GsonHelper.getAsInt(chunkRulesObject, "consuming"),
                GsonHelper.getAsInt(chunkRulesObject, "max_plant_count"),
                GsonHelper.getAsDouble(chunkRulesObject, "queue_fill_factor", 2.0D),
                parseIntRange(GsonHelper.getAsJsonObject(chunkRulesObject, "evaluation_interval_days"), "evaluation_interval_days"));
    }

    private List<PlantDefinition> parsePlants(JsonArray plantArray) {
        List<PlantDefinition> plants = new ArrayList<>();
        for (JsonElement element : plantArray) {
            JsonObject plantObject = GsonHelper.convertToJsonObject(element, "plant");
            plants.add(new PlantDefinition(
                    parseId(plantObject, "plant_id"),
                    GsonHelper.getAsString(plantObject, "category"),
                    GsonHelper.getAsInt(plantObject, "weight"),
                    GsonHelper.getAsInt(plantObject, "point_value"),
                    GsonHelper.getAsLong(plantObject, "max_age_ticks"),
                    parseSpawnRules(GsonHelper.getAsJsonObject(plantObject, "spawn_rules"))));
        }
        return plants;
    }

    private PlantSpawnRules parseSpawnRules(JsonObject spawnRulesObject) {
        return new PlantSpawnRules(
                GsonHelper.getAsString(spawnRulesObject, "placement"),
                GsonHelper.getAsBoolean(spawnRulesObject, "require_sky", true),
                GsonHelper.getAsInt(spawnRulesObject, "max_local_density"),
                parseIdList(GsonHelper.getAsJsonArray(spawnRulesObject, "allowed_base_blocks"), "allowed_base_blocks"));
    }

    private FloatRange parseFloatRange(JsonObject rangeObject, String fieldName) {
        return new FloatRange(
                GsonHelper.getAsDouble(rangeObject, "min"),
                GsonHelper.getAsDouble(rangeObject, "max"));
    }

    private IntRange parseIntRange(JsonObject rangeObject, String fieldName) {
        return new IntRange(
                GsonHelper.getAsInt(rangeObject, "min"),
                GsonHelper.getAsInt(rangeObject, "max"));
    }

    private List<ResourceLocation> parseIdList(JsonArray jsonArray, String fieldName) {
        List<ResourceLocation> values = new ArrayList<>();
        for (JsonElement element : jsonArray) {
            values.add(parseId(element, fieldName));
        }
        if (values.isEmpty()) {
            throw new JsonParseException(fieldName + " cannot be empty");
        }
        return values;
    }

    private ResourceLocation parseId(JsonObject json, String memberName) {
        return parseId(GsonHelper.getAsString(json, memberName), memberName);
    }

    private ResourceLocation parseId(JsonElement element, String fieldName) {
        return parseId(GsonHelper.convertToString(element, fieldName), fieldName);
    }

    private ResourceLocation parseId(String value, String fieldName) {
        try {
            return ResourceLocation.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException("Invalid resource location for " + fieldName + ": " + value, exception);
        }
    }

    private @Nullable ResourceLocation parseOptionalId(JsonObject json, String memberName) {
        String value = GsonHelper.getAsString(json, memberName, "");
        return value.isBlank() ? null : parseId(value, memberName);
    }

    private void logSourceBiomeSummary(List<SuccessionPathDefinition> parsedPaths) {
        String summary = parsedPaths.stream()
                .flatMap(path -> path.sourceBiomes().stream())
                .collect(Collectors.groupingBy(
                        ResourceLocation::toString,
                        LinkedHashMap::new,
                        Collectors.counting()))
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));

        EcofluxConstants.LOGGER.info(
                "Ecoflux succession source summary: {}",
                summary.isBlank() ? "no source biomes loaded" : summary);
    }
}
