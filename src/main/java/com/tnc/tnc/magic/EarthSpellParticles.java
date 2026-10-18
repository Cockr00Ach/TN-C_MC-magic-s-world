package com.tnc.tnc.magic;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;

/** Local terrain debris. Fixed cadence, no server particle packets or world edits. */
public final class EarthSpellParticles {
    private EarthSpellParticles() {}
    public static int count(int tier,boolean opening) {return opening?8+tier*2:2+tier;}
    public static void tick(WindEarthVisualEntity entity) {
        if(!entity.level().isClientSide()||entity.tickCount%4!=0||entity.tickCount>=entity.life()-8)return;
        boolean opening=entity.tickCount<=12;
        var block=entity.kind()==6&&entity.tier()>=4?Blocks.ROOTED_DIRT:Blocks.COARSE_DIRT;
        var particle=new BlockParticleOption(ParticleTypes.BLOCK,block.defaultBlockState());
        var owner=entity.followsOwner()?entity.level().getEntity(entity.ownerId()):null;
        var centre=owner==null?entity.position():owner.position();
        int count=count(entity.tier(),opening);
        for(int i=0;i<count;i++) {
            double angle=(i+.37*entity.tickCount)*Math.PI*2/count;
            double radius=entity.kind()==3?.65:entity.radius()*(.3+.45*((i*7)%11)/10.0);
            double y=entity.kind()==3?1.1:entity.kind()==6?.35+.5*(i%3):.15;
            entity.level().addParticle(particle,centre.x+Math.cos(angle)*radius,centre.y+y,centre.z+Math.sin(angle)*radius,
                    Math.cos(angle)*.025,opening?.08:.025,Math.sin(angle)*.025);
        }
    }
}
