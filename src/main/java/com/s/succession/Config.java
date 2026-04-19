package com.s.succession;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_PROTOTYPE_PATH = BUILDER
            .comment("Enable the prototype chunk-level plains to forest succession path.")
            .define("enablePrototypePath", true);

    public static final ModConfigSpec.IntValue PROTOTYPE_SCAN_INTERVAL_TICKS = BUILDER
            .comment("How often the prototype system re-evaluates the player's current chunk.")
            .defineInRange("prototypeScanIntervalTicks", 200, 20, 24000);

    public static final ModConfigSpec.DoubleValue PROTOTYPE_PROGRESS_PER_SCAN = BUILDER
            .comment("Base progress added to an eligible chunk each scan before the vegetation score multiplier.")
            .defineInRange("prototypeProgressPerScan", 0.22D, 0.01D, 1.0D);

    public static final ModConfigSpec.DoubleValue PROTOTYPE_DECAY_PER_SCAN = BUILDER
            .comment("Progress removed from a tracked chunk when it no longer matches the plains to forest prototype.")
            .defineInRange("prototypeDecayPerScan", 0.05D, 0.0D, 1.0D);

    public static final ModConfigSpec.BooleanValue ENABLE_VISUAL_MARKERS = BUILDER
            .comment("Place randomized grass/sapling/tree markers so the prototype path is visible in-world.")
            .define("enableVisualMarkers", true);

    public static final ModConfigSpec.BooleanValue ENABLE_BIOME_SWAP = BUILDER
            .comment("Use the vanilla fillbiome command to convert a completed prototype chunk from plains to forest.")
            .define("enableBiomeSwap", true);

    public static final ModConfigSpec.BooleanValue VERBOSE_LOGGING = BUILDER
            .comment("Log prototype scans, stage transitions, and biome swaps.")
            .define("verboseLogging", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
