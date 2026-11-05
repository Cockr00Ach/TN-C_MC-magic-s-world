package com.tnc.tnc.warrior.client;

import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Read the grip and farthest blade point once, from the actual authoring model. */
final class WarriorModelBlade {
    record Blade(Vec3 grip,Vec3 tip) {}
    private static final Map<ResourceLocation,Optional<Blade>> CACHE=new HashMap<>();
    private static final Map<ResourceLocation,Blade> BAKED=new HashMap<>();
    static Blade fromBaked(ResourceLocation id,net.minecraft.client.resources.model.BakedModel model,Vec3 grip){
        return BAKED.computeIfAbsent(id,key->{
            Vec3 tip=grip;double distance=0;
            // Generated sprite weapons and inherited models need their actual
            // baked vertex bounds, not a family-length vector scaled twice.
            for(int side=-1;side<6;side++)for(var quad:model.getQuads(null,side<0?null:net.minecraft.core.Direction.values()[side],net.minecraft.util.RandomSource.create(42))){
                int[] data=quad.getVertices();int stride=data.length/4;
                for(int i=0;i<4;i++){
                    Vec3 p=new Vec3(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]));
                    if(p.distanceToSqr(grip)>distance){distance=p.distanceToSqr(grip);tip=p;}
                }
            }
            return new Blade(grip,distance>1e-9?tip:grip.add(0,1,0));
        });
    }
    static Optional<Blade> find(ResourceLocation item){return CACHE.computeIfAbsent(item,id->{
        try {
            ResourceLocation path=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"item/"+id.getPath());
            com.google.gson.JsonObject json=null;Vec3 grip=null;
            Set<ResourceLocation> seen=new HashSet<>();
            for(int depth=0;depth<8 && seen.add(path);depth++){
                var resource=Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(path.getNamespace(),"models/"+path.getPath()+".json"));
                if(resource.isEmpty())return Optional.empty();
                try(var reader=resource.get().openAsReader()){json=JsonParser.parseReader(reader).getAsJsonObject();}
                if(grip==null && json.has("tnc_grip")){
                    var g=json.getAsJsonArray("tnc_grip");grip=new Vec3(g.get(0).getAsDouble(),g.get(1).getAsDouble(),g.get(2).getAsDouble());
                }
                if(json.has("elements"))break;
                if(!json.has("parent"))return Optional.empty();path=ResourceLocation.parse(json.get("parent").getAsString());
            }
            if(json==null || !json.has("elements"))return Optional.empty();
            if(grip==null){
                if(!id.getNamespace().equals("tnc") || !(id.getPath().equals("spear") || id.getPath().startsWith("wind_relic_")))return Optional.empty();
                grip=id.getPath().equals("wind_relic_sword")?new Vec3(8,8,-.8):new Vec3(8,8,8);
            }
            Vec3 tip=grip;double distance=0;
            for(var value:json.getAsJsonArray("elements")){
                var element=value.getAsJsonObject();var a=element.getAsJsonArray("from");var b=element.getAsJsonArray("to");
                for(int ix=0;ix<2;ix++)for(int iz=0;iz<2;iz++){
                    Vec3 p=new Vec3((ix==0?a:b).get(0).getAsDouble(),(a.get(1).getAsDouble()+b.get(1).getAsDouble())*.5,(iz==0?a:b).get(2).getAsDouble());
                    if(p.distanceToSqr(grip)>distance){distance=p.distanceToSqr(grip);tip=p;}
                }
            }
            return distance>0?Optional.of(new Blade(grip.scale(1/16.0),tip.scale(1/16.0))):Optional.empty();
        }catch(Exception error){com.mojang.logging.LogUtils.getLogger().warn("Invalid blade metadata for {}",id,error);return Optional.empty();}
    });}
    static void clear(){CACHE.clear();BAKED.clear();}
}
