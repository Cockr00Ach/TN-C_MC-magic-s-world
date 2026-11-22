package com.tnc.tnc.magic.free.client;

import com.tnc.tnc.magic.compat.SpellEngineBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Uses the engine's ordinary aiming, windup, channel, mana and cooldown path. */
public final class EquippedSpellVoiceClient {
    private static ResourceLocation pending,active;
    private static int delay;
    static void queue(String id){pending=ResourceLocation.tryParse(id);delay=3;}
    static String tick(boolean usable){
        if(!SpellEngineBridge.enginePresent()){pending=active=null;return "";}
        return Engine.tick(usable);
    }
    static void clear(){pending=active=null;}
    public static boolean ownsCurrentProcess(){
        if(active==null||Minecraft.getInstance().player==null)return false;
        return Engine.ownsCurrentProcess();
    }
    private static final class Engine {
        static boolean ownsCurrentProcess(){
            var caster=(net.spell_engine.internals.casting.SpellCasterClient)Minecraft.getInstance().player;
            var process=caster.getSpellCastProcess();
            return process!=null&&active.equals(process.id());
        }
        static String tick(boolean usable){
            var player=Minecraft.getInstance().player;
            if(player==null){clear();return "";}
            var caster=(net.spell_engine.internals.casting.SpellCasterClient)player;
            var process=caster.getSpellCastProcess();
            if(active!=null&&(process==null||!active.equals(process.id())))active=null;
            if(!usable){
                pending=null;
                if(active!=null){caster.cancelSpellCast();active=null;}
                return "";
            }
            if(pending==null||--delay>0)return "";
            var id=pending;pending=null;
            if(caster.isCastingSpell())return "当前技能正在施放，请稍后再吟唱。";
            var attempt=caster.startSpellCast(player.getMainHandItem(),id);
            if(!attempt.isSuccess())return "技能未能发动：请检查冷却、魔力、法杖与施法条件。";
            active=id;
            var entry=com.tnc.tnc.magic.SpellCatalog.byId(id);
            return "技能吟唱 · "+(entry==null?id.toString():entry.displayName());
        }
    }
    private EquippedSpellVoiceClient(){}
}
