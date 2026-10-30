package com.tnc.tnc.world.ink.client;
import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.world.ink.InkWingedTigerEntity;
import com.tnc.tnc.world.ink.InkWingOpacity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import java.util.*;
public final class InkWingedTigerRenderer extends GeoEntityRenderer<InkWingedTigerEntity>{
    private final Map<InkWingedTigerEntity,Map<String,Deque<Vec3>>> trails=new WeakHashMap<>();
    private final Map<InkWingedTigerEntity,Map<String,Integer>> sampled=new WeakHashMap<>();
    private final Map<InkWingedTigerEntity,Boolean> foldedStates=new WeakHashMap<>();
    public InkWingedTigerRenderer(EntityRendererProvider.Context c){super(c,new InkWingedTigerModel());withScale(com.tnc.tnc.world.ink.InkTigerAttackRules.BODY_SCALE);shadowRadius=4.8F;}
    @Override public RenderType getRenderType(InkWingedTigerEntity e,ResourceLocation texture,MultiBufferSource buffers,float partial){
        return RenderType.entityTranslucent(texture);
    }
    @Override public void renderCubesOfBone(PoseStack pose,GeoBone bone,VertexConsumer out,int light,int overlay,
            float r,float g,float b,float a){
        super.renderCubesOfBone(pose,bone,out,light,overlay,r,g,b,a*InkWingOpacity.alpha(bone.getName()));
    }
    @Override public void render(InkWingedTigerEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        boolean folded=e.inkMove()==0||e.inkMove()==4;Boolean previous=foldedStates.put(e,folded);
        // Old open-wing trails must not hang in the air after the wings fold against the body.
        if(previous!=null&&previous!=folded){trails.remove(e);sampled.remove(e);}
        super.render(e,yaw,partial,pose,buffers,light);
        var paths=trails.get(e);if(paths!=null)for(var path:paths.values())
            InkCraneRitualVisuals.brushTrail(path,e.position(),e.tickCount+partial,e.inkMove()==0?.18:.55,pose,buffers);
        InkTigerSpellVisuals.caster(e,partial,pose,buffers);
    }
    @Override public void renderRecursively(PoseStack pose,InkWingedTigerEntity e,GeoBone bone,RenderType type,
            MultiBufferSource buffers,VertexConsumer out,boolean rerender,float partial,int light,int overlay,
            float r,float g,float b,float a){
        if(bone.getName().startsWith("emitter_"))bone.setTrackingMatrices(true);
        if(bone.getName().equals("glow_eyes"))light=LightTexture.FULL_BRIGHT;
        super.renderRecursively(pose,e,bone,type,buffers,out,rerender,partial,light,overlay,r,g,b,a);
        if(!rerender&&bone.getName().startsWith("emitter_")
                &&(bone.getName().equals("emitter_tail")||bone.getName().startsWith("emitter_folded_")== (e.inkMove()==0))&&e.tickCount%2==0){
            var times=sampled.computeIfAbsent(e,k->new HashMap<>());
            if(times.getOrDefault(bone.getName(),-10)==e.tickCount)return;
            times.put(bone.getName(),e.tickCount);
            var p=bone.getWorldPosition();Vec3 point=new Vec3(p.x,p.y,p.z);
            if(!Double.isFinite(point.x)||point.distanceToSqr(e.position())>1600)return;
            var path=trails.computeIfAbsent(e,k->new HashMap<>()).computeIfAbsent(bone.getName(),k->new ArrayDeque<>());
            if(path.isEmpty()||path.peekFirst().distanceToSqr(point)>.0005){path.addFirst(point);while(path.size()>26)path.removeLast();}
        }
    }
    @Override protected float getDeathMaxRotation(InkWingedTigerEntity e){return 0;}
}
