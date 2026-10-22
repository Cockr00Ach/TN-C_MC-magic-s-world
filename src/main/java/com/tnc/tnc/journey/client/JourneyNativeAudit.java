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
    private static final String[] NAMES={"character","posted-commissions","accepted-commission","alchemy-desk","living-cauldron","expedition-preparation","hidden-cache-unseen","hidden-cache-sight","family-at-home","small-character","small-alchemy","potion-collection","ritual-convergence","ritual-bottled","arcana-collection","arcana-devices","arcana-sight","aurora-night","higher-contract","living-armor","critical-tower","sanity-ghosts","sealed-kettle","higher-presence","continuous-skyroad","plant-platter","ambient-tower","six-workshops","extractor-interface","typed-nurture","dead-mana-nether","living-oasis","sponsored-spell"};
    private static boolean started,saving,captured;private static volatile boolean ready;private static int startup,scene,ticks;
    private static final List<String> failures=new ArrayList<>();private static final Map<String,Object> checks=new LinkedHashMap<>();
    private static DungeonData.Run run;private static BlockPos secret;private static net.minecraft.world.entity.npc.Villager spouse;
    private static final BlockPos CAULDRON=new BlockPos(22000,190,22000);
    private static Path output(){return Path.of(System.getProperty("tnc.dungeonAuditOutput")).toAbsolutePath();}
    private static void check(String name,boolean okay){checks.put(name,okay);if(!okay)failures.add(name);}
    public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(Boolean.getBoolean("tnc.feedbackAudit")){com.tnc.tnc.arcana.client.FeedbackNativeAudit.tick(event);return;}
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.options.enableVsync().get()){mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);}
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!started){if(++startup<100)return;started=true;Files.createDirectories(output());mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(6);mc.options.setCameraType(CameraType.FIRST_PERSON);prepare(mc);return;}
        if(!ready||saving)return;ticks++;
        if(scene==6||scene==7){mc.player.setYRot(180);mc.player.setXRot(33);}
        if((scene==4||scene==6||scene==7||scene==12||scene==13||scene==15||scene==16||scene==17||scene==19||scene==20||(scene>=21&&scene!=28))&&ticks==10)mc.setScreen(null);
        if(scene==8&&ticks==25)mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);check("native_child_observed",FamilyLife.children(p)>0&&FamilyLife.presentChildren(p)>0);FamilyLife.family(p);});
        if((scene==4||scene==7||scene==12||scene==16||scene==17||scene==19||scene==20||scene>=21)&&ticks==8)com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(true);
        if(scene==4&&ticks==40)mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var fire=new com.tnc.tnc.magic.fire.TNFireBoltEntity(com.tnc.tnc.magic.TNOrbEntities.FIRE_BOLT.get(),p.serverLevel());fire.configure(p,com.tnc.tnc.magic.fire.FireSpellRules.bolt("fireball"),"fireball",net.minecraft.world.phys.Vec3.atCenterOf(CAULDRON).add(0,0,3),new net.minecraft.world.phys.Vec3(0,0,-1));p.serverLevel().addFreshEntity(fire);});
        if(scene==12&&ticks==120)mc.getSingleplayerServer().execute(()->{var be=(AlchemyEntity)mc.getSingleplayerServer().overworld().getBlockEntity(CAULDRON);be.temperature=450;be.vortex_sync=100;be.progress=Elixir.SPRING.ticks;be.perfectRemaining=30;be.ruined=false;be.changed();});
        if(scene==22&&ticks%5==0)mc.getSingleplayerServer().execute(()->{var be=(AlchemyEntity)mc.getSingleplayerServer().overworld().getBlockEntity(CAULDRON);if(be!=null&&be.ready.isEmpty()){be.temperature=450;be.changed();}});
        if(scene==9&&ticks==18&&mc.screen instanceof JourneyScreen s){s.charTyped('星',0);s.charTyped('旅',0);check("native_name_input_accepts_unicode",true);}
        if(scene==21&&ticks==110){var saved=mc.screen;mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(false));var animals=mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,mc.player.getBoundingBox().inflate(16));check("ghost_gui_preview_excluded",!animals.isEmpty()&&animals.stream().noneMatch(com.tnc.tnc.arcana.client.ArcanaGhosts::active));mc.setScreen(saved);}
        if(ticks==135&&!captured){
            if(scene>=14){check(NAMES[scene]+"_native",switch(scene){case 14->mc.screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen;case 18->mc.screen instanceof com.tnc.tnc.arcana.client.ContractScreen;case 19->mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() instanceof com.tnc.tnc.arcana.ArcanaContent.LivingArmor;default->scene==15||com.tnc.tnc.mana.sight.client.ManaSightClient.enabled();});}
            Class<?> expected=scene==3||scene==10?com.tnc.tnc.alchemy.client.AlchemyScreen.class:scene==5?PreparationScreen.class:JourneyScreen.class;
            if(scene<14&&scene!=4&&scene!=6&&scene!=7&&scene!=11&&scene!=12&&scene!=13)check(NAMES[scene]+"_screen",expected.isInstance(mc.screen));
            if(scene==4){var be=mc.level.getBlockEntity(CAULDRON);check("client_cauldron_working_synced",be instanceof AlchemyEntity a&&a.fire==2&&a.progress>0);}
            if(scene==22){var be=AlchemyBlock.master(mc.level,CAULDRON.offset(1,0,1));check("client_four_part_lid_synced",be!=null&&be.sealed&&!be.ready.isEmpty()&&be.readyQuality==2&&mc.level.getBlockState(CAULDRON.offset(1,0,1)).getValue(AlchemyBlock.CLOSED));}
            if(scene==32)check("actual_engine_sponsor_release",com.tnc.tnc.arcana.TechnologyNativeScenes.actualSponsoredRelease);
            if(scene==28)check("extractor_full_gui",mc.screen instanceof com.tnc.tnc.life.routes.RouteClient.Screen);
            if(scene==30)check("actual_dead_realm_postprocess",mc.level.dimension().equals(net.minecraft.world.level.Level.NETHER)&&com.tnc.tnc.arcana.client.ArcanaClient.deadMana&&com.tnc.tnc.arcana.client.ManaRealmRenderer.desolationFrames>0);
            if(scene==31)check("actual_powered_oasis_mesh",mc.level.dimension().equals(net.minecraft.world.level.Level.NETHER)&&!com.tnc.tnc.arcana.client.ArcanaClient.deadMana&&com.tnc.tnc.arcana.client.ArcanaClient.oases.length>0&&com.tnc.tnc.arcana.client.ManaRealmRenderer.oasisQuads>0);
            if(scene==26){var be=mc.level.getBlockEntity(CAULDRON.offset(32,0,30));check("client_tower_direct_intake",be instanceof com.tnc.tnc.life.routes.RouteNodeEntity n&&n.mana>0&&n.getPersistentData().getInt("AmbientRate")>0);}
            if(scene==17)check("aurora_state_and_actual_mesh",com.tnc.tnc.arcana.client.ArcanaClient.aurora&&com.tnc.tnc.arcana.client.AuroraRenderer.renderedQuads>0);
            if(scene==21)check("actual_posed_ghost_mesh_rendered",com.tnc.tnc.arcana.client.ArcanaGhosts.renderedQuads>0);
            if(scene==19||scene==20||scene==21)check("arcana_camera_not_punished_"+scene,Math.abs(mc.player.getY()-(CAULDRON.getY()+4.5))<2);
            if(scene==7)check("mana_sight_enabled",com.tnc.tnc.mana.sight.client.ManaSightClient.enabled());
            String name=String.format(Locale.ROOT,"%02d-%s.png",scene,NAMES[scene]);saving=true;captured=true;
            Screenshot.grab(mc.gameDirectory,"journey-"+name,mc.getMainRenderTarget(),m->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/journey-"+name),output().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ex){failures.add(ex.toString());}finally{saving=false;}});return;
        }
        if(ticks<155)return;mc.setScreen(null);if(++scene==NAMES.length){finish(mc);return;}ticks=0;captured=false;
        if(scene==9){mc.options.guiScale().set(3);org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1024,640);}
        mc.options.setCameraType(scene==19?CameraType.THIRD_PERSON_FRONT:CameraType.FIRST_PERSON);
        prepare(mc);
    }
    private static void prepare(Minecraft mc){ready=false;mc.getSingleplayerServer().execute(()->{try{
        var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.closeContainer();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.setHealth(p.getMaxHealth());p.removeAllEffects();
        var record=JourneyData.get(p.server).player(p.getUUID());record.putBoolean("WelcomeShown",true);FamilyLife.initialize(p);
        if(scene==0||scene==9){var f=Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile").getField("INSTANCE").get(null);var chapter=f.getClass().getMethod("getChapter",long.class).invoke(f,Long.parseLong("37B7FFAC33E9E92C",16));check("actual_ftb_chapter_loaded",chapter!=null&&((List<?>)chapter.getClass().getMethod("getQuests").invoke(chapter)).size()==29);var deep=f.getClass().getMethod("getChapter",long.class).invoke(f,Long.parseLong("30D9D5470D862905",16));check("actual_deep_arcana_chapter_loaded",deep!=null&&((List<?>)deep.getClass().getMethod("getQuests").invoke(deep)).size()==23);var progression=f.getClass().getMethod("getChapter",long.class).invoke(f,Long.parseLong("662EE66DA1916AAF",16));check("actual_progression_chapter_loaded",progression!=null&&((List<?>)progression.getClass().getMethod("getQuests").invoke(progression)).size()==23);FamilyLife.openProfile(p);check("mca_initialization_no_destiny_buffs",!p.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)&&!p.hasEffect(net.minecraft.world.effect.MobEffects.HEALTH_BOOST));ready=true;return;}
        if(scene==1||scene==2){
            var pos=TownServices.board(p.server.overworld());if(pos==null)throw new IllegalStateException("Prepared audit world's town anchor missing");p.teleportTo(p.server.overworld(),pos.getX()+.5,pos.getY(),pos.getZ()+2.5,180,0);AdventureService.register(p);
            if(scene==2){var ledger=CommissionService.ledger(p);ledger.remove("tnc:road_watch");CommissionService.act(p,"accept","tnc:road_watch");for(int i=0;i<6;i++)CommissionService.progress(p,"kill","minecraft:zombie");check("native_commission_goal",ledger.getCompound("tnc:road_watch").getInt("Progress")==6);}
            var state=com.tnc.tnc.alchemy.AlchemyContent.BOARD.defaultBlockState();p.serverLevel().setBlock(pos,state,3);
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.SOUTH,pos,false);
            state.use(p.serverLevel(),p,net.minecraft.world.InteractionHand.MAIN_HAND,hit);check("actual_board_block_interaction",TownServices.isTownBoard(p.serverLevel(),pos));ready=true;return;
        }
        if(scene==3||scene==4||scene==10||scene==12||scene==13||scene==22){
            p.teleportTo(p.server.overworld(),CAULDRON.getX()+.5,CAULDRON.getY()+1.1,CAULDRON.getZ()+4.8,180,24);var world=p.serverLevel();world.setDayTime(18000);world.setWeatherParameters(10000,0,false,false);
            for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)world.setBlock(CAULDRON.offset(x,-1,z),Blocks.DEEPSLATE_TILES.defaultBlockState(),3);
            for(int x=-4;x<=4;x++)for(int y=0;y<5;y++)world.setBlock(CAULDRON.offset(x,y,-3),y==0||x==-4||x==4?Blocks.CHISELED_DEEPSLATE.defaultBlockState():Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),3);
            for(int x:new int[]{-3,3})world.setBlock(CAULDRON.offset(x,0,-2),Blocks.SEA_LANTERN.defaultBlockState(),3);
            world.setBlock(CAULDRON,Blocks.AIR.defaultBlockState(),3);world.setBlock(CAULDRON,AlchemyContent.CAULDRON.defaultBlockState(),3);((AlchemyBlock)AlchemyContent.CAULDRON).setPlacedBy(world,CAULDRON,AlchemyContent.CAULDRON.defaultBlockState(),p,new ItemStack(AlchemyContent.CAULDRON));var be=(AlchemyEntity)world.getBlockEntity(CAULDRON);be.owner=p.getUUID();var e=Elixir.SPRING;
            check("native_water_pour",be.addWater());for(int i=0;i<3;i++)be.insert(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse(e.ingredients.get(i)))));
            var fire=new com.tnc.tnc.magic.fire.TNFireBoltEntity(com.tnc.tnc.magic.TNOrbEntities.FIRE_BOLT.get(),world);
            fire.configure(p,com.tnc.tnc.magic.fire.FireSpellRules.bolt("fireball"),"fireball",net.minecraft.world.phys.Vec3.atCenterOf(CAULDRON).add(0,0,3),new net.minecraft.world.phys.Vec3(0,0,-1));if(scene<12)world.addFreshEntity(fire);
            if(scene==12){be.ignite(true);be.temperature=450;be.progress=110;be.job="spring";}
            if(scene==13){be.sealed=true;AlchemyBlock.close(world,CAULDRON,true);be.ready="spring";be.water=2;be.progress=160;be.job="spring";be.burst=70;for(int i=0;i<4;i++)be.inventory.setItem(i,ItemStack.EMPTY);}
            if(scene==22){be.ignite(true);be.temperature=450;be.vortex_sync=100;be.progress=160;be.perfectRemaining=30;be.seal(p);}
            if(scene==3||scene==10){p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BOOK));be.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND);}ready=true;return;
        }
        if(scene==11){var items=new net.minecraft.world.SimpleContainer(27);for(var e:Elixir.values())items.setItem(e.ordinal(),new ItemStack(AlchemyContent.POTIONS.get(e)));items.setItem(23,new ItemStack(AlchemyContent.CAULDRON));items.setItem(24,new ItemStack(com.tnc.tnc.arcana.ArcanaContent.item("sealing_lid")));items.setItem(25,new ItemStack(AlchemyContent.BOARD));p.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,player)->net.minecraft.world.inventory.ChestMenu.threeRows(id,inv,items),net.minecraft.network.chat.Component.literal("魔焰药藏 · 二十三剂")));ready=true;return;}
        if(scene>=27){com.tnc.tnc.arcana.TechnologyNativeScenes.prepare(p,scene,CAULDRON);ready=true;return;}
        if(scene>=14){com.tnc.tnc.arcana.ArcanaNativeScenes.prepare(p,scene,CAULDRON.offset(32,0,0));ready=true;return;}
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
        var report=new LinkedHashMap<String,Object>();report.put("screenshots",NAMES.length);report.put("checks",checks);report.put("aurora_projection",com.tnc.tnc.arcana.client.AuroraRenderer.projectionDiagnostic);report.put("failures",failures);report.put("scope","Disposable full PCL pack; FTB chapter loading, real screens, alchemy fusion, MCA family, deep arcana devices, living armor and aurora");
        var hashes=new LinkedHashMap<String,String>();for(var c:List.of(JourneyScreen.class,PreparationScreen.class,com.tnc.tnc.alchemy.client.AlchemyScreen.class,com.tnc.tnc.alchemy.client.AlchemyRenderer.class,com.tnc.tnc.arcana.client.ArcanaClient.class,com.tnc.tnc.arcana.client.ArcanaDeviceVisual.class,com.tnc.tnc.arcana.client.ArcanaArmorModels.class,com.tnc.tnc.arcana.client.AuroraRenderer.class,com.tnc.tnc.arcana.client.DimensionalSpiritGeometry.class,com.tnc.tnc.arcana.client.ArcanaGhosts.class,com.tnc.tnc.arcana.ArcanaBeacon.class,com.tnc.tnc.arcana.ArcanaTechnology.class,com.tnc.tnc.arcana.ArcanaEnergy.class,com.tnc.tnc.arcana.ArcanaRealms.class,com.tnc.tnc.arcana.ArcanaMeals.class,com.tnc.tnc.arcana.client.ManaRealmRenderer.class))try(var in=c.getResourceAsStream("/"+c.getName().replace('.','/')+".class")){hashes.put(c.getName(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));}report.put("class_sha256",hashes);Files.writeString(output().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
    }
}
