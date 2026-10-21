package com.tnc.tnc.arcana;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
/** The prediction and event seed are persisted before the event, so reloads cannot reroll it. */
public final class ArcanaSky extends SavedData {
    private long nextDay=-1,auroraDay=-1;
    public static ArcanaSky get(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(t->{var d=new ArcanaSky();d.nextDay=t.getLong("NextDay");d.auroraDay=t.getLong("AuroraDay");return d;},ArcanaSky::new,"tnc_arcana_sky");}
    public static void tick(ServerLevel l){var d=get(l);long day=l.getServer().overworld().getGameTime()/24000;
        if(d.nextDay<0){d.nextDay=day+5+Math.floorMod(l.getSeed(),4);d.setDirty();}
        if(day>=d.nextDay){d.auroraDay=day;d.nextDay=day+5+Math.floorMod(l.getSeed()^day*883L,4);d.setDirty();}
    }
    public static boolean aurora(ServerLevel l){tick(l);long time=Math.floorMod(l.getDayTime(),24000);return get(l).auroraDay==l.getServer().overworld().getGameTime()/24000&&time>=12500&&time<=23500&&l.dimensionType().hasSkyLight();}
    public static void forceTonight(ServerLevel l){tick(l);var d=get(l);d.auroraDay=l.getServer().overworld().getGameTime()/24000;d.setDirty();l.setDayTime(l.getDayTime()-Math.floorMod(l.getDayTime(),24000)+18000);}
    public static String forecast(ServerLevel l){tick(l);return "下一次极光约在 "+Math.max(0,get(l).nextDay-l.getServer().overworld().getGameTime()/24000)+" 个世界日后";}
    @Override public CompoundTag save(CompoundTag t){t.putLong("NextDay",nextDay);t.putLong("AuroraDay",auroraDay);return t;}
}
