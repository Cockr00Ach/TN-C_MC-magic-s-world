package com.tnc.tnc.warrior;

import com.tnc.tnc.bronze.BronzeWeaponItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adapt the unique thrown dagger to existing warrior target rules and impact effects. */
@Mod.EventBusSubscriber(modid="tnc")
public final class WarriorBronzeBridge {
    private WarriorBronzeBridge(){}
    public static boolean canThrow(Player p){
        return p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p)
            &&!com.tnc.tnc.boss.fire.TortoiseStun.isStunned(p)
            &&(!(p instanceof ServerPlayer server)||!WarriorComboService.performing(server,p.level().getGameTime()));
    }
    public static boolean validTarget(ServerPlayer player,LivingEntity target){
        return WarriorComboService.validTarget(player,target);
    }
    public static void impact(ServerPlayer player,LivingEntity target,boolean heavy){WarriorCombatEffects.hitSpark(player,target,heavy);}
    @SubscribeEvent public static void attack(AttackEntityEvent e){
        var stack=e.getEntity().getMainHandItem();
        if(stack.getItem() instanceof BronzeWeaponItem&&BronzeWeaponItem.inFlight(stack,e.getEntity().level()))e.setCanceled(true);
    }
    @SubscribeEvent public static void anvil(AnvilUpdateEvent e){
        for(var stack:new net.minecraft.world.item.ItemStack[]{e.getLeft(),e.getRight()})
            if(stack.getItem() instanceof BronzeWeaponItem&&stack.hasTag()&&stack.getTag().contains(BronzeWeaponItem.FLIGHT)){e.setCanceled(true);return;}
    }
}
