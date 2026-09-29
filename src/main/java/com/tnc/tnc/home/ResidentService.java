package com.tnc.tnc.home;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.*;
import com.tnc.tnc.npc.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ResidentService {
    public record Definition(String id,String name,BlockPos bed,String preference,VillagerProfession profession){}
    public static final List<Definition> ALL=List.of(
        new Definition("lin","林禾",new BlockPos(324,95,246),"bread",VillagerProfession.FARMER),
        new Definition("qiao","乔麦",new BlockPos(324,95,248),"vegetable",VillagerProfession.FARMER),
        new Definition("lan","蓝汐",new BlockPos(370,88,279),"fish",VillagerProfession.FISHERMAN),
        new Definition("yan","燕岚",new BlockPos(370,88,283),"sweet",VillagerProfession.LIBRARIAN),
        new Definition("he","何舟",new BlockPos(226,97,341),"meat",VillagerProfession.BUTCHER),
        new Definition("su","苏苒",new BlockPos(228,97,341),"soup",VillagerProfession.CLERIC));
    private static final Map<UUID,UUID> SESSIONS=new HashMap<>();
    private static final Map<Long,Long> LOADED_SINCE=new HashMap<>();
    public static Definition definition(String id){return ALL.stream().filter(v->v.id.equals(id)).findFirst().orElse(null);}
    static CompoundTag records(net.minecraft.server.MinecraftServer s){var d=AdventureSavedData.get(s);if(!d.housing.contains("Residents",10))d.housing.put("Residents",new CompoundTag());return d.housing.getCompound("Residents");}
    static CompoundTag relation(ServerPlayer p,String id){
        var store=AdventureSavedData.get(p.server);if(!store.housing.contains("Relations",10))store.housing.put("Relations",new CompoundTag());
        var all=store.housing.getCompound("Relations");String key=p.getUUID()+":"+id;if(!all.contains(key,10)){var t=new CompoundTag();t.putLong("Epoch",-1);t.putLong("MealEpoch",-1);all.put(key,t);store.setDirty();}return all.getCompound(key);
    }
    public static String stage(int value){return value>=85?"伴侣意愿":value>=70?"亲密":value>=45?"朋友":value>=20?"熟悉":"陌生";}
    private static Component choice(String label,String action){return Component.literal("["+label+"] ").withStyle(s->s.withColor(net.minecraft.ChatFormatting.GOLD).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/neighbor "+action)));}
    public static void open(ServerPlayer p,ResidentEntity npc){
        var def=definition(npc.identity());if(def==null||!p.isAlive()||p.isSpectator()||com.tnc.tnc.combat.DownedCombat.isDowned(p))return;
        SESSIONS.put(p.getUUID(),npc.getUUID());var r=relation(p,def.id);
        p.sendSystemMessage(Component.literal(def.name+" · 成年邻居 · "+stage(r.getInt("Affection"))+"（"+r.getInt("Affection")+"/100）\n喜欢："+preference(def.preference)+"。手持料理赠食；同一种连续赠送收益减半。"));
        p.sendSystemMessage(Component.empty().append(choice("送一份料理","gift")).append(choice("一起吃饭 · 两份","dine")).append(choice("邀请共同生活","live")).append(choice("结束共同生活","farewell")));
        AdventureService.milestone(p,"neighbor_met");
    }
    private static String preference(String s){return switch(s){case "bread"->"面包与烘焙";case "vegetable"->"蔬菜";case "fish"->"鱼料理";case "sweet"->"甜食";case "meat"->"熟肉";default->"汤与炖菜";};}
    static boolean likes(Definition d,String item){return switch(d.preference){case "bread"->item.contains("bread")||item.contains("pie");case "vegetable"->item.contains("carrot")||item.contains("potato")||item.contains("salad");case "fish"->item.contains("fish")||item.contains("salmon")||item.contains("cod");case "sweet"->item.contains("cookie")||item.contains("berry")||item.contains("pie");case "meat"->item.contains("cooked_")&&!item.contains("fish")&&!item.contains("cod")&&!item.contains("salmon");default->item.contains("soup")||item.contains("stew")||item.contains("feast");};}
    public static String act(ServerPlayer p,String action){
        if(p.serverLevel()!=p.server.overworld())return "生活互动仅在主世界住宅区办理。";
        var session=SESSIONS.get(p.getUUID());var entity=session==null?null:p.serverLevel().getEntity(session);
        if(!(entity instanceof ResidentEntity npc)||npc.distanceToSqr(p)>64||!p.isAlive()||p.isSpectator()||com.tnc.tnc.combat.DownedCombat.isDowned(p))return "请先到邻居身边右键交流。";
        var def=definition(npc.identity());if(def==null||npc.isBaby())return "这位居民不能参与该互动。";
        return action.equals("live")?invite(p,npc):action.equals("farewell")?leave(p,npc):food(p,npc,def,action.equals("dine"));
    }
    static String food(ServerPlayer p,ResidentEntity npc,Definition d,boolean dine){
        var held=p.getMainHandItem();if(!held.isEdible())return "请主手拿着可食用料理。";
        var r=relation(p,d.id);long epoch=AdventureSavedData.get(p.server).activeTicks/24000;
        if(r.getLong("Epoch")!=epoch){r.putLong("Epoch",epoch);r.putInt("Gifts",0);}
        if(dine&&r.getLong("MealEpoch")==epoch)return "这一轮已经共餐过。远征归来再一起吃饭吧。";
        if(!dine&&r.getInt("Gifts")>=2)return "这一轮两次赠食已经够啦，20分钟有效游戏时间后再带新菜。";
        if(dine&&!p.canEat(held.getFoodProperties(p).canAlwaysEat()))return "现在不饿，稍后再共餐。";
        int amount=dine?2:1;if(held.getCount()<amount)return "共餐需要两份同种料理，一人一份。";
        String item=ForgeRegistries.ITEMS.getKey(held.getItem()).toString();
        // Only the offered main-hand stack is spent; no matching substitute elsewhere.
        int slot=p.getInventory().selected;var offered=held.copy();var result=held.copyWithCount(1);var container=com.tnc.tnc.life.FoodContainers.remainder(held);
        var start=new net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start(p,result,held.getUseDuration());
        if(dine&&net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(start))return "当前不能共餐，料理未消耗。";
        var direct=new InventoryTransaction(p.getInventory());if(!direct.takeFromSlot(slot,amount))return "料理数量变动，请重试。";
        if(!container.isEmpty()&&!direct.add(container.copyWithCount(1)))return "请为返还容器腾出位置；料理未扣除。";
        direct.commit();
        int gain=likes(d,item)?8:4;if(item.contains("rotten_flesh")||item.contains("spider_eye"))gain=1;if(item.equals(r.getString("LastDish")))gain=Math.max(1,gain/2);
        r.putInt("Affection",Math.min(100,r.getInt("Affection")+gain+(dine?4:0)));r.putString("LastDish",item);
        var dishes=r.getList("Dishes",8);if(dishes.stream().noneMatch(v->v.getAsString().equals(item)))dishes.add(StringTag.valueOf(item));r.put("Dishes",dishes);
        if(dine){
            r.putLong("MealEpoch",epoch);r.putInt("Meals",r.getInt("Meals")+1);
            var after=result.finishUsingItem(p.level(),p);var finish=new net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish(p,offered.copyWithCount(1),start.getDuration(),after);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(finish);after=finish.getResultStack();
            if(!after.isEmpty()){var returns=new InventoryTransaction(p.getInventory());if(returns.add(after))returns.commit();else p.drop(after,false);}
            com.tnc.tnc.life.LifeEvents.taste(p,offered.getItem());AdventureService.milestone(p,"shared_meal");
        }
        else r.putInt("Gifts",r.getInt("Gifts")+1);
        AdventureSavedData.get(p.server).setDirty();return d.name+"："+(likes(d,item)?"这正合我的口味。":"谢谢你带来的料理。")+" 好感 "+r.getInt("Affection")+"/100。";
    }
    static String invite(ServerPlayer p,ResidentEntity npc){
        if(p.serverLevel()!=p.server.overworld()||npc.level()!=p.server.overworld())return "生活互动仅在主世界住宅区办理。";
        var r=relation(p,npc.identity());if(r.getInt("Affection")<70||r.getList("Dishes",8).size()<3||r.getInt("Meals")<3)return "邻居还没准备好：需要亲密70、三种料理和三次共餐。可以选择一直做朋友。";
        if(!HousingService.owned(p))return "需要你自己购买并装修的住宅。";
        var bed=HousingService.furnishedBed(p);var stand=bed==null?null:safeBeside(p.server.overworld(),bed);if(stand==null)return "家中需要两张床和床旁两格高的安全活动空间。";
        var record=records(p.server).getCompound(npc.identity());if(record.hasUUID("Partner"))return "这位邻居已有共同生活的约定。";
        for(var d:ALL){var other=records(p.server).getCompound(d.id);if(other.hasUUID("Partner")&&other.getUUID("Partner").equals(p.getUUID()))return "已有一位同住邻居。请先明确结束原来的约定。";}
        if(!p.server.overworld().hasChunkAt(stand))return "住宅区块未加载，请先回家看看。";
        // Clicking the explicit invitation is the player's choice; the adult resident agrees only after readiness checks.
        record.putUUID("Partner",p.getUUID());record.putLong("Pos",stand.asLong());npc.teleportTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5);
        AdventureSavedData.get(p.server).setDirty();AdventureService.milestone(p,"living_together");return npc.getName().getString()+"：我愿意搬来共同生活。远征多久都不会扣钱或掉好感。";
    }
    static String leave(ServerPlayer p,ResidentEntity npc){
        if(p.serverLevel()!=p.server.overworld()||npc.level()!=p.server.overworld())return "生活互动仅在主世界住宅区办理。";
        var record=records(p.server).getCompound(npc.identity());if(!record.hasUUID("Partner")||!record.getUUID("Partner").equals(p.getUUID()))return "你们目前没有共同生活的约定。";
        var origin=HousingService.sourceOrigin(p.server.overworld());var d=definition(npc.identity());var stand=origin==null?null:safeBeside(p.server.overworld(),origin.offset(d.bed));if(stand==null)return "原住处区块未加载，先走近原住宅再办理。";
        record.remove("Partner");record.putLong("Pos",stand.asLong());npc.teleportTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5);AdventureSavedData.get(p.server).setDirty();return "已结束共同生活。友情保留，邻居回到原住宅。";
    }
    public static BlockPos safeBeside(ServerLevel level,BlockPos bed){
        for(int y=0;y<=1;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
            var p=bed.offset(x,y,z);if(!level.hasChunkAt(p)||!level.hasChunkAt(p.below()))continue;
            if(level.getBlockState(p).getCollisionShape(level,p).isEmpty()&&level.getBlockState(p.above()).getCollisionShape(level,p.above()).isEmpty()&&level.getBlockState(p.below()).isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP)&&level.getFluidState(p).isEmpty())return p;
        }return null;
    }
    public static void tick(ServerLevel level){
        if(!SkyIslandAnchors.isComplete(level))return;var origin=HousingService.sourceOrigin(level);if(origin==null)return;var store=AdventureSavedData.get(level.getServer());var all=records(level.getServer());
        for(var d:ALL){
            var record=all.getCompound(d.id);var position=record.contains("Pos")?BlockPos.of(record.getLong("Pos")):origin.offset(d.bed);long chunk=net.minecraft.world.level.ChunkPos.asLong(position);
            if(!level.hasChunkAt(position)){LOADED_SINCE.remove(chunk);continue;}
            long since=LOADED_SINCE.computeIfAbsent(chunk,k->level.getGameTime());if(level.getGameTime()-since<40)continue;
            UUID id=record.hasUUID("UUID")?record.getUUID("UUID"):UUID.nameUUIDFromBytes(("tnc:"+origin.asLong()+":"+d.id).getBytes(StandardCharsets.UTF_8));
            if(level.getEntity(id)!=null||record.hasUUID("UUID"))continue; // Never mistake an unloaded entity for a missing one.
            // Known resident locations, never arbitrary bed scanning or chunk forcing.
            var bed=origin.offset(d.bed);if(!record.hasUUID("UUID")&&!(level.getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock))continue;
            var stand=record.hasUUID("UUID")?safeBeside(level,position):safeBeside(level,bed);if(stand==null)continue;
            var npc=TNNpcs.RESIDENT.get().create(level);if(npc==null)continue;npc.setUUID(id);npc.identity(d.id);npc.setCustomName(Component.literal(d.name));npc.setCustomNameVisible(true);npc.setVillagerData(npc.getVillagerData().setProfession(d.profession));npc.moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5,180,0);
            if(level.addFreshEntity(npc)){record.putUUID("UUID",id);record.putLong("Pos",stand.asLong());all.put(d.id,record);store.setDirty();}
        }
    }
    public static void clear(){SESSIONS.clear();LOADED_SINCE.clear();}
}
