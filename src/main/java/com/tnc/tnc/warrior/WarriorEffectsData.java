package com.tnc.tnc.warrior;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Cosmetic choice only, independent from talent, damage and plot ownership. */
public final class WarriorEffectsData extends SavedData {
    private final Map<UUID,WarriorEffectStyle> styles=new HashMap<>();
    public static WarriorEffectsData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(WarriorEffectsData::load,WarriorEffectsData::new,"tnc_warrior_effects_v1");}
    public WarriorEffectStyle style(UUID id){return styles.getOrDefault(id,WarriorEffectStyle.STEEL);}
    public void set(UUID id,WarriorEffectStyle style){styles.put(id,style);setDirty();}
    public static WarriorEffectsData load(CompoundTag tag){var d=new WarriorEffectsData();for(Tag raw:tag.getList("Styles",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var s=WarriorEffectStyle.find(t.getString("Style"));if(t.hasUUID("Id")&&s!=null)d.styles.put(t.getUUID("Id"),s);}return d;}
    @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();styles.forEach((id,s)->{var t=new CompoundTag();t.putUUID("Id",id);t.putString("Style",s.id);list.add(t);});tag.put("Styles",list);return tag;}
}
