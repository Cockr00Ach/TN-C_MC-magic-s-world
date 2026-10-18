package com.tnc.tnc.mana.sight.client;

import net.minecraft.client.Minecraft;
import java.lang.reflect.Field;

/** Optional Oculus phase detection, without linking the common mod to a shader dependency. */
final class ManaParticleShaderCompat {
    private static boolean checked;
    private static Field phase;
    static int skippedOpaquePasses;
    static boolean opaquePass(){
        var engine=Minecraft.getInstance().particleEngine;
        if(!checked){
            checked=true;
            for(var f:engine.getClass().getDeclaredFields()){
                if(f.getType().getName().equals("net.irisshaders.iris.fantastic.ParticleRenderingPhase")){
                    f.setAccessible(true);phase=f;break;
                }
            }
        }
        if(phase==null)return false;
        try{boolean opaque=phase.get(engine) instanceof Enum<?> e&&e.name().equals("OPAQUE");if(opaque)skippedOpaquePasses++;return opaque;}
        catch(IllegalAccessException e){throw new IllegalStateException("Cannot inspect Oculus particle phase",e);}
    }
}
