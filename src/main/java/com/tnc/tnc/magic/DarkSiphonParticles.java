package com.tnc.tnc.magic;

import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Moving blood beads: every strand flows from enemy to caster, never the reverse. */
public final class DarkSiphonParticles {
    private static final DustParticleOptions BLOOD=new DustParticleOptions(new Vector3f(.65F,.012F,.035F),.7F);
    private DarkSiphonParticles(){}
    public static void tick(TNDarkDrainEntity entity) {
        if(!entity.level().isClientSide()||entity.tickCount%4!=0)return;
        var a=entity.level().getEntity(entity.victimId());var b=entity.level().getEntity(entity.casterId());
        if(!(a instanceof LivingEntity victim)||!(b instanceof LivingEntity caster)||!a.isAlive()||!b.isAlive())return;
        Vec3 source=victim.position().add(0,victim.getBbHeight()*.55,0);
        Vec3 end=caster.position().add(0,caster.getBbHeight()*.62,0);
        Vec3 delta=end.subtract(source),side=delta.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<1e-6)side=new Vec3(1,0,0);else side=side.normalize();
        for(int strand=0;strand<DarkSiphonRules.strands(entity.tier());strand++)for(int bead=0;bead<DarkSiphonRules.BEADS;bead++) {
            double t=DarkSiphonRules.flow(entity.tickCount,bead);
            double fan=Math.sin(t*Math.PI)*(.09+strand*.14);
            double turn=t*5+strand*Math.PI*2/DarkSiphonRules.strands(entity.tier());
            Vec3 p=source.add(delta.scale(t)).add(side.scale(fan*Math.cos(turn)))
                    .add(0,fan*Math.sin(turn)-.24*Math.sin(t*Math.PI),0);
            Vec3 motion=delta.normalize().scale(.08);
            entity.level().addParticle(BLOOD,p.x,p.y,p.z,motion.x,motion.y,motion.z);
        }
    }
}
