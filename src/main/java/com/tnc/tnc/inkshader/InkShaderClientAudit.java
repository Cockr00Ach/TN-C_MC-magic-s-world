package com.tnc.tnc.inkshader;

import com.tnc.tnc.world.ink.*;
import com.tnc.tnc.world.ink.client.InkCelestialDragonRenderer;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Disposable real Oculus/Embeddium client audit, including the downloaded original ink shader. Not installed. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class InkShaderClientAudit {
    private static int stage,ticks,total,dragonId,brushId;private static boolean opening,setup,scheduled,finishing;
    private static volatile boolean ready;private static volatile String serverFailure;private static int ground;
    private static final List<String> failures=new ArrayList<>();private static final Set<String> shots=new LinkedHashSet<>();
    private static final List<Map<String,Object>> states=new ArrayList<>();
    private static final String ORIGINAL="ComplementaryUnbound_r5.6.1-tnc-ink-clear-20261024.zip";
    private static final Path OUTPUT=Path.of(System.getProperty("tnc.inkShaderAuditOutput","D:/ModTest/work/ink-shader-client-20261027"));
    private static final boolean VISUAL=Boolean.getBoolean("tnc.inkShaderVisualOnly");
    private static final boolean FX_OFF=Boolean.getBoolean("tnc.inkFxOff");
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void load(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent event){if(Boolean.getBoolean("tnc.inkShaderAudit"))event.enqueueWork(()->{
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.journey.client.JourneyClient.class);
        });}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ClientTickEvent event)throws Exception {
        if(!Boolean.getBoolean("tnc.inkShaderAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(++total>5000){failures.add("Native shader audit timed out at stage "+stage);finish();return;}
        if(mc.getOverlay()!=null)return;
        if(stage==9){
            if(mc.level!=null||mc.getConnection()!=null)return;
            if(++ticks<30)return;
            // There is no world rendering pipeline on the disconnected screen; the restored preference is still on.
            check("disconnect restoration",true,ORIGINAL,false);
            if(InkShaderClient.auditFiles().journalExists())failures.add("Recovery journal remained after disconnect");
            finish();return;
        }
        if(mc.level==null||mc.player==null){
            if(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&!opening){opening=true;mc.createWorldOpenFlows().loadLevel(mc.screen,"ink-shader-gallery");}
            if(mc.screen!=null)for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&Set.of("Proceed","Continue","I know what I'm doing!","继续").contains(b.getMessage().getString())){b.onPress();break;}
            return;
        }
        if(mc.screen!=null)mc.setScreen(null);
        var policy=InkShaderClient.auditSession();if(policy==null)return;
        if(policy.failed()){failures.add("Automatic switcher failed");finish();return;}
        if(serverFailure!=null){failures.add(serverFailure);finish();return;}
        mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);
        mc.options.renderDistance().set(6);mc.options.framerateLimit().set(100);mc.options.hideGui=false;
        if(!setup){
            setup=true;check(VISUAL?"originally off visual scene":"startup interrupted-session recovery",!VISUAL,ORIGINAL,!VISUAL);
            if(!policy.active()&&InkShaderClient.auditFiles().journalExists())failures.add("Startup recovery record was not cleared");
            mc.getSingleplayerServer().execute(()->{try{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();
                p.getInventory().setItem(0,new ItemStack(Items.DIAMOND_SWORD));p.getInventory().setItem(1,new ItemStack(Items.GOLDEN_APPLE));p.getInventory().setItem(2,new ItemStack(Items.LAPIS_LAZULI));
                var realm=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);realm.setDayTime(6000);
                realm.getChunk(1,1);ground=realm.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,16,16);
                for(var old:realm.getAllEntities())if(old instanceof InkCelestialDragonEntity)old.discard();
                if(Boolean.getBoolean("tnc.inkBrushOnly")){
                    for(var old:realm.getAllEntities())if(old instanceof InkBrushBossEntity)old.discard();
                    var brush=InkBrushRegistration.BRUSH.create(realm);brush.setNoAi(true);brush.moveTo(16,ground,16,0,0);realm.addFreshEntity(brush);brushId=brush.getId();
                    p.teleportTo(realm,23,ground+5,32,155,7);p.getAbilities().flying=true;p.onUpdateAbilities();stage=10;ticks=0;ready=true;return;
                }
                var dragon=InkBossRegistration.INK_CELESTIAL_DRAGON.create(realm);dragon.setNoAi(true);dragon.setNoGravity(true);dragon.setGroundedForObservation(false);dragon.moveTo(16,ground+8,16,90,0);realm.addFreshEntity(dragon);dragonId=dragon.getId();
                headCamera(p,dragon);p.getAbilities().flying=true;p.setNoGravity(true);p.onUpdateAbilities();ready=true;
            }catch(Throwable e){serverFailure=e.toString();}});return;
        }
        if(!ready)return;
        if(stage==10){
            var brush=mc.level.getEntity(brushId);
            if(brush==null)return;
            mc.player.setPos(brush.getX()+7,ground+5,brush.getZ()+16);mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setYRot(155);mc.player.yRotO=155;mc.player.setXRot(7);mc.player.xRotO=7;
            if(Boolean.getBoolean("tnc.inkBrushOnly")){
                double x=16+ticks*.10;
                mc.getSingleplayerServer().execute(()->{var mob=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM).getEntity(brushId);if(mob!=null){mob.setPos(x,ground,16);mob.setOnGround(true);}});
            }
            if(++ticks==100){shot("07-black-brush-walking.png");}
            if(ticks>=110){
                if(brush.getX()<18)failures.add("Brush did not navigate across the floor");
                int count=0;for(var point:((InkBrushBossEntity)brush).inkTrail())count++;
                if(count<4||count>64)failures.add("Brush trail outside bounded walking sample budget: "+count);
                finish();
            }return;
        }
        // The server can reset creative flight during dimension transfer. Keep this disposable camera fixed.
        if(mc.level.dimension().equals(InkRealmTravel.INK_REALM)&&stage<4){
            double scale=InkDragonCombatRules.BODY_SCALE;
            Vec3 eye=stage<2?new Vec3(16-5.7*scale,ground+8+6.7*scale,16+6*scale):new Vec3(16,ground+16,66);
            mc.player.setPos(eye.x,eye.y,eye.z);mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setNoGravity(true);mc.player.getAbilities().flying=true;
            mc.player.setYRot(180);mc.player.yRotO=180;mc.player.setXRot(stage<2?7:32);mc.player.xRotO=mc.player.getXRot();
        }
        boolean ink=mc.level.dimension().equals(InkRealmTravel.INK_REALM);
        if(stage<5&&!ink||stage==6&&!ink||stage==8&&!ink)return;
        if((stage==5||stage==7)&&ink)return;
        ticks++;
        if(stage==0&&ticks>=70){
            check("Ink automatically disables Complementary",false,ORIGINAL,false);shot("01-ink-auto-off-dragon.png");
            if(!FX_OFF&&!InkShaderClient.mode(InkShaderSession.Mode.INK))failures.add("Cannot select the downloaded Ink Wash pack");next(1);
        }else if(stage==1&&ticks>=70){
            check("Ink Wash active in custom dimension",!FX_OFF,FX_OFF?ORIGINAL:InkShaderSession.INK_PACK,!FX_OFF);shot("02-ink-wash-dragon.png");
            if(((Number)InkCelestialDragonRenderer.structureDiagnostics().get("cameraPasses")).longValue()==0)failures.add("Dragon mesh never reached the camera pass");
            mc.getSingleplayerServer().execute(()->{try{
                var realm=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);var dragon=(net.minecraft.world.entity.Mob)realm.getEntity(dragonId);
                InkRitualEntity.spawn(dragon,null,InkRitualEntity.ERUPTION,new Vec3(16,ground+.05,43),220);
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.teleportTo(realm,16,ground+10,66,180,25);
            }catch(Throwable e){serverFailure=e.toString();}});next(2);
        }else if(stage==2&&ticks>=50){
            check("Ink Wash rotating transparent seal",!FX_OFF,FX_OFF?ORIGINAL:InkShaderSession.INK_PACK,!FX_OFF);shot("03-ink-wash-seal.png");
            spawnOrb();next(3);
        }else if(stage==3&&ticks==36){shot("04-ink-wash-orb-gather.png");}
        else if(stage==3&&ticks>=82){
            shot("05-ink-wash-orb-aftermath.png");InkShaderClient.mode(InkShaderSession.Mode.OFF);next(4);
        }else if(stage==4&&ticks>=45){
            check("Return to off mode keeps original lease",false,ORIGINAL,false);
            if(!new InkShaderSession.State(!VISUAL,ORIGINAL).equals(policy.original()))failures.add("Mode switch overwrote the original Complementary state");
            teleportOutside();next(5);
        }else if(stage==5&&ticks>=60){
            check("Leaving restores original pack",!VISUAL,ORIGINAL,!VISUAL);shot(VISUAL?"06-overworld-restored-off.png":"06-overworld-restored-complementary.png");
            if(VISUAL){
                mc.getSingleplayerServer().execute(()->{try{
                    var realm=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);
                    for(var old:realm.getAllEntities())if(old instanceof InkCelestialDragonEntity||old instanceof InkBrushBossEntity)old.discard();
                    var brush=InkBrushRegistration.BRUSH.create(realm);brush.moveTo(16,ground,16,0,0);realm.addFreshEntity(brush);brushId=brush.getId();brush.getNavigation().moveTo(35,ground,16,1);
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.teleportTo(realm,23,ground+5,32,155,7);p.getAbilities().flying=true;p.onUpdateAbilities();
                }catch(Throwable e){serverFailure=e.toString();}});next(10);return;
            }
            InkShaderClient.auditBackend().apply(new InkShaderSession.State(false,ORIGINAL));teleportInside();next(6);
        }else if(stage==6&&ticks>=45){
            check("Originally off stays off inside",false,ORIGINAL,false);teleportOutside();next(7);
        }else if(stage==7&&ticks>=45){
            check("Originally off stays off after exit",false,ORIGINAL,false);
            InkShaderClient.auditBackend().apply(new InkShaderSession.State(true,ORIGINAL));teleportInside();next(8);
        }else if(stage==8&&ticks>=45){
            check("Ink off before disconnect",false,ORIGINAL,false);
            mc.getConnection().getConnection().disconnect(Component.literal("Ink shader audit complete"));next(9);
        }
    }
    private static void headCamera(net.minecraft.server.level.ServerPlayer p,InkCelestialDragonEntity dragon){
        double scale=InkDragonCombatRules.BODY_SCALE;Vec3 head=dragon.position().add(new Vec3(-5.7,5,0).scale(scale)).add(0,scale,0);
        p.teleportTo((net.minecraft.server.level.ServerLevel)dragon.level(),head.x,head.y+scale*.7,head.z+scale*6,180,7);
    }
    private static void spawnOrb(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
        var realm=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);var dragon=(net.minecraft.world.entity.Mob)realm.getEntity(dragonId);
        InkRitualEntity.spawn(dragon,null,InkRitualEntity.ORB,new Vec3(16,ground+4,43),180);
    });}
    private static void teleportOutside(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
        var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.teleportTo(mc.getSingleplayerServer().overworld(),.5,100,.5,0,20);
    });}
    private static void teleportInside(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
        var realm=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.teleportTo(realm,16,ground+10,66,180,25);
    });}
    private static void next(int value){stage=value;ticks=0;}
    private static void shot(String name){var mc=Minecraft.getInstance();if(shots.add(name))Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});}
    private static void check(String label,boolean enabled,String pack,boolean rendering)throws Exception {
        var b=InkShaderClient.auditBackend();var state=b.read();boolean actual=b.renderingShaders();
        states.add(Map.of("check",label,"enabled",state.enabled(),"pack",String.valueOf(state.pack()),"rendering",actual,"changes",InkShaderClient.auditSession().changes()));
        if(state.enabled()!=enabled||!Objects.equals(state.pack(),pack)||actual!=rendering)failures.add(label+": "+state+", rendering="+actual);
    }
    private static void finish()throws Exception {
        if(finishing)return;finishing=true;
        var report=new LinkedHashMap<String,Object>();report.put("failures",failures);report.put("states",states);report.put("screenshots",shots);
        report.put("shader",InkShaderSession.INK_PACK);report.put("scope","Isolated Forge 1.20.1 / Oculus 1.8.0 / Embeddium 0.3.31. No full-pack FPS claim.");
        Files.writeString(OUTPUT.resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));Minecraft.getInstance().stop();
    }
}
