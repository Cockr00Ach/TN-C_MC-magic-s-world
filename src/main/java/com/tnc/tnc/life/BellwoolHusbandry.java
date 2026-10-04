package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.HousingService;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps production working for vanilla sheep attuned in old saves. New players
 * raise the distinct bellwool species instead; ordinary sheep cannot convert.
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class BellwoolHusbandry {
    private static final String ATTUNED = "tncBellwoolAttuned";
    private static final String NEXT_BONUS = "tncBellwoolNextBonus";
    private static final long BONUS_COOLDOWN = 48_000L;

    private BellwoolHusbandry() {}

    public static final class FodderItem extends Item {
        public FodderItem() { super(new Properties()); }
        @Override public void appendHoverText(ItemStack stack, Level level,
                java.util.List<Component> lines, TooltipFlag flag) {
            lines.add(Component.literal("给野生响铃羊喂食繁殖；普通绵羊不会变成响铃羊。"));
        }
    }

    public static final class FleeceItem extends Item {
        public FleeceItem() { super(new Properties()); }
        @Override public void appendHoverText(ItemStack stack, Level level,
                java.util.List<Component> lines, TooltipFlag flag) {
            lines.add(Component.literal("活羊剪得；可换铜币、交酒馆，或与线合成牵绳。"));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof Sheep sheep)) return;
        if (HousingService.ownedAt(player.serverLevel(), sheep.blockPosition()) != null
                && TownProtection.denied(player, sheep.blockPosition())) return;

        ItemStack held = event.getItemStack();
        if (held.is(TNMod.BELLWOOL_FODDER.get())) {
            player.displayClientMessage(Component.literal("普通绵羊不会变成响铃羊。到草原或草甸寻找有青角和铜铃的独立羊种。"), true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        if (!held.is(Items.SHEARS) || !sheep.getPersistentData().getBoolean(ATTUNED)
                || sheep.isBaby() || sheep.isSheared()) return;

        // Handle the entire shear here; allowing vanilla to continue would drop
        // ordinary wool twice when a bonus is ready.
        if (shearAndBonus(sheep, held, player, event.getHand()))
            player.displayClientMessage(Component.literal("收得一团响绒。留下羊继续吃草，过两天还能再剪。"), true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    static boolean shearAndBonus(Sheep sheep, ItemStack shears, ServerPlayer player, InteractionHand hand) {
        sheep.shear(SoundSource.PLAYERS);
        shears.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        long now = sheep.level().getGameTime();
        if (now >= sheep.getPersistentData().getLong(NEXT_BONUS)) {
            sheep.spawnAtLocation(new ItemStack(TNMod.RESONANT_FLEECE.get()));
            sheep.getPersistentData().putLong(NEXT_BONUS, now + BONUS_COOLDOWN);
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
                    sheep.getX(), sheep.getY() + 1.2, sheep.getZ(), 5, .35, .2, .35, .03);
            return true;
        }
        return false;
    }
}
