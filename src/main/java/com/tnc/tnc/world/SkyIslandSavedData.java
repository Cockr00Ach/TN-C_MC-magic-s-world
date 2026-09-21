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

    /**
     * 取该维度的天空岛状态。
     *
     * <p>2026-09-22 由任务系统BBB 把可见性从包级改为 {@code public} ——
     * 剧情 NPC 需要问它要锚点坐标（见下方 {@link #anchorPos(String)}）。
     * 只扩大可见性，逻辑一字未改。
     */
    public static SkyIslandSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                SkyIslandSavedData::load,
                SkyIslandSavedData::new,
                FILE_ID
        );
    }

    // ------------------------------------------------------------------
    //  给别的专题用的公开只读接口（2026-09-22 由任务系统BBB 添加）
    //
    //  背景：剧情 NPC 的位置要"跟着天空岛走"（天空岛按存档生成，每个存档位置不同），
    //  所以 NPC 系统需要问这里要坐标。上面的 get() 是包级可见，跨包读不到，
    //  而绕路去 readTagFromDisk 读原始 NBT 是不可靠的（实测读不到，见提交 79b1e3d 之后）。
    //
    //  这里**只加一个只读方法**，不改动本类任何既有逻辑与字段。
    // ------------------------------------------------------------------

    /**
     * 天空岛是否已生成完成 —— 别的系统（如固定 NPC）应当等它 true 之后再放东西。
     *
     * <p>生成**中途** center/arrival 这类坐标就已经有值了，但路面还没铺完，
     * 那时放东西会掉进虚空、而且随后地形还会变。
     */
    public boolean isSkyIslandComplete() {
        return phase == Phase.COMPLETE && layoutReady;
    }

    /**
     * 取某个锚点的世界坐标，供"跟着天空岛走"的系统使用。
     *
     * @param anchor {@code CENTER} / {@code ARRIVAL} / {@code ORIGIN} / {@code GROUND_PORTAL}
     * @return 坐标；锚点名不认识、或天空岛尚未生成完成时返回 {@code null}
     */
    public net.minecraft.core.BlockPos anchorPos(String anchor) {
        if (!isSkyIslandComplete() || anchor == null) {
            return null;
        }
        return switch (anchor.toUpperCase(java.util.Locale.ROOT)) {
            case "CENTER" -> new net.minecraft.core.BlockPos(centerX, centerY, centerZ);
            case "ARRIVAL" -> new net.minecraft.core.BlockPos(arrivalX, arrivalY, arrivalZ);
            case "ORIGIN" -> new net.minecraft.core.BlockPos(originX, originY, originZ);
            case "GROUND_PORTAL" -> new net.minecraft.core.BlockPos(groundPortalX, groundPortalY, groundPortalZ);
            default -> null;
        };
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
