package com.tnc.tnc.tavern.client;

import net.minecraft.client.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Shot/reverse-shot staging, with the real seated player rather than a replacement actor. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class TavernOpeningScene {
    private static Vec3 hostEye;
    private static ArmorStand camera;private static CameraType previous;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static ListTag lines;private static int self,age,line,time;
    public static boolean active(){return camera!=null;}
    public static boolean playerSpeaking(){return active()&&time>35&&!lines.getCompound(line).getString("Speaker").isEmpty()&&!lines.getCompound(line).getString("Speaker").equals("Self");}
    public static void start(CompoundTag t){
        close();var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        self=t.getInt("Self");lines=t.getList("Lines",10);if(lines.isEmpty())return;
        world=mc.level;hostEye=null;age=line=time=0;previous=mc.options.getCameraType();
        camera=new ArmorStand(world,mc.player.getX(),mc.player.getY(),mc.player.getZ());camera.setInvisible(true);camera.setNoGravity(true);
        mc.setScreen(null);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(camera);update();
    }
    public static void close(){var mc=Minecraft.getInstance();if(camera!=null&&mc.getCameraEntity()==camera)mc.setCameraEntity(mc.player);if(previous!=null)mc.options.setCameraType(previous);camera=null;previous=null;world=null;}
    private static void update(){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        var actor=world.getEntity(self);
        // The full pack can replace the NPC's client entity while its dialogue is running.
        // Keep a stable shot anchor and reacquire the replacement instead of freezing the camera.
        if(actor==null&&hostEye!=null){
            var nearby=world.getEntitiesOfClass(com.tnc.tnc.npc.SelfNpcEntity.class,
                    new net.minecraft.world.phys.AABB(hostEye,hostEye).inflate(5));
            if(!nearby.isEmpty()){actor=nearby.get(0);self=actor.getId();}
        }
        if(actor!=null)hostEye=actor.getEyePosition();
        if(hostEye==null)return;
        Vec3 hero=mc.player.getEyePosition().add(0,-.1,0),host=hostEye;
        boolean hostSpeaking=lines.getCompound(line).getString("Speaker").equals("Self");
        Vec3 focus=hostSpeaking?host.add(0,.25,0):hero.add(0,.35,0);
        // Stay on the same side of the dialogue axis; a gentle push-in instead of constant orbiting.
        double push=Math.min(1,time/120.)*.25;
        Vec3 eye=hostSpeaking?hero.add(.4,.9,1.8-push):hero.add(.7,1.0,2.2-push);
        if(line==0){focus=hero.lerp(host,.5).add(0,.65,0);eye=focus.add(-.5,.45,3.5-push);}
        var hit=world.clip(new net.minecraft.world.level.ClipContext(focus,eye,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)eye=hit.getLocation().lerp(focus,.12);
        mc.setCameraEntity(camera);
        var delta=focus.subtract(eye);camera.setPos(eye.x,eye.y-camera.getEyeHeight(),eye.z);
        camera.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));camera.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())));
        camera.xo=camera.xOld=camera.getX();camera.yo=camera.yOld=camera.getY();camera.zo=camera.zOld=camera.getZ();camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();
        if(Boolean.getBoolean("tnc.openingAudit")&&time==36)System.out.println("OPENING_CAMERA line="+line+" speaker="+lines.getCompound(line).getString("Speaker")+" hero="+hero+" host="+host+" eye="+eye+" yaw="+camera.getYRot()+" actual="+mc.getCameraEntity().getId());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!active())return;var mc=Minecraft.getInstance();
        if(mc.level!=world||mc.player==null||!mc.player.isAlive()){close();return;}if(mc.isPaused())return;
        int index=0,t=age++;while(index<lines.size()&&t>=lines.getCompound(index).getInt("Ticks"))t-=lines.getCompound(index++).getInt("Ticks");
        if(index>=lines.size()){close();return;}line=index;time=t;update();
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent e){if(active()){e.getInput().forwardImpulse=0;e.getInput().leftImpulse=0;e.getInput().jumping=false;e.getInput().shiftKeyDown=false;}}
    @SubscribeEvent public static void interact(InputEvent.InteractionKeyMappingTriggered e){if(active()){e.setCanceled(true);e.setSwingHand(false);}}
    @SubscribeEvent public static void hand(RenderHandEvent e){if(active())e.setCanceled(true);}
    @SubscribeEvent public static void names(RenderNameTagEvent e){if(active()||com.tnc.tnc.skyacademy.client.FirstSightScene.locked())e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void angles(ViewportEvent.ComputeCameraAngles e){if(active()){e.setYaw(camera.getYRot());e.setPitch(camera.getXRot());e.setRoll(0);}}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void gui(RenderGuiEvent.Pre e){
        if(!active())return;e.setCanceled(true);var mc=Minecraft.getInstance();var g=e.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight();
        g.fill(0,0,w,24,0xff000000);g.fill(0,h-66,w,h,0xff000000);
        var text=lines.getCompound(line);g.drawCenteredString(mc.font,text.getString("Speaker"),w/2,h-55,0xffd9bd91);
        var wrapped=mc.font.split(Component.literal(text.getString("Text")),Math.min(440,w-40));
        for(int i=0;i<wrapped.size();i++)g.drawString(mc.font,wrapped.get(i),(w-mc.font.width(wrapped.get(i)))/2,h-38+i*12,0xffeee7dc,false);
    }
    private TavernOpeningScene(){}
}
