package com.tnc.tnc.magic.wind;

import com.tnc.tnc.magic.*;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.lang.ref.WeakReference;
import java.util.*;

/** A single attack path: inward wind, then one modeled weapon that owns its damage. */
public final class WindArsenal {
    private static final Map<LivingEntity,List<WeakReference<WindWeaponEntity>>> OWNED=new WeakHashMap<>();
    private WindArsenal(){}
    public static double attackAge(TNWindSpiritEntity e,float partial){return e.level().getGameTime()+partial-e.windAttackTime();}
    public static int form(TNWindSpiritEntity e){return e.windWeaponForm();}
    public static Vec3 direction(TNWindSpiritEntity e){return e.windAttackDirection();}
    public static WindWeaponEntity attack(TNWindSpiritEntity spirit,LivingEntity owner){
        if(!(spirit.level() instanceof ServerLevel level)||!owner.isAlive()||owner.level()!=level)return null;
        double radius=WindArsenalRules.radius(spirit.tier());
        var enemies=level.getEntitiesOfClass(LivingEntity.class,spirit.getBoundingBox().inflate(radius),
                e->e!=owner&&!(e instanceof Player)&&e.isAlive()&&!e.isSpectator()&&!e.isAlliedTo(owner));
        enemies.removeIf(e->e.distanceToSqr(spirit)>radius*radius||!owner.hasLineOfSight(e));
        enemies.sort(Comparator.comparingDouble(e->e.distanceToSqr(spirit)));
        if(enemies.isEmpty())return null;
        Vec3 centre=spirit.position().add(0,.25,0);
        for(int i=0;i<Math.min(12,enemies.size());i++){
            var enemy=enemies.get(i);var delta=centre.subtract(enemy.getBoundingBox().getCenter());
            double force=(.08+spirit.tier()*.012)*(1-Math.min(1,delta.length()/radius));
            var motion=enemy.getDeltaMovement().add(delta.normalize().scale(force));
            if(motion.lengthSqr()>.55*.55)motion=motion.normalize().scale(.55);
            enemy.setDeltaMovement(motion);enemy.hurtMarked=true;
        }
        var mine=OWNED.computeIfAbsent(owner,k->new ArrayList<>());
        mine.removeIf(ref->{var weapon=ref.get();return weapon==null||!weapon.isAlive();});
        if(mine.size()>=WindArsenalRules.MAX_PROJECTILES)return null;
        Vec3 aim=enemies.get(0).getBoundingBox().getCenter().subtract(centre).normalize();
        if(aim.lengthSqr()<.01)aim=new Vec3(0,0,1);
        int form=WindArsenalRules.weapon(spirit.tier(),spirit.slot(),level.getGameTime()/10);
        var weapon=WindArsenalRegistration.WEAPON.create(level);if(weapon==null)return null;
        weapon.configure(owner,centre.add(aim.scale(.6)),aim,form,spirit.tier(),4*TNWindMechanics.damageMultiplierForTier(spirit.tier()));
        if(!level.addFreshEntity(weapon))return null;
        mine.add(new WeakReference<>(weapon));
        spirit.markWindAttack(form,aim);
        return weapon;
    }
}
