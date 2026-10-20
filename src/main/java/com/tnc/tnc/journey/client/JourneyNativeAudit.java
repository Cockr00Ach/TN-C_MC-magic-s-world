package com.tnc.tnc.journey.client;

import com.tnc.tnc.journey.*;
import com.tnc.tnc.alchemy.*;
import com.tnc.tnc.adventure.*;
import com.tnc.tnc.dungeon.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.util.*;

/** Explicitly enabled only in the disposable full-pack audit world. */
public final class JourneyNativeAudit {
    private static final String[] NAMES={"character","posted-commissions","accepted-commission","alchemy-desk","living-cauldron","expedition-preparation","hidden-cache-unseen","hidden-cache-sight","family-at-home","small-character","small-alchemy"};
    private static boolean started,saving,captured;private static volatile boolean ready;private static int startup,scene,ticks;
    private static final List<String> failures=new ArrayList<>();private static final Map<String,Object> checks=new LinkedHashMap<>();
    private static DungeonData.Run run;private static BlockPos secret;private static net.minecraft.world.entity.npc.Villager spouse;
    private static final BlockPos CAULDRON=new BlockPos(22000,190,22000);
    private static Path output(){return Path.of(System.getProperty("tnc.dungeonAuditOutput")).toAbsolutePath();}
    private static void check(String name,boolean okay){checks.put(name,okay);if(!okay)failures.add(name);}
    public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.options.enableVsync().get()){mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);}
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!started){if(++startup<100)return;started=true;Files.createDirectories(output());mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(6);mc.options.setCameraType(CameraType.FIRST_PERSON);prepare(mc);return;}
        if(!ready||saving)return;ticks++;
        if(scene==6||scene==7){mc.player.setYRot(180);mc.player.setXRot(33);}
        if((scene==4||scene==6||scene==7)&&ticks==10)mc.setScreen(null);
        if(scene==8&&ticks==25)mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);check("native_child_observed",FamilyLife.children(p)>0&&FamilyLife.presentChildren(p)>0);FamilyLife.family(p);});
        if(scene==7&&ticks==12)com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(true);
        if(scene==9&&ticks==18&&mc.screen instanceof JourneyScreen s){s.charTyped('星',0);s.charTyped('旅',0);check("native_name_input_accepts_unicode",true);}
        if(ticks==110&&!captured){
            Class<?> expected=scene==3||scene==10?com.tnc.tnc.alchemy.client.AlchemyScreen.class:scene==5?PreparationScreen.class:JourneyScreen.class;
            if(scene!=4&&scene!=6&&scene!=7)check(NAMES[scene]+"_screen",expected.isInstance(mc.screen));
            if(scene==4){var be=mc.level.getBlockEntity(CAULDRON);check("client_cauldron_working_synced",be instanceof AlchemyEntity a&&a.working);}
            if(scene==7)check("mana_sight_enabled",com.tnc.tnc.mana.sight.client.ManaSightClient.enabled());
            String name=String.format(Locale.ROOT,"%02d-%s.png",scene,NAMES[scene]);saving=true;captured=true;
            Screenshot.grab(mc.gameDirectory,"journey-"+name,mc.getMainRenderTarget(),m->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/journey-"+name),output().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ex){failures.add(ex.toString());}finally{saving=false;}});return;
        }
        if(ticks<130)return;mc.setScreen(null);if(++scene==NAMES.length){finish(mc);return;}ticks=0;captured=false;
        if(scene==9){mc.options.guiScale().set(3);org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1024,640);}
        prepare(mc);
    }
    private static void prepare(Minecraft mc){ready=false;mc.getSingleplayerServer().execute(()->{try{
        var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.closeContainer();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.setHealth(p.getMaxHealth());p.removeAllEffects();
        var record=JourneyData.get(p.server).player(p.getUUID());record.putBoolean("WelcomeShown",true);FamilyLife.initialize(p);
        if(scene==0||scene==9){var f=Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile").getField("INSTANCE").get(null);var chapter=f.getClass().getMethod("getChapter",long.class).invoke(f,Long.parseLong("37B7FFAC33E9E92C",16));check("actual_ftb_chapter_loaded",chapter!=null&&((List<?>)chapter.getClass().getMethod("getQuests").invoke(chapter)).size()==22);FamilyLife.openProfile(p);check("mca_initialization_no_destiny_buffs",!p.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)&&!p.hasEffect(net.minecraft.world.effect.MobEffects.HEALTH_BOOST));ready=true;return;}
        if(scene==1||scene==2){
            var pos=TownServices.board(p.server.overworld());if(pos==null)throw new IllegalStateException("Prepared audit world's town anchor missing");p.teleportTo(p.server.overworld(),pos.getX()+.5,pos.getY(),pos.getZ()+2.5,180,0);AdventureService.register(p);
            if(scene==2){var ledger=CommissionService.ledger(p);ledger.remove("tnc:road_watch");CommissionService.act(p,"accept","tnc:road_watch");for(int i=0;i<6;i++)CommissionService.progress(p,"kill","minecraft:zombie");check("native_commission_goal",ledger.getCompound("tnc:road_watch").getInt("Progress")==6);}
            CommissionService.open(p);ready=true;return;
        }
        if(scene==3||scene==4||scene==10){
            p.teleportTo(p.server.overworld(),CAULDRON.getX()+.5,CAULDRON.getY()+1.1,CAULDRON.getZ()+4.8,180,24);var world=p.serverLevel();world.setDayTime(18000);world.setWeatherParameters(10000,0,false,false);
            for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)world.setBlock(CAULDRON.offset(x,-1,z),Blocks.DEEPSLATE_TILES.defaultBlockState(),3);
            for(int x=-4;x<=4;x++)for(int y=0;y<5;y++)world.setBlock(CAULDRON.offset(x,y,-3),y==0||x==-4||x==4?Blocks.CHISELED_DEEPSLATE.defaultBlockState():Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),3);
            for(int x:new int[]{-3,3})world.setBlock(CAULDRON.offset(x,0,-2),Blocks.SEA_LANTERN.defaultBlockState(),3);
            world.setBlock(CAULDRON,AlchemyContent.CAULDRON.defaultBlockState(),3);var be=(AlchemyEntity)world.getBlockEntity(CAULDRON);be.owner=p.getUUID();var e=Elixir.SPRING;for(int i=0;i<3;i++)be.inventory.setItem(i,new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse(e.ingredients.get(i))),64));be.inventory.setItem(4,new ItemStack(AlchemyContent.FUEL,64));be.inventory.setItem(5,new ItemStack(Items.GLASS_BOTTLE,64));be.ignite(true);be.inventory.setItem(6,ItemStack.EMPTY);
            if(scene!=4)net.minecraftforge.network.NetworkHooks.openScreen(p,be,b->b.writeBlockPos(CAULDRON));ready=true;return;
        }
        if(scene==5){
            var data=DungeonData.get(p.server);run=new DungeonData.Run(UUID.randomUUID(),p.getUUID(),183733,78,DungeonTheme.ASTRAL);data.runs.put(run.id,run);data.profile(p.getUUID()).putUUID("Run",run.id);var level=p.server.getLevel(DungeonRealm.KEY);
            DungeonRealm.build(level,run,DungeonRealm.lobbyCells(run),()->{DungeonService.teleport(p,run,DungeonRealm.lobby(run).above());p.getInventory().clearContent();p.getInventory().setItem(9,new ItemStack(Items.BREAD,12));var st=new Preparation.Storage(p);st.setItem(0,new ItemStack(Items.COBBLESTONE,32));st.setItem(1,new ItemStack(Items.OAK_LOG,16));st.setItem(27,new ItemStack(AlchemyContent.POTIONS.get(Elixir.SPRING),4));st.setItem(28,new ItemStack(AlchemyContent.POTIONS.get(Elixir.BLOOM),3));Preparation.open(p,1);ready=true;});return;
        }
        if(scene==6){
            run.floor=1;var rooms=run.graph().rooms.stream().filter(r->r.kind()==DungeonGraph.Kind.TREASURE).toList();var room=rooms.get(Math.floorMod((int)(run.seed+run.floor),rooms.size()));secret=DungeonRealm.center(run,room).offset(6,1,5);var level=p.server.getLevel(DungeonRealm.KEY);
            DungeonRealm.build(level,run,DungeonRealm.floorCells(run),()->{run.state().built=true;run.state().cleared=255;run.state().visited=255;DungeonService.teleport(p,run,secret.offset(-1,1,4));p.connection.teleport(secret.getX()+.5,p.getY(),secret.getZ()+4.5,180,20);check("generated_hidden_cache",level.getBlockState(secret).is(AlchemyContent.HIDDEN_CACHE));ready=true;});return;
        }
        if(scene==7){ready=true;return;}
        if(scene==8){
            com.tnc.tnc.mana.sight.ManaSightServer.subscribe(p,true);p.teleportTo(p.server.overworld(),CAULDRON.getX()+2.5,190,CAULDRON.getZ()+.5,180,0);
            spouse=com.tnc.tnc.home.McaResidents.create(p.serverLevel(),com.tnc.tnc.home.ResidentService.ALL.get(1),p.blockPosition().east(),UUID.randomUUID());spouse.setNoAi(true);p.serverLevel().addFreshEntity(spouse);com.tnc.tnc.home.McaResidents.setHome(spouse,p.blockPosition());var saved=FamilyLife.mca(p);saved.getClass().getMethod("marry",net.minecraft.world.entity.Entity.class).invoke(saved,spouse);var node=FamilyLife.entry(p);var child=com.tnc.tnc.home.McaResidents.create(p.serverLevel(),com.tnc.tnc.home.ResidentService.ALL.get(2),p.blockPosition().north(),UUID.randomUUID());child.setAge(-24000);child.setNoAi(true);p.serverLevel().addFreshEntity(child);node.getClass().getMethod("addChild",UUID.class).invoke(node,child.getUUID());
            check("native_real_mca_spouse",spouse.getUUID().equals(FamilyLife.partner(p)));check("native_home_recognized",FamilyLife.home(p,spouse));FamilyLife.family(p);ready=true;return;
        }
    }catch(Throwable ex){failures.add("Scene "+scene+": "+ex);ready=true;}});}
    private static void finish(Minecraft mc)throws Exception{
        var report=new LinkedHashMap<String,Object>();report.put("screenshots",NAMES.length);report.put("checks",checks);report.put("failures",failures);report.put("scope","Disposable full PCL pack; actual network screens, MCA spouse and child, brewing and generated dungeon hidden cache");
        var hashes=new LinkedHashMap<String,String>();for(var c:List.of(JourneyScreen.class,PreparationScreen.class,com.tnc.tnc.alchemy.client.AlchemyScreen.class,com.tnc.tnc.alchemy.client.AlchemyRenderer.class))try(var in=c.getResourceAsStream("/"+c.getName().replace('.','/')+".class")){hashes.put(c.getName(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));}report.put("class_sha256",hashes);Files.writeString(output().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
    }
}
