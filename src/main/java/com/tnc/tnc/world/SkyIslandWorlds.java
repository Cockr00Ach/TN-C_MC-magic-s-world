package com.tnc.tnc.world;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
/** Central routing point for the pending island migration. Existing complete islands stay put. */
public final class SkyIslandWorlds {
 static final class Choice extends SavedData {
  boolean realm;
  static Choice get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(t->{var c=new Choice();c.realm=t.getBoolean("Realm");return c;},Choice::new,"tnc_sky_world_choice");}
  public CompoundTag save(CompoundTag t){t.putBoolean("Realm",realm);return t;}
 }
 public static boolean separate(MinecraftServer s){return Choice.get(s).realm;}
 public static ServerLevel island(MinecraftServer s){
  if(!separate(s))return s.overworld();
  var target=s.getLevel(com.tnc.tnc.world.skyhaven.SkyhavenContent.KEY);
  if(target==null)throw new IllegalStateException("天空岛维度缺失；停止施工，不能回退并覆盖主世界");
  return target;
 }
 /** Only invoked before a fresh island starts; never opts a completed legacy island in. */
 static void chooseFresh(MinecraftServer s){var legacy=SkyIslandSavedData.get(s.overworld());if(legacy.version!=0||legacy.phase!=SkyIslandSavedData.Phase.IDLE)throw new IllegalStateException("已有岛屿不自动迁移");var c=Choice.get(s);c.realm=true;c.setDirty();}
 private SkyIslandWorlds(){}
}
