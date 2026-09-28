package com.tnc.tnc.magic.compat;

import net.minecraft.world.entity.player.Player;

/** Keep optional SpellEngine symbols out of the combat event class. */
public final class CombatCasting {
    private CombatCasting() {}
    public record Cast(net.minecraft.resources.ResourceLocation id,float progress) {}
    public static void cancel(Player p) {if(SpellEngineBridge.enginePresent())Impl.cancel(p);}
    public static Cast current(Player p) {return SpellEngineBridge.enginePresent()?Impl.current(p):null;}
    private static final class Impl {
        static void cancel(Player p) {
            if(p instanceof net.spell_engine.internals.casting.SpellCasterEntity caster)caster.setSpellCastProcess(null);
        }
        static Cast current(Player p) {
            if(!(p instanceof net.spell_engine.internals.casting.SpellCasterEntity caster))return null;
            var process=caster.getSpellCastProcess();if(process==null)return null;
            return new Cast(process.id(),Math.min(1,(float)process.spellCastTicksSoFar(p.level().getGameTime())/Math.max(1,process.length())));
        }
    }
}
