package com.tnc.tnc.bronze.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.bronze.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Render two authored meshes from one real stack; no secondary item is created. */
public final class BronzeTwinRenderer extends BlockEntityWithoutLevelRenderer {
    public enum View { PRIMARY,SECONDARY,COMBINED }
    public static final class Extension implements IClientItemExtensions {
        private BronzeTwinRenderer renderer;
        @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new BronzeTwinRenderer();return renderer;}
    }
    private record Face(float[][] vertices,float[][] uv,float[] normal){}
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("tnc","textures/item/bronze_atlas.png");
    private static final Map<View,List<Face>> MODELS=new EnumMap<>(View.class);
    private static final ThreadLocal<View> HELD=new ThreadLocal<>();
    private static final String[] SIDES={"down","up","west","north","east","south"};
    private static final int[][] INDICES={{5,4,0,1},{2,3,7,6},{0,4,7,3},{1,0,3,2},{5,1,2,6},{4,5,6,7}};
    public BronzeTwinRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    public static void clear(){MODELS.clear();}
    public static void beginHeld(LivingEntity owner,ItemStack stack,HumanoidArm arm){
        HELD.set(BronzeTwinWeapon.active(owner)&&stack==owner.getMainHandItem()&&arm==owner.getMainArm()?View.PRIMARY:View.COMBINED);
    }
    public static void endHeld(){HELD.remove();}
    public static void withView(View view,Runnable action){
        View previous=HELD.get();HELD.set(view);
        try{action.run();}finally{if(previous==null)HELD.remove();else HELD.set(previous);}
    }
    private static List<Face> load(View view){
        String id=switch(view){case PRIMARY->"bronze_twin_main";case SECONDARY->"bronze_twin_off";case COMBINED->"bronze_halberd";};
        var resource=ResourceLocation.fromNamespaceAndPath("tnc","models/item/"+id+".json");
        try(var reader=Minecraft.getInstance().getResourceManager().getResourceOrThrow(resource).openAsReader()){
            var json=JsonParser.parseReader(reader).getAsJsonObject();var result=new ArrayList<Face>();
            for(var raw:json.getAsJsonArray("elements")){
                var e=raw.getAsJsonObject();var from=e.getAsJsonArray("from");var to=e.getAsJsonArray("to");
                float x=from.get(0).getAsFloat()/16,y=from.get(1).getAsFloat()/16,z=from.get(2).getAsFloat()/16;
                float X=to.get(0).getAsFloat()/16,Y=to.get(1).getAsFloat()/16,Z=to.get(2).getAsFloat()/16;
                float[][] vertices={{x,y,z},{X,y,z},{X,Y,z},{x,Y,z},{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};
                for(int side=0;side<SIDES.length;side++){
                    var uv=e.getAsJsonObject("faces").getAsJsonObject(SIDES[side]).getAsJsonArray("uv");
                    float a=uv.get(0).getAsFloat()/16,b=uv.get(1).getAsFloat()/16,c=uv.get(2).getAsFloat()/16,d=uv.get(3).getAsFloat()/16;
                    float[][] points=new float[4][];for(int i=0;i<4;i++)points[i]=vertices[INDICES[side][i]];
                    var n=new org.joml.Vector3f(points[1]).sub(new org.joml.Vector3f(points[0])).cross(new org.joml.Vector3f(points[2]).sub(new org.joml.Vector3f(points[0]))).normalize();
                    result.add(new Face(points,new float[][]{{c,b},{a,b},{a,d},{c,d}},new float[]{n.x,n.y,n.z}));
                }
            }
            return List.copyOf(result);
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load bronze paired mesh "+id,e);}
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        boolean hand=context.firstPerson()||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        View view=View.COMBINED;
        if(hand){
            view=HELD.get();
            if(view==null){var player=Minecraft.getInstance().player;view=context.firstPerson()&&player!=null&&!BronzeTwinWeapon.active(player)?View.COMBINED:View.PRIMARY;}
        }
        var vertices=ItemRenderer.getFoilBufferDirect(buffers,RenderType.entityCutoutNoCull(TEXTURE),true,stack.hasFoil());
        var p=pose.last();
        for(var face:MODELS.computeIfAbsent(view,BronzeTwinRenderer::load))for(int i=0;i<4;i++){
            var v=face.vertices[i];var uv=face.uv[i];var normal=face.normal;
            vertices.vertex(p.pose(),v[0],v[1],v[2]).color(255,255,255,255).uv(uv[0],uv[1]).overlayCoords(overlay).uv2(light).normal(p.normal(),normal[0],normal[1],normal[2]).endVertex();
        }
    }
}
