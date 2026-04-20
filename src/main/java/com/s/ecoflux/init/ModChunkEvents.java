package com.s.ecoflux.init;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.PlantQueueEntry;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.PlantDefinition;
import com.s.ecoflux.config.SuccessionConfigRegistry;
import com.s.ecoflux.config.SuccessionPathDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

public final class ModChunkEvents {
    private ModChunkEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ModChunkEvents::onChunkLoad);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel)) {
            return;
        }

        ChunkAccess chunk = event.getChunk();
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getCurrentBiome().isPresent()) {
            return;
        }

        initializeChunkData(chunk, chunkData);
    }

    private static void initializeChunkData(ChunkAccess chunk, SuccessionChunkData chunkData) {
        Optional<ResourceKey<Biome>> currentBiomeKey = sampleChunkCenterBiome(chunk);
        if (currentBiomeKey.isEmpty()) {
            EcofluxConstants.LOGGER.warn("Could not resolve center biome for chunk {}", chunk.getPos());
            return;
        }

        ChunkClimateSample climateSample = sampleChunkClimate(chunk, currentBiomeKey.get());
        Optional<SuccessionPathDefinition> matchedPath = SuccessionConfigRegistry.findBestMatch(
                climateSample.biomeKey(),
                climateSample.temperature(),
                climateSample.downfall());

        chunkData.clearRuntimeState();
        chunkData.setCurrentBiome(climateSample.biomeKey());

        if (matchedPath.isPresent()) {
            SuccessionPathDefinition path = matchedPath.get();
            chunkData.setPreviousBiome(toBiomeKey(path.fallbackBiome()));
            chunkData.setTargetBiome(toBiomeKey(path.targetBiome()));
            chunkData.setConsumingValue(path.chunkRules().consuming());
            chunkData.setMaxPlantCount(path.chunkRules().maxPlantCount());
            chunkData.replacePlantQueue(buildInitialQueue(chunk.getPos(), path));
            EcofluxConstants.LOGGER.debug(
                    "Initialized Ecoflux chunk {} with path {} and {} queued plants",
                    chunk.getPos(),
                    path.pathId(),
                    chunkData.getPlantQueue().size());
            return;
        }

        chunkData.setPreviousBiome(null);
        chunkData.setTargetBiome(null);
        chunkData.setConsumingValue(0);
        chunkData.setMaxPlantCount(0);
        chunkData.replacePlantQueue(List.of());
        EcofluxConstants.LOGGER.debug(
                "Initialized Ecoflux chunk {} without a matching succession path for biome {}",
                chunk.getPos(),
                climateSample.biomeKey().location());
    }

    private static Optional<ResourceKey<Biome>> sampleChunkCenterBiome(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int sampleY = sampleSurfaceY(chunk);
        ResourceKey<Biome> biomeKey = chunk.getNoiseBiome(
                        QuartPos.fromBlock(chunkPos.getMiddleBlockX()),
                        QuartPos.fromBlock(sampleY),
                        QuartPos.fromBlock(chunkPos.getMiddleBlockZ()))
                .unwrapKey()
                .orElse(null);
        return Optional.ofNullable(biomeKey);
    }

    private static ChunkClimateSample sampleChunkClimate(ChunkAccess chunk, ResourceKey<Biome> biomeKey) {
        ChunkPos chunkPos = chunk.getPos();
        int sampleY = sampleSurfaceY(chunk);
        BlockPos samplePos = chunkPos.getMiddleBlockPosition(sampleY);
        Biome biome = chunk.getNoiseBiome(
                        QuartPos.fromBlock(samplePos.getX()),
                        QuartPos.fromBlock(samplePos.getY()),
                        QuartPos.fromBlock(samplePos.getZ()))
                .value();
        return new ChunkClimateSample(
                biomeKey,
                biome.getBaseTemperature(),
                biome.getModifiedClimateSettings().downfall());
    }

    private static int sampleSurfaceY(ChunkAccess chunk) {
        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        return Math.max(minY, Math.min(surface, maxY));
    }

    private static @Nullable ResourceKey<Biome> toBiomeKey(@Nullable net.minecraft.resources.ResourceLocation biomeId) {
        return biomeId == null ? null : ResourceKey.create(Registries.BIOME, biomeId);
    }

    private static List<PlantQueueEntry> buildInitialQueue(ChunkPos chunkPos, SuccessionPathDefinition path) {
        int queueCapacity = path.chunkRules().queueCapacity();
        int totalWeight = path.plants().stream().mapToInt(PlantDefinition::weight).sum();
        Random random = new Random(chunkPos.toLong());
        List<PlantQueueEntry> queue = new ArrayList<>(queueCapacity);
        for (int i = 0; i < queueCapacity; i++) {
            PlantDefinition plant = pickWeightedPlant(path.plants(), totalWeight, random);
            queue.add(new PlantQueueEntry(plant.plantId(), plant.pointValue(), plant.weight(), plant.maxAgeTicks()));
        }
        return List.copyOf(queue);
    }

    private static PlantDefinition pickWeightedPlant(List<PlantDefinition> plants, int totalWeight, Random random) {
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (PlantDefinition plant : plants) {
            cursor += plant.weight();
            if (roll < cursor) {
                return plant;
            }
        }
        return plants.get(plants.size() - 1);
    }

    private record ChunkClimateSample(ResourceKey<Biome> biomeKey, double temperature, double downfall) {
    }
}
