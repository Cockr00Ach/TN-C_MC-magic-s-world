package com.tnc.tnc.world;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Forge event bridge kept deliberately thin; all durable behavior lives in the manager. */
public final class SkyIslandEvents {
    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tnc").then(Commands.literal("skylandscape")
                .requires(s->s.hasPermission(2))
                .then(Commands.literal("status").executes(c->{c.getSource().sendSuccess(
                        ()->Component.literal(SkyLandscapeUpgrade.status(c.getSource().getServer())),false);return 1;}))
                .then(Commands.literal("retry").executes(c->{SkyLandscapeUpgrade.retry(c.getSource().getServer());
                    c.getSource().sendSuccess(()->Component.literal("重新检查天空岛整修现场；不会强行覆盖冲突方块。"),false);return 1;}))));
        event.getDispatcher().register(Commands.literal("tnc").then(Commands.literal("skyportal")
                .requires(s -> s.hasPermission(2)).then(Commands.literal("refresh").executes(c -> {
                    try {
                        SkyIslandManager.refreshPortalArchitecture(c.getSource().getServer());
                        c.getSource().sendSuccess(() -> Component.literal("两座传送阵外观已更新；天空岛、NPC 和传送坐标保持不变。"), true);
                        return 1;
                    } catch (SkyIslandManager.PortalChunksPending pending) {
                        c.getSource().sendSuccess(()->Component.literal(pending.getMessage()),false);
                        return 1;
                    } catch (Exception e) {
                        c.getSource().sendFailure(Component.literal("传送阵外观未更新：" + e.getMessage()));
                        return 0;
                    }
                })).then(Commands.literal("preview").executes(c -> {
                    try {
                        SkyIslandManager.previewRitual(c.getSource().getPlayerOrException());
                        c.getSource().sendSuccess(()->Component.literal("开始 30 秒循环视觉预演，可在阵外观察；预演本身不会传送。"),false);
                        return 1;
                    } catch (Exception e) {
                        c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;
                    }
                }))));
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkyIslandPlayerNotifications.suppressLegacyFirstJoinDialog(player);
            SkyIslandManager.onPlayerLogin(player);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SkyIslandManager.tick(event.getServer());
            SkyLandscapeUpgrade.tick(event.getServer());
            com.tnc.tnc.tavern.TavernUpgrade.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        SkyIslandManager.onServerStopping(event.getServer());
        SkyLandscapeUpgrade.stop(event.getServer());
        com.tnc.tnc.tavern.TavernUpgrade.stop(event.getServer());
    }
}
