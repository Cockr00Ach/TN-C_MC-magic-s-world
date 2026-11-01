package com.tnc.tnc.world.wind.client;

import com.tnc.tnc.world.wind.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Bounded visual leases. Skipping the camera does not bypass a server-owned impalement. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class CanglanExecutionClient {
    private static final Map<Integer,CanglanCinematicNetwork.Execution> STATES=new HashMap<>();
    private static final Set<UUID> TRANSLATED=new HashSet<>();
    public static void accept(CanglanCinematicNetwork.Execution p){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(p.dimension()))return;
        if(p.phase()==CanglanExecutionPose.END){var old=STATES.get(p.boss());if(old!=null&&old.started()==p.started())STATES.remove(p.boss());return;}
        if(p.phase()<0||p.phase()>CanglanExecutionPose.FLUNG||Math.abs(mc.level.getGameTime()-p.started())>200)return;
        if(STATES.size()>=16&&!STATES.containsKey(p.boss()))return;
        var old=STATES.get(p.boss());if(old!=null&&(old.started()>p.started()||old.started()==p.started()&&old.phase()>p.phase()))return;
        STATES.put(p.boss(),p);
    }
    public static CanglanCinematicNetwork.Execution state(int boss){return STATES.get(boss);}
    public static boolean driving(CanglanCinematicNetwork.Execution p,float partial){return p!=null&&p.phase()<CanglanExecutionPose.END&&age(p,partial)<=CanglanExecutionPose.DURATION;}
    public static double age(CanglanCinematicNetwork.Execution p,float partial){var mc=Minecraft.getInstance();return mc.level==null?0:Math.max(0,mc.level.getGameTime()+partial-p.started());}
    public static boolean locked(){
        var mc=Minecraft.getInstance();if(mc.player==null||!mc.player.isAlive())return false;
        return STATES.values().stream().anyMatch(p->p.victim()==mc.player.getId()&&p.phase()==CanglanExecutionPose.PINNED&&CanglanExecutionPose.pinned(age(p,0)));
    }
    public static Vec3 attachment(CanglanWarlordEntity boss,CanglanCinematicNetwork.Execution p,float partial,float height){
        return CanglanExecutionPose.tip(boss,age(p,partial)).subtract(0,height*.55,0);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null){STATES.clear();return;}
        STATES.values().removeIf(p->!mc.level.dimension().location().equals(p.dimension())||age(p,0)>CanglanExecutionPose.DURATION+10);
        if(locked()){mc.player.setDeltaMovement(Vec3.ZERO);mc.player.fallDistance=0;}
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent e){
        if(!locked())return;var i=e.getInput();i.forwardImpulse=i.leftImpulse=0;i.up=i.down=i.left=i.right=i.jumping=false;i.shiftKeyDown=false;
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered e){if(locked()){e.setCanceled(true);e.setSwingHand(false);}}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void player(RenderPlayerEvent.Pre e){
        if(e.isCanceled())return;
        for(var p:STATES.values())if(p.victim()==e.getEntity().getId()&&p.phase()==CanglanExecutionPose.PINNED&&CanglanExecutionPose.pinned(age(p,e.getPartialTick()))){
            var mc=Minecraft.getInstance();var entity=mc.level.getEntity(p.boss());if(!(entity instanceof CanglanWarlordEntity boss))return;
            Vec3 desired=attachment(boss,p,e.getPartialTick(),e.getEntity().getBbHeight());
            Vec3 shown=e.getEntity().getPosition(e.getPartialTick());Vec3 delta=desired.subtract(shown);
            e.getPoseStack().pushPose();TRANSLATED.add(e.getEntity().getUUID());e.getPoseStack().translate(delta.x,delta.y,delta.z);return;
        }
    }
    @SubscribeEvent public static void playerPost(RenderPlayerEvent.Post e){if(TRANSLATED.remove(e.getEntity().getUUID()))e.getPoseStack().popPose();}
    /** Render-side fallback also runs if GeckoLib's trigger/controller packet was late or skipped. */
    public static void pose(CanglanWarlordEntity entity,software.bernie.geckolib.cache.object.GeoBone root,float partial){
        var execution=state(entity.getId());if(!driving(execution,partial))return;
        poseBone(root,age(execution,partial));
    }
    private static void poseBone(software.bernie.geckolib.cache.object.GeoBone bone,double age){
        if(CanglanExecutionPose.bones().contains(bone.getName())){
            var r=CanglanExecutionPose.rotation(bone.getName(),age);
            bone.setRotX((float)Math.toRadians(-r[0]));bone.setRotY((float)Math.toRadians(-r[1]));bone.setRotZ((float)Math.toRadians(r[2]));
            bone.setPosX(0);bone.setPosY(0);bone.setPosZ(0);
        }
        for(var child:bone.getChildBones())poseBone(child,age);
    }
}
