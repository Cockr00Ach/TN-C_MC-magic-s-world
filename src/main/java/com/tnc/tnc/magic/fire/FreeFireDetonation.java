package com.tnc.tnc.magic.fire;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Lets free-form finishers settle damage through the fixed fire chain's confirmed-hit rule. */
public final class FreeFireDetonation {
    private FreeFireDetonation() {}

    public static boolean hit(ServerPlayer caster, LivingEntity victim, float damage) {
        return TNFireRework.detonationDamage(caster, victim, damage);
    }
}
