package com.tnc.tnc.magic.water;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Resolve players and real mob casters without fake players or cached world references. */
final class WaterSpellOwners {
    private WaterSpellOwners() {}

    static LivingEntity resolve(ServerLevel level, UUID id) {
        if (id == null) return null;
        LivingEntity caster = level.getServer().getPlayerList().getPlayer(id);
        if (caster == null && level.getEntity(id) instanceof LivingEntity living) caster = living;
        if (caster == null || !caster.isAlive() || caster.level() != level) return null;
        if (caster instanceof Player p && (p.isSpectator()
                || com.tnc.tnc.combat.DownedCombat.isDowned(p))) return null;
        return caster;
    }
}
