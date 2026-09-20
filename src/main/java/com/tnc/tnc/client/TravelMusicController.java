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

    // Test tuning: first track after about one second; only 2-6 seconds between tracks.
    // Production tuning can later change only these three constants.
    private static final int INITIAL_DELAY_TICKS = 20;
    private static final int MIN_DELAY_TICKS = 40;
    private static final int MAX_DELAY_TICKS = 120;
    private static final int STARTUP_GRACE_TICKS = 40;

    private static final Deque<ResourceLocation> queue = new ArrayDeque<>();
    private static List<ResourceLocation> catalog = List.of();
    private static SoundInstance currentTrack;
    private static ResourceLocation lastTrack;
    private static int waitTicks = INITIAL_DELAY_TICKS;
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
            waitTicks = INITIAL_DELAY_TICKS;
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
        return ThreadLocalRandom.current().nextInt(MIN_DELAY_TICKS, MAX_DELAY_TICKS + 1);
    }

    private static void leaveWorld(Minecraft minecraft) {
        if (!inWorld) {
            return;
        }
        if (currentTrack != null) {
            minecraft.getSoundManager().stop(currentTrack);
        }
        currentTrack = null;
        currentAge = 0;
        waitTicks = INITIAL_DELAY_TICKS;
        refreshTicks = 0;
        queue.clear();
        lastTrack = null;
        inWorld = false;
    }
}
