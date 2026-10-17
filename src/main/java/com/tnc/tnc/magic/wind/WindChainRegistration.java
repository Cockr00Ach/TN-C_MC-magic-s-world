package com.tnc.tnc.magic.wind;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WindChainRegistration {
    public static EntityType<WindBallEntity> BALL;
    public static EntityType<WindVolleyEntity> VOLLEY;
    @SubscribeEvent public static void register(RegisterEvent e){
        e.register(Registries.ENTITY_TYPE,h->{
            BALL=EntityType.Builder.<WindBallEntity>of(WindBallEntity::new,MobCategory.MISC).sized(.6F,.6F).clientTrackingRange(16).updateInterval(1).noSave().build("tnc:wind_ball_cast");
            VOLLEY=EntityType.Builder.<WindVolleyEntity>of(WindVolleyEntity::new,MobCategory.MISC).sized(1,1).clientTrackingRange(16).updateInterval(1).noSave().build("tnc:wind_weapon_volley");
            h.register(id("wind_ball_cast"),BALL);h.register(id("wind_weapon_volley"),VOLLEY);
        });
        e.register(Registries.MOB_EFFECT,h->{for(int chain=0;chain<2;chain++)for(int t=1;t<=5;t++)h.register(id("wind_cast_"+chain+"_"+t),new CastMarker(chain,t));});
    }
    private static ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath("tnc",s);}
    /** A one-tick self effect is the spell release bridge; no optional engine listener or polling. */
    public static final class CastMarker extends MobEffect {
        final int chain,tier;
        CastMarker(int chain,int tier){super(MobEffectCategory.BENEFICIAL,0xAFECDC);this.chain=chain;this.tier=tier;}
    }
}
