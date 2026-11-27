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
    private record Session(String site,long token,long until,boolean replay){}
    private record Lease(UUID owner,long until){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<MinecraftServer,Map<String,Lease>> LEASES=new WeakHashMap<>();
    private static long token;
    public static CompoundTag data(ServerPlayer p){var root=JourneyData.get(p.server).player(p.getUUID());if(!root.contains("SkyAcademy",10))root.put("SkyAcademy",new CompoundTag());return root.getCompound("SkyAcademy");}
    static void dirty(ServerPlayer p){JourneyData.get(p.server).setDirty();}
    public static int completed(ServerPlayer p){return data(p).getInt("Completed");}
    private static long now(ServerPlayer p){return com.tnc.tnc.world.SkyIslandWorlds.island(p.server).getGameTime();}
    private static boolean near(ServerPlayer p,String site,double radius){var pos=SkyAcademyWorld.at(p.server,site);return p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p)&&p.serverLevel()==com.tnc.tnc.world.SkyIslandWorlds.island(p.server)&&pos!=null&&pos.closerToCenterThan(p.position(),radius);}
    public static void finish(ServerPlayer p,int flag,String key){
        var t=data(p);if((t.getInt("Completed")&flag)!=0)return;
        t.putInt("Completed",t.getInt("Completed")|flag);dirty(p);AdventureService.milestone(p,"academy_"+key);
        p.sendSystemMessage(Component.literal("§b现场研习完成："+title(key)+"。下一段邀请已加入旅途指引。"));sync(p);
    }
    public static String title(String site){return switch(site){case "arrival"->"云上初见";case "stargaze"->"今夜，看看世界的另一面";case "wind"->"出发前，读懂无形的路";case "farm"->"田庄里的两种呼吸";case "workshop"->"让第一盏灯亮起来";default->"云溪实地研习";};}
    public static String lesson(String site){return switch(site){
        case "stargaze"->"先看看山下的暖灯、湖泊与树冠。等真正的夜晚到来，我会把视线交还给你；亲手按F8，看见流动在风里的魔力。不要急着赶路，让它从近处一直展开到星空。";
        case "wind"->"听见了吗？只有一阵风，三只铃却前后响了三声。站在这里，跟我看它从哪一只铃里钻出来，又往哪里去。等风绕回来，打开魔力视野，沿着它的来路，依次右键触碰三座风铃。";
        case "farm"->"风帆花在露天迎风产魔，成熟的根要在魔法土和流动魔力旁。把集露盏放在它相邻的一格。另一边的跃泉蹄兽通过活动产魔：手持牧务杖，先右键动物，再右键集息器。无需驯养和喂食，喂食只是最多20%的加成。开启F8看真实流向。";
        case "workshop"->"装置自己不会凭空有魔力。先看储池→晶纤→灯的演示。轮到你时，中间会留一个缺口，把青辉导魔晶纤放进去；用F8观察传输，等灯真正亮起。以后折光镜负责跨越远距离，这条最小回路是它的基础。";
        default->"沿着山路来找我们。每次只练习一种新本领。";
    };}
    public static void open(ServerPlayer p,String site){
        open(p,site,false);
    }
    public static void replayFirstSight(ServerPlayer p){open(p,"stargaze",true);}
    private static void open(ServerPlayer p,String site,boolean replay){
        if(!NAMES.containsKey(site)||!SkyAcademyWorld.available(p.server)||!near(p,site,8))return;
        if (!site.equals("stargaze") && !LessonClearings.ready(p.server, site)) return;
        if(site.equals("stargaze")&&!replay){
            if((completed(p)&AcademyRules.NIGHT)!=0){greetStargazer(p);return;}
            if(firstSightLesson(p))return;
        }
        if(site.equals("stargaze")&&!FirstSightPeak.ready(p.server))return;
        if(site.equals("stargaze")){ensureMentors(p.server);var at=SkyAcademyWorld.at(p.server,site);if(at!=null)for(var npc:p.serverLevel().getEntitiesOfClass(TownServiceNpc.class,new AABB(at).inflate(8),n->n.role().equals("academy_stargaze"))){float yaw=(float)Math.toDegrees(Math.atan2(npc.getX()-p.getX(),p.getZ()-npc.getZ()));npc.setYRot(yaw);npc.setYHeadRot(yaw);npc.setYBodyRot(yaw);}}
        var packet=new CompoundTag();packet.putString("Type","dialogue");packet.putString("Site",site);packet.putString("Title",title(site));packet.putString("Name",NAMES.get(site));packet.putString("Text",lesson(site));
        boolean allowed=AcademyRules.unlocked(completed(p),site);packet.putBoolean("Allowed",allowed);packet.putBoolean("Night",AcademyRules.night(p.level().getDayTime()));
        long id=++token;SESSIONS.put(p.getUUID(),new Session(site,id,now(p)+2400,replay));packet.putLong("Token",id);packet.putString("Next",AcademyRules.next(completed(p)));if(site.equals("stargaze")&&allowed){action(p,id,"begin");return;}AcademyNetwork.send(p,packet);
    }
    private static void greetStargazer(ServerPlayer p){
        String line=AcademyRules.night(p.level().getDayTime())?"你来得正好。陪我再看一会儿星星吧。":"上回我们数过的星星，白天也还在那儿。";
        p.displayClientMessage(Component.literal("瑟兰 · "+playerName(p)+"，"+line),true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void greetOnAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event){
        if(event.getEntity() instanceof ServerPlayer p && event.getTarget() instanceof TownServiceNpc npc
                && npc.role().equals("academy_stargaze") && (completed(p)&AcademyRules.NIGHT)!=0 && near(p,"stargaze",8)){
            event.setCanceled(true);
            greetStargazer(p);
        }
    }
    public static void action(ServerPlayer p,long id,String action){
        if (action.equals("wind_skip")) {
            var lesson = data(p);
            if (lesson.getString("Active").equals("wind") && near(p, "wind", 28)
                    && now(p) < lesson.getLong("DemoUntil")) {
                lesson.putLong("DemoUntil", now(p));
                dirty(p);
            }
            return;
        }
        if(action.equals("sight_edge")) {
            if(!firstSightLesson(p)||!FirstSightPeak.ready(p.server)||p.serverLevel()!=com.tnc.tnc.world.SkyIslandWorlds.island(p.server))return;
            var edge=SkyAcademyWorld.at(p.server,"stargaze").south(6);
            p.stopRiding();p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.teleportTo(p.serverLevel(),edge.getX()+.5,edge.getY(),edge.getZ()+.5,0,12);
            return;
        }

        var session=SESSIONS.get(p.getUUID());if(session==null||session.token!=id||session.until<now(p)||!near(p,session.site,8)||!AcademyRules.unlocked(completed(p),session.site))return;
        if(!action.equals("begin"))return;
        String site=session.site;
        if (!site.equals("stargaze") && !LessonClearings.ready(p.server, site)) return;
        if(site.equals("stargaze")&&!session.replay&&(completed(p)&AcademyRules.NIGHT)!=0){
            SESSIONS.remove(p.getUUID());greetStargazer(p);return;
        }
        if(site.equals("stargaze")&&!AcademyRules.night(p.level().getDayTime())){p.sendSystemMessage(Component.literal("瑟兰：我们等到夜晚。你可以先回镇准备物资；天色暗下后再来，不会改变其他人的时间。"));return;}
        if(site.equals("stargaze")){p.stopRiding();var view=SkyAcademyWorld.at(p.server,site).south(6);p.teleportTo(p.serverLevel(),view.getX()+.5,view.getY(),view.getZ()+.5,180,0);for(var npc:p.serverLevel().getEntitiesOfClass(TownServiceNpc.class,new AABB(SkyAcademyWorld.at(p.server,site)).inflate(8),n->n.role().equals("academy_stargaze"))){npc.setYRot(0);npc.setYHeadRot(0);npc.setYBodyRot(0);}}
        if((site.equals("farm")||site.equals("workshop"))&&!claim(p,site)){p.sendSystemMessage(Component.literal("导师：练习台正在由另一位学徒使用。请稍候；你的进度会保留。"));return;}
        int duration = switch (site) {
            case "stargaze" -> FirstSightScript.duration(playerName(p));
            case "wind" -> AcademyRules.WIND_DEMO_TICKS;
            default -> 240;
        };
        var t=data(p);t.putString("Active",site);t.putBoolean("Practice",false);t.putLong("DemoUntil",now(p)+duration);t.putBoolean("SawOff",!ManaSightServer.enabled(p));t.remove("Exposure");
        if (site.equals("wind")) {
            t.putInt("Rune", 0);
            t.remove("WindLastTouch");
        }
        if(site.equals("farm")||site.equals("workshop"))AcademyStations.setup(p,site,true);
        dirty(p);shot(p,site,duration,session.replay);sync(p);SESSIONS.remove(p.getUUID());
    }
    static boolean claim(ServerPlayer p,String site){
        var map=LEASES.computeIfAbsent(p.server,s->new HashMap<>());var current=map.get(site);
        if(current!=null&&!current.owner.equals(p.getUUID())&&current.until>now(p)&&p.server.getPlayerList().getPlayer(current.owner)!=null)return false;
        map.put(site,new Lease(p.getUUID(),now(p)+6000));return true;
    }
    public static boolean leased(ServerPlayer p,String site){var l=LEASES.getOrDefault(p.server,Map.of()).get(site);return l!=null&&l.owner.equals(p.getUUID())&&l.until>now(p);}
    public static boolean trainingProtected(ServerPlayer p,BlockPos pos){
        if(!SkyAcademyWorld.available(p.server)||p.serverLevel()!=com.tnc.tnc.world.SkyIslandWorlds.island(p.server))return false;
        for(String site:List.of("farm","workshop")){var at=AcademyStations.base(p.server,site);if(at!=null&&new AABB(at.offset(-2,-2,-2),at.offset(10,6,6)).contains(net.minecraft.world.phys.Vec3.atCenterOf(pos)))return !leased(p,site);}
        return false;
    }
    /** Editing permissions differ from machine-use permissions: a learner may
     * power the whole demonstration, but may only replace its practice piece. */
    public static boolean trainingEditDenied(ServerPlayer p,BlockPos pos){
        if(p.isCreative()||!SkyAcademyWorld.available(p.server)||p.serverLevel()!=com.tnc.tnc.world.SkyIslandWorlds.island(p.server))return false;
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
    public static String playerName(ServerPlayer p){String name=JourneyData.get(p.server).player(p.getUUID()).getString("Name");return name.isBlank()?p.getGameProfile().getName():name;}
    private static void putLocation(ServerPlayer player, CompoundTag packet) {
        var sites = new CompoundTag();
        SkyAcademyWorld.sites(player.server).forEach((key, local) -> sites.putLong(key, local.asLong()));
        packet.put("Sites", sites);
        packet.putLong("Origin", SkyAcademyWorld.origin(player.server).asLong());
    }
    static void shot(ServerPlayer p,String site,int ticks,boolean replay){var t=new CompoundTag();t.putString("Type","shot");t.putString("PlayerName",playerName(p));t.putBoolean("FirstSight",replay||(!data(p).getBoolean("FirstSightOpened")&&(completed(p)&AcademyRules.NIGHT)==0));t.putString("Site",site);putLocation(p,t);t.putInt("Ticks",ticks);AcademyNetwork.send(p,t);}
    static void sync(ServerPlayer p){var t=new CompoundTag();t.putString("Type","state");t.putInt("Completed",completed(p));putLocation(p,t);t.putString("Active",data(p).getString("Active"));t.putInt("Rune",data(p).getInt("Rune"));t.putInt("Exposure",data(p).getInt("Exposure"));t.putBoolean("Practice",data(p).getBoolean("Practice"));AcademyNetwork.send(p,t);}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||event.getServer().getTickCount()%10!=0)return;
        var server=event.getServer();if(!SkyAcademyWorld.available(server))return;
        if(server.getTickCount()%100==0)ensureMentors(server);
        for(var p:server.getPlayerList().getPlayers()){
              if(com.tnc.tnc.world.SkyhavenArrival.active(p))continue;
            if(p.serverLevel()!=com.tnc.tnc.world.SkyIslandWorlds.island(server)||!p.isAlive())continue;
            var t=data(p);
            if(FirstSightPeak.ready(server)&&(completed(p)&AcademyRules.ARRIVAL)!=0&&(completed(p)&AcademyRules.NIGHT)==0
                    &&!t.getString("Active").equals("stargaze")&&near(p,"stargaze",6)&&AcademyRules.night(p.level().getDayTime()))open(p,"stargaze");
            String site=t.getString("Active");long demo=t.getLong("DemoUntil");
            // The opening used to teleport only once; movement corrections or a mount
            // could displace the real player while the cinematic camera hid the change.
            // Keep the authored edge position only during the bounded pre-reveal window.
            if(site.equals("stargaze") && near(p,site,28) && now(p)<demo+400
                    && !ManaSightServer.enabled(p) && FirstSightPeak.ready(server)) {
                var edge=SkyAcademyWorld.at(server,"stargaze").south(6);
                var target=net.minecraft.world.phys.Vec3.atBottomCenterOf(edge);
                if(p.position().distanceToSqr(target)>.0225 || p.isPassenger()) {
                    p.stopRiding();
                    p.teleportTo(p.serverLevel(),target.x,target.y,target.z,p.getYRot(),p.getXRot());
                }
                p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                p.fallDistance=0;
            }

            if(!site.isEmpty()&&near(p,site,28)&&now(p)>=demo){
                if(site.equals("stargaze")&&AcademyRules.night(p.level().getDayTime())){
                    if(!ManaSightServer.enabled(p)){t.putBoolean("SawOff",true);t.putInt("Exposure",0);}
                    else if(t.getBoolean("SawOff")){t.putBoolean("FirstSightOpened",true);t.remove("ReplayFirstSight");int exposure=t.getInt("Exposure")+10;t.putInt("Exposure",exposure);if(exposure>=200){finish(p,AcademyRules.NIGHT,"stargaze");t.putString("Active","");}}
                }
                if((site.equals("farm")||site.equals("workshop"))&&LessonClearings.ready(server, site)&&claim(p,site)){
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
        var l=com.tnc.tnc.world.SkyIslandWorlds.island(server);for(var e:NAMES.entrySet()){
            var at=SkyAcademyWorld.at(server,e.getKey());if(at==null||!l.hasChunkAt(at)||l.players().stream().noneMatch(p->at.closerToCenterThan(p.position(),64)))continue;
            if(e.getKey().equals("stargaze")&&!FirstSightPeak.ready(server))continue;
            if (!e.getKey().equals("stargaze") && !LessonClearings.ready(server, e.getKey())) continue;
            String role="academy_"+e.getKey();var found=l.getEntitiesOfClass(TownServiceNpc.class,new AABB(at).inflate(e.getKey().equals("stargaze")?80:12),n->n.role().equals(role));
            if(!found.isEmpty()){if(e.getKey().equals("stargaze")){var npc=found.get(0);if(!at.closerToCenterThan(npc.position(),2))npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,180,0);for(int i=1;i<found.size();i++)found.get(i).discard();}continue;}
            var npc=com.tnc.tnc.npc.TNNpcs.SERVICE_NPC.get().create(l);if(npc==null)continue;
            npc.role(role);npc.setCustomName(Component.literal(e.getValue()));npc.setCustomNameVisible(true);npc.setVillagerData(npc.getVillagerData().setProfession(e.getKey().equals("farm")?VillagerProfession.FARMER:VillagerProfession.LIBRARIAN));npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,180,0);l.addFreshEntity(npc);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void rune(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) return;
        var hit = event.getPos();
        var state = player.serverLevel().getBlockState(hit);
        int index = WindChimePillar.voice(state);
        if (index < 0 || !near(player, "wind", 28) || !SkyAcademyWorld.available(player.server)
                || !WindChimePillar.base(hit, state).equals(SkyAcademyWorld.at(player.server, "rune" + index))
                || !hit.closerToCenterThan(player.getEyePosition(), 6.5)) return;

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        touchWind(player, index);
    }

    static void touchWind(ServerPlayer player, int index) {
        var lesson = data(player);
        if (!lesson.getString("Active").equals("wind")) {
            tapBrass(player);
            if ((completed(player) & AcademyRules.WIND) == 0)
                player.displayClientMessage(Component.literal("风铃轻轻晃了晃。去听听岚怎么说。"), true);
            return;
        }
        long tick = now(player);
        if (tick < lesson.getLong("DemoUntil")) return;
        if (!ManaSightServer.enabled(player)) {
            tapBrass(player);
            player.displayClientMessage(Component.literal("打开魔力视野，看看风先穿过哪一只铃。"), true);
            return;
        }
        if (lesson.contains("WindLastTouch") && lesson.getInt("WindLastIndex") == index
                && tick - lesson.getLong("WindLastTouch") < 10) return;
        int before = lesson.getInt("Rune");
        int step = AcademyRules.rune(before, index);
        if (before > 0 && before == step) return;
        lesson.putInt("WindLastIndex", index);
        lesson.putLong("WindLastTouch", tick);
        lesson.putInt("Rune", step);
        dirty(player);

        var feedback = new CompoundTag();
        feedback.putString("Type", "wind_touch");
        feedback.putLong("Origin", SkyAcademyWorld.origin(player.server).asLong());
        feedback.putInt("Index", index);
        feedback.putInt("Step", step);
        AcademyNetwork.send(player, feedback);
        player.displayClientMessage(Component.literal(switch (step) {
            case 0 -> "风散了。等下一阵风，从最先响起的那只铃开始。";
            case 1 -> "第一声 · 风应了。";
            case 2 -> "第二声 · 余音接上了。";
            default -> "第三声 · 三铃和鸣。";
        }), true);
        if (step == 3) {
            lesson.putString("Active", "");
            finish(player, AcademyRules.WIND, "wind");
        }
        sync(player);
    }
    private static void tapBrass(ServerPlayer player) {
        player.playNotifySound(net.minecraft.sounds.SoundEvents.COPPER_HIT,
                net.minecraft.sounds.SoundSource.BLOCKS, .32f, 1.1f);
    }
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent e){
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("skylesson").executes(c -> {
            var player = c.getSource().getPlayerOrException();
            String next = AcademyRules.next(completed(player));
            if (next.equals("complete")) {
                player.sendSystemMessage(Component.literal("云溪实地研习已全部完成。"));
                return 1;
            }
            String destination = switch (next) {
                case "wind" -> LessonClearings.highlands(player.server)
                        ? "沿观星山东南山径，去听风鞍部找岚。"
                        : "从观星山向西，去听风鞍部找岚。";
                case "farm" -> LessonClearings.highlands(player.server)
                        ? "去西南湖畔的露汀牧圃找莉禾。"
                        : "去西南草甸脚下的露汀牧圃找莉禾。";
                case "workshop" -> "沿南门出城，到南门灯渠找诺恩。";
                default -> lesson(next);
            };
            var at = SkyAcademyWorld.at(player.server, next);
            player.sendSystemMessage(Component.literal(title(next) + "\n" + destination
                    + (at == null ? "" : "\n" + at.toShortString())));
            return 1;
        }));
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){SESSIONS.remove(e.getEntity().getUUID());if(e.getEntity() instanceof ServerPlayer p&&data(p).getString("Active").equals("stargaze")){data(p).putString("Active","");dirty(p);}}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){SESSIONS.clear();LEASES.remove(e.getServer());}
    private static final class SkyIslandSavedDataBridge {static BlockPos arrival(MinecraftServer s){return com.tnc.tnc.world.SkyIslandSavedData.get(com.tnc.tnc.world.SkyIslandWorlds.island(s)).anchorPos("ARRIVAL");}}
}
