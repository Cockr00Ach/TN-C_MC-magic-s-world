package com.tnc.tnc.life.routes.mech;

import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.fire.*;
import com.tnc.tnc.magic.wind.WindChainRegistration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Prepare the actual existing spell entity without spawning it or charging personal mana. */
public final class ZeroSpellPayload {
    public static Entity prepare(ServerPlayer p,ZeroRules.Form form,Vec3 muzzle){
        switch(form){
            case WATER -> {
                var e=TNOrbEntities.WATER_BOLT.get().create(p.serverLevel());
                if(e!=null)e.configure(p,2,muzzle,p.getLookAngle().scale(2));
                return e;
            }
            case FIRE -> {
                var e=TNOrbEntities.FIRE_BOLT.get().create(p.serverLevel());
                if(e!=null)e.configure(p,FireSpellRules.bolt(form.spell),form.spell,muzzle,p.getLookAngle().scale(TNFireBoltEntity.LAUNCH_SPEED));
                return e;
            }
            case WIND -> {
                // The normal hand-cast four-field limiter must not replace automatic rounds.
                var e=WindChainRegistration.BALL.create(p.serverLevel());
                if(e!=null){e.configure(p,2);e.setPos(muzzle);}
                return e;
            }
            default -> {
                if(!com.tnc.tnc.magic.compat.SpellEngineBridge.enginePresent())return null;
                return Engine.prepare(p,form,muzzle);
            }
        }
    }
    /** Isolated to keep a missing optional engine from breaking dedicated/headless test startup. */
    private static final class Engine {
        static Entity prepare(ServerPlayer p,ZeroRules.Form form,Vec3 muzzle){
            var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc",form.spell);
            var spell=net.spell_engine.internals.SpellRegistry.getSpell(id);
            if(spell==null||spell.release==null||spell.release.target==null||spell.release.target.projectile==null)return null;
            var shot=spell.release.target.projectile;
            var context=new net.spell_engine.internals.SpellHelper.ImpactContext().position(muzzle)
                    .power(net.spell_power.api.SpellPower.getSpellPower(spell.school,p));
            // Same constructor, perks, speed, range and spell ID as SpellHelper.shootProjectile.
            // Returning the entity lets the caller charge only an accepted server spawn.
            var e=new net.spell_engine.entity.SpellProjectile(p.level(),p,muzzle.x,muzzle.y,muzzle.z,
                    net.spell_engine.entity.SpellProjectile.Behaviour.FLY,id,null,context,shot.projectile.perks.copy());
            var dir=p.getLookAngle();net.minecraft.world.entity.projectile.Projectile projectile=e;
            projectile.shoot(dir.x,dir.y,dir.z,shot.launch_properties.velocity,shot.projectile.divergence);
            e.range=spell.range;projectile.setXRot(p.getXRot());projectile.setYRot(p.getYRot());
            return e;
        }
    }
    private ZeroSpellPayload(){}
}
