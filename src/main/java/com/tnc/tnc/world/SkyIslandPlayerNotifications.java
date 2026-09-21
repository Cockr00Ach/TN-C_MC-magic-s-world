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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Player-facing sky-island titles, reminders and compatibility flags. */
final class SkyIslandPlayerNotifications {
    private static final String LEGACY_FIRST_JOIN_SHOWN = "ysjxmodelFirstJoinDialogShown";
    private static final String AWAKENING_TITLE_VERSION = "tncSkyIslandAwakeningTitleVersion";
    private static final String COMPLETE_TITLE_VERSION = "tncSkyIslandCompleteTitleVersion";

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
        SkyIslandNotificationPolicy.Decision decision = SkyIslandNotificationPolicy.onLogin(
                islandComplete, awakeningSeen, completionSeen);

        if (decision.title() == SkyIslandNotificationPolicy.Title.AWAKENING) {
            showTitle(player,
                    Component.literal("天空岛向你投来注视")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                    Component.literal("远方的浮岛正在苏醒")
                            .withStyle(ChatFormatting.DARK_PURPLE),
                    15, 90, 25);
            persisted.putInt(AWAKENING_TITLE_VERSION, manifestVersion);
        } else if (decision.title() == SkyIslandNotificationPolicy.Title.COMPLETE) {
            showCompletionTitle(player, manifestVersion, groundPortal);
        }

        if (decision.sendCoordinates()) {
            sendPortalReminder(player, groundPortal);
        }
    }

    static void onGenerationComplete(ServerPlayer player, int manifestVersion, BlockPos groundPortal) {
        boolean completionSeen = persisted(player).getInt(COMPLETE_TITLE_VERSION) >= manifestVersion;
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onGenerationComplete(completionSeen);
        if (decision.title() == SkyIslandNotificationPolicy.Title.COMPLETE) {
            showCompletionTitle(player, manifestVersion, groundPortal);
        }
        if (decision.sendCoordinates()) {
            sendPortalReminder(player, groundPortal);
        }
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
}
