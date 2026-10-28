package com.tnc.tnc.skyacademy.client;

import com.tnc.tnc.skyacademy.*;
import com.tnc.tnc.mana.sight.client.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Random;

/** Local camera only. Server movement, world time and other players are never changed. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class AcademyClient {
    private static BlockPos origin;private static String active="",shot="";private static int complete,exposure,age,duration,rune,hintAge;
    private static ArmorStand camera;private static CameraType previous;private static net.minecraft.client.multiplayer.ClientLevel owner;
    private static boolean practice,restoreSight,changedSight;private static final Random random=new Random(261026);
    public static void receive(CompoundTag t){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        switch(t.getString("Type")){
            case "dialogue"->mc.setScreen(new AcademyScreen(t));
            case "shot"->{origin=BlockPos.of(t.getLong("Origin"));start(t.getString("Site"),t.getInt("Ticks"));}
            case "state"->{origin=BlockPos.of(t.getLong("Origin"));int next=t.getInt("Completed");if(next!=complete)hintAge=300;complete=next;active=t.getString("Active");exposure=t.getInt("Exposure");rune=t.getInt("Rune");practice=t.getBoolean("Practice");}
        }
    }
    public static boolean cinematic(){return camera!=null;}
    public static String shot(){return shot;}
    public static BlockPos origin(){return origin;}
    private static void start(String name,int ticks){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getCameraEntity()!=mc.player)return;
        mc.setScreen(null);shot=name;age=0;duration=Math.min(360,Math.max(40,ticks));previous=mc.options.getCameraType();
        restoreSight=ManaSightClient.enabled();boolean desired=!name.equals("arrival")&&!name.equals("stargaze");changedSight=restoreSight!=desired;if(changedSight)ManaSightClient.setEnabled(desired);
        camera=new ArmorStand(mc.level,mc.player.getX(),mc.player.getY(),mc.player.getZ());camera.setInvisible(true);camera.setNoGravity(true);owner=mc.level;
        update();if(camera!=null){mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(camera);}
    }
    public static void stop(){var mc=Minecraft.getInstance();boolean stargaze=shot.equals("stargaze");if(camera!=null&&mc.getCameraEntity()==camera)mc.setCameraEntity(mc.player);if(previous!=null)mc.options.setCameraType(previous);if(changedSight&&!stargaze)ManaSightClient.setEnabled(restoreSight);changedSight=false;camera=null;previous=null;shot="";}
    private static Vec3 point(String site){return Vec3.atCenterOf(origin.offset(SkyAcademyWorld.SITES.get(site)));}
    private static double ease(double a){a=Math.max(0,Math.min(1,a));return a*a*(3-2*a);}
    private static void update(){
        var mc=Minecraft.getInstance();if(camera==null||origin==null)return;
        double t=ease(age/(double)duration);Vec3 eye,focus;var at=point(shot.equals("arrival")?"arrival":shot);
        if(shot.equals("arrival")){
            // Rise from the gateway foreground to the skyline; stay in the player's chunk radius.
            var entry=Vec3.atCenterOf(origin.offset(209,90,424));eye=entry.add(-20+40*t,12+18*Math.sin(t*Math.PI/2),18-12*t);
            focus=Vec3.atCenterOf(origin.offset(260,119,265)).add((t-.5)*75,0,0);
        }else if(shot.equals("stargaze")){
            eye=at.add(-6+12*t,5,9);focus=Vec3.atCenterOf(origin.offset(260,105,260));
        }else if(shot.equals("wind")){
            eye=at.add(10-18*t,5,4);focus=point("rune1").add(0,1,0);
        }else {
            var b=point(shot).add(shot.equals("farm")?-3:-5,0,shot.equals("farm")?-10:-4);
            focus=b.add(shot.equals("farm")?(t<.5?0:5):2,.65,0);eye=b.add(-3+10*t,4.5,8);
        }
        // Never render through a wall, or switch camera into an unreceived chunk.
        var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(focus,eye,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)eye=hit.getLocation().lerp(focus,.08);
        if(!mc.level.hasChunkAt(BlockPos.containing(eye))){stop();return;}
        var dir=focus.subtract(eye);camera.xo=camera.getX();camera.yo=camera.getY();camera.zo=camera.getZ();camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();
        camera.setPos(eye.x,eye.y-camera.getEyeHeight(),eye.z);float yaw=(float)Math.toDegrees(Math.atan2(-dir.x,dir.z));
        camera.setYRot(age==0?yaw:camera.yRotO+net.minecraft.util.Mth.wrapDegrees(yaw-camera.yRotO));camera.setXRot((float)-Math.toDegrees(Math.atan2(dir.y,dir.horizontalDistance())));
        if(age==0){camera.xo=camera.getX();camera.yo=camera.getY();camera.zo=camera.getZ();camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(owner!=null&&owner!=mc.level){stop();origin=null;active="";complete=0;exposure=0;owner=null;}
        if(mc.level==null||mc.player==null)return;owner=mc.level;if(mc.isPaused())return;if(hintAge>0)hintAge--;
        if(camera!=null){
            if(!mc.player.isAlive()||mc.player.isSpectator()||mc.options.keyShift.isDown()||mc.screen!=null||mc.getCameraEntity()!=camera||++age>=duration){
                boolean night=shot.equals("stargaze");stop();if(night){mc.player.setYRot(0);mc.player.setXRot(-8);mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("瑟兰：现在，亲手按F8。让风告诉你答案。"),true);}
            }else update();
        }
        if(origin!=null&&mc.level.dimension()==net.minecraft.world.level.Level.OVERWORLD&&ManaSightClient.enabled())scenery();
    }
    private static void scenery(){
        var mc=Minecraft.getInstance();var eye=mc.player.getEyePosition();long clock=mc.level.getGameTime();
        var ridge=point("stargaze");
        if(eye.distanceToSqr(ridge)<48*48&&AcademyRules.night(mc.level.getDayTime())){
            // An environment-sized river of individual luminous grains, never a solid ribbon wall.
            double reveal=active.equals("stargaze")?Math.min(1,exposure/160.):1;
            int quality=mc.options.particles().get()==ParticleStatus.MINIMAL?18:mc.options.particles().get()==ParticleStatus.DECREASED?34:56;
            String[] roots={"farm","lake","wind","workshop"};int[] colors={0x6CE7A7,0x8CDAFF,0xF3DC99,0x84F5E6};
            for(int i=0;i<quality;i++){
                int branch=i%4;double u=random.nextDouble()*reveal;var source=point(roots[branch]);var center=Vec3.atCenterOf(origin.offset(260,170,210));
                Vec3 base=source.lerp(center,u);double curve=Math.sin(u*Math.PI);
                Vec3 at=base.add(Math.sin(u*6+branch)*curve*22+(random.nextDouble()-.5)*8,curve*20+(random.nextDouble()-.5)*8,Math.cos(u*6+branch)*curve*15+(random.nextDouble()-.5)*8);
                var to=at.add(center.subtract(source).normalize().scale(12)).add(0,3,0);
                ManaSightParticles.scenic(at,to,colors[branch],i%13==0?1:0,(float)(.12+random.nextDouble()*.22),65+random.nextInt(40));
            }
            for(int i=0;i<quality/3;i++){
                double a=random.nextDouble()*Math.PI*2,r=10+random.nextDouble()*36;
                var at=ridge.add(Math.cos(a)*r,10+random.nextDouble()*36,Math.sin(a)*r);
                ManaSightParticles.scenic(at,at.add(-Math.sin(a)*3,2,Math.cos(a)*3),i%3==0?0xFFE5A9:0xBAEAFF,i%6==0?1:0,.14f,100);
            }
            if(active.equals("stargaze")&&exposure>0&&exposure<25&&clock%20==0)mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,.55f,.65f);
        }
        if(eye.distanceToSqr(point("wind"))<40*40){
            int[] counts={2,1,3};for(int i=0;i<3;i++){var base=point("rune"+i);for(int n=0;n<counts[i];n++){
                double a=clock*.025+n*Math.PI*2/counts[i];var at=base.add(Math.cos(a)*.5,1.1+Math.sin(clock*.04)*.12,Math.sin(a)*.5);
                ManaSightParticles.effect(at,at.add(0,.07,0),0x9CFFF0,1,3,.16f,14);
            }}
        }
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent e){if(camera!=null){e.getInput().forwardImpulse=0;e.getInput().leftImpulse=0;e.getInput().jumping=false;}}
    @SubscribeEvent public static void hand(RenderHandEvent e){if(camera!=null)e.setCanceled(true);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void angles(ViewportEvent.ComputeCameraAngles e){if(camera!=null){float t=(float)e.getPartialTick();e.setYaw(net.minecraft.util.Mth.rotLerp(t,camera.yRotO,camera.getYRot()));e.setPitch(net.minecraft.util.Mth.lerp(t,camera.xRotO,camera.getXRot()));e.setRoll(0);}}
    @SubscribeEvent public static void gui(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(origin==null||mc.player==null||mc.options.hideGui)return;var g=e.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight();
        if(camera!=null){g.fill(0,0,w,22,0xe8051018);g.fill(0,h-44,w,h,0xe8051018);g.drawCenteredString(mc.font,AcademyLessons.title(shot),w/2,8,0xEAD9B5);
            String line=switch(shot){case "arrival"->"云溪 · 灯火之间，风替旅人记住归途";case "stargaze"->"先看一眼普通的夜色，稍后亲手按F8";case "wind"->"一枚、两枚、三枚光点：顺序比石头的位置更重要";case "farm"->age<duration/2?"成熟魔植 → 相邻集露盏，真实魔力慢慢积存":"牧务杖选择动物 → 绑定集息器；活动即产魔";default->"储池 → 双端晶纤 → 魔导灯；接通才会输送";};
            g.drawCenteredString(mc.font,line,w/2,h-33,0xD6EEE8);g.drawCenteredString(mc.font,"潜行键跳过镜头 · 实操仍需亲手完成",w/2,h-18,0xA5AAA7);return;
        }
        if(!active.isEmpty()){
            String text=switch(active){case "stargaze"->"夜景研习 · "+(ManaSightClient.enabled()?"看见魔脉如何汇入星空":"亲手按F8打开魔力视野");case "wind"->"F8寻找光点 · 依次右键一、二、三枚光点的石头（"+rune+"/3）";case "farm"->"练习：相邻放集露盏；牧务杖选兽，再右键集息器";default->"练习：补上晶纤缺口，观察魔力输送直到灯亮";};
            var at=point(active);if(mc.player.position().distanceToSqr(at)<32*32)g.drawCenteredString(mc.font,text,w/2,h-63,0xC8E8DD);
        }else if(hintAge>0){String next=AcademyRules.next(complete);var site=SkyAcademyWorld.SITES.get(next);if(site!=null)g.drawCenteredString(mc.font,"新的邀约 · "+AcademyLessons.title(next)+" · /skylesson 查看地点",w/2,h-63,0xC8E8DD);}
    }
}
