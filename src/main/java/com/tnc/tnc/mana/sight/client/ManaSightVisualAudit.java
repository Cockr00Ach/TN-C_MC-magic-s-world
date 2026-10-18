package com.tnc.tnc.mana.sight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.tnc.tnc.life.routes.*;
import java.nio.file.*;
import java.util.*;

/** Opt-in isolated visual test; it never runs in an ordinary user session. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class ManaSightVisualAudit {
    private static boolean started,captured;
    private static volatile boolean ready;
    private static volatile boolean saving;
    private static int ticks,stage,frames,maxParticles,maxFlows;
    private static final List<String> failures=new ArrayList<>();
    private static final boolean MECH=Boolean.getBoolean("tnc.mechAudit");
    private static net.minecraft.world.entity.monster.Zombie target;
    private static Path folder(){return Path.of(System.getProperty("tnc.manaSightAuditOutput","../work/mana-sight-particles-audit")).toAbsolutePath();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(!Boolean.getBoolean("tnc.manaSightAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!started){started=true;Files.createDirectories(folder());mc.setScreen(null);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.options.guiScale().set(2);mc.options.pauseOnLostFocus=false;mc.resizeDisplay();setup(mc);return;}
        if(!ready||saving)return;
        ++ticks;maxParticles=Math.max(maxParticles,ManaSightParticles.count());maxFlows=Math.max(maxFlows,ManaSightClient.activeCount());
        if(ManaSightParticles.count()>5200)failures.add("Particle hard cap exceeded");
        int delay=switch(stage){case 0,6->40;case 1->25;case 12,14->12;case 13->25;default->100;};
        if(ticks==20)mc.gui.getChat().clearMessages(false);
        if(stage==3&&ticks%2==0&&frames<60){grab(mc,"motion-"+String.format("%03d",frames++)+".png");return;}
        if(ticks<delay||stage==3&&frames<60)return;
        if(!captured){grab(mc,stage+"-"+new String[]{"normal","activation","daylight","motion","transfer","night","off","reenter","mech-front","mech-back","mech-firstperson-fire","mech-barrier","mech-dash","mech-overdrive","mech-nova","paired-mirrors"}[stage]+".png");captured=true;return;}
        if(stage==6&&ManaSightParticles.sightCount()!=0)failures.add("Owned particles survived toggle off");
        if(++stage==(MECH?16:8)){
            if(maxParticles<300)failures.add("Particle field failed to populate");
            if(maxFlows==0||ManaSightParticles.emittedTransfers()==0)failures.add("No server-confirmed transfer particles");
            var report=new LinkedHashMap<String,Object>();report.put("failures",failures);report.put("max_particles",maxParticles);report.put("max_actual_flows",maxFlows);report.put("emitted_transfer_particles",ManaSightParticles.emittedTransfers());report.put("screenshots",MECH?16:8);report.put("mech_and_mirror_scenes",MECH);report.put("motion_frames",frames);report.put("shader_pack",System.getProperty("tnc.manaSightAuditShader","none"));report.put("oculus_opaque_passes_skipped",ManaParticleShaderCompat.skippedOpaquePasses);report.put("scope","Isolated actual Minecraft client rendering and server-confirmed machine flow");
            Files.writeString(folder().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();return;
        }
        ticks=0;captured=false;
        if(stage==1||stage==7)ManaSightClient.setEnabled(true);
        if(stage==4)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).connection.teleport(1028.5,223,1035.5,180,25));
        if(stage==5)mc.getSingleplayerServer().execute(()->{mc.getSingleplayerServer().overworld().setDayTime(18000);mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).connection.teleport(1028.5,223,1041.5,180,-5);});
        if(stage==6)ManaSightClient.setEnabled(false);
        if(MECH&&stage>=8)mechStage(mc);

    }
    private static void mechStage(Minecraft mc){
        if(stage==8){
            var gun=new ItemStack(RouteContent.item("mech_spellgun"));
            if(!(net.minecraftforge.client.extensions.common.IClientItemExtensions.of(gun).getCustomRenderer() instanceof com.tnc.tnc.life.routes.mech.MechGunRenderer))failures.add("Gun custom renderer not attached");
            for(String part:List.of("body","barrels","glow")){var model=mc.getModelManager().getModel(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","item/mech_spellgun_"+part));if(model.getQuads(null,null,net.minecraft.util.RandomSource.create(1)).isEmpty())failures.add("Gun geometry missing: "+part);}
            ManaSightClient.setEnabled(false);mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);mc.getSingleplayerServer().execute(()->{
            var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);p.serverLevel().setDayTime(6000);var armor=new ItemStack(RouteContent.item("mana_mech"));p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,armor);RouteChargeLedger.get(p.serverLevel()).deposit(armor,12000,12000);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(RouteContent.item("mech_spellgun")));p.getInventory().selected=0;p.getInventory().setItem(0,p.getMainHandItem());p.connection.teleport(1028.5,222,1036.5,180,6);
            target=net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());target.setPos(1028.5,221,1021.5);target.setNoAi(true);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(600);target.setHealth(600);p.serverLevel().addFreshEntity(target);
        });}
        if(stage==9)mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        if(stage==10||stage==13){mc.options.setCameraType(stage==10?net.minecraft.client.CameraType.FIRST_PERSON:net.minecraft.client.CameraType.THIRD_PERSON_FRONT);mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND);}
        if(stage==11||stage==14||stage==15){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
        if(stage==11)mc.getSingleplayerServer().execute(()->com.tnc.tnc.life.routes.mech.MechCombat.activate(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0),com.tnc.tnc.life.routes.mech.MechNetwork.Action.SHIELD));
        if(stage==12){mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);mc.getSingleplayerServer().execute(()->com.tnc.tnc.life.routes.mech.MechCombat.activate(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0),com.tnc.tnc.life.routes.mech.MechNetwork.Action.DASH));}
        if(stage==13)mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getOrCreateTag().putInt("MechHeat",0);com.tnc.tnc.life.routes.mech.MechCombat.activate(p,com.tnc.tnc.life.routes.mech.MechNetwork.Action.OVERDRIVE);});
        if(stage==14){mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);mc.getSingleplayerServer().execute(()->com.tnc.tnc.life.routes.mech.MechCombat.activate(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0),com.tnc.tnc.life.routes.mech.MechNetwork.Action.NOVA));}
        if(stage==15){mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);ManaSightClient.setEnabled(true);mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).connection.teleport(1031.5,224,1036.5,180,15));}
    }
    private static void grab(Minecraft mc,String name){saving=true;Screenshot.grab(mc.gameDirectory,"mana-particle-"+name,mc.getMainRenderTarget(),message->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/mana-particle-"+name),folder().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ex){failures.add("Screenshot failed: "+ex.getMessage());}finally{saving=false;}});}
    private static void setup(Minecraft mc){mc.getSingleplayerServer().execute(()->{
        var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);var l=p.serverLevel();p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,ItemStack.EMPTY);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.stopUsingItem();
        for(var guest:l.getEntitiesOfClass(com.tnc.tnc.tavern.TavernGuestEntity.class,new net.minecraft.world.phys.AABB(996,210,996,1060,240,1060)))guest.discard();
        l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(var at:BlockPos.betweenClosed(996,219,996,1060,235,1060)){
            l.getChunkAt(at);l.setBlock(at,at.getY()==219?Blocks.STONE.defaultBlockState():at.getY()==220?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        }
        for(int[] t:new int[][]{{1013,1019},{1039,1021},{1011,1040},{1045,1039},{1027,1009}}){
            for(int y=221;y<228;y++)l.setBlock(new BlockPos(t[0],y,t[1]),Blocks.OAK_LOG.defaultBlockState(),2);
            for(int y=225;y<=230;y++)for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)if(x*x+z*z+Math.pow((y-227)*1.2,2)<13)l.setBlock(new BlockPos(t[0]+x,y,t[1]+z),Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true),2);
        }
        for(int x=1024;x<1033;x++)for(int z=1024;z<1031;z++)l.setBlock(new BlockPos(x,220,z),RouteContent.SOIL.get().defaultBlockState(),2);
        l.setBlock(new BlockPos(1028,220,1027),RouteContent.MANA_LIQUID.get().defaultBlockState(),3);
        String[] names={"dance_bell","dawn_disk","echo_bean","mirror_lotus","prism_crown","turnpage_fern"};
        for(int i=0;i<names.length;i++){
            var b=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc",names[i]));var state=b.defaultBlockState();
            for(var property:state.getProperties())if(property.getName().equals("age")&&property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty age)state=state.setValue(age,age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(3));
            l.setBlock(new BlockPos(1025+i,221,1026+i%2*2),state,3);
        }
        for(int i=0;i<5;i++){
            var at=new BlockPos(1025+i,221,1031);String id=i==0?RouteKind.RESERVOIR.id:i==4?RouteKind.CHARGER.id:RouteKind.WIRE.id;
            var block=RouteContent.NODES.get(id);if(block==null)throw new IllegalStateException(id);
            l.setBlock(at,block.get().defaultBlockState(),3);var node=(RouteNodeEntity)l.getBlockEntity(at);node.owner=p.getUUID();node.mana=i==0?5000:0;
            if(i==4)node.inventory.setItem(0,new ItemStack(RouteContent.item("dew_ward_charm")));
            RouteNetwork.add(l,at);
        }
        var a=new BlockPos(1024,221,1031);var b=new BlockPos(1035,224,1026);
        for(var at:java.util.List.of(a,b)){l.setBlock(at,RouteContent.NODES.get(RouteKind.FAR_MIRROR.id).get().defaultBlockState(),3);var mirror=(RouteNodeEntity)l.getBlockEntity(at);mirror.owner=p.getUUID();RouteNetwork.add(l,at);}
        var lamp=b.east();l.setBlock(lamp,RouteContent.NODES.get(RouteKind.BRIGHT_LAMP.id).get().defaultBlockState(),3);((RouteNodeEntity)l.getBlockEntity(lamp)).owner=p.getUUID();RouteNetwork.add(l,lamp);
        String linked=RouteMirrorLinks.link(l,a,b,p.getUUID());if(!linked.isEmpty())failures.add("Optical fixture: "+linked);
        for(int i=0;i<5;i++){var at=new BlockPos(1025+i,221,1031);if(l.getBlockState(at).getBlock() instanceof RouteWireBlock wire)wire.refresh(l,at);}
        for(var old:l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,new net.minecraft.world.phys.AABB(996,210,996,1060,240,1060),e->!(e instanceof net.minecraft.world.entity.player.Player)))old.discard();
        l.setDayTime(6000);l.setWeatherParameters(6000,0,false,false);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.connection.teleport(1028.5,223,1041.5,180,0);ready=true;
    });}
}
