package com.tnc.tnc.skyacademy;

import com.tnc.tnc.adventure.*;
import com.tnc.tnc.journey.JourneyData;
import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.life.pasture.*;
import com.tnc.tnc.mana.sight.ManaSightServer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Per-player checkpoints, distance-checked dialogue and real machine observations. */
@Mod.EventBusSubscriber(modid="tnc")
public final class AcademyLessons {
    public static final Map<String,String> NAMES=Map.of("stargaze","观星人 · 瑟兰","wind","遗迹向导 · 岚","farm","田庄园丁 · 莉禾","workshop","工坊导师 · 诺恩");
    private record Session(String site,long token,long until){}
    private record Lease(UUID owner,long until){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<MinecraftServer,Map<String,Lease>> LEASES=new WeakHashMap<>();
    private static long token;
    public static CompoundTag data(ServerPlayer p){var root=JourneyData.get(p.server).player(p.getUUID());if(!root.contains("SkyAcademy",10))root.put("SkyAcademy",new CompoundTag());return root.getCompound("SkyAcademy");}
    static void dirty(ServerPlayer p){JourneyData.get(p.server).setDirty();}
    public static int completed(ServerPlayer p){return data(p).getInt("Completed");}
    private static long now(ServerPlayer p){return p.server.overworld().getGameTime();}
    private static boolean near(ServerPlayer p,String site,double radius){var pos=SkyAcademyWorld.at(p.server,site);return p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p)&&p.serverLevel()==p.server.overworld()&&pos!=null&&pos.closerToCenterThan(p.position(),radius);}
    public static void finish(ServerPlayer p,int flag,String key){
        var t=data(p);if((t.getInt("Completed")&flag)!=0)return;
        t.putInt("Completed",t.getInt("Completed")|flag);dirty(p);AdventureService.milestone(p,"academy_"+key);
        p.sendSystemMessage(Component.literal("§b现场研习完成："+title(key)+"。下一段邀请已加入旅途指引。"));sync(p);
    }
    public static String title(String site){return switch(site){case "arrival"->"云上初见";case "stargaze"->"今夜，看看世界的另一面";case "wind"->"出发前，读懂无形的路";case "farm"->"田庄里的两种呼吸";case "workshop"->"让第一盏灯亮起来";default->"云溪实地研习";};}
    public static String lesson(String site){return switch(site){
        case "stargaze"->"先看看山下的暖灯、湖泊与树冠。等真正的夜晚到来，我会把视线交还给你；亲手按F8，看见流动在风里的魔力。不要急着赶路，让它从近处一直展开到星空。";
        case "wind"->"地下城的门未必用钥匙打开。有些路只能被看见：打开F8，石拱旁三块石头会显出一、二、三枚光点。先看我的演示，再按这个顺序右键触碰石头；选错可以重来。";
        case "farm"->"风帆花在露天迎风产魔，成熟的根要在魔法土和流动魔力旁。把集露盏放在它相邻的一格。另一边的跃泉蹄兽通过活动产魔：手持牧务杖，先右键动物，再右键集息器。无需驯养和喂食，喂食只是最多20%的加成。开启F8看真实流向。";
        case "workshop"->"装置自己不会凭空有魔力。先看储池→晶纤→灯的演示。轮到你时，中间会留一个缺口，把青辉导魔晶纤放进去；用F8观察传输，等灯真正亮起。以后折光镜负责跨越远距离，这条最小回路是它的基础。";
        default->"沿着山路来找我们。每次只练习一种新本领。";
    };}
    public static void open(ServerPlayer p,String site){
        if(!NAMES.containsKey(site)||!SkyAcademyWorld.available(p.server)||!near(p,site,8))return;
        var packet=new CompoundTag();packet.putString("Type","dialogue");packet.putString("Site",site);packet.putString("Title",title(site));packet.putString("Name",NAMES.get(site));packet.putString("Text",lesson(site));
        boolean allowed=AcademyRules.unlocked(completed(p),site);packet.putBoolean("Allowed",allowed);packet.putBoolean("Night",AcademyRules.night(p.level().getDayTime()));
        long id=++token;SESSIONS.put(p.getUUID(),new Session(site,id,now(p)+2400));packet.putLong("Token",id);packet.putString("Next",AcademyRules.next(completed(p)));AcademyNetwork.send(p,packet);
    }
    public static void action(ServerPlayer p,long id,String action){
        var session=SESSIONS.get(p.getUUID());if(session==null||session.token!=id||session.until<now(p)||!near(p,session.site,8)||!AcademyRules.unlocked(completed(p),session.site))return;
        if(!action.equals("begin"))return;
        String site=session.site;
        if(site.equals("stargaze")&&!AcademyRules.night(p.level().getDayTime())){p.sendSystemMessage(Component.literal("瑟兰：我们等到夜晚。你可以先回镇准备物资；天色暗下后再来，不会改变其他人的时间。"));return;}
        if((site.equals("farm")||site.equals("workshop"))&&!claim(p,site)){p.sendSystemMessage(Component.literal("导师：练习台正在由另一位学徒使用。请稍候；你的进度会保留。"));return;}
        var t=data(p);t.putString("Active",site);t.putBoolean("Practice",false);t.putLong("DemoUntil",now(p)+(site.equals("stargaze")?200:240));t.putBoolean("SawOff",!ManaSightServer.enabled(p));t.remove("Exposure");
        if(site.equals("wind"))t.putInt("Rune",0);
        if(site.equals("farm")||site.equals("workshop"))AcademyStations.setup(p,site,true);
        dirty(p);shot(p,site,site.equals("stargaze")?200:240);SESSIONS.remove(p.getUUID());
    }
    static boolean claim(ServerPlayer p,String site){
        var map=LEASES.computeIfAbsent(p.server,s->new HashMap<>());var current=map.get(site);
        if(current!=null&&!current.owner.equals(p.getUUID())&&current.until>now(p)&&p.server.getPlayerList().getPlayer(current.owner)!=null)return false;
        map.put(site,new Lease(p.getUUID(),now(p)+6000));return true;
    }
    public static boolean leased(ServerPlayer p,String site){var l=LEASES.getOrDefault(p.server,Map.of()).get(site);return l!=null&&l.owner.equals(p.getUUID())&&l.until>now(p);}
    public static boolean trainingProtected(ServerPlayer p,BlockPos pos){
        if(!SkyAcademyWorld.available(p.server)||p.serverLevel()!=p.server.overworld())return false;
        for(String site:List.of("farm","workshop")){var at=AcademyStations.base(p.server,site);if(at!=null&&new AABB(at.offset(-2,-2,-2),at.offset(10,6,6)).contains(net.minecraft.world.phys.Vec3.atCenterOf(pos)))return !leased(p,site);}
        return false;
    }
    /** Editing permissions differ from machine-use permissions: a learner may
     * power the whole demonstration, but may only replace its practice piece. */
    public static boolean trainingEditDenied(ServerPlayer p,BlockPos pos){
        if(p.isCreative()||!SkyAcademyWorld.available(p.server)||p.serverLevel()!=p.server.overworld())return false;
        for(String site:List.of("farm","workshop")){
            var at=AcademyStations.base(p.server,site);
            if(at!=null&&new AABB(at.offset(-2,-2,-2),at.offset(10,6,6)).contains(net.minecraft.world.phys.Vec3.atCenterOf(pos))){
                var t=data(p);var slot=site.equals("farm")?at.east():at.east(2);
                return !leased(p,site)||!t.getBoolean("Practice")||!site.equals(t.getString("Active"))||!pos.equals(slot);
            }
        }
        return false;
    }
    public static boolean firstSightLesson(ServerPlayer p){return data(p).getString("Active").equals("stargaze")&&near(p,"stargaze",24)&&now(p)-data(p).getLong("DemoUntil")<2400;}
    static void shot(ServerPlayer p,String site,int ticks){var t=new CompoundTag();t.putString("Type","shot");t.putString("Site",site);t.putLong("Origin",SkyAcademyWorld.origin(p.server).asLong());t.putInt("Ticks",ticks);AcademyNetwork.send(p,t);}
    static void sync(ServerPlayer p){var t=new CompoundTag();t.putString("Type","state");t.putInt("Completed",completed(p));t.putLong("Origin",SkyAcademyWorld.origin(p.server).asLong());t.putString("Active",data(p).getString("Active"));t.putInt("Rune",data(p).getInt("Rune"));t.putInt("Exposure",data(p).getInt("Exposure"));t.putBoolean("Practice",data(p).getBoolean("Practice"));AcademyNetwork.send(p,t);}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||event.getServer().getTickCount()%10!=0)return;
        var server=event.getServer();if(!SkyAcademyWorld.available(server))return;
        if(server.getTickCount()%100==0)ensureMentors(server);
        for(var p:server.getPlayerList().getPlayers()){
            if(p.serverLevel()!=server.overworld()||!p.isAlive())continue;
            var t=data(p);
            if((completed(p)&AcademyRules.ARRIVAL)==0&&JourneyData.get(server).player(p.getUUID()).getBoolean("Named")){
                var entry=SkyIslandSavedDataBridge.arrival(server);
                if(entry!=null&&entry.closerToCenterThan(p.position(),24)){finish(p,AcademyRules.ARRIVAL,"arrival");shot(p,"arrival",300);}
            }
            String site=t.getString("Active");long demo=t.getLong("DemoUntil");
            if(!site.isEmpty()&&near(p,site,28)&&now(p)>=demo){
                if(site.equals("stargaze")&&AcademyRules.night(p.level().getDayTime())){
                    if(!ManaSightServer.enabled(p)){t.putBoolean("SawOff",true);t.putInt("Exposure",0);}
                    else if(t.getBoolean("SawOff")){int exposure=t.getInt("Exposure")+10;t.putInt("Exposure",exposure);if(exposure>=200){finish(p,AcademyRules.NIGHT,"stargaze");t.putString("Active","");}}
                }
                if((site.equals("farm")||site.equals("workshop"))&&claim(p,site)){
                    if(t.getBoolean("Practice")&&!AcademyStations.owned(p,site)){AcademyStations.setup(p,site,false);}
                    if(!t.getBoolean("Practice")){AcademyStations.setup(p,site,false);t.putBoolean("Practice",true);}
                    if(AcademyStations.complete(p,site)){finish(p,site.equals("farm")?AcademyRules.FARM:AcademyRules.WORKSHOP,site);t.putString("Active","");t.putBoolean("Practice",false);}
                }
                dirty(p);
            }
            if(server.getTickCount()%40==0)sync(p);
        }
    }
    private static void ensureMentors(MinecraftServer server){
        var l=server.overworld();for(var e:NAMES.entrySet()){
            var at=SkyAcademyWorld.at(server,e.getKey());if(at==null||!l.hasChunkAt(at)||l.players().stream().noneMatch(p->at.closerToCenterThan(p.position(),64)))continue;
            String role="academy_"+e.getKey();var found=l.getEntitiesOfClass(TownServiceNpc.class,new AABB(at).inflate(12),n->n.role().equals(role));
            if(!found.isEmpty())continue;
            var npc=com.tnc.tnc.npc.TNNpcs.SERVICE_NPC.get().create(l);if(npc==null)continue;
            npc.role(role);npc.setCustomName(Component.literal(e.getValue()));npc.setCustomNameVisible(true);npc.setVillagerData(npc.getVillagerData().setProfession(e.getKey().equals("farm")?VillagerProfession.FARMER:VillagerProfession.LIBRARIAN));npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,180,0);l.addFreshEntity(npc);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void rune(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e){
        if(e.getHand()!=InteractionHand.MAIN_HAND||!(e.getEntity() instanceof ServerPlayer p)||!SkyAcademyWorld.available(p.server))return;
        var t=data(p);if(!t.getString("Active").equals("wind")||!near(p,"wind",24)||!ManaSightServer.enabled(p)||now(p)<t.getLong("DemoUntil"))return;
        for(int i=0;i<3;i++)if(e.getPos().equals(SkyAcademyWorld.at(p.server,"rune"+i))){
            if(!e.getPos().closerToCenterThan(p.getEyePosition(),6))return;
            int next=AcademyRules.rune(t.getInt("Rune"),i);t.putInt("Rune",next);dirty(p);e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
            p.displayClientMessage(Component.literal(next==0?"光点散开了。重新寻找只有一枚光点的石头。":"符文回应 · "+next+" / 3"),true);
            p.playNotifySound(next==0?net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value():net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.PLAYERS,.6f,.8f+next*.2f);
            if(next==3){finish(p,AcademyRules.WIND,"wind");t.putString("Active","");}sync(p);return;
        }
    }
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent e){
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("skylesson").executes(c->{var p=c.getSource().getPlayerOrException();var next=AcademyRules.next(completed(p));var at=SkyAcademyWorld.at(p.server,next);p.sendSystemMessage(Component.literal(SkyAcademyWorld.status(p.server)+"\n"+title(next)+(at==null?"":" · "+at.toShortString())+"\n"+lesson(next)));return 1;}));
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){SESSIONS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){SESSIONS.clear();LEASES.remove(e.getServer());}
    private static final class SkyIslandSavedDataBridge {static BlockPos arrival(MinecraftServer s){return com.tnc.tnc.world.SkyIslandSavedData.get(s.overworld()).anchorPos("ARRIVAL");}}
}
