package com.tnc.tnc.warrior.client;
import com.tnc.tnc.warrior.WarriorExecutionFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.PoseStack;
public final class WarriorExecutionVisual {
 private record Mark(long at,long until){}
 private static final java.util.Map<Integer,Long> GIANTS=new java.util.HashMap<>();
 public static boolean giant(int id){return world!=null&&Minecraft.getInstance().level==world&&GIANTS.getOrDefault(id,0L)>world.getGameTime();}
 private static final java.util.Map<Integer,Mark> MARKS=new java.util.HashMap<>();
 private static net.minecraft.client.multiplayer.ClientLevel world;
 public static void accept(WarriorExecutionFx p){var mc=Minecraft.getInstance();if(mc.level!=world){MARKS.clear();GIANTS.clear();world=mc.level;}if(world==null)return;
  if(p.kind()==3){WarriorUltimatePayoffHud.grant(p.owner(),p.duration());return;}
  if(p.kind()==2){GIANTS.put(p.owner(),world.getGameTime()+p.duration());return;}
  if(p.kind()==1)WarriorUltimateCamera.execution(p.owner(),p.target(),p.duration());else{MARKS.entrySet().removeIf(e->e.getValue().until<world.getGameTime());if(MARKS.size()>256)MARKS.remove(MARKS.keySet().iterator().next());MARKS.put(p.target(),new Mark(world.getGameTime(),world.getGameTime()+p.duration()));}
 }
 @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
 public static final class Registration {
  @net.minecraftforge.eventbus.api.SubscribeEvent public static void layers(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers e){
   for(var type:net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValues()){try{net.minecraft.client.renderer.entity.EntityRenderer<?> renderer=e.getRenderer((net.minecraft.world.entity.EntityType)type);if(renderer instanceof LivingEntityRenderer living)attach(living);}catch(ClassCastException ignored){/* Non-living and custom Geo renderers have a separate wound overlay. */}}
   for(String skin:e.getSkins()){var renderer=e.getSkin(skin);if(renderer!=null)attach(renderer);}
  }
  private static <T extends LivingEntity,M extends EntityModel<T>> void attach(LivingEntityRenderer<T,M> r){r.addLayer(new DarkLayer<>(r));}
 }
 private static final class DarkLayer<T extends LivingEntity,M extends EntityModel<T>> extends RenderLayer<T,M>{
  DarkLayer(RenderLayerParent<T,M> parent){super(parent);}
  @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,T entity,float swing,float amount,float partial,float age,float yaw,float pitch){
   if(Minecraft.getInstance().level!=world){MARKS.clear();world=Minecraft.getInstance().level;return;}if(world==null)return;
   var mark=MARKS.get(entity.getId());if(mark==null)return;double elapsed=world.getGameTime()+partial-mark.at;double left=mark.until-world.getGameTime()-partial;
   if(left<=0){MARKS.remove(entity.getId());return;}
   float alpha=(float)(Math.min(1,elapsed/20)*Math.min(1,left/20)*.9);pose.pushPose();pose.scale(1.003f,1.003f,1.003f);
   getParentModel().renderToBuffer(pose,buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),light,OverlayTexture.NO_OVERLAY,.015f,.008f,.025f,alpha);pose.popPose();
  }
 }

 @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
 public static final class Wounds {
  @net.minecraftforge.eventbus.api.SubscribeEvent public static void geo(software.bernie.geckolib.event.GeoRenderEvent.Entity.Post e){
   if(Minecraft.getInstance().level!=world||world==null||!(e.getEntity() instanceof LivingEntity entity)||!(entity instanceof software.bernie.geckolib.core.animatable.GeoAnimatable animatable))return;
   var mark=MARKS.get(entity.getId());if(mark==null)return;double elapsed=world.getGameTime()+e.getPartialTick()-mark.at,left=mark.until-world.getGameTime()-e.getPartialTick();if(left<=0)return;
   float alpha=(float)(Math.min(1,elapsed/20)*Math.min(1,left/20)*.9);
   software.bernie.geckolib.renderer.GeoRenderer renderer=e.getRenderer();var type=RenderType.entityTranslucent(renderer.getTextureLocation(animatable));
   e.getPoseStack().pushPose();e.getPoseStack().scale(1.002f,1.002f,1.002f);
   renderer.reRender(e.getModel(),e.getPoseStack(),e.getBufferSource(),animatable,type,e.getBufferSource().getBuffer(type),e.getPartialTick(),e.getPackedLight(),OverlayTexture.NO_OVERLAY,.015f,.008f,.025f,alpha);e.getPoseStack().popPose();
  }
  private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(16384));
  @net.minecraftforge.eventbus.api.SubscribeEvent public static void draw(net.minecraftforge.client.event.RenderLevelStageEvent e){
   if(e.getStage()!=net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER||world==null||Minecraft.getInstance().level!=world)return;
   var out=BUFFER.getBuffer(com.tnc.tnc.client.lightning.LightningRenderTypes.DARK);var camera=e.getCamera().getPosition();
   for(var entry:MARKS.entrySet()){
    var entity=world.getEntity(entry.getKey());double left=entry.getValue().until-world.getGameTime();if(!(entity instanceof LivingEntity t)||left<=0||!t.isAlive()||t.distanceToSqr(camera)>4096)continue;
    float alpha=(float)(Math.min(1,(world.getGameTime()-entry.getValue().at)/20.0)*Math.min(1,left/20)*.95);
    var f=net.minecraft.world.phys.Vec3.directionFromRotation(0,t.yBodyRot);var side=new net.minecraft.world.phys.Vec3(f.z,0,-f.x);
    var center=t.getPosition(e.getPartialTick()).add(0,t.getBbHeight()*.6,0).add(f.scale(t.getBbWidth()*.5+.025));
    double size=Math.min(2,t.getBbHeight()*.35),width=.07+t.getBbWidth()*.04;
    for(int sign:new int[]{-1,1}){var a=center.add(side.scale(-size)).add(0,-size*sign,0);var b=center.add(side.scale(size)).add(0,size*sign,0);
     for(var v:new net.minecraft.world.phys.Vec3[]{a.add(0,width,0),a.add(0,-width,0),b.add(0,-width,0),b.add(0,width,0)})out.vertex(v.x-camera.x,v.y-camera.y,v.z-camera.z).color(.025f,.008f,.035f,alpha).uv(0,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0,1,0).endVertex();
    }
   }BUFFER.endBatch();
  }
 }
}
