package com.tnc.tnc.adventure;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Optional, persistent life stories. MCA remains the authority for friendship and family. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class TownStories {
    public record Chapter(String title,String scene,String goal,int coins,int xp){}
    public record Story(String id,String title,String opening,List<Chapter> chapters,String keepsake,String unlock){}
    public static final List<Story> ALL=List.of(
        new Story("supper","酒馆的一桌晚餐","Self说，旅人留下来的理由，有时只是有人替他留了一把椅子。艾琳想请你一起准备今晚的饭。",List.of(
            new Chapter("篮里的第一份收成","林禾把空菜篮交给你：先让这张桌子有点田野的味道吧。","交付普通小麦×8、胡萝卜×8。",25,40),
            new Chapter("锅里冒出的香气","何舟愿意看火，乔麦正在擦桌子。没有冒险者等级的门槛，做一顿热饭就够了。","交付熟牛肉×4、烤马铃薯×4。",35,60),
            new Chapter("给你留的位置","桌上还有一杯热奶的位置。去叫两位邻居，回来把最后的食材交给艾琳。","本章右键交流两位不同成年镇民；交付牛奶桶×1、鸡蛋×4，返还桶。",60,100)),"一桌晚餐的合影页","feast_table"),
        new Story("journey","带回远方的见闻","蓝汐说，岛外还有许多河流。去看没有人催你征服的世界，再把故事带回来。",List.of(
            new Chapter("三种远方的颜色","不需要击败任何首领。脚下的土地、树影和风，就是这趟旅途的收获。","接取后在岛外亲自进入三种不同群系。每五秒记录一次。",30,80),
            new Chapter("纸上的山与海","燕岚已经替你空出一页。将见闻写下来，后来的人就不必只听勇者的战绩。","交付普通纸×3、墨囊×1。",35,60),
            new Chapter("有人愿意听","回到熟悉的街道，与三位邻居说说你看到的地方。每个人都会记得你带回了故事。","本章右键交流三位不同成年镇民，再向艾琳归档。",65,100)),"远方的盖章札记","travel_lamp"),
        new Story("home","为自己点一盏灯","米洛说，房契是一张纸，归处却要由你亲手慢慢建立。没有人替你决定住在哪一栋。",List.of(
            new Chapter("门牌下的名字","去银行看房、实地走一走。只在你选好并正式买下后，这一章才算完成。","拥有一栋已完成交付的自有住宅。",30,60),
            new Chapter("空屋里的温度","织夏说，先有一张床、一盏灯和一件喜欢的家具，就可以开始生活了。","自家房屋边界内实际放好普通床、亮度至少5的光源、沉浸家具。",45,80),
            new Chapter("第一夜，第一封归信","灯还亮着，床也不再是陌生的。完整地在自己的家中睡一夜，再回来告诉艾琳。","本章在自家床完整睡过一夜，回酒馆归档。",75,100)),"写着门牌的归处纪念页","memory_shelf"));
    public static Story find(String id){return ALL.stream().filter(s->s.id().equals(id)).findFirst().orElse(null);}
    static CompoundTag journal(ServerPlayer p){
        var store=AdventureSavedData.get(p.server);if(!store.housing.contains("TownStories",10))store.housing.put("TownStories",new CompoundTag());
        var all=store.housing.getCompound("TownStories");String id=p.getUUID().toString();if(!all.contains(id,10)){all.put(id,new CompoundTag());store.setDirty();}return all.getCompound(id);
    }
    static CompoundTag record(ServerPlayer p,String id){var j=journal(p);if(!j.contains(id,10)){j.put(id,new CompoundTag());AdventureSavedData.get(p.server).setDirty();}return j.getCompound(id);}
    public static boolean complete(ServerPlayer p,String id){return find(id)!=null&&record(p,id).getInt("Stage")>=4;}
    public static boolean unlocked(ServerPlayer p,String goods){var story=ALL.stream().filter(s->s.unlock().equals(goods)).findFirst().orElse(null);return story==null||complete(p,story.id());}
    static boolean living(ServerPlayer p){return p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p);}
    public static String advance(ServerPlayer p,String id){
        var story=find(id);if(story==null)return "没有这份札记。";
        if(!TownServices.near(p,"guild"))return "请回酒馆找艾琳归档。";
        var profile=AdventureService.profile(p);if(!profile.registered())return "先在艾琳这里登记冒险者，再领取札记。";
        var r=record(p,id);int stage=r.getInt("Stage");
        if(stage==0){r.putInt("Stage",1);AdventureSavedData.get(p.server).setDirty();return "已领取「"+story.title()+"」。没有期限，按你自己的步调完成。";}
        if(stage>=4)return "这段故事已经归档，纪念与家具购买资格会一直保留。";
        if(!ready(p,id,r))return "这一章尚未完成："+story.chapters().get(stage-1).goal();
        var chapter=story.chapters().get(stage-1);if(!profile.canCredit(chapter.coins()))return "余额已达上限，材料与本章记录保留。";
        var tx=new InventoryTransaction(p.getInventory());
        if(!takeOrdinary(p,tx,materials(id,stage)))return "普通材料不足，命名与特殊物品保留，未扣任何材料。";
        if(id.equals("supper")&&stage==3&&!tx.add(new ItemStack(Items.BUCKET)))return "请为返还空桶腾出位置，未扣材料。";
        if(stage==3&&!tx.add(keepsake(p,story)))return "请为唯一纪念页腾出一个位置，材料与奖励保留。";
        tx.commit();profile.credit(chapter.coins(),"归航札记·"+chapter.title());profile.addXp(chapter.xp());r.putInt("Stage",stage+1);
        if(stage==3)AdventureService.milestone(p,"town_story_"+id);
        AdventureService.refreshGrowth(p);AdventureSavedData.get(p.server).setDirty();
        return "「"+chapter.title()+"」已归档：+"+chapter.coins()+"铜，+"+chapter.xp()+"冒险经验。"+(stage==3?"纪念页已入行囊，织夏新增专属家具。":"下一章已打开。");
    }
    static List<ItemStack> materials(String id,int stage){return switch(id+stage){
        case "supper1"->List.of(new ItemStack(Items.WHEAT,8),new ItemStack(Items.CARROT,8));
        case "supper2"->List.of(new ItemStack(Items.COOKED_BEEF,4),new ItemStack(Items.BAKED_POTATO,4));
        case "supper3"->List.of(new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.EGG,4));
        case "journey2"->List.of(new ItemStack(Items.PAPER,3),new ItemStack(Items.INK_SAC));default->List.of();};}
    static boolean takeOrdinary(ServerPlayer p,InventoryTransaction tx,List<ItemStack> requirements){
        for(var required:requirements){int left=required.getCount();for(int i=0;i<36&&left>0;i++){var s=p.getInventory().getItem(i);if(!s.is(required.getItem())||!ShopService.plain(s))continue;int n=Math.min(left,s.getCount());if(!tx.takeFromSlot(i,n))return false;left-=n;}if(left>0)return false;}return true;
    }
    private static ItemStack keepsake(ServerPlayer p,Story s){
        var page=new ItemStack(Items.PAPER);page.setHoverName(Component.literal("归航 · "+s.keepsake()));var tag=page.getOrCreateTag();tag.putString("TncTownStory",s.id());tag.putUUID("RememberedBy",p.getUUID());
        var lore=new ListTag();String line=s.id().equals("home")?"属于"+p.getGameProfile().getName()+"的"+HousingService.mine(p,PlotCatalog.Kind.HOME).number()+"号归处":"艾琳盖章：RouchNao会记得这段故事。";
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line))));lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("可装入展示框，纪念没有期限。"))));tag.getCompound("display").put("Lore",lore);return page;
    }
    static boolean ready(ServerPlayer p,String id,CompoundTag r){
        int stage=r.getInt("Stage");if(stage<1||stage>3)return false;
        if(id.equals("journey"))return stage==1?r.getList("Biomes",8).size()>=3:stage==3?r.getList("Guests",8).size()>=3:true;
        if(id.equals("supper"))return stage!=3||r.getList("Guests",8).size()>=2;
        if(id.equals("home"))return HousingService.owned(p)&&(stage==1||stage==2&&furnishing(p)==7||stage==3&&r.getBoolean("Rested"));
        return false;
    }
    static int furnishing(ServerPlayer p){
        var plot=HousingService.mine(p,PlotCatalog.Kind.HOME);if(plot==null||!HousingService.owned(p))return 0;var origin=BlockPos.of(HousingService.home(p).getLong("Origin"));var l=p.server.overworld();int found=0;
        for(var pos:BlockPos.betweenClosed(origin.offset(plot.min()),origin.offset(plot.max()))){if(!l.hasChunkAt(pos))continue;var state=l.getBlockState(pos);
            if(state.getBlock() instanceof BedBlock&&state.getValue(BedBlock.PART)==BedPart.HEAD)found|=1;
            if(state.getLightEmission(l,pos)>=5)found|=2;var key=ForgeRegistries.BLOCKS.getKey(state.getBlock());if(key!=null&&key.getNamespace().equals("immersive_furniture"))found|=4;if(found==7)break;
        }return found;
    }
    public static CompoundTag snapshot(ServerPlayer p){
        var out=new CompoundTag();for(var s:ALL){var r=record(p,s.id());var t=r.copy();if(s.id().equals("home")&&r.getInt("Stage")==2){int f=furnishing(p);t.putInt("Furnishing",f);t.putBoolean("Ready",f==7);}else t.putBoolean("Ready",ready(p,s.id(),r));out.put(s.id(),t);}return out;
    }
    static void recordBiome(ServerPlayer p,String biome){var r=record(p,"journey");if(r.getInt("Stage")!=1||r.getList("Biomes",8).size()>=3)return;addUnique(p,r,"Biomes",biome);}
    static void addUnique(ServerPlayer p,CompoundTag r,String key,String value){var list=r.getList(key,8);if(list.stream().noneMatch(t->t.getAsString().equals(value))){list.add(StringTag.valueOf(value));r.put(key,list);AdventureSavedData.get(p.server).setDirty();}}
    @SubscribeEvent public static void explore(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%100!=0||!living(p)||record(p,"journey").getInt("Stage")!=1)return;
        var origin=HousingService.sourceOrigin(p.server.overworld());if(origin==null)return;var local=p.blockPosition().subtract(origin);
        if(p.serverLevel()==p.server.overworld()&&local.getX()>=0&&local.getX()<520&&local.getZ()>=0&&local.getZ()<520&&local.getY()>=30&&local.getY()<240)return;
        p.serverLevel().getBiome(p.blockPosition()).unwrapKey().ifPresent(key->recordBiome(p,key.location().toString()));
    }
    static String residentId(ServerPlayer p,Entity npc){
        if(!(npc instanceof Villager villager)||villager.isBaby()||!McaResidents.nativeResident(npc))return null;
        var records=AdventureSavedData.get(p.server).housing.getCompound("Residents");for(var d:ResidentService.ALL){var r=records.getCompound(d.id());if(r.hasUUID("UUID")&&r.getUUID("UUID").equals(npc.getUUID()))return d.id();}return null;
    }
    static void rememberVisit(ServerPlayer p,String id){
        for(var story:List.of("supper","journey")){var r=record(p,story);if(r.getInt("Stage")==3)addUnique(p,r,"Guests",id);}
    }
    @SubscribeEvent public static void visit(PlayerInteractEvent.EntityInteract e){
        if(!(e.getEntity() instanceof ServerPlayer p)||e.getHand()!=InteractionHand.MAIN_HAND||!living(p)||p.serverLevel()!=p.server.overworld()||p.distanceToSqr(e.getTarget())>36)return;var id=residentId(p,e.getTarget());if(id==null)return;
        rememberVisit(p,id);var greetings=journal(p).getCompound("Greetings");long day=Math.max(0,System.currentTimeMillis()/86_400_000L);if(greetings.contains(id)&&greetings.getLong(id)>=day)return;
        greetings.putLong(id,day);journal(p).put("Greetings",greetings);AdventureSavedData.get(p.server).setDirty();var d=ResidentService.definition(id);
        String line=complete(p,"supper")?"还记得酒馆那一桌热饭吗？下次也给你留位置。":complete(p,"journey")?"你上次说的远方，我还记着。有新故事记得带回来。":complete(p,"home")?"新家住得还习惯吗？晚些时候记得给自己留一盏灯。":switch(id){case "lin"->"新鲜的收成可以卖给织夏。今天店里的推荐品，可能正好是你的菜。";case "qiao"->"不必每天赶路。有空坐下来吃顿饭，也算一天的收获。";case "lan"->"岛外的水会流向哪里？回来时告诉我你看到了什么。";case "yan"->"不是每一页札记都要写战斗。记下一个陌生地方，也很好。";case "he"->"给旅人留一碗热汤，是我喜欢这里的原因。";default->"有人记得你回来的声音，这里才像一个家。";};p.sendSystemMessage(Component.literal(d.name()+"："+line));
    }
    static void rememberRest(ServerPlayer p,BlockPos bed,int sleptTicks){
        if(sleptTicks<100||!living(p)||p.serverLevel()!=p.server.overworld()||!HousingService.owned(p)||record(p,"home").getInt("Stage")!=3)return;
        var plot=HousingService.mine(p,PlotCatalog.Kind.HOME);var origin=BlockPos.of(HousingService.home(p).getLong("Origin"));if(bed!=null&&plot.contains(bed.subtract(origin))){record(p,"home").putBoolean("Rested",true);AdventureSavedData.get(p.server).setDirty();}
    }
    @SubscribeEvent public static void wake(PlayerWakeUpEvent e){if(e.getEntity() instanceof ServerPlayer p&&!e.wakeImmediately()&&!e.updateLevel())rememberRest(p,p.getSleepingPos().orElse(null),p.getSleepTimer());}
}
