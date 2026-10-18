package com.tnc.tnc.boss.client;

import com.google.gson.*;
import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.nio.file.*;
import java.util.*;

/** Opt-in native GeckoLib material/pose gallery, excluded from the installed patch. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class DarkGiantUpperbodyAudit {
    private static final ResourceLocation GEO=id("geo/entity/dark_giant.geo.json"),TEX=id("textures/entity/dark_giant_bedrock.png"),ANI=id("animations/entity/dark_giant.animation.json"),GLOW=id("textures/entity/dark_giant_emissive.png");
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("tnc",path);}
    private static boolean shown,saving;private static volatile boolean saved;private static int frames;
    private static BakedGeoModel baked;private static JsonObject clips;private static final List<String> failures=new ArrayList<>();
    private static final Dummy dummy=new Dummy();
    private static final GeoModel<Dummy> model=new GeoModel<>(){
        public ResourceLocation getModelResource(Dummy d){return GEO;}
        public ResourceLocation getTextureResource(Dummy d){return TEX;}
        public ResourceLocation getAnimationResource(Dummy d){return ANI;}
    };
    private static GeoObjectRenderer<Dummy> renderer;
    private static final class Dummy implements GeoAnimatable {
        private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
        public void registerControllers(AnimatableManager.ControllerRegistrar c){}
        public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
        public double getTick(Object o){return 0;}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(!Boolean.getBoolean("tnc.darkUpperbodyAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.getOverlay()!=null)return;
        if(!shown){
            baked=model.getBakedModel(GEO);DarkGiantArmorMesh.apply(baked);renderer=new GeoObjectRenderer<>(model);
            try(var reader=mc.getResourceManager().getResource(ANI).orElseThrow().openAsReader()){clips=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");}
            shown=true;mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(new Gallery());
        }
        if(++frames<35)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,"dark_giant_upperbody.png",mc.getMainRenderTarget(),m->saved=true);return;}
        if(saved){Files.writeString(Path.of(System.getProperty("tnc.darkUpperbodyAuditReport")),new Gson().toJson(Map.of("failures",failures,"nativeGeckoRender",true,"poses",List.of("idle","walk","cast_hands","cast_sky_hands","cast_siphon"))));mc.stop();}
    }
    private static double[] vector(JsonElement e){
        if(e.isJsonObject())e=e.getAsJsonObject().get("vector");
        var a=e.getAsJsonArray();return new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()};
    }
    private static double[] sample(JsonElement track,double time){
        if(track.isJsonArray())return vector(track);
        var keys=new TreeMap<Double,double[]>();track.getAsJsonObject().entrySet().forEach(e->keys.put(Double.parseDouble(e.getKey()),vector(e.getValue())));
        var lo=keys.floorEntry(time);var hi=keys.ceilingEntry(time);if(lo==null)return keys.firstEntry().getValue();if(hi==null||hi.getKey().equals(lo.getKey()))return lo.getValue();
        double f=(time-lo.getKey())/(hi.getKey()-lo.getKey());double[] out=new double[3];for(int i=0;i<3;i++)out[i]=lo.getValue()[i]+f*(hi.getValue()[i]-lo.getValue()[i]);return out;
    }
    private static void pose(String clip,double time){
        var channels=clips.getAsJsonObject(clip).getAsJsonObject("bones");
        for(var root:baked.topLevelBones())pose(root,channels,time);
    }
    private static void pose(GeoBone bone,JsonObject channels,double time){
        var v=channels.has(bone.getName())&&channels.getAsJsonObject(bone.getName()).has("rotation")?sample(channels.getAsJsonObject(bone.getName()).get("rotation"),time):new double[3];
        var initial=bone.getInitialSnapshot();float d=(float)(Math.PI/180);
        bone.setRotX(initial.getRotX()-(float)v[0]*d);bone.setRotY(initial.getRotY()-(float)v[1]*d);bone.setRotZ(initial.getRotZ()+(float)v[2]*d);
        bone.getChildBones().forEach(child->pose(child,channels,time));
    }
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Dark overlord articulated arms"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF10131D);g.drawString(font,"DARK OVERLORD / ARTICULATED SHOULDERS, ELBOWS, WRISTS AND FINGERS / NATIVE GECKOLIB RENDER",30,22,0xE4D4F5,false);
            var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
            for(int i=0;i<6;i++){
                int cw=width/3,ch=(height-85)/2,x=(i%3)*cw,y=55+(i/3)*ch;
                g.fill(x+12,y,x+cw-12,y+ch-12,0xFF222333);g.drawString(font,new String[]{"IDLE / FRONT / RELAXED ELBOWS","WALK / SHOULDER AND ELBOW COUNTERSWING","NIGHT HAND / FORWARD PALM RELEASE","SKY HAND / CONNECTED OVERHEAD REACH","SIPHON / ELBOW RETRACTION","GAUNTLET / THUMB AND FOUR FINGERS"}[i],x+25,y+15,0xDAC5EF,false);
                g.flush();g.enableScissor(x+14,y+35,x+cw-14,y+ch-15);g.pose().pushPose();
                try{
                    pose(new String[]{"idle","walk","cast_hands","cast_sky_hands","cast_siphon","idle"}[i],new double[]{0,.55,2.2,2.6,2.35,0}[i]);
                    g.pose().translate(x+cw/2,y+ch-28,500);float scale=i==5?175:46;
                    g.pose().scale(scale,-scale,scale);g.pose().mulPose(Axis.YP.rotationDegrees(i==0?180:145));
                    if(i==5)g.pose().translate(-1.55,-1.5,0);
                    for(var type:List.of(RenderType.entityCutoutNoCull(TEX),RenderType.entityTranslucentEmissive(GLOW))){
                        var out=buffers.getBuffer(type);for(var root:baked.topLevelBones())renderer.renderRecursively(g.pose(),dummy,root,type,buffers,out,true,0,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,1,1,1,1);
                    }
                    buffers.endBatch();
                }catch(Throwable t){if(!failures.contains(t.toString()))failures.add(t.toString());}
                finally{g.pose().popPose();g.disableScissor();}
            }
        }
    }
}
