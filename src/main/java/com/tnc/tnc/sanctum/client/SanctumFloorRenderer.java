package com.tnc.tnc.sanctum.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.sanctum.SanctumFloorMarks.Mark;
import com.tnc.tnc.sanctum.UnlitSanctum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Marks use the same location, tick and radius as the server hit test, independent of particle density. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class SanctumFloorRenderer {
    private record Key(UUID owner,String key){}
    private static final Map<Key,Mark> marks=new LinkedHashMap<>();
    private static final Map<Key,Mark> contacts=new LinkedHashMap<>();
    static Mark contact(UUID owner,String key,long castStart){
        Mark mark=contacts.get(new Key(owner,key));
        return mark==null||mark.start()<castStart?null:mark;
    }
    static net.minecraft.world.phys.Vec3 lockedPoint(UUID owner,String key,long castStart){
        Mark mark=contact(owner,key,castStart);return mark==null?null:mark.center();
    }
    private static final MultiBufferSource.BufferSource buffers=MultiBufferSource.immediate(new BufferBuilder(32768));
    private static final RenderType SEAL=material("seal"), POOL=material("pool"), PLAIN=material("plain");
    private static ClientLevel world;
    private static RenderType material(String name){
        return RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath("tnc","textures/effects/sanctum/"+name+".png"));
    }
    private static void level(ClientLevel current){
        if(world!=current){marks.clear();contacts.clear();world=current;}
    }
    public static void accept(Mark mark){
        level(Minecraft.getInstance().level);
        if(world==null||!world.dimension().equals(UnlitSanctum.LEVEL))return;
        if(mark.duration()==0){
            marks.keySet().removeIf(key->key.owner().equals(mark.owner()));
            contacts.keySet().removeIf(key->key.owner().equals(mark.owner()));return;
        }
        SanctumProjectedBlade.lock(mark.owner(),mark.key(),mark.center(),mark.start());
        if(mark.key().startsWith("dance_cut_")){
            contacts.put(new Key(mark.owner(),mark.key()),mark);return;
        }
        switch(mark.key()){
            case "wing_from","wing_center","wing_to","hook_tip","grab_tip","grab_grip","final_cut" -> {
                // The floor warning can expire on impact; the rigid claw still has to settle and retract.
                contacts.values().removeIf(old->mark.start()-old.start()>160);
                contacts.put(new Key(mark.owner(),mark.key()),mark);
            }
        }
        marks.put(new Key(mark.owner(),mark.key()),mark);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        level(Minecraft.getInstance().level);
        if(world==null||marks.isEmpty())return;
        double time=world.getGameTime()+e.getPartialTick();
        var camera=e.getCamera().getPosition();
        var stack=e.getPoseStack();
        var iterator=marks.values().iterator();
        while(iterator.hasNext()){
            var mark=iterator.next();
            float age=(float)Math.max(0,time-mark.start());
            boolean column=mark.key().startsWith("gold_")||mark.key().startsWith("fire_");
            if(age>=mark.duration()+(column?8:0)){iterator.remove();continue;}
            if(camera.distanceToSqr(mark.center())>150*150)continue;
            stack.pushPose();
            try {
                boolean pool=mark.key().startsWith("pool_");
                stack.translate(mark.center().x-camera.x,mark.center().y-camera.y+(pool?.026:.045),mark.center().z-camera.z);
                var pose=stack.last();
                if(mark.key().equals("wing_from")||mark.key().equals("wing_to"))continue;
                if(mark.key().equals("wing_center")){
                    var start=lockedPoint(mark.owner(),"wing_from",mark.start());
                    var end=lockedPoint(mark.owner(),"wing_to",mark.start());
                    if(start!=null&&end!=null)lane(pose,start.subtract(mark.center()),end.subtract(mark.center()),mark.outer(),age);
                    continue;
                }
                if(column && age>=mark.duration()){
                    float progress=(age-mark.duration())/8;
                    beam(pose,mark.outer(),mark.rgb(),progress);
                    continue;
                }
                float radius=mark.speed()==0?mark.outer():Math.min(mark.outer(),Math.max(.07f,mark.speed()*age));
                if(mark.inner()>0){
                    float thickness=mark.outer()-mark.inner();
                    band(pose,mark.rgb(),radius,Math.max(0,radius-thickness),220);
                }else{
                    if(pool)stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(mark.key().hashCode()%360));
                    float pulse=(float)(.5+.5*Math.cos(age*.32));
                    var out=buffers.getBuffer(pool?POOL:SEAL);
                    int alpha=pool?230:(int)(176+70*pulse);
                    corner(out,pose,-radius,-radius,0,0,mark.rgb(),alpha);
                    corner(out,pose,-radius, radius,0,1,mark.rgb(),alpha);
                    corner(out,pose, radius, radius,1,1,mark.rgb(),alpha);
                    corner(out,pose, radius,-radius,1,0,mark.rgb(),alpha);
                }
            }finally{stack.popPose();}
        }
        buffers.endBatch();
    }
    private static void lane(PoseStack.Pose pose,net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,float radius,float age){
        var middle=net.minecraft.world.phys.Vec3.ZERO;
        var out=buffers.getBuffer(PLAIN);
        segment(out,pose,from,middle,radius,age);segment(out,pose,middle,to,radius,age);
        int entry=com.tnc.tnc.sanctum.SanctumTimings.WING_IN-com.tnc.tnc.sanctum.SanctumTimings.WING_LOCK;
        int contact=com.tnc.tnc.sanctum.SanctumTimings.WING_IMPACT-com.tnc.tnc.sanctum.SanctumTimings.WING_LOCK;
        int exit=com.tnc.tnc.sanctum.SanctumTimings.WING_OUT-com.tnc.tnc.sanctum.SanctumTimings.WING_LOCK;
        if(age>=entry&&age<=exit){
            var tip=age<=contact?from.lerp(middle,(age-entry)/(contact-entry)):middle.lerp(to,(age-contact)/(exit-contact));
            var dx=new net.minecraft.world.phys.Vec3(.48,0,0);var dz=new net.minecraft.world.phys.Vec3(0,0,.48);
            tile(out,pose,tip.subtract(dx),tip.add(dz),tip.add(dx),tip.subtract(dz),0xffd4a1,248,.035f);
        }
    }
    private static void segment(VertexConsumer out,PoseStack.Pose pose,net.minecraft.world.phys.Vec3 a,net.minecraft.world.phys.Vec3 b,float r,float age){
        var d=b.subtract(a).multiply(1,0,1);double length=d.length();
        if(length<.05)return;
        var along=d.scale(1/length);var perp=new net.minecraft.world.phys.Vec3(-along.z,0,along.x);
        var outer=perp.scale(r);var inner=perp.scale(Math.max(0,r-.14));
        tile(out,pose,a.subtract(outer),b.subtract(outer),b.add(outer),a.add(outer),0x831831,93,0);
        for(int sign:new int[]{-1,1}){
            var edge=outer.scale(sign);var inset=inner.scale(sign);
            tile(out,pose,a.add(edge),b.add(edge),b.add(inset),a.add(inset),0xf46b69,228,.012F);
        }
        double run=age*.11%3.8;
        for(double n=run+.65;n<length-.65;n+=3.8){
            var c=a.add(along.scale(n));var point=c.add(along.scale(.53));var tail=c.subtract(along.scale(.28));
            tile(out,pose,point,tail.add(perp.scale(.37)),c,tail.subtract(perp.scale(.37)),0xffb083,203,.025F);
        }
        if(a.lengthSqr()<.005 || b.lengthSqr()<.005){
            boolean start=a.lengthSqr()>.005;var tip=start?a:b;
            var forward=along.scale(start?-1:1);var prev=tip.add(outer);
            for(int i=1;i<=8;i++){
                var arc=tip.add(outer.scale(Math.cos(i*Math.PI/8))).add(forward.scale(r*Math.sin(i*Math.PI/8)));
                tile(out,pose,tip,prev,arc,arc,0x831831,93,0);
                tile(out,pose,prev,arc,tip.lerp(arc,1-.14/r),tip.lerp(prev,1-.14/r),0xf46b69,228,.012F);prev=arc;
            }
        }
    }
    private static void tile(VertexConsumer out,PoseStack.Pose pose,net.minecraft.world.phys.Vec3 a,net.minecraft.world.phys.Vec3 b,
                             net.minecraft.world.phys.Vec3 c,net.minecraft.world.phys.Vec3 d,int color,int alpha,float y){
        if((b.z-a.z)*(c.x-a.x)-(b.x-a.x)*(c.z-a.z)<0){var swap=b;b=d;d=swap;}
        vertex(out,pose,(float)a.x,y,(float)a.z,0,0,color,alpha);vertex(out,pose,(float)b.x,y,(float)b.z,1,0,color,alpha);
        vertex(out,pose,(float)c.x,y,(float)c.z,1,1,color,alpha);vertex(out,pose,(float)d.x,y,(float)d.z,0,1,color,alpha);
    }
    private static void band(PoseStack.Pose pose,int rgb,float radius,float inner,int alpha){
        int sides=Math.max(24,(int)Math.ceil(radius*3));
        var out=buffers.getBuffer(PLAIN);
        for(int i=0;i<sides;i++){
            double a=i*2*Math.PI/sides,b=(i+1)*2*Math.PI/sides;
            float ax=(float)Math.cos(a),az=(float)Math.sin(a),bx=(float)Math.cos(b),bz=(float)Math.sin(b);
            corner(out,pose,ax*radius,az*radius,0,0,rgb,alpha);
            corner(out,pose,ax*inner,az*inner,0,1,rgb,alpha);
            corner(out,pose,bx*inner,bz*inner,1,1,rgb,alpha);
            corner(out,pose,bx*radius,bz*radius,1,0,rgb,alpha);
        }
    }
    private static void beam(PoseStack.Pose pose,float radius,int rgb,float progress){
        var out=buffers.getBuffer(PLAIN);
        for(int layer=0;layer<2;layer++){
            float width=radius*(layer==0?.86f:.22f)*(1-progress*.64f);
            int alpha=(int)((layer==0?92:205)*(1-progress));
            int tint=layer==0?rgb:0xffe5bd;
            for(int i=0;i<16;i++){
                double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
                float ax=width*(float)Math.cos(a),az=width*(float)Math.sin(a);
                float bx=width*(float)Math.cos(b),bz=width*(float)Math.sin(b);
                vertex(out,pose,ax,0,az,0,0,tint,alpha);
                vertex(out,pose,bx,0,bz,1,0,tint,alpha);
                vertex(out,pose,bx*.6f,18,bz*.6f,1,1,tint,alpha/2);
                vertex(out,pose,ax*.6f,18,az*.6f,0,1,tint,alpha/2);
            }
        }
    }
    private static void corner(VertexConsumer out,PoseStack.Pose pose,float x,float z,float u,float v,int rgb,int alpha){
        vertex(out,pose,x,0,z,u,v,rgb,alpha);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float z,float u,float v,int rgb,int alpha){
        out.vertex(pose.pose(),x,y,z).color((rgb>>16)&255,(rgb>>8)&255,rgb&255,alpha)
                .uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(),0,1,0).endVertex();
    }
}
