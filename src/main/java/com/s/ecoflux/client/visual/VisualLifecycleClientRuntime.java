package com.s.ecoflux.client.visual;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;

public final class VisualLifecycleClientRuntime {
    public static final VisualLifecycleClientRuntime INSTANCE = new VisualLifecycleClientRuntime();

    private final Map<String, VisualLifecycleInstance> trackedInstances = new ConcurrentHashMap<>();
    private final Map<Long, VisualLifecycleInstance> trackedInstancesByPos = new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> manualWorldRenderPass = ThreadLocal.withInitial(() -> false);

    private VisualLifecycleClientRuntime() {
    }

    public String start(BlockPos pos) {
        ClientLevel level = currentLevel();
        if (level == null) {
            return "Visual lifecycle start failed: no client level is loaded.";
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return "Visual lifecycle start skipped: " + pos + " is air.";
        }
        Optional<VisualLifecycleAdapter> adapter = VisualLifecycleRegistry.INSTANCE.find(state);
        if (adapter.isEmpty()) {
            return "Visual lifecycle start skipped: no adapter could be resolved at " + pos + ".";
        }

        VisualLifecycleInstance instance = new VisualLifecycleInstance(
                adapter.get().typeId(),
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                pos.immutable(),
                level.getGameTime(),
                adapter.get().createProfile(state),
                null,
                null,
                VisualLifecycleTrackingSource.MANUAL);
        trackedInstances.put(key(level, pos), instance);
        trackedInstancesByPos.put(pos.asLong(), instance);
        markDirty(pos);
        return "Visual lifecycle started at " + pos + " for " + instance.blockId() + ".";
    }

    public String stop(BlockPos pos) {
        ClientLevel level = currentLevel();
        if (level == null) {
            return "Visual lifecycle stop failed: no client level is loaded.";
        }

        VisualLifecycleInstance removed = trackedInstances.remove(key(level, pos));
        trackedInstancesByPos.remove(pos.asLong());
        markDirty(pos);
        return removed == null
                ? "Visual lifecycle stop skipped: nothing tracked at " + pos + "."
                : "Visual lifecycle stopped at " + pos + ".";
    }

    public String forceStage(BlockPos pos, VisualLifecycleStage stage) {
        ClientLevel level = currentLevel();
        if (level == null) {
            return "Visual lifecycle force-stage failed: no client level is loaded.";
        }

        String key = key(level, pos);
        VisualLifecycleInstance instance = trackedInstances.get(key);
        if (instance == null) {
            return "Visual lifecycle force-stage skipped: nothing tracked at " + pos + ".";
        }

        VisualLifecycleInstance updated = instance.withForcedStage(stage);
        trackedInstances.put(key, updated);
        trackedInstancesByPos.put(pos.asLong(), updated);
        markDirty(pos);
        return "Visual lifecycle at " + pos + " forced to stage " + stage + ".";
    }

    public String inspect(BlockPos pos) {
        ClientLevel level = currentLevel();
        if (level == null) {
            return "Visual lifecycle inspect failed: no client level is loaded.";
        }

        BlockState state = level.getBlockState(pos);
        Optional<VisualLifecycleAdapter> adapter = VisualLifecycleRegistry.INSTANCE.find(state);
        VisualLifecycleInstance instance = trackedInstances.get(key(level, pos));
        String adapterText = adapter.map(found -> found.typeId().toString()).orElse("none");
        if (instance == null) {
            return "Visual lifecycle inspect " + pos + ": block="
                    + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + " adapter=" + adapterText
                    + " tracked=false";
        }

        int baseColor = defaultColor(level, pos, state);
        VisualLifecycleAdapter resolvedAdapter = adapter.orElse(GrassVisualLifecycleAdapter.INSTANCE);
        VisualLifecycleRenderState renderState = resolvedAdapter.resolveState(instance, level.getGameTime(), baseColor);
        return "Visual lifecycle inspect " + pos
                + ": block=" + instance.blockId()
                + " adapter=" + instance.adapterId()
                + " source=" + instance.source()
                + " stage=" + renderState.stage()
                + " progress=" + String.format("%.2f", renderState.stageProgress())
                + " scale=" + String.format("%.2f", renderState.scale())
                + " tint=0x" + Integer.toHexString(renderState.tintedColor());
    }

    public String list() {
        ClientLevel level = currentLevel();
        if (level == null) {
            return "Visual lifecycle list failed: no client level is loaded.";
        }
        if (trackedInstances.isEmpty()) {
            return "Visual lifecycle list: no tracked plants.";
        }

        String dimensionPrefix = level.dimension().location() + "|";
        String joined = trackedInstances.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(dimensionPrefix))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(instance -> instance.pos().asLong()))
                .limit(8)
                .map(instance -> instance.pos() + ":" + instance.blockId() + ":" + instance.source())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        return "Visual lifecycle tracked=" + trackedInstances.size() + " [" + joined + "]";
    }

    public String clear() {
        trackedInstances.clear();
        trackedInstancesByPos.clear();
        refreshAll();
        return "Visual lifecycle cleared.";
    }

    public List<VisualLifecycleInstance> trackedInCurrentLevel() {
        ClientLevel level = currentLevel();
        if (level == null || trackedInstances.isEmpty()) {
            return List.of();
        }

        String dimensionPrefix = level.dimension().location() + "|";
        return trackedInstances.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(dimensionPrefix))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(instance -> instance.pos().asLong()))
                .toList();
    }

    public void refreshAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    public void tick() {
        ClientLevel level = currentLevel();
        if (level == null) {
            trackedInstances.clear();
            trackedInstancesByPos.clear();
            return;
        }

        if (level.getGameTime() % 5L != 0L) {
            return;
        }

        trackedInstances.entrySet().removeIf(entry -> {
            VisualLifecycleInstance instance = entry.getValue();
            BlockState state = level.getBlockState(instance.pos());
            Optional<VisualLifecycleAdapter> adapter = VisualLifecycleRegistry.INSTANCE.find(state);
            boolean remove = adapter.isEmpty() || !adapter.get().typeId().equals(instance.adapterId());
            if (remove) {
                trackedInstancesByPos.remove(instance.pos().asLong());
                markDirty(instance.pos());
            } else {
                trackedInstancesByPos.put(instance.pos().asLong(), instance);
                markDirty(instance.pos());
            }
            return remove;
        });
    }

    public VisualLifecycleRenderState getRenderState(BlockPos pos, BlockState state) {
        VisualLifecycleInstance instance = trackedInstancesByPos.get(pos.asLong());
        if (instance == null) {
            return null;
        }

        Optional<VisualLifecycleAdapter> adapter = VisualLifecycleRegistry.INSTANCE.find(state);
        if (adapter.isEmpty() || !adapter.get().typeId().equals(instance.adapterId())) {
            return null;
        }

        ClientLevel level = currentLevel();
        if (level == null) {
            return null;
        }

        return adapter.get().resolveState(instance, level.getGameTime(), defaultColor(level, pos, state));
    }

    public int adjustTint(BlockState state, BlockPos pos, int baseColor) {
        VisualLifecycleRenderState renderState = getRenderState(pos, state);
        return renderState == null ? baseColor : renderState.tintedColor();
    }

    public boolean isTrackedForCurrentVisualPass(BlockPos pos, BlockState state) {
        return getRenderState(pos, state) != null;
    }

    public void beginManualWorldRenderPass() {
        manualWorldRenderPass.set(true);
    }

    public void endManualWorldRenderPass() {
        manualWorldRenderPass.set(false);
    }

    public boolean isManualWorldRenderPass() {
        return manualWorldRenderPass.get();
    }

    private static int defaultColor(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)) {
            return BiomeColors.getAverageGrassColor(level, pos);
        }
        if (state.is(BlockTags.SAPLINGS)) {
            return BiomeColors.getAverageFoliageColor(level, pos);
        }
        if (state.is(Blocks.DEAD_BUSH)) {
            return 0xA78F63;
        }
        return 0xFFFFFF;
    }

    private static void markDirty(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.setSectionDirty(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getY()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
        }
    }

    public void syncVegetationChunk(ResourceLocation dimensionId, ChunkPos chunkPos, List<com.s.ecoflux.network.VegetationVisualSyncEntry> entries) {
        ClientLevel level = currentLevel();
        if (level == null || !level.dimension().location().equals(dimensionId)) {
            return;
        }

        Set<Long> incomingPositions = new HashSet<>(entries.size());
        for (com.s.ecoflux.network.VegetationVisualSyncEntry entry : entries) {
            incomingPositions.add(entry.pos().asLong());
        }

        trackedInstances.entrySet().removeIf(entry -> {
            VisualLifecycleInstance instance = entry.getValue();
            if (instance.source() != VisualLifecycleTrackingSource.VEGETATION_SYSTEM) {
                return false;
            }
            if (!entry.getKey().startsWith(dimensionId + "|")) {
                return false;
            }
            if (!sameChunk(instance.pos(), chunkPos) || incomingPositions.contains(instance.pos().asLong())) {
                return false;
            }

            trackedInstancesByPos.remove(instance.pos().asLong());
            markDirty(instance.pos());
            return true;
        });

        for (com.s.ecoflux.network.VegetationVisualSyncEntry entry : entries) {
            BlockState state = level.getBlockState(entry.pos());
            if (state.isAir()) {
                continue;
            }

            Optional<VisualLifecycleAdapter> adapter = VisualLifecycleRegistry.INSTANCE.find(state);
            if (adapter.isEmpty()) {
                continue;
            }

            VisualLifecycleInstance instance = new VisualLifecycleInstance(
                    adapter.get().typeId(),
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    entry.pos().immutable(),
                    level.getGameTime(),
                    adapter.get().createProfile(state),
                    null,
                    new VisualLifecycleExternalState(mapVegetationStage(entry.stage()), entry.stageProgress()),
                    VisualLifecycleTrackingSource.VEGETATION_SYSTEM);
            trackedInstances.put(key(level, entry.pos()), instance);
            trackedInstancesByPos.put(entry.pos().asLong(), instance);
            markDirty(entry.pos());
        }
    }

    private static ClientLevel currentLevel() {
        return Minecraft.getInstance().level;
    }

    private static VisualLifecycleStage mapVegetationStage(com.s.ecoflux.plant.VegetationLifecycleStage stage) {
        return switch (stage) {
            case BORN, JUVENILE -> VisualLifecycleStage.BORN;
            case GROWING -> VisualLifecycleStage.GROWING;
            case MATURE, TRANSFORMED -> VisualLifecycleStage.MATURE;
            case AGING, DEAD -> VisualLifecycleStage.AGING;
        };
    }

    private static boolean sameChunk(BlockPos pos, ChunkPos chunkPos) {
        return SectionPos.blockToSectionCoord(pos.getX()) == chunkPos.x
                && SectionPos.blockToSectionCoord(pos.getZ()) == chunkPos.z;
    }

    private static String key(ClientLevel level, BlockPos pos) {
        ResourceLocation dimensionId = level.dimension().location();
        return dimensionId + "|" + pos.asLong();
    }
}
