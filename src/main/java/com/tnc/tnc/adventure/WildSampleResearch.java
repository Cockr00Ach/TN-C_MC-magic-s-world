package com.tnc.tnc.adventure;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.OriginalCrops;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One research delivery per species and player, at the actual tavern board. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class WildSampleResearch {
    private WildSampleResearch() {}

    private static String species(ItemStack stack) {
        if (stack.is(OriginalCrops.ROAD_BELL_WILD_SAMPLE)) return "road_bell";
        if (stack.is(OriginalCrops.NIGHT_GOURD_WILD_SAMPLE)) return "night_gourd";
        if (stack.is(OriginalCrops.TIDE_REED_WILD_SAMPLE)) return "tide_reed";
        return null;
    }

    static boolean claim(ServerPlayer player, ItemStack sample) {
        String id = species(sample);
        if (id == null || sample.isEmpty()) return false;
        var profile = AdventureService.profile(player);
        String milestone = "wild_research_" + id;
        if (!profile.registered() || profile.hasMilestone(milestone)) return false;
        sample.shrink(1);
        AdventureService.milestone(player, milestone);
        long copper = Math.min(120L, AdventureRules.MAX_COINS - profile.coins());
        profile.credit(copper, "酒馆野外物种研究");
        profile.addXp(180);
        profile.reputation = Math.min(1_000_000, profile.reputation + 20);
        AdventureSavedData.get(player.server).setDirty();
        AdventureService.refreshGrowth(player);
        player.sendSystemMessage(Component.literal("酒馆已登记" + switch (id) {
            case "road_bell" -> "路铃麦";
            case "night_gourd" -> "夜窗瓠";
            default -> "听潮苇";
        } + "的首份野外标本：+" + copper + "铜、+180经验、+20声望。种子可带回家继续培育。"));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void deliver(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !TownServices.atBoard(player)
                || !event.getPos().equals(TownServices.board(player.serverLevel()))) return;
        if (claim(player, event.getItemStack())) {
            AdventureService.milestone(player, "visited_board");
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
