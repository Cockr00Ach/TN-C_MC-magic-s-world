package com.tnc.tnc.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** Durable checkpoint for island generation and the paired portal coordinates. */
public final class SkyIslandSavedData extends SavedData {
    static final String FILE_ID = "tnc_sky_island_v5";

    enum Phase {
        IDLE,
        SURVEY,
        BUILDING,
        FINALIZING,
        COMPLETE,
        ERROR
    }

    int version;
    Phase phase = Phase.IDLE;
    int nextPiece;
    boolean layoutReady;

    int originX;
    int originY;
    int originZ;
    int centerX;
    int centerY;
    int centerZ;
    int arrivalX;
    int arrivalY;
    int arrivalZ;

    int groundPortalX;
    int groundPortalY;
    int groundPortalZ;

    int surveyCandidate;
    int surveySample;
    int surveyScore;
    int bestSurveyScore = Integer.MAX_VALUE;
    int bestOriginX;
    int bestOriginZ;

    long waitUntilTick;
    final List<Long> activeChunks = new ArrayList<>();
    String lastError = "";

    static SkyIslandSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                SkyIslandSavedData::load,
                SkyIslandSavedData::new,
                FILE_ID
        );
    }

    static SkyIslandSavedData load(CompoundTag tag) {
        SkyIslandSavedData data = new SkyIslandSavedData();
        data.version = tag.getInt("Version");
        try {
            data.phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException ignored) {
            data.phase = Phase.IDLE;
        }
        data.nextPiece = Math.max(0, tag.getInt("NextPiece"));
        data.layoutReady = tag.getBoolean("LayoutReady");

        data.originX = tag.getInt("OriginX");
        data.originY = tag.getInt("OriginY");
        data.originZ = tag.getInt("OriginZ");
        data.centerX = tag.getInt("CenterX");
        data.centerY = tag.getInt("CenterY");
        data.centerZ = tag.getInt("CenterZ");
        data.arrivalX = tag.getInt("ArrivalX");
        data.arrivalY = tag.getInt("ArrivalY");
        data.arrivalZ = tag.getInt("ArrivalZ");
        data.groundPortalX = tag.getInt("GroundPortalX");
        data.groundPortalY = tag.getInt("GroundPortalY");
        data.groundPortalZ = tag.getInt("GroundPortalZ");

        data.surveyCandidate = Math.max(0, tag.getInt("SurveyCandidate"));
        data.surveySample = Math.max(0, tag.getInt("SurveySample"));
        data.surveyScore = Math.max(0, tag.getInt("SurveyScore"));
        data.bestSurveyScore = tag.contains("BestSurveyScore")
                ? tag.getInt("BestSurveyScore") : Integer.MAX_VALUE;
        data.bestOriginX = tag.getInt("BestOriginX");
        data.bestOriginZ = tag.getInt("BestOriginZ");
        data.waitUntilTick = Math.max(0L, tag.getLong("WaitUntilTick"));
        for (long packed : tag.getLongArray("ActiveChunks")) {
            data.activeChunks.add(packed);
        }
        data.lastError = tag.getString("LastError");
        return data;
    }

    void resetFor(SkyIslandManifest manifest) {
        version = manifest.version();
        phase = Phase.SURVEY;
        nextPiece = 0;
        layoutReady = false;
        surveyCandidate = 0;
        surveySample = 0;
        surveyScore = 0;
        bestSurveyScore = Integer.MAX_VALUE;
        bestOriginX = 0;
        bestOriginZ = 0;
        waitUntilTick = 0L;
        activeChunks.clear();
        lastError = "";
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", version);
        tag.putString("Phase", phase.name());
        tag.putInt("NextPiece", nextPiece);
        tag.putBoolean("LayoutReady", layoutReady);

        tag.putInt("OriginX", originX);
        tag.putInt("OriginY", originY);
        tag.putInt("OriginZ", originZ);
        tag.putInt("CenterX", centerX);
        tag.putInt("CenterY", centerY);
        tag.putInt("CenterZ", centerZ);
        tag.putInt("ArrivalX", arrivalX);
        tag.putInt("ArrivalY", arrivalY);
        tag.putInt("ArrivalZ", arrivalZ);
        tag.putInt("GroundPortalX", groundPortalX);
        tag.putInt("GroundPortalY", groundPortalY);
        tag.putInt("GroundPortalZ", groundPortalZ);

        tag.putInt("SurveyCandidate", surveyCandidate);
        tag.putInt("SurveySample", surveySample);
        tag.putInt("SurveyScore", surveyScore);
        tag.putInt("BestSurveyScore", bestSurveyScore);
        tag.putInt("BestOriginX", bestOriginX);
        tag.putInt("BestOriginZ", bestOriginZ);
        tag.putLong("WaitUntilTick", waitUntilTick);
        long[] packed = new long[activeChunks.size()];
        for (int i = 0; i < activeChunks.size(); i++) {
            packed[i] = activeChunks.get(i);
        }
        tag.putLongArray("ActiveChunks", packed);
        tag.putString("LastError", lastError == null ? "" : lastError);
        return tag;
    }
}
