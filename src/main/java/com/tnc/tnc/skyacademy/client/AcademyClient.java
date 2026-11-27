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
import java.util.HashMap;
import java.util.Map;

/** Local camera only. Server movement, world time and other players are never changed. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class AcademyClient {
    private static BlockPos origin;private static String active="",shot="";private static int complete,exposure,age,duration,rune,hintAge;
    private static ArmorStand camera;private static CameraType previous;private static net.minecraft.client.multiplayer.ClientLevel owner;
    private static Map<String,BlockPos> sites=SkyAcademyWorld.SITES;
    private static boolean practice,restoreSight,changedSight;private static final Random random=new Random(261026);
    public static void receive(CompoundTag t){
        if ("island_layout".equals(t.getString("Type"))) { SkyhavenLayoutClient.receive(t.getBoolean("Rebuilt"), t.getBoolean("Highlands")); return; }
        if(t.getString("Type").equals("arrival_loading")){com.tnc.tnc.world.skyhaven.SkyhavenArrivalClient.receive(t);return;}
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        if(owner!=mc.level){resetWorld();owner=mc.level;}
        if(t.contains("Sites",net.minecraft.nbt.Tag.TAG_COMPOUND)){
            var incoming=new HashMap<>(SkyAcademyWorld.SITES);var data=t.getCompound("Sites");
            for(var key:SkyAcademyWorld.SITES.keySet())if(data.contains(key,net.minecraft.nbt.Tag.TAG_LONG))incoming.put(key,BlockPos.of(data.getLong(key)));
            if(!sites.equals(incoming)){sites=Map.copyOf(incoming);WindLessonVisuals.clear();}
        }
        if(t.getString("Type").equals("tavern_opening")){com.tnc.tnc.tavern.client.TavernOpeningScene.start(t);return;}
        if(t.getString("Type").equals("tavern_end")){com.tnc.tnc.tavern.client.TavernOpeningScene.close();return;}
        switch(t.getString("Type")){
            case "dialogue"->mc.setScreen(new AcademyScreen(t));
            case "shot"->{origin=BlockPos.of(t.getLong("Origin"));if(t.getString("Site").equals("stargaze")){FirstSightScene.start(t);}else start(t.getString("Site"),t.getInt("Ticks"));}
            case "wind_touch"->WindLessonVisuals.touch(BlockPos.of(t.getLong("Origin")),t.getInt("Index"),t.getInt("Step"));
            case "state"->{origin=BlockPos.of(t.getLong("Origin"));int next=t.getInt("Completed");if(next!=complete)hintAge=300;complete=next;active=t.getString("Active");exposure=t.getInt("Exposure");rune=t.getInt("Rune");practice=t.getBoolean("Practice");}
        }
    }
    public static boolean cinematic(){return camera!=null||FirstSightScene.locked()||com.tnc.tnc.tavern.client.TavernOpeningScene.active();}
    public static String shot(){return shot;}
    public static BlockPos origin(){return origin;}
    private static void start(String name,int ticks){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getCameraEntity()!=mc.player)return;
        mc.setScreen(null);shot=name;age=0;duration=Math.min(360,Math.max(40,ticks));previous=mc.options.getCameraType();
        restoreSight=ManaSightClient.enabled();boolean desired=!name.equals("arrival")&&!name.equals("stargaze");changedSight=!name.equals("wind")&&restoreSight!=desired;if(changedSight)ManaSightClient.setEnabled(desired);
        camera=new ArmorStand(mc.level,mc.player.getX(),mc.player.getY(),mc.player.getZ());camera.setInvisible(true);camera.setNoGravity(true);owner=mc.level;
        update();if(camera!=null){mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(camera);}
    }
    public static void stop(){
        var mc=Minecraft.getInstance();
        var attached=mc.getCameraEntity();
        if(camera!=null&&(attached==camera||attached==mc.player||attached==null)){
            if(attached==camera)mc.setCameraEntity(mc.player);
            if(previous!=null)mc.options.setCameraType(previous);
        }
        if(changedSight&&!shot.equals("stargaze")&&owner==mc.level&&mc.player!=null)ManaSightClient.setEnabled(restoreSight);
        changedSight=false;camera=null;previous=null;shot="";
    }
    static BlockPos offset(String site){return sites.get(site);}
    private static Vec3 point(String site){return Vec3.atCenterOf(origin.offset(offset(site)));}
    private static void resetWorld(){stop();WindLessonVisuals.clear();origin=null;active="";complete=exposure=rune=hintAge=0;owner=null;sites=SkyAcademyWorld.SITES;}
    private static double ease(double a){a=Math.max(0,Math.min(1,a));return a*a*(3-2*a);}
    private static void update(){
        var mc=Minecraft.getInstance();if(camera==null||origin==null)return;
        double t=ease(age/(double)duration);Vec3 eye,focus,clipStart=null;var at=point(shot.equals("arrival")?"arrival":shot);
        if(shot.equals("arrival")){
            // Rise from the gateway foreground to the skyline; stay in the player's chunk radius.
            var entry=Vec3.atCenterOf(origin.offset(SkyhavenLayoutClient.rebuilt() ? new BlockPos(158,90,448) : new BlockPos(209,90,424)));eye=entry.add(-20+40*t,12+18*Math.sin(t*Math.PI/2),18-12*t);
            focus=Vec3.atCenterOf(origin.offset(260,119,265)).add((t-.5)*75,0,0);
        }else if(shot.equals("stargaze")){
            eye=at.add(-6+12*t,5,9);focus=Vec3.atCenterOf(origin.offset(260,105,260));
        }else if(shot.equals("wind")){
            var frame=WindLessonVisuals.path(origin).shot(age*(double)WindLessonPath.LENGTH/duration);
            eye=frame.eye();focus=frame.subject();clipStart=frame.clipStart();
        }else {
            boolean farm=shot.equals("farm");
            var b=point(shot).add(farm?-3:-5,0,farm?-10:-4);
            double travel=ease((t-.20)/.60);
            focus=farm?b.add(.5+5.1*travel,.38,.65*travel):b.add(4*travel,.10,0);
            eye=focus.add(-.78+1.5*t,farm?1.85:1.5,farm?4.8:3.85);
            clipStart=focus.add(0,.15,.85);
        }
        // Never render through a wall, or switch camera into an unreceived chunk.
        if(clipStart==null)clipStart=focus;
        var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(clipStart,eye,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)eye=hit.getLocation().lerp(clipStart,.08);
        if(!mc.level.hasChunkAt(BlockPos.containing(eye))){if(shot.equals("wind"))AcademyNetwork.action(0,"wind_skip");stop();return;}
        var dir=focus.subtract(eye);camera.xo=camera.getX();camera.yo=camera.getY();camera.zo=camera.getZ();camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();
        camera.setPos(eye.x,eye.y-camera.getEyeHeight(),eye.z);float yaw=(float)Math.toDegrees(Math.atan2(-dir.x,dir.z));
        camera.setYRot(age==0?yaw:camera.yRotO+net.minecraft.util.Mth.wrapDegrees(yaw-camera.yRotO));camera.setXRot((float)-Math.toDegrees(Math.atan2(dir.y,dir.horizontalDistance())));
        if(age==0){camera.xo=camera.getX();camera.yo=camera.getY();camera.zo=camera.getZ();camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(owner!=null&&owner!=mc.level)resetWorld();
        if(mc.level==null||mc.player==null)return;owner=mc.level;
        if(camera!=null&&shot.equals("wind")&&mc.screen!=null){AcademyNetwork.action(0,"wind_skip");stop();}
        if(mc.isPaused())return;if(hintAge>0)hintAge--;
        if(camera!=null){
            if(!mc.player.isAlive()||mc.player.isSpectator()||mc.options.keyShift.isDown()||mc.screen!=null||mc.getCameraEntity()!=camera||++age>=duration){
                if(shot.equals("wind")&&age<duration&&mc.player.isAlive())AcademyNetwork.action(0,"wind_skip");
                boolean night=shot.equals("stargaze");stop();if(night){mc.player.setYRot(0);mc.player.setXRot(-8);mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("瑟兰：现在，亲手按F8。让风告诉你答案。"),true);}
            }else update();
        }
        boolean island=mc.level.dimension()==net.minecraft.world.level.Level.OVERWORLD||mc.level.dimension().equals(com.tnc.tnc.world.skyhaven.SkyhavenContent.KEY);
        WindLessonVisuals.tick(island?origin:null,camera!=null&&shot.equals("wind"),age,duration,active.equals("wind"),rune);
        if(origin!=null&&island&&ManaSightClient.enabled())scenery();
    }
    private static void scenery(){
        var mc=Minecraft.getInstance();var eye=mc.player.getEyePosition();long clock=mc.level.getGameTime();
        var ridge=point("stargaze");
        if(eye.distanceToSqr(ridge)<48*48&&ManaSightParticles.sceneryAllowed(mc.level)){
            // An environment-sized river of individual luminous grains, never a solid ribbon wall.
            double reveal=1;
            int quality=mc.options.particles().get()==ParticleStatus.MINIMAL?18:mc.options.particles().get()==ParticleStatus.DECREASED?34:56;
            String[] roots={"farm","lake","wind","workshop"};int[] colors={0x6CE7A7,0x8CDAFF,0xF3DC99,0x84F5E6};
            for(int i=0;i<quality/2;i++){
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
    }
    @SubscribeEvent public static void windWorld(RenderLevelStageEvent e){WindLessonVisuals.render(e);}
    @SubscribeEvent public static void lens(ViewportEvent.ComputeFov e){if(camera!=null&&shot.equals("wind"))e.setFOV(48);}
    @SubscribeEvent public static void lockedInput(InputEvent.InteractionKeyMappingTriggered e){if(camera!=null&&shot.equals("wind")){e.setSwingHand(false);e.setCanceled(true);}}
    @SubscribeEvent public static void hideActor(RenderPlayerEvent.Pre e){
        if(camera!=null&&shot.equals("wind")&&(e.getEntity()==Minecraft.getInstance().player
                || e.getEntity().distanceToSqr(camera)<18*18))e.setCanceled(true);
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent e){if(camera!=null){e.getInput().forwardImpulse=0;e.getInput().leftImpulse=0;e.getInput().jumping=false;}}
    @SubscribeEvent public static void hand(RenderHandEvent e){if(camera!=null)e.setCanceled(true);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void angles(ViewportEvent.ComputeCameraAngles e){if(camera!=null){float t=(float)e.getPartialTick();e.setYaw(net.minecraft.util.Mth.rotLerp(t,camera.yRotO,camera.getYRot()));e.setPitch(net.minecraft.util.Mth.lerp(t,camera.xRotO,camera.getXRot()));e.setRoll(0);}else WindLessonVisuals.shake(e);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void windGui(RenderGuiEvent.Pre event){
        if(camera==null||!shot.equals("wind"))return;
        event.setCanceled(true);
        var mc=Minecraft.getInstance();if(mc.options.hideGui)return;
        var g=event.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight(),bar=Math.min(24,h/12);
        g.fill(0,0,w,bar,0xed102125);g.fill(0,h-bar,w,h,0xed102125);
        int t=age*WindLessonPath.LENGTH/duration;
        String line=t<43?"岚　「看铃心。第一缕风要来了。」":t<91?"「听，它在下一枚铃里换了个音。」":t<137?"「跟住这阵风，最后一声藏在哪儿？」":"「现在，把这三声还给山谷。」";
        g.drawCenteredString(mc.font,line,w/2,h-bar/2-4,0xE6EFE0);
        String skip=mc.options.keyShift.getTranslatedKeyMessage().getString()+" · 跳过";
        g.drawString(mc.font,skip,w-mc.font.width(skip)-8,bar/2-4,0xACBDB7,false);
    }
    @SubscribeEvent public static void gui(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(origin==null||mc.player==null||mc.options.hideGui)return;var g=e.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight();
        if(camera!=null&&shot.equals("wind"))return;
        if(camera!=null){g.fill(0,0,w,22,0xe8051018);g.fill(0,h-44,w,h,0xe8051018);g.drawCenteredString(mc.font,AcademyLessons.title(shot),w/2,8,0xEAD9B5);
            String line=switch(shot){case "arrival"->"云溪 · 灯火之间，风替旅人记住归途";case "stargaze"->"先看一眼普通的夜色，稍后亲手按F8";case "farm"->age<duration/2?"成熟魔植 → 相邻集露盏，真实魔力慢慢积存":"牧务杖选择动物 → 绑定集息器；活动即产魔";default->"储池 → 双端晶纤 → 魔导灯；接通才会输送";};
            g.drawCenteredString(mc.font,line,w/2,h-33,0xD6EEE8);g.drawCenteredString(mc.font,"潜行键跳过镜头 · 实操仍需亲手完成",w/2,h-18,0xA5AAA7);return;
        }
        if(!active.isEmpty()){
            String text=switch(active){case "stargaze"->"";case "wind"->ManaSightClient.enabled()?"顺着风吹过的次序，右键轻触铃柱　"+rune+" / 3":"按 "+ManaSightClient.KEY.getTranslatedKeyMessage().getString()+" 打开魔力视野，看看风先吹向哪一枚铃";case "farm"->"练习：相邻放集露盏；牧务杖选兽，再右键集息器";default->"练习：补上晶纤缺口，观察魔力输送直到灯亮";};
            var at=point(active);if(mc.player.position().distanceToSqr(at)<32*32)g.drawCenteredString(mc.font,text,w/2,h-63,0xC8E8DD);
        }else if(hintAge>0){String next=AcademyRules.next(complete);var site=offset(next);if(site!=null)g.drawCenteredString(mc.font,"新的邀约 · "+AcademyLessons.title(next)+" · /skylesson 查看地点",w/2,h-63,0xC8E8DD);}
    }
}
