package com.tnc.tnc.world.ink;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Synced vanilla status effect; no permanent player NBT or global tick-rate changes. */
@Mod.EventBusSubscriber(modid="tnc")
public final class InkDragonDread {
    public static final int DURATION=240;
    public static final float SPEED=.25F;
    public static MobEffect EFFECT;
    private InkDragonDread(){}
    private static final class Dread extends MobEffect {
        Dread(){super(MobEffectCategory.HARMFUL,0x383838);
            addAttributeModifier(Attributes.MOVEMENT_SPEED,"7b45259a-a14c-4f0b-ad4c-96cc00fb9a52",-.75,AttributeModifier.Operation.MULTIPLY_TOTAL);
            addAttributeModifier(Attributes.ATTACK_SPEED,"e4d087cd-e61d-4943-992b-07ce06aef638",-.75,AttributeModifier.Operation.MULTIPLY_TOTAL);
        }
    }
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registry {
        @SubscribeEvent public static void register(RegisterEvent e){e.register(Registries.MOB_EFFECT,h->{EFFECT=new Dread();h.register(ResourceLocation.fromNamespaceAndPath("tnc","ink_dragon_dread"),EFFECT);});}
    }
    public static boolean active(LivingEntity e){return e!=null&&EFFECT!=null&&e.hasEffect(EFFECT);}
    public static boolean apply(LivingEntity e){
        if(e.level().isClientSide||!e.isAlive()||e instanceof Player p&&(p.isCreative()||p.isSpectator()))return false;
        return e.addEffect(new MobEffectInstance(EFFECT,DURATION,0,false,true,false));
    }
    public static boolean advances(int tick){return (tick&3)==0;}
    @SubscribeEvent public static void usingItem(LivingEntityUseItemEvent.Tick event){
        if(active(event.getEntity())&&!advances(event.getEntity().tickCount)&&event.getDuration()>0)event.setDuration(event.getDuration()+1);
    }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e){if(EFFECT!=null)e.getEntity().removeEffect(EFFECT);}
    @SubscribeEvent public static void mining(PlayerEvent.BreakSpeed e){if(active(e.getEntity()))e.setNewSpeed(e.getNewSpeed()*.25F);}
    @SubscribeEvent public static void added(net.minecraftforge.event.entity.living.MobEffectEvent.Added e){
        if(!e.getEntity().level().isClientSide&&e.getEffectInstance().getEffect()==EFFECT&&e.getOldEffectInstance()==null)InkSpellTimeBridge.retime(e.getEntity(),4);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void removed(net.minecraftforge.event.entity.living.MobEffectEvent.Remove e){
        if(!e.getEntity().level().isClientSide&&e.getEffect()==EFFECT)InkSpellTimeBridge.retime(e.getEntity(),.25);
    }
    @SubscribeEvent public static void expired(net.minecraftforge.event.entity.living.MobEffectEvent.Expired e){
        if(!e.getEntity().level().isClientSide&&e.getEffectInstance()!=null&&e.getEffectInstance().getEffect()==EFFECT)InkSpellTimeBridge.retime(e.getEntity(),.25);
    }
}
