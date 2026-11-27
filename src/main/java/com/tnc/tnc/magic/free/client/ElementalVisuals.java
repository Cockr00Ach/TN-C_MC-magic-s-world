package com.tnc.tnc.magic.free.client;

import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.free.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import static com.tnc.tnc.magic.free.ElementalIntent.*;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class ElementalVisuals {
    private record Seen(ElementalNetwork.Visual packet,long received,Vec3 previous){}
    private static final Map<Long,Seen> VIEWS=new LinkedHashMap<>();
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(262144));
    private static final RenderType GLOW=WaterEffectPass.create(ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/eclipse/white.png"));
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static long rendered;
    static Map<String,Object> auditSnapshot(){return Map.of("rendered",rendered,"forms",VIEWS.values().stream().filter(v->v.packet.phase()==ElementalNetwork.FORM).count(),"active",VIEWS.values().stream().filter(v->v.packet.phase()!=ElementalNetwork.FORM).count());}
    public static void accept(ElementalNetwork.Visual packet){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(packet.dimension())||packet.ability()<0||packet.ability()>=Ability.values().length)return;
        if (packet.phase() == ElementalNetwork.END) {
            Seen ended = VIEWS.remove(packet.id());
            long tick = mc.level.getGameTime();
            if (ended != null && ended.packet.phase() == ElementalNetwork.BURN
                    && Ability.values()[packet.ability()] == Ability.VIOLET_FIRE
                    && ended.packet.started() + ended.packet.duration() > tick + 9) {
                var residue = new ElementalNetwork.Visual(packet.dimension(), packet.owner(), packet.id(),
                        FireCastVisuals.VIOLET_OUT, packet.ability(), packet.flags(), packet.power(),
                        packet.at(), Vec3.ZERO, tick, 8, packet.target());
                VIEWS.put(-packet.id(), new Seen(residue, tick, null));
            }
            return;
        }
        // Impact is transient and does not replace the parent area's regular position.
        long key=packet.phase()==ElementalNetwork.IMPACT?-packet.id():packet.id();
        long tick = mc.level.getGameTime();
        Seen last = VIEWS.get(key);
        Vec3 previous = last != null && movingFire(packet) && movingFire(last.packet)
                ? flightPosition(last, tick) : null;
        VIEWS.put(key,new Seen(packet,tick,previous));while(VIEWS.size()>192)VIEWS.remove(VIEWS.keySet().iterator().next());
    }
    public static boolean maintaining(UUID owner){return VIEWS.values().stream().anyMatch(v->v.packet.owner().equals(owner)&&v.packet.phase()==ElementalNetwork.ACTIVE&&Ability.values()[v.packet.ability()].channel());}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level!=world){VIEWS.clear();world=mc.level;}if(world==null)return;long now=world.getGameTime();VIEWS.values().removeIf(v->now-v.received>((v.packet.phase()==ElementalNetwork.IMPACT||v.packet.phase()==ElementalNetwork.ARC)?16:Math.max(30,v.packet.duration())));}
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)resources->Minecraft.getInstance().execute(ElementalMeshes::clear));}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        Vec3 camera=event.getCamera().getPosition();double now=mc.level.getGameTime()+event.getPartialTick();var pose=new PoseStack();
        var fire = new FireCastVisuals(pose, BUFFER, now);
        rendered++;
        for(var seen:VIEWS.values()){
            var p=seen.packet;var ability=Ability.values()[p.ability()];var intent=new Intent(ability,p.flags(),0,p.power());
            Vec3 at=p.at();double age=Math.max(0,now-p.started());var anchor=p.target()<0?null:mc.level.getEntity(p.target());
            boolean isFire = ability.element() == Element.FIRE;
            if (anchor != null && ((isFire && p.phase() != ElementalNetwork.IMPACT)
                    || (!isFire && p.phase() != ElementalNetwork.FORM
                    && (p.phase() == ElementalNetwork.STATUS || ElementalArt.anchors(ability))))) {
                at = anchor.getPosition(event.getPartialTick()).add(0, anchor.getBbHeight() * .5, 0);
            } else if (movingFire(p)) {
                at = flightPosition(seen, now);
            } else if (!isFire && p.phase() == ElementalNetwork.ACTIVE && (ability.target == Target.PROJECTILE || p.target() == -2)) {
                at = at.add(p.velocity().scale(Math.min(3, now - seen.received)));
            }
            if(at.distanceToSqr(camera)>100*100)continue;
            if (ability == Ability.VIOLET_FIRE && p.phase() == ElementalNetwork.FORM) {
                var caster = mc.level.getPlayerByUUID(p.owner());
                if (caster != null) fire.violetCalling(camera, caster.getEyePosition(event.getPartialTick()),
                        caster.getLookAngle(), at, age, p.duration());
            }
            pose.pushPose();pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
            if (isFire) fire.draw(p, intent, age, Math.max(0, now - seen.received));
            else ElementalArt.draw(pose, BUFFER, p, intent, age, now, now - seen.received);
            pose.popPose();
        }
        BUFFER.endBatch();
    }
    private static boolean movingFire(ElementalNetwork.Visual packet) {
        Ability ability = Ability.values()[packet.ability()];
        return ability.element() == Element.FIRE && packet.phase() == ElementalNetwork.ACTIVE
                && (packet.target() == -2 || (packet.target() == -1 && ability.target == Target.PROJECTILE));
    }

    private static Vec3 flightPosition(Seen seen, double now) {
        double elapsed = Math.min(3, Math.max(0, now - seen.received));
        Vec3 at = seen.packet.at().add(seen.packet.velocity().scale(elapsed));
        if (seen.previous != null && elapsed < 2) {
            at = at.add(seen.previous.subtract(seen.packet.at()).scale(1 - elapsed / 2));
        }
        return at;
    }
    static void line(PoseStack pose,Vec3 from,Vec3 to,double width,float r,float g,float b,double alpha){Vec3 right=to.subtract(from).cross(new Vec3(0,0,1)).normalize().scale(width);if(right.lengthSqr()<1e-8)right=new Vec3(width,0,0);quad(pose,BUFFER.getBuffer(GLOW),from.subtract(right),from.add(right),to.add(right),to.subtract(right),r,g,b,(float)alpha,0,1);}
    private static void quad(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float r,float g,float blue,float alpha,float bottom,float top){Vec3[] points={a,b,c,d};for(int n=0;n<4;n++){Vec3 v=points[n];out.vertex(pose.last().pose(),(float)v.x,(float)v.y,(float)v.z).color(r,g,blue,Math.max(0,Math.min(1,alpha))).uv(n==0||n==3?0:1,n<2?bottom:top).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.last().normal(),0,0,1).endVertex();}}
    private ElementalVisuals(){}
}
