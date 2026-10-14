package com.tnc.tnc.boss;

import com.tnc.tnc.magic.water.TNWaterFieldEntity;
import com.tnc.tnc.magic.water.TNWaterSpellEntity;
import net.minecraft.world.entity.LivingEntity;

/** Uses the same real water entities/renderers as the player's spell chains. */
public final class LeviathanSpells {
    private LeviathanSpells() {}

    public static boolean cast(TNWaterLeviathanEntity boss, LivingEntity target, String spell) {
        if (target==null || !boss.isAlive() || !target.isAlive()) return false;
        return switch(spell) {
            case "tsunami", "sea_god_crypt" -> TNWaterFieldEntity.castBoss(boss,target,spell);
            case "dragon_roar", "dragon_ruin" -> TNWaterSpellEntity.castBoss(boss,target,spell);
            default -> false;
        };
    }
}
