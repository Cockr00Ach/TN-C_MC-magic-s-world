package com.tnc.tnc.client;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Client-only shuffled travel playlist which replaces vanilla background music. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TravelMusicController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EVENT_PREFIX = "music.travel.";

    private static final int STARTUP_GRACE_TICKS = 40;

    /**
     * ★ 2026-09-30：boss 战音乐（作者："音乐效果没出来"）
     *
     * <h2>为什么"凋零那套"没有音乐可放</h2>
     * 原版凋零**根本没有专属 BGM** ✗ —— 全游戏只有末影龙有 boss 音乐
     * （{@code minecraft:music.dragon}）。所以这里直接借**末影龙那首**当 boss 曲 ✓。
     *
     * <h2>为什么不能交给原版 MusicManager</h2>
     * 本整合包的背景音乐由**我们自己**接管：{@code MusicManagerMixin} 把原版音乐关掉了
     * （见 {@link #shouldSuppressVanillaMusic()}）✗ ⇒ 竖琴/原版那套放不出 boss 曲 ✗。
     * 所以这里走和旅行音乐**同一条通道**（{@code SoundSource.MUSIC} 的 SoundInstance ✓）：
     * 玩家附近有活着的黑暗衍 ⇒ 停掉旅行音乐、改放这一首 ✓；boss 走了/死了再切回旅行音乐 ✓。
     */
    /**
     * boss 曲的**音效 id** ✓ —— 直接用 {@code ResourceLocation}（和旅行音乐那几条一样 ✓），
     * 不走 {@code SoundEvents} 常量：那个常量在这里是 {@code Holder} 形态、取不到 id ✗。
     */
    private static final ResourceLocation BOSS_MUSIC =
            ResourceLocation.fromNamespaceAndPath("minecraft", "music.dragon");
    /** 多近算"在打 boss"（格）✓ —— 比 64 格施法距离略小一点，免得刚看见就切音乐 ✓ */
    private static final double BOSS_MUSIC_RANGE = 56.0D;
    private static SoundInstance bossTrack;
    private static int bossAge;

    private static final Deque<ResourceLocation> queue = new ArrayDeque<>();
    private static List<ResourceLocation> catalog = List.of();
    private static SoundInstance currentTrack;
    private static ResourceLocation lastTrack;
    private static int waitTicks;
    private static int currentAge;
    private static int refreshTicks;
    private static boolean inWorld;

    private TravelMusicController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            leaveWorld(minecraft);
            return;
        }

        if (!inWorld) {
            inWorld = true;
            waitTicks = randomInitialDelay();
            if (refreshCatalog(minecraft.getSoundManager())) {
                // Stop a vanilla track which may have started before our catalog was ready.
                // MusicManagerMixin suppresses future vanilla music without mutating its timer.
                minecraft.getMusicManager().stopPlaying();
            }
        }

        if (++refreshTicks >= 400) {
            refreshTicks = 0;
            if (refreshCatalog(minecraft.getSoundManager())) {
                minecraft.getMusicManager().stopPlaying();
            }
        }

        SoundManager sounds = minecraft.getSoundManager();

        // ★ boss 战优先：附近有活着的黑暗衍 ⇒ 旅行音乐让位、改放 boss 曲 ✓（见 BOSS_MUSIC 的说明）
        if (tickBossMusic(minecraft, sounds)) {
            return;
        }

        if (currentTrack != null) {
            currentAge++;
            if (currentAge < STARTUP_GRACE_TICKS || sounds.isActive(currentTrack)) {
                return;
            }
            LOGGER.info("[TN-C Music] finished {}", currentTrack.getLocation());
            currentTrack = null;
            currentAge = 0;
            waitTicks = randomDelay();
        }

        if (minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F || catalog.isEmpty()) {
            return;
        }
        if (waitTicks-- > 0) {
            return;
        }

        ResourceLocation next = nextTrack();
        if (next == null) {
            waitTicks = randomDelay();
            return;
        }

        currentTrack = new SimpleSoundInstance(
                next,
                SoundSource.MUSIC,
                1.0F,
                1.0F,
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        );
        currentAge = 0;
        lastTrack = next;
        sounds.play(currentTrack);
        LOGGER.info("[TN-C Music] playing {} ({} track(s) available)", next, catalog.size());
    }

    /**
     * boss 战音乐那一支 ✓ —— 返回 true 表示"现在归 boss 音乐管"（旅行音乐这一 tick 什么都别做）。
     *
     * <p>曲子放完（{@code isActive} 变 false）就在下一 tick 重放 ✓ ——
     * 原版那首 boss 曲本身不循环 ✗，而战斗可能打很久 ⇒ 靠这里续 ✓。
     */
    private static boolean tickBossMusic(Minecraft minecraft, SoundManager sounds) {
        boolean bossNear = minecraft.level != null && minecraft.player != null
                && !minecraft.level.getEntitiesOfClass(
                        com.tnc.tnc.boss.YanDarkBossEntity.class,
                        minecraft.player.getBoundingBox().inflate(BOSS_MUSIC_RANGE)).isEmpty();

        if (!bossNear) {
            if (bossTrack != null) {                     // boss 走了/死了：收掉 boss 曲，旅行音乐恢复 ✓
                sounds.stop(bossTrack);
                bossTrack = null;
                bossAge = 0;
                LOGGER.info("[TN-C Music] boss music stopped");
            }
            return false;
        }

        if (currentTrack != null) {                      // 旅行音乐让位 ✓
            sounds.stop(currentTrack);
            currentTrack = null;
            currentAge = 0;
        }
        if (bossTrack != null) {
            bossAge++;
            if (bossAge < STARTUP_GRACE_TICKS || sounds.isActive(bossTrack)) {
                return true;
            }
            bossTrack = null;                            // 放完了 ⇒ 下面重放 ✓
            bossAge = 0;
        }
        if (minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F) {
            return true;
        }
        bossTrack = new SimpleSoundInstance(
                BOSS_MUSIC,
                SoundSource.MUSIC,
                1.0F,
                1.0F,
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        );
        bossAge = 0;
        sounds.play(bossTrack);
        LOGGER.info("[TN-C Music] boss music {} (dark boss nearby)", BOSS_MUSIC);
        return true;
    }

    private static boolean refreshCatalog(SoundManager sounds) {
        boolean wasEmpty = catalog.isEmpty();
        List<ResourceLocation> discovered = sounds.getAvailableSounds().stream()
                .filter(id -> TNMod.MODID.equals(id.getNamespace()))
                .filter(id -> id.getPath().startsWith(EVENT_PREFIX))
                .sorted((left, right) -> left.toString().compareTo(right.toString()))
                .toList();
        if (!discovered.equals(catalog)) {
            catalog = discovered;
            queue.clear();
            LOGGER.info("[TN-C Music] discovered {} travel track(s)", catalog.size());
        }
        return wasEmpty && !catalog.isEmpty();
    }

    /** Used by the client MusicManager mixin to disable only vanilla background music. */
    public static boolean shouldSuppressVanillaMusic() {
        return inWorld && !catalog.isEmpty();
    }

    private static ResourceLocation nextTrack() {
        if (queue.isEmpty()) {
            List<ResourceLocation> shuffled = new ArrayList<>(catalog);
            Collections.shuffle(shuffled);
            if (shuffled.size() > 1 && shuffled.get(0).equals(lastTrack)) {
                Collections.swap(shuffled, 0, 1);
            }
            queue.addAll(shuffled);
        }
        return queue.pollFirst();
    }

    private static int randomDelay() {
        return TravelMusicTiming.randomInterTrackDelay(ThreadLocalRandom.current());
    }

    private static int randomInitialDelay() {
        return TravelMusicTiming.randomInitialDelay(ThreadLocalRandom.current());
    }

    private static void leaveWorld(Minecraft minecraft) {
        if (!inWorld) {
            return;
        }
        if (currentTrack != null) {
            minecraft.getSoundManager().stop(currentTrack);
        }
        if (bossTrack != null) {                         // ★ boss 曲也要收 ✓
            minecraft.getSoundManager().stop(bossTrack);
            bossTrack = null;
            bossAge = 0;
        }
        currentTrack = null;
        currentAge = 0;
        waitTicks = 0;
        refreshTicks = 0;
        queue.clear();
        lastTrack = null;
        inWorld = false;
    }
}
