package com.tnc.tnc.boss;

import com.tnc.tnc.magic.TNMagicCircleEntity;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.world.phys.Vec3;

/** A committed T5 windup: rise, hover on a large seal, release, and return safely. */
final class DarkGiantSkyCast {
    static final int RISE_TICKS=12,HOLD_TICKS=12,RETURN_TICKS=20;
    final Vec3 home,landing,hover;
    final boolean previousGravity;
    private int age,recovery;
    private TNMagicCircleEntity circle;
    DarkGiantSkyCast(TNDarkGiantEntity boss,Vec3 landing){
        this.home=boss.position();this.landing=landing;this.hover=landing.add(0,44,0);
        previousGravity=boss.isNoGravity();boss.setNoGravity(true);
    }
    static Vec3 risePosition(Vec3 from,Vec3 hover,int tick){
        double t=Math.min(1,tick/(double)RISE_TICKS),smooth=t*t*(3-2*t);
        Vec3 horizontal=from.lerp(hover,smooth);
        return new Vec3(horizontal.x,from.y+(hover.y-from.y)*Math.min(1,t*1.7),horizontal.z);
    }
    void windup(TNDarkGiantEntity boss){
        age++;place(boss,risePosition(home,hover,age));
        if(age>=RISE_TICKS&&circle==null){
            circle=TNOrbEntities.MAGIC_CIRCLE.get().create(boss.level());
            if(circle!=null){
                circle.configure(Math.max(28,boss.getBbWidth()*1.5),140,TNMagicCircleEntity.STYLE_DARK_RITUAL);
                circle.setPos(boss.position().add(0,-.15,0));boss.level().addFreshEntity(circle);
            }
        }
        follow(boss);
    }
    boolean recover(TNDarkGiantEntity boss){
        recovery++;
        double t=Math.max(0,Math.min(1,(recovery-HOLD_TICKS)/(double)RETURN_TICKS));
        place(boss,hover.lerp(home,t*t*(3-2*t)));follow(boss);
        if(recovery<HOLD_TICKS+RETURN_TICKS)return false;
        close(boss);return true;
    }
    private void place(TNDarkGiantEntity boss,Vec3 position){
        boss.getNavigation().stop();boss.setPos(position);boss.setDeltaMovement(Vec3.ZERO);boss.fallDistance=0;
    }
    private void follow(TNDarkGiantEntity boss){if(circle!=null)circle.setPos(boss.position().add(0,-.15,0));}
    void close(TNDarkGiantEntity boss){boss.setNoGravity(previousGravity);boss.fallDistance=0;if(circle!=null)circle.discard();}
}
