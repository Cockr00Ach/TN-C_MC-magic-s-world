package com.tnc.tnc.arcana;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class ArcanaMealEffects {
    public static final MobEffect[] MEALS=new MobEffect[5];public static MobEffect HARMONY,NO_MAGIC;
    @SubscribeEvent public static void register(net.minecraftforge.registries.RegisterEvent e){e.register(net.minecraft.core.registries.Registries.MOB_EFFECT,h->{
        String[] ids={"meal_blazing","meal_rooted","meal_vitality","meal_clarity","meal_gale"};int[] colors={0xff8158,0xd7ad65,0xc6e884,0x79dfe5,0x7ff2c6};
        for(int i=0;i<5;i++){var b=new MobEffect(MobEffectCategory.BENEFICIAL,colors[i]){};MEALS[i]=b;h.register(net.minecraft.resources.ResourceLocation.parse("tnc:"+ids[i]),b);}
        MEALS[1].addAttributeModifier(Attributes.ARMOR,"4e59b960-f2a4-4d58-a78d-bb1edc460731",4,AttributeModifier.Operation.ADDITION).addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE,"4e59b960-f2a4-4d58-a78d-bb1edc460732",1,AttributeModifier.Operation.ADDITION);
        MEALS[2].addAttributeModifier(Attributes.MAX_HEALTH,"4e59b960-f2a4-4d58-a78d-bb1edc460733",6,AttributeModifier.Operation.ADDITION);
        MEALS[4].addAttributeModifier(Attributes.MOVEMENT_SPEED,"4e59b960-f2a4-4d58-a78d-bb1edc460734",.3,AttributeModifier.Operation.MULTIPLY_TOTAL);
        HARMONY=new MobEffect(MobEffectCategory.BENEFICIAL,0xffde91){};NO_MAGIC=new MobEffect(MobEffectCategory.HARMFUL,0x6d476c){};
        h.register(net.minecraft.resources.ResourceLocation.parse("tnc:meal_harmony"),HARMONY);h.register(net.minecraft.resources.ResourceLocation.parse("tnc:meal_no_magic"),NO_MAGIC);
    });}
    public static void tick(net.minecraft.server.level.ServerPlayer p){if(p.hasEffect(MEALS[1]))p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,30,p.getEffect(MEALS[1]).getAmplifier(),false,false));if(p.hasEffect(MEALS[2])&&!p.hasEffect(MobEffects.REGENERATION))p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,120,0,false,false));if(p.hasEffect(MEALS[3])){var m=com.tnc.tnc.magic.MagicStone.getOrNull(p);if(m!=null)m.addMana(3*(p.getEffect(MEALS[3]).getAmplifier()+1));}if(p.hasEffect(MEALS[4]))p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,30,0,false,false));}
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc") public static final class Combat {
        @SubscribeEvent public static void burn(net.minecraftforge.event.entity.living.LivingHurtEvent e){if(e.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer p&&p.hasEffect(MEALS[0])&&e.getEntity()!=p)e.getEntity().setSecondsOnFire(4*(p.getEffect(MEALS[0]).getAmplifier()+1));}
    }
    private ArcanaMealEffects(){}
}
