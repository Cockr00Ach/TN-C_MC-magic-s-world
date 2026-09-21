package com.tnc.tnc.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Player-facing sky-island titles, reminders and compatibility flags. */
final class SkyIslandPlayerNotifications {
    private static final String LEGACY_FIRST_JOIN_SHOWN = "ysjxmodelFirstJoinDialogShown";
    private static final String AWAKENING_TITLE_VERSION = "tncSkyIslandAwakeningTitleVersion";
    private static final String COMPLETE_TITLE_VERSION = "tncSkyIslandCompleteTitleVersion";
    private static final String SEQUENCE_VERSION = "tncSkyIslandNotificationSequenceVersion";
    private static final String AWAKENING_REMAINING_TICKS = "tncSkyIslandAwakeningRemainingTicks";
    private static final String COMPLETION_PENDING = "tncSkyIslandCompletionPending";
    private static final String COMPLETION_DELAY_TICKS = "tncSkyIslandCompletionDelayTicks";
    private static final String IMPACT_DELAY_TICKS = "tncSkyIslandImpactDelayTicks";
    private static final int IMPACT_GAP_TICKS = 12;

    private SkyIslandPlayerNotifications() {
    }

    /**
     * ysjxmodel's normal-priority login listener checks this exact PlayerPersisted flag before
     * sending OpenFirstJoinDialogPacket. TN-C writes it from a highest-priority listener so only
     * the obsolete convenience-store opening is suppressed; the rest of ysjxmodel's login work
     * still runs normally.
     */
    static void suppressLegacyFirstJoinDialog(ServerPlayer player) {
        persisted(player).putBoolean(LEGACY_FIRST_JOIN_SHOWN, true);
    }

    static void onLogin(ServerPlayer player, int manifestVersion, boolean islandComplete,
                        BlockPos groundPortal) {
        CompoundTag persisted = persisted(player);
        boolean awakeningSeen = persisted.getInt(AWAKENING_TITLE_VERSION) >= manifestVersion;
        boolean completionSeen = persisted.getInt(COMPLETE_TITLE_VERSION) >= manifestVersion;
        initializeSequence(persisted, manifestVersion, awakeningSeen);
        SkyIslandNotificationPolicy.Decision decision = SkyIslandNotificationPolicy.onLogin(
                islandComplete, awakeningSeen, completionSeen,
                persisted.getBoolean(COMPLETION_PENDING),
                persisted.getInt(COMPLETION_DELAY_TICKS));

        if (decision.title() == SkyIslandNotificationPolicy.Title.COMPLETE) {
            showCompletionTitle(player, manifestVersion, groundPortal);
        }

        if (decision.sendCoordinates()) {
            sendPortalReminder(player, groundPortal);
        }
        if (islandComplete && !awakeningSeen && !completionSeen) {
            persisted.putBoolean(COMPLETION_PENDING, true);
        }
        player.server.getPlayerList().saveAll();
    }

    static void onGenerationComplete(ServerPlayer player, int manifestVersion, BlockPos groundPortal) {
        CompoundTag persisted = persisted(player);
        boolean awakeningSeen = persisted.getInt(AWAKENING_TITLE_VERSION) >= manifestVersion;
        boolean completionSeen = persisted.getInt(COMPLETE_TITLE_VERSION) >= manifestVersion;
        boolean completionPending = persisted.getBoolean(COMPLETION_PENDING);
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onGenerationComplete(
                        awakeningSeen, completionSeen, completionPending);
        if (!completionSeen && !awakeningSeen) {
            persisted.putBoolean(COMPLETION_PENDING, true);
        } else if (decision.title() == SkyIslandNotificationPolicy.Title.COMPLETE) {
            showCompletionTitle(player, manifestVersion, groundPortal);
            if (decision.sendCoordinates()) {
                sendPortalReminder(player, groundPortal);
            }
        }
    }

    static void tick(MinecraftServer server, int manifestVersion, boolean islandComplete,
                     BlockPos groundPortal) {
        boolean activeSequence = false;
        boolean transitioned = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TickResult result = tickPlayer(player, manifestVersion, islandComplete, groundPortal);
            activeSequence |= result.active();
            transitioned |= result.transitioned();
        }
        if (transitioned || SkyIslandNotificationPolicy.periodicSaveDue(
                activeSequence, server.overworld().getGameTime())) {
            server.getPlayerList().saveAll();
        }
    }

    private static TickResult tickPlayer(ServerPlayer player, int manifestVersion, boolean islandComplete,
                                         BlockPos groundPortal) {
        CompoundTag persisted = persisted(player);
        boolean awakeningSeen = persisted.getInt(AWAKENING_TITLE_VERSION) >= manifestVersion;
        boolean completionSeen = persisted.getInt(COMPLETE_TITLE_VERSION) >= manifestVersion;
        initializeSequence(persisted, manifestVersion, awakeningSeen);

        if (!awakeningSeen) {
            int remaining = persisted.getInt(AWAKENING_REMAINING_TICKS);
            if (!SkyIslandNotificationPolicy.countdownExpired(remaining)) {
                persisted.putInt(AWAKENING_REMAINING_TICKS, remaining - 1);
                return new TickResult(true, false);
            }
            showAwakening(player, manifestVersion);
            persisted.putInt(AWAKENING_REMAINING_TICKS, 0);
            persisted.putInt(IMPACT_DELAY_TICKS, IMPACT_GAP_TICKS);
            if (islandComplete && !completionSeen) {
                persisted.putBoolean(COMPLETION_PENDING, true);
            }
            if (persisted.getBoolean(COMPLETION_PENDING)) {
                persisted.putInt(COMPLETION_DELAY_TICKS,
                        SkyIslandNotificationPolicy.completionGapTicks());
            }
            return new TickResult(true, true);
        }

        tickImpactSound(player, persisted);
        int completionDelay = persisted.getInt(COMPLETION_DELAY_TICKS);
        if (completionDelay > 0) {
            completionDelay = SkyIslandNotificationPolicy.advanceDelay(completionDelay);
            persisted.putInt(COMPLETION_DELAY_TICKS, completionDelay);
            if (completionDelay > 0) {
                return new TickResult(true, false);
            }
        }
        if (islandComplete && !completionSeen && persisted.getBoolean(COMPLETION_PENDING)) {
            showCompletionTitle(player, manifestVersion, groundPortal);
            sendPortalReminder(player, groundPortal);
            persisted.putBoolean(COMPLETION_PENDING, false);
            return new TickResult(false, true);
        }
        boolean impactPending = persisted.getInt(IMPACT_DELAY_TICKS) > 0;
        return new TickResult(impactPending, false);
    }

    private static void initializeSequence(CompoundTag persisted, int manifestVersion,
                                           boolean awakeningSeen) {
        if (persisted.getInt(SEQUENCE_VERSION) >= manifestVersion) {
            return;
        }
        persisted.putInt(SEQUENCE_VERSION, manifestVersion);
        persisted.putInt(AWAKENING_REMAINING_TICKS,
                awakeningSeen ? 0 : SkyIslandNotificationPolicy.initialCountdownTicks());
        persisted.putBoolean(COMPLETION_PENDING, false);
        persisted.putInt(COMPLETION_DELAY_TICKS, 0);
        persisted.putInt(IMPACT_DELAY_TICKS, 0);
    }

    private static void showAwakening(ServerPlayer player, int manifestVersion) {
        showTitle(player,
                Component.literal("天空岛向你投来注视")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                Component.literal("远方的浮岛正在苏醒")
                        .withStyle(ChatFormatting.DARK_PURPLE),
                15, 90, 25);
        player.playNotifySound(SoundEvents.PORTAL_TRIGGER, SoundSource.AMBIENT, 0.9F, 0.65F);
        persisted(player).putInt(AWAKENING_TITLE_VERSION, manifestVersion);
    }

    private static void tickImpactSound(ServerPlayer player, CompoundTag persisted) {
        int remaining = persisted.getInt(IMPACT_DELAY_TICKS);
        if (remaining <= 0) {
            return;
        }
        if (remaining == 1) {
            player.playNotifySound(SoundEvents.WARDEN_SONIC_BOOM, SoundSource.AMBIENT, 1.2F, 0.8F);
        }
        persisted.putInt(IMPACT_DELAY_TICKS, remaining - 1);
    }

    private static void showCompletionTitle(ServerPlayer player, int manifestVersion, BlockPos groundPortal) {
        String coordinates = "X " + groundPortal.getX() + "  Y " + groundPortal.getY()
                + "  Z " + groundPortal.getZ();
        showTitle(player,
                Component.literal("传送阵已被唤醒")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                Component.literal(coordinates).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                10, 120, 30);
        persisted(player).putInt(COMPLETE_TITLE_VERSION, manifestVersion);
        persisted(player).putBoolean(COMPLETION_PENDING, false);
    }

    private static void showTitle(ServerPlayer player, Component title, Component subtitle,
                                  int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private static void sendPortalReminder(ServerPlayer player, BlockPos groundPortal) {
        String plainCoordinates = groundPortal.getX() + " " + groundPortal.getY() + " " + groundPortal.getZ();
        MutableComponent coordinates = Component.literal("X " + groundPortal.getX()
                + " / Y " + groundPortal.getY() + " / Z " + groundPortal.getZ());
        coordinates.setStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withBold(true)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, plainCoordinates))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("点击复制坐标"))));

        MutableComponent message = Component.literal("✦ 地面传送阵：")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(coordinates)
                .append(Component.literal("——请前往该处，寻找通往天空岛的入口。")
                        .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(message);
    }

    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag forgeData = player.getPersistentData();
        CompoundTag persisted = forgeData.getCompound(Player.PERSISTED_NBT_TAG);
        forgeData.put(Player.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }

    private record TickResult(boolean active, boolean transitioned) {
    }
}
