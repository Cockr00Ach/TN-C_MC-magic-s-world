package com.tnc.tnc.magic.free;

import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.SpellCatalog;
import java.util.*;

/** Exact authored skill names take priority only when learned and equipped. */
public final class EquippedSpellVoice {
    public record Match(SpellCatalog.Entry spell,int slot) {}
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
