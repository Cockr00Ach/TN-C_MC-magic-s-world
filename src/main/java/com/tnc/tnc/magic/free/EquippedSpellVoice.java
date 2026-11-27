package com.tnc.tnc.magic.free;

import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.SpellCatalog;
import java.util.*;

/** Exact authored skill names take priority only when learned and equipped. */
public final class EquippedSpellVoice {
    public record Match(SpellCatalog.Entry spell,int slot) {}
    /** Repair a whole spoken skill name against the player's current loadout before
     * a free shape alias (such as 水球 -> 水弹) can discard the intended bound skill. */
    public static String repairName(String speech, MagicStoneData data) {
        if (data == null || speech == null || speech.isBlank() || containsName(speech)) return speech;
        String command = compact(speech);
        String prefix = command.startsWith("技能") ? "技能" : "";
        String heard = command.substring(prefix.length());
        if (heard.length() < 2) return speech;
        if (prefix.isEmpty() && (WaterSpeech.isExactSpelling(heard)
                || Arrays.stream(ElementalIntent.Ability.values()).anyMatch(a -> a.aliases.contains(heard)))) {
            return speech;
        }
        String name = null;
        for (int slot = 0; slot < MagicStoneData.LOADOUT_SLOTS; slot++) {
            var id = data.getSlot(slot);
            if (id == null || !data.hasLearned(id)) continue;
            var spell = SpellCatalog.byId(id);
            if (spell == null || !ElementalSpeech.samePronunciation(heard, spell.displayName())) continue;
            if (name != null && !name.equals(spell.displayName())) return speech;
            name = spell.displayName();
        }
        return name == null ? speech : prefix + name;
    }
    public static boolean containsName(String speech){
        String text=compact(speech);
        return SpellCatalog.all().stream().anyMatch(e->text.contains(compact(e.displayName())));
    }
    public static Optional<Match> match(String speech,MagicStoneData data){
        if(data==null)return Optional.empty();
        String text=compact(speech);SpellCatalog.Entry best=null;int end=-1,length=-1;
        var free=FreeMagicIntent.parse(speech);
        boolean composition=free.map(i->i.modifiers()!=0||i.mode()!=FreeMagicIntent.Mode.SINGLE).orElse(false);
        for(var entry:SpellCatalog.all()){
            String name=compact(entry.displayName());
            // "巨大水球" remains an authored free form; "技能水球" explicitly chooses the bound spell.
            if(composition&&!text.contains("技能")&&name.length()<=2)continue;
            int at=text.lastIndexOf(name);if(at<0)continue;
            int nextEnd=at+name.length();
            if(nextEnd>end||nextEnd==end&&name.length()>length){best=entry;end=nextEnd;length=name.length();}
        }
        if(best==null||!data.hasLearned(best.id()))return Optional.empty();
        for(int slot=0;slot<MagicStoneData.LOADOUT_SLOTS;slot++)
            if(best.id().equals(data.getSlot(slot)))return Optional.of(new Match(best,slot));
        return Optional.empty();
    }
    private static String compact(String text){return FreeMagicIntent.clean(text).replaceAll("[\\s\\p{P}]","");}
    private EquippedSpellVoice(){}
}
