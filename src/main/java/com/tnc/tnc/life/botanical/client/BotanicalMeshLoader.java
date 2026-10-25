package com.tnc.tnc.life.botanical.client;

import com.google.gson.*;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.geometry.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Curved botanical surfaces baked once into the normal block atlas/render pipeline. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class BotanicalMeshLoader implements IGeometryLoader<BotanicalMeshLoader.Mesh> {
    @SubscribeEvent public static void register(ModelEvent.RegisterGeometryLoaders event){
        event.register("botanical_mesh",new BotanicalMeshLoader());
    }
    @Override public Mesh read(JsonObject json,JsonDeserializationContext context){
        boolean glow=!json.has("glow_enabled")||json.get("glow_enabled").getAsBoolean();
        if(json.has("surfaces"))return new Mesh(json.getAsJsonArray("surfaces"),glow,json.has("staff_render")&&json.get("staff_render").getAsBoolean());
        var path=ResourceLocation.parse(json.get("geometry").getAsString());
        try(var reader=net.minecraft.client.Minecraft.getInstance().getResourceManager().getResourceOrThrow(path).openAsReader()){
            return new Mesh(JsonParser.parseReader(reader).getAsJsonArray(),glow,false);
        }catch(java.io.IOException e){throw new JsonParseException("Missing botanical geometry "+path,e);}
    }
    public record Mesh(JsonArray surfaces,boolean glowEnabled,boolean staff) implements IUnbakedGeometry<Mesh> {
        @Override public BakedModel bake(IGeometryBakingContext context,ModelBaker baker,
                Function<Material,TextureAtlasSprite> sprites,ModelState state,ItemOverrides overrides,ResourceLocation location){
            var sprite=sprites.apply(context.getMaterial("plant"));
            var result=new SimpleBakedModel.Builder(false,true,true,context.getTransforms(),overrides).particle(sprite);
            for(var value:surfaces){
                var face=value.getAsJsonObject();var points=face.getAsJsonArray("vertices");var uv=face.getAsJsonArray("uv");
                var positions=new Vector3f[4];
                for(int i=0;i<4;i++){
                    var p=points.get(i).getAsJsonArray();positions[i]=new Vector3f(p.get(0).getAsFloat()/16-.5f,p.get(1).getAsFloat()/16-.5f,p.get(2).getAsFloat()/16-.5f);
                    state.getRotation().getMatrix().transformPosition(positions[i]);positions[i].add(.5f,.5f,.5f);
                }
                var normal=new Vector3f(positions[1]).sub(positions[0]).cross(new Vector3f(positions[2]).sub(positions[0]));
                if(normal.lengthSquared()<1e-12)
                    normal=new Vector3f(positions[2]).sub(positions[0]).cross(new Vector3f(positions[3]).sub(positions[0]));
                if(normal.lengthSquared()<1e-12)continue;normal.normalize();
                int packed=((int)(normal.x*127)&255)|(((int)(normal.y*127)&255)<<8)|(((int)(normal.z*127)&255)<<16);
                int[] data=new int[32];boolean glow=glowEnabled&&face.has("glow")&&face.get("glow").getAsBoolean();
                for(int i=0;i<4;i++){
                    var p=positions[i];var tex=uv.get(i).getAsJsonArray();int n=i*8;
                    data[n]=Float.floatToRawIntBits(p.x);data[n+1]=Float.floatToRawIntBits(p.y);data[n+2]=Float.floatToRawIntBits(p.z);
                    data[n+3]=-1;data[n+4]=Float.floatToRawIntBits(sprite.getU(tex.get(0).getAsDouble()));
                    data[n+5]=Float.floatToRawIntBits(sprite.getV(tex.get(1).getAsDouble()));
                    data[n+6]=glow?15728880:0;data[n+7]=packed;
                }
                result.addUnculledFace(new BakedQuad(data,glow?4:-1,Direction.getNearest(normal.x,normal.y,normal.z),sprite,!glow));
            }
            var baked=result.build(context.getRenderType(ResourceLocation.parse("minecraft:cutout")));
            if(!staff)return baked;
            return new net.minecraftforge.client.model.BakedModelWrapper<BakedModel>(baked){
                @Override public boolean isCustomRenderer(){return true;}
                @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext display,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){
                    originalModel.applyTransform(display,pose,left);return this;
                }
            };
        }
    }
}
