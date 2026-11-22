package com.tnc.tnc.skyacademy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tnc")
public final class WaterfallHermit {
    private static final String[] LINES = {
        "外面那道瀑布，我听了三十年。今天，才多听见一双脚步。过来坐。",
        "把眼睛闭上。左边那股水落在石头上，右边那股落在潭里。听出分别了么？",
        "下次从山顶看见这道白水，记得里面还有一盏灯。"
    };

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void speak(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof Villager master)
                || !master.getTags().contains("tnc_waterfall_hermit")) return;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var notes = com.tnc.tnc.journey.JourneyData.get(player.server);
        var progress = notes.player(player.getUUID());
        int index = Math.floorMod(progress.getInt("WaterfallConversation"), LINES.length);
        master.lookAt(player, 180F, 60F);
        player.sendSystemMessage(Component.empty().append(master.getName()).append("：").append(LINES[index]));
        progress.putInt("WaterfallConversation", (index + 1) % LINES.length);
        ((net.minecraft.world.level.saveddata.SavedData) notes).setDirty();
    }

    private WaterfallHermit() {}
}
