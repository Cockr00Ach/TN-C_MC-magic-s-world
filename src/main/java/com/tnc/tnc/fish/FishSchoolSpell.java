package com.tnc.tnc.fish;

import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.compat.CombatCasting;
import com.tnc.tnc.network.MagicStoneNetwork;
import com.tnc.tnc.warrior.WarriorBronzeBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public final class FishSchoolSpell {
    private FishSchoolSpell() {}
    public static InteractionResult cast(ServerPlayer player, ItemStack stack, boolean metal) {
        if (!WarriorBronzeBridge.canThrow(player) || CombatCasting.current(player)!=null || player.getCooldowns().isOnCooldown(stack.getItem())) return InteractionResult.FAIL;
        if (stack.hasTag() && stack.getTag().contains(FishSwordEvents.PENDING)) return InteractionResult.FAIL;
        var mana=MagicStone.getOrNull(player);
        int cost=FishSchoolGeometry.mana(metal);
        boolean free=player.getAbilities().instabuild;
        if (!free && (mana==null || !mana.spendMana(cost))) {
            player.displayClientMessage(Component.translatable("message.tnc.fish_mana",cost),true);
            return InteractionResult.FAIL;
        }
        var school=new FishSchoolEntity(FishSwords.school(),player.level());
        school.initialize(player,metal);
        if (!player.level().addFreshEntity(school)) {
            if (!free) { mana.addMana(cost); MagicStoneNetwork.syncTo(player); }
            return InteractionResult.FAIL;
        }
        int cooldown=FishSchoolGeometry.cooldown(metal);
        // Both fish swords share the active cooldown, so swapping the two cannot bypass it.
        player.getCooldowns().addCooldown(FishSwords.living(),cooldown);
        player.getCooldowns().addCooldown(FishSwords.metal(),cooldown);
        MagicStone.markCast(player);
        MagicStoneNetwork.syncTo(player);
        player.level().playSound(null,player.blockPosition(),SoundEvents.BUCKET_EMPTY_FISH,SoundSource.PLAYERS,.8f,metal?.85f:1.2f);
        return InteractionResult.CONSUME;
    }
}
