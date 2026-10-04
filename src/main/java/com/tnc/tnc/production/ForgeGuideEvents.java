package com.tnc.tnc.production;

import com.tnc.tnc.TNMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** First forge-core craft supplies the physical guide without a second crafting chore. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class ForgeGuideEvents {
    private static final String READ_GUIDE = "tncForgeGuideIssued";
    private ForgeGuideEvents() {}

    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getCrafting().is(MagicForgeContent.FORGE_ITEM)
                || player.getPersistentData().getBoolean(READ_GUIDE)) return;
        player.getPersistentData().putBoolean(READ_GUIDE, true);
        ItemStack book = new ItemStack(MagicForgeContent.GUIDE_ITEM);
        if (!player.getInventory().add(book)) player.drop(book, false);
    }
}
