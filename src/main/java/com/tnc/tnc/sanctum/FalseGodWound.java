package com.tnc.tnc.sanctum;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.Vec3;

/** The low claw can be reached by an ordinary weapon. Its carrier routes the original attack
 * through the body's normal Forge hooks, armour, phase locks and shared invulnerability frames. */
final class FalseGodWound extends IronGolem implements net.minecraft.world.entity.monster.Enemy {
    private final FalseGodFight fight;

    FalseGodWound(FalseGodFight fight,Vec3 feet,float yaw) {
        super(EntityType.IRON_GOLEM,fight.level);
        this.fight=fight;
        moveTo(feet.x,feet.y,feet.z,yaw,0);
        noPhysics=true;
        setNoAi(true);setNoGravity(true);setSilent(true);setPersistenceRequired();
        setCustomName(net.minecraft.network.chat.Component.literal("伪神 · 地面裂痕"));
        addTag("tnc_false_god_wound");
        SanctumAppearance.mark(this,fight.phase==1?"angel_seal":"wound");
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        return fight.body!=null && fight.body.isAlive() && fight.body.hurt(source,amount);
    }
    @Override public boolean isPushable(){return false;}
    @Override public boolean shouldBeSaved(){return false;}
}
