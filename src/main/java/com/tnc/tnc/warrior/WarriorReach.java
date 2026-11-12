package com.tnc.tnc.warrior;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned heavy reach; the same peak drives solid weapon rendering and every melee path. */
@Mod.EventBusSubscriber(modid="tnc")
public final class WarriorReach {
    private static final UUID REACH=UUID.fromString("321142ad-81ab-496d-8b25-7d40c715b313");
    private WarriorReach() {}
    public static boolean heavy(WarriorWeaponFamily family){
        return family==WarriorWeaponFamily.GREATSWORD || family==WarriorWeaponFamily.HAMMER;
    }
    public static boolean scaled(WarriorWeaponFamily family){return family!=null&&family.melee();}
    public static final double MIN_PEAK=3,MAX_PEAK=8;
    public static double peak(int points){return MIN_PEAK+(MAX_PEAK-MIN_PEAK)*TemperingRules.clamp(points)/(double)TemperingRules.MAX_POINTS;}
    public static double scale(WarriorWeaponFamily family,int points,int cap){
        return scaled(family)?peak(points):1;
    }
    public static double scale(ServerPlayer player){
        var data=TemperingData.get(player.server);
        return scale(WarriorWeapons.family(player.getMainHandItem()),data.points(player.getUUID()),data.cap(player.getUUID()));
    }
    public static double basicScale(ServerPlayer player){
        return scale(player);
    }
    static void apply(AttributeInstance attribute,boolean enabled,boolean creative,int points){
        var old=attribute.getModifier(REACH);
        double base=attribute.getValue()/(old==null?1:1+old.getAmount());
        if(!enabled || base<=0){if(old!=null)attribute.removeModifier(REACH);return;}
        // Forge adds creative's 3-block bonus after evaluating the attribute; multiply that too.
        double amount=(peak(points)-1)*(1+(creative?3:0)/base);
        if(old!=null && Math.abs(old.getAmount()-amount)<1e-8)return;
        if(old!=null)attribute.removeModifier(REACH);
        attribute.addTransientModifier(new AttributeModifier(REACH,"Heavy weapon growth reach",amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    public static void update(ServerPlayer player){
        if(player.server==null)return;
        var attribute=player.getAttribute(ForgeMod.ENTITY_REACH.get());if(attribute==null)return;
        apply(attribute,player.isAlive()&&!player.isSpectator()&&scaled(WarriorWeapons.family(player.getMainHandItem())),
                player.isCreative(),TemperingData.get(player.server).points(player.getUUID()));
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase==TickEvent.Phase.END && event.player instanceof ServerPlayer player)update(player);
    }
    @SubscribeEvent public static void equipped(LivingEquipmentChangeEvent event){
        if(event.getSlot()==net.minecraft.world.entity.EquipmentSlot.MAINHAND && event.getEntity() instanceof ServerPlayer player)update(player);
    }
}
