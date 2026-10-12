package com.tnc.tnc.client;

import com.tnc.tnc.magic.EarthParticles;
import net.minecraft.client.particle.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class EarthDustParticle extends DustParticle {
    private EarthDustParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites){
        super(level,x,y,z,vx,vy,vz,new DustParticleOptions(new Vector3f(.48F,.27F,.10F),1.4F),sprites);
        setColor(.48F,.27F,.10F);
    }
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event){
        event.registerSpriteSet(EarthParticles.DUST.get(),sprites->(type,level,x,y,z,vx,vy,vz)->new EarthDustParticle(level,x,y,z,vx,vy,vz,sprites));
    }
}
