package com.tnc.tnc.equipment;

import java.util.ArrayList;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Immunity is tied to actual equipment, never an inventory copy or a saved player flag. */
@Mod.EventBusSubscriber(modid="tnc")
public final class PurityAmulet {
    private PurityAmulet(){}
    public static boolean equipped(LivingEntity entity){
        var item=PurityAmuletRegistration.ITEM;
        return item!=null&&(entity.getItemBySlot(EquipmentSlot.OFFHAND).is(item)||PurityAccessoryBridge.equipped(entity,item));
    }
    public static boolean negative(MobEffect effect){return !effect.isBeneficial();}
    public static void cleanse(LivingEntity entity){
        for(var effect:new ArrayList<>(entity.getActiveEffects()))
            if(negative(effect.getEffect()))entity.removeEffect(effect.getEffect());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void applicable(MobEffectEvent.Applicable event){
        if(negative(event.getEffectInstance().getEffect())&&equipped(event.getEntity()))event.setResult(Event.Result.DENY);
    }
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase==TickEvent.Phase.START&&!event.player.level().isClientSide&&equipped(event.player))cleanse(event.player);
    }
}
