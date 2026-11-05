package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.warrior.*;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

/** Attack-only, weapon-bound swept volume, with a strict per-player geometry budget. */
public final class WarriorTrail {
    record Profile(double outerStart, float outerAlpha, double coreStart, float coreAlpha,
                           boolean tipStreak, float tipStreakAlpha, long life, long finisherLife,
                           int samples, int finisherSamples, float headBoost, boolean edgeLine) {
    }

    /** 长剑：细、快、脆，尾巴短但亮，剑尖一条锐利的剑影。 */
    private static final Profile LONGSWORD_PROFILE =
            new Profile(0.18, 0.50f, 0.54, 1.00f, true, 0.60f, 200, 320, 16, 26, 1.24f, true);
    /** 重剑：宽、厚、慢，斩气从几乎整把剑开始铺，尾巴留得久，像一个"风压面"。 */
    private static final Profile GREATSWORD_PROFILE =
            new Profile(0.02, 0.66f, 0.24, 0.82f, true, 0.66f, 280, 440, 20, 30, 1.12f, true);
    /** 战锤：钝器，没有刃口、没有剑影，只有一大片暖色的"闷"光，短促。 */
    private static final Profile HAMMER_PROFILE =
            new Profile(0.00, 0.78f, 0.62, 0.62f, false, 0f, 165, 250, 14, 20, 1.32f, false);
    /** 长枪：又长又细，亮芯收得紧、尾巴掉得快 —— 突刺要"一道白光穿过去"的观感。 */
    private static final Profile SPEAR_PROFILE =
            new Profile(0.30, 0.46f, 0.64, 1.00f, true, 0.54f, 185, 290, 18, 26, 1.28f, true);

    static Profile profile(WarriorWeaponFamily family) {
        if (family == null) {
            return HAMMER_PROFILE;
        }
        return switch (family) {
            case LONGSWORD -> LONGSWORD_PROFILE;
            case GREATSWORD -> GREATSWORD_PROFILE;
            case HAMMER -> HAMMER_PROFILE;
            case SPEAR -> SPEAR_PROFILE;
            default -> LONGSWORD_PROFILE;
        };
    }

    private static final class State {
        String weapon;
        boolean firstPerson;
        boolean leftHand;
        final Deque<WarriorTrailVolume.Point> history=new ArrayDeque<>();
        List<WarriorTrailVolume.Point> curve=List.of();
        long version,cachedVersion=-1;
        int budget;
        double lastSeen,lastAttack=-10000,start;
        boolean attacking;
        float speed=6;
        State(String weapon){this.weapon=weapon;}
    }
    private static final Map<Integer,State> STATES=new HashMap<>();
    private static Object world;
    private static double nextCleanup;
    private WarriorTrail() {}
    private static double now(){return Util.getNanos()/1_000_000.0;}
    static boolean finisher(int id){var a=WarriorAnimationClient.active(id);return a!=null && a.animation().finisher();}
    static boolean attacking(AbstractClientPlayer p){
        var a=WarriorAnimationClient.active(p.getId());
        if(a!=null && a.progress(Minecraft.getInstance().getFrameTime())>=1 && !a.hold())a=null;
        return WarriorTrailAppearance.attacking(a==null?null:a.animation(),a!=null&&a.hold(),p.swinging);
    }
    /** Called before the ghost from the same actual held-item draw. */
    public static void capture(LivingEntity entity,ItemStack stack,ItemDisplayContext context,boolean left,PoseStack mount){
        if(!(entity instanceof AbstractClientPlayer player) || (!context.firstPerson() && context!=ItemDisplayContext.THIRD_PERSON_LEFT_HAND && context!=ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                || left!=(player.getMainArm()==HumanoidArm.LEFT))return;
        var mc=Minecraft.getInstance();
        // A local player may also be drawn by a body/shadow mod. Never mix view-space and world-space history.
        if(player==mc.player && context.firstPerson()!=mc.options.getCameraType().isFirstPerson())return;
        var key=ForgeRegistries.ITEMS.getKey(stack.getItem());
        var family=WarriorWeapons.family(stack);
        if(world!=player.level()){clear();world=player.level();}
        if(key==null || family==null || family==WarriorWeaponFamily.BOW || !player.isAlive() || player.isSpectator()){
            STATES.remove(player.getId());return;
        }
        Vec3 camera=mc.gameRenderer.getMainCamera().getPosition();
        if(camera.distanceToSqr(player.position())>1024){STATES.remove(player.getId());return;}
        double time=now();
        if(time>nextCleanup){STATES.values().removeIf(s->time-s.lastSeen>2000);nextCleanup=time+1000;}
        String identity=key.toString();State state=STATES.get(player.getId());
        if(state==null || !state.weapon.equals(identity) || state.firstPerson!=context.firstPerson() || state.leftHand!=left){state=new State(identity);STATES.put(player.getId(),state);}
        state.firstPerson=context.firstPerson();
        state.leftHand=left;
        state.lastSeen=time;
        boolean attack=attacking(player);
        double life=finisher(player.getId())?profile(family).finisherLife():profile(family).life();
        while(!state.history.isEmpty() && time-state.history.peekFirst().at()>life){state.history.removeFirst();state.version++;}
        if(!attack){state.attacking=false;return;}
        if(!state.attacking && time-state.lastAttack>140){state.start=time;state.history.clear();state.version++;}
        state.attacking=true;state.lastAttack=time;
        var previous=state.history.peekLast();
        if(previous!=null && time-previous.at()<7.5)return;
        var model=mc.getItemRenderer().getModel(stack,player.level(),player,player.getId()+context.ordinal());
        PoseStack pose=new PoseStack();pose.last().pose().set(mount.last().pose());
        model.getTransforms().getTransform(context).apply(left,pose);
        var authored=WarriorModelBlade.find(key);
        Vec3 grip=authored.isPresent()?authored.get().grip():WarriorModelGrip.find(key).orElse(new Vec3(3/16.0,3/16.0,.5));
        Vec3 tip=authored.isPresent()?authored.get().tip():WarriorModelBlade.fromBaked(key,model,grip).tip();
        Vec3 origin=context.firstPerson()?Vec3.ZERO:camera;
        Vec3 hiltWorld=world(pose,grip,origin),tipWorld=world(pose,tip,origin);
        if(previous!=null){
            double elapsed=time-previous.at();
            if(elapsed>160 || previous.hilt().distanceToSqr(hiltWorld)>4){state.history.clear();state.version++;}
            else {
                double distance=previous.tip().distanceTo(tipWorld);
                if(distance<.0005)return;
                float target=(float)(distance*1000/Math.max(1,elapsed));
                state.speed+=(target-state.speed)*(float)(1-Math.exp(-elapsed/45));
            }
        }
        state.history.addLast(new WarriorTrailVolume.Point(hiltWorld,tipWorld,time));state.version++;
        while(state.history.size()>WarriorTrailVolume.HISTORY)state.history.removeFirst();
    }
    private static Vec3 world(PoseStack pose,Vec3 p,Vec3 camera){
        var v=pose.last().pose().transformPosition(new Vector3f((float)p.x-.5f,(float)p.y-.5f,(float)p.z-.5f));
        return new Vec3(v.x,v.y,v.z).add(camera);
    }
    static float ghostVisibility(AbstractClientPlayer player){
        State s=STATES.get(player.getId());if(s==null)return 0;
        double t=now();
        return WarriorTrailAppearance.visibility(t-s.start,t-s.lastAttack,s.attacking && attacking(player));
    }
    public static void renderFirstPerson(LivingEntity entity,ItemDisplayContext context,boolean left,MultiBufferSource buffer){
        if(context.firstPerson() && entity instanceof AbstractClientPlayer player
                && left==(player.getMainArm()==HumanoidArm.LEFT))render(player,buffer,true);
    }
    static void render(AbstractClientPlayer player,MultiBufferSource buffer){render(player,buffer,false);}
    private static void render(AbstractClientPlayer player,MultiBufferSource buffer,boolean firstPerson){
        State state=STATES.get(player.getId());if(state==null || state.firstPerson!=firstPerson || state.history.size()<2)return;
        var mc=Minecraft.getInstance();Vec3 camera=mc.gameRenderer.getMainCamera().getPosition();
        double distance=camera.distanceToSqr(player.position());if(distance>1024)return;
        var family=WarriorWeapons.family(player.getMainHandItem());if(family==null || family==WarriorWeaponFamily.BOW)return;
        double time=now(),life=finisher(player.getId())?profile(family).finisherLife():profile(family).life();
        if(time-state.history.peekLast().at()>life){state.history.clear();state.curve=List.of();state.version++;return;}
        int budget=distance>144?WarriorTrailVolume.FAR_POINTS:WarriorTrailVolume.NEAR_POINTS;
        if(state.cachedVersion!=state.version || state.budget!=budget){
            state.curve=WarriorTrailVolume.resample(new ArrayList<>(state.history),budget);state.cachedVersion=state.version;state.budget=budget;
        }
        var stack=player.getMainHandItem();var key=ForgeRegistries.ITEMS.getKey(stack.getItem());
        String element=stack.hasTag()?stack.getTag().getString(WindInfusion.ELEMENT_KEY):"";
        var style=WarriorTrailAppearance.of(key==null?"":key.getPath(),family,element);
        double scale=WarriorPhantomRenderer.disabled()?1:Math.max(1,WarriorPhantomRenderer.scaleFor(player));
        var out=WarriorTrailRenderType.buffer(buffer);
        Vec3 origin=firstPerson?Vec3.ZERO:camera;
        float ramp=(float)Math.max(0,Math.min(1,(time-state.start)/40));
        float intensity=ramp*(.40f+.22f*Math.min(1,state.speed/12))*(firstPerson?.78f:1f);
        WarriorTrailVolume.emit(state.curve,time,life,profile(family).outerStart(),scale,style,intensity,
                new WarriorTrailVolume.Sink(){
                    @Override public void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float aa,float ab,float ac,float ad){
                        gradient(a,b,c,d,color,color,color,color,aa,ab,ac,ad);
                    }
                    @Override public void gradient(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int ca,int cb,int cc,int cd,float aa,float ab,float ac,float ad){
                        vertex(out,a,origin,ca,aa);vertex(out,b,origin,cb,ab);vertex(out,c,origin,cc,ac);vertex(out,d,origin,cd,ad);
                    }
                });
    }
    private static void vertex(VertexConsumer out,Vec3 p,Vec3 camera,int color,float alpha){
        out.vertex(p.x-camera.x,p.y-camera.y,p.z-camera.z).color((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha).endVertex();
    }
    static void clear(){STATES.clear();world=null;}
}
