package com.tnc.tnc.boss;

import com.google.gson.Gson;
import com.tnc.tnc.magic.DarkFogCloudEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.Spell;

/** Per-cast cloud data: expands the boss domain without changing the player's registry spell. */
final class TNDarkGiantFog extends DarkFogCloudEntity {
    private Spell bossSpell;
    TNDarkGiantFog(EntityType<? extends DarkFogCloudEntity> type,Level level){super(type,level);}
    void prepare(Spell original){
        var copy=new Spell();
        try{
            for(var field:Spell.class.getFields())
                if(!java.lang.reflect.Modifier.isStatic(field.getModifiers()))field.set(copy,field.get(original));
        }catch(IllegalAccessException failure){throw new IllegalStateException("Cannot prepare boss domain",failure);}
        var gson=new Gson();
        copy.release=gson.fromJson(gson.toJson(original.release),Spell.Release.class);
        var cloud=copy.release.target.cloud;
        cloud.volume.radius*=30;
        cloud.impact_tick_interval=Math.max(20,cloud.impact_tick_interval);
        if(cloud.client_data!=null)cloud.client_data.particles=new net.spell_engine.api.spell.ParticleBatch[0];
        bossSpell=copy;
    }
    @Override public Spell getSpell(){return bossSpell==null?super.getSpell():bossSpell;}
}
