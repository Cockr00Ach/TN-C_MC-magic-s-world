package com.tnc.tnc.home;

import com.tnc.tnc.adventure.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import java.util.*;

/** MCA is the sole source of relationships; this adapter only authors town residents and a home move. */
public final class McaResidents {
    private static final String ROOT="forge.net.mca.";
    public static boolean available(){return ModList.get().isLoaded("mca");}
    public static boolean nativeResident(Entity entity){return entity.getClass().getName().equals(ROOT+"entity.VillagerEntityMCA");}
    @SuppressWarnings({"unchecked","rawtypes"}) public static Villager create(ServerLevel level,ResidentService.Definition d,BlockPos stand,UUID uuid)throws ReflectiveOperationException{
        var factoryClass=Class.forName(ROOT+"entity.VillagerFactory");var factory=factoryClass.getMethod("newVillager",Level.class).invoke(null,level);
        var genderClass=Class.forName(ROOT+"entity.ai.relationship.Gender");String gender=Set.of("qiao","lan","su").contains(d.id())?"FEMALE":"MALE";
        factoryClass.getMethod("withGender",genderClass).invoke(factory,Enum.valueOf((Class)genderClass,gender));
        factoryClass.getMethod("withName",String.class).invoke(factory,d.name());factoryClass.getMethod("withAge",int.class).invoke(factory,0);
        factoryClass.getMethod("withProfession",net.minecraft.world.entity.npc.VillagerProfession.class).invoke(factory,d.profession());factoryClass.getMethod("withPosition",BlockPos.class).invoke(factory,stand);
        var npc=(Villager)factoryClass.getMethod("build").invoke(factory);npc.setUUID(uuid);npc.setPersistenceRequired();npc.setCustomName(Component.literal(d.name()));npc.setCustomNameVisible(true);return npc;
    }
    @SuppressWarnings("unchecked") public static void setHome(Villager npc,BlockPos bed)throws ReflectiveOperationException{
        var brain=(Brain<?>)npc.getClass().getMethod("getMCABrain").invoke(npc);brain.setMemory(MemoryModuleType.HOME,GlobalPos.of(npc.level().dimension(),bed));
    }
    public static void tick(ServerLevel level){
        var origin=HousingService.sourceOrigin(level);if(origin==null||!com.tnc.tnc.npc.SkyIslandAnchors.isComplete(level))return;
        var all=ResidentService.records(level.getServer());var store=AdventureSavedData.get(level.getServer());
        for(var d:ResidentService.ALL){var record=all.getCompound(d.id());var bed=origin.offset(d.bed());var oldPos=record.contains("Pos")?BlockPos.of(record.getLong("Pos")):bed;
            if(!level.hasChunkAt(oldPos)||!level.hasChunkAt(bed))continue;
            UUID id=record.hasUUID("UUID")?record.getUUID("UUID"):UUID.nameUUIDFromBytes(("tnc:"+origin.asLong()+":"+d.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));var old=level.getEntity(id);
            if(old!=null&&nativeResident(old)){if(!record.getBoolean("NativeMca")){record.putBoolean("NativeMca",true);record.remove("Partner");all.put(d.id(),record);store.setDirty();}continue;}
            if(record.hasUUID("UUID")&&old==null)continue; // An unloaded villager must not be duplicated.
            if(old!=null&&!(old instanceof ResidentEntity))continue;
            if(!(level.getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock))continue;
            var stand=ResidentService.safeBeside(level,bed);if(stand==null)continue;
            try{
                // Distinct deterministic migration ID lets the old resident survive a canceled add.
                UUID nativeId=old==null?id:UUID.nameUUIDFromBytes((id+":mca-v1").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var already=level.getEntity(nativeId);Villager npc;
                if(already!=null){if(!(already instanceof Villager v)||!nativeResident(v))continue;npc=v;}
                else{npc=create(level,d,stand,nativeId);setHome(npc,bed);if(!level.addFreshEntity(npc)){com.mojang.logging.LogUtils.getLogger().error("MCA resident could not join town: {}",d.id());continue;}}
                record.putUUID("UUID",nativeId);record.putLong("Pos",stand.asLong());record.putBoolean("NativeMca",true);record.remove("Partner");all.put(d.id(),record);store.setDirty();
                if(old!=null)old.discard();
            }catch(ReflectiveOperationException|LinkageError ex){com.mojang.logging.LogUtils.getLogger().error("MCA town adapter failed for {}",d.id(),ex);}
        }
    }
    public static String moveFamily(ServerPlayer p){
        if(!available())return "当前未加载凡家物语。";
        if(p.serverLevel()!=p.server.overworld()||!p.isAlive()||p.isSpectator()||com.tnc.tnc.combat.DownedCombat.isDowned(p)||!HousingService.owned(p))return "先回到你自己的住宅。";
        var plot=HousingService.mine(p,PlotCatalog.Kind.HOME);var record=HousingService.home(p);var origin=BlockPos.of(record.getLong("Origin"));if(!plot.contains(p.blockPosition().subtract(origin)))return "请站在你购买的房子里，让伴侣跟随你到家。";
        var bed=HousingService.furnishedBed(p);if(bed==null)return "家中需要两张普通床，并留出床旁活动空间。";
        var nearby=p.serverLevel().getEntitiesOfClass(Villager.class,p.getBoundingBox().inflate(8),McaResidents::nativeResident);
        for(var npc:nearby)try{var rel=npc.getClass().getMethod("getRelationships").invoke(npc);if(!(Boolean)rel.getClass().getMethod("isMarriedTo",UUID.class).invoke(rel,p.getUUID()))continue;
            var current=(Optional<?>)npc.getClass().getMethod("getResidency").invoke(npc).getClass().getMethod("getHome").invoke(npc.getClass().getMethod("getResidency").invoke(npc));if(current.isPresent()&&current.get() instanceof GlobalPos pos&&pos.dimension()==p.level().dimension()&&plot.contains(pos.pos().subtract(origin)))return "伴侣已经把这里当成家。";
            setHome(npc,bed);AdventureService.milestone(p,"family_home");return "已将伴侣的凡家物语住处设为你的家。婚姻、怀孕与孩子继续通过居民原生界面操作。";
        }catch(ReflectiveOperationException e){com.mojang.logging.LogUtils.getLogger().error("MCA family home adapter failed",e);return "原生住处接口未能更新，请用凡家物语的设为住处检查。";}
        return "让已与你结婚的凡家物语伴侣跟随到这里，再输入 /neighbor home。";
    }
}
