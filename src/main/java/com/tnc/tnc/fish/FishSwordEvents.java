package com.tnc.tnc.fish;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tnc")
public final class FishSwordEvents {
    public static final String PENDING = "TncFishMetalization";
    private FishSwordEvents() {}
    private static boolean pending(ItemStack stack) {
        return stack.is(FishSwords.living()) && stack.hasTag() && stack.getTag().contains(PENDING);
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT)) return;
        var type = event.getEntity().getType();
        if (type != EntityType.COD && type != EntityType.SALMON && type != EntityType.TROPICAL_FISH && type != EntityType.PUFFERFISH) return;
        if (!FishSwordRules.succeeds(player.getRandom().nextInt(FishSwordRules.ROLL_BOUND), FishSwordRules.dropChance(event.getLootingLevel()))) return;
        var fish = event.getEntity();
        event.getDrops().add(new ItemEntity(fish.level(), fish.getX(), fish.getY(), fish.getZ(), new ItemStack(FishSwords.living())));
    }
    @SubscribeEvent public static void anvil(AnvilUpdateEvent event) {
        if (!event.getLeft().is(FishSwords.living())) return;
        if (pending(event.getLeft())) { event.setCanceled(true); return; }
        boolean block = event.getRight().is(Items.IRON_BLOCK);
        if (!block && !event.getRight().is(Items.IRON_INGOT)) return;
        ItemStack output = event.getLeft().copy();
        output.setCount(1);
        CompoundTag marker = new CompoundTag();
        marker.putInt("Chance", block ? FishSwordRules.BLOCK_CHANCE : FishSwordRules.INGOT_CHANCE);
        output.getOrCreateTag().put(PENDING, marker);
        String name = event.getName();
        if (name != null) {
            if (name.isBlank()) output.resetHoverName();
            else if (!name.equals(event.getLeft().getHoverName().getString())) output.setHoverName(Component.literal(name));
        }
        // Preview is an unresolved living sword. No RNG runs until it actually leaves the result slot.
        event.setOutput(output);
        event.setMaterialCost(1);
        event.setCost(1);
    }
    static ItemStack settle(ItemStack stack, net.minecraft.util.RandomSource random) {
        if (!pending(stack)) return stack;
        int chance = stack.getTag().getCompound(PENDING).getInt("Chance");
        chance = chance == FishSwordRules.BLOCK_CHANCE ? chance : FishSwordRules.INGOT_CHANCE;
        ItemStack clean = stack.copy();
        clean.getTag().remove(PENDING);
        if (!FishSwordRules.succeeds(random.nextInt(FishSwordRules.ROLL_BOUND), chance)) return clean;
        // Serializing the full stack retains names, enchantments, custom tags and Forge capabilities.
        CompoundTag saved = clean.save(new CompoundTag());
        saved = FishSwordData.metalized(saved, clean.getMaxDamage(), FishSwordRules.METAL_DURABILITY);
        ItemStack metal = ItemStack.of(saved);
        metal.setDamageValue(FishSwordRules.convertedDamage(clean.getDamageValue(), clean.getMaxDamage(), metal.getMaxDamage()));
        return metal;
    }
    private static ItemStack settleForPlayer(ItemStack stack, ServerPlayer player) {
        if (!pending(stack)) return stack;
        ItemStack result = settle(stack, player.getRandom());
        boolean success = result.is(FishSwords.metal());
        player.displayClientMessage(Component.translatable(success ? "message.tnc.fish_success" : "message.tnc.fish_failure"), true);
        player.level().playSound(null, player.blockPosition(), success ? SoundEvents.ANVIL_USE : SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .7f, success ? 1.25f : .7f);
        return result;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack before = player.getInventory().getItem(slot);
            ItemStack after = settleForPlayer(before, player);
            if (after != before) { player.getInventory().setItem(slot, after); changed = true; }
        }
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack after = settleForPlayer(carried, player);
        if (after != carried) { player.containerMenu.setCarried(after); changed = true; }
        if (changed) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        }
    }
    @SubscribeEvent public static void droppedResult(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ItemEntity item && pending(item.getItem())) {
            item.setItem(settle(item.getItem(), event.getLevel().getRandom()));
        }
    }
}

