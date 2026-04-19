package com.s.succession;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

final class PrototypeChunkState {
    private static final String PROGRESS_KEY = "progress";
    private static final String LAST_SCORE_KEY = "last_score";
    private static final String LAST_SCAN_TIME_KEY = "last_scan_time";
    private static final String EARLY_MARKERS_KEY = "early_markers";
    private static final String MID_MARKERS_KEY = "mid_markers";
    private static final String COMPLETED_KEY = "completed";
    private static final String LAST_SCAN_REASON_KEY = "last_scan_reason";

    private float progress;
    private float lastScore;
    private long lastScanTime;
    private boolean earlyMarkersPlaced;
    private boolean midMarkersPlaced;
    private boolean completed;
    private String lastScanReason = "not_scanned";

    static PrototypeChunkState fromTag(CompoundTag tag) {
        PrototypeChunkState state = new PrototypeChunkState();
        state.progress = Mth.clamp(tag.getFloat(PROGRESS_KEY), 0.0F, 1.0F);
        state.lastScore = Mth.clamp(tag.getFloat(LAST_SCORE_KEY), 0.0F, 1.0F);
        state.lastScanTime = tag.getLong(LAST_SCAN_TIME_KEY);
        state.earlyMarkersPlaced = tag.getBoolean(EARLY_MARKERS_KEY);
        state.midMarkersPlaced = tag.getBoolean(MID_MARKERS_KEY);
        state.completed = tag.getBoolean(COMPLETED_KEY);
        state.lastScanReason = tag.contains(LAST_SCAN_REASON_KEY) ? tag.getString(LAST_SCAN_REASON_KEY) : "not_scanned";
        return state;
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat(PROGRESS_KEY, progress);
        tag.putFloat(LAST_SCORE_KEY, lastScore);
        tag.putLong(LAST_SCAN_TIME_KEY, lastScanTime);
        tag.putBoolean(EARLY_MARKERS_KEY, earlyMarkersPlaced);
        tag.putBoolean(MID_MARKERS_KEY, midMarkersPlaced);
        tag.putBoolean(COMPLETED_KEY, completed);
        tag.putString(LAST_SCAN_REASON_KEY, lastScanReason);
        return tag;
    }

    float progress() {
        return progress;
    }

    void setProgress(float progress) {
        this.progress = Mth.clamp(progress, 0.0F, 1.0F);
    }

    float lastScore() {
        return lastScore;
    }

    void setLastScore(float lastScore) {
        this.lastScore = Mth.clamp(lastScore, 0.0F, 1.0F);
    }

    long lastScanTime() {
        return lastScanTime;
    }

    void setLastScanTime(long lastScanTime) {
        this.lastScanTime = lastScanTime;
    }

    boolean earlyMarkersPlaced() {
        return earlyMarkersPlaced;
    }

    void setEarlyMarkersPlaced(boolean earlyMarkersPlaced) {
        this.earlyMarkersPlaced = earlyMarkersPlaced;
    }

    boolean midMarkersPlaced() {
        return midMarkersPlaced;
    }

    void setMidMarkersPlaced(boolean midMarkersPlaced) {
        this.midMarkersPlaced = midMarkersPlaced;
    }

    boolean completed() {
        return completed;
    }

    void setCompleted(boolean completed) {
        this.completed = completed;
    }

    String lastScanReason() {
        return lastScanReason;
    }

    void setLastScanReason(String lastScanReason) {
        this.lastScanReason = lastScanReason;
    }

    String stageName() {
        if (completed) {
            return "converted";
        }
        if (progress >= PrototypeSuccessionSystem.MID_STAGE_THRESHOLD) {
            return "late";
        }
        if (progress >= PrototypeSuccessionSystem.EARLY_STAGE_THRESHOLD) {
            return "mid";
        }
        if (progress > 0.0F) {
            return "early";
        }
        return "dormant";
    }
}
