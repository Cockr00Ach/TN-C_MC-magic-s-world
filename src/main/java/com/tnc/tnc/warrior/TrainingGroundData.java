package com.tnc.tnc.warrior;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** Plot ownership and return anchors persist independently of transient drill progress. */
public final class TrainingGroundData extends SavedData {
    public record ReturnPoint(String dimension,double x,double y,double z,float yaw,float pitch){
        static ReturnPoint of(ServerPlayer p){return new ReturnPoint(p.level().dimension().location().toString(),p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());}
        CompoundTag save(){var t=new CompoundTag();t.putString("Dimension",dimension);t.putDouble("X",x);t.putDouble("Y",y);t.putDouble("Z",z);t.putFloat("Yaw",yaw);t.putFloat("Pitch",pitch);return t;}
        static ReturnPoint load(CompoundTag t){
            String d=t.getString("Dimension");double x=t.getDouble("X"),y=t.getDouble("Y"),z=t.getDouble("Z");float yaw=t.getFloat("Yaw"),pitch=t.getFloat("Pitch");
            if(ResourceLocation.tryParse(d)==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                ||Math.abs(x)>29999980||Math.abs(z)>29999980||Math.abs(y)>4096||!Float.isFinite(yaw)||!Float.isFinite(pitch))return null;
            return new ReturnPoint(d,x,y,z,yaw,Math.max(-90,Math.min(90,pitch)));
        }
    }
    private final Map<UUID,Integer> plots=new HashMap<>();
    private final Map<UUID,ReturnPoint> returns=new HashMap<>();
    private int nextPlot;
    public static TrainingGroundData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(TrainingGroundData::load,TrainingGroundData::new,"tnc_training_ground_v1");}
    public int plot(UUID id){return plots.getOrDefault(id,-1);}
    public int allocate(UUID id){
        if(plots.containsKey(id))return plots.get(id);
        if(nextPlot>=TrainingGroundRules.MAX_PLOTS)throw new IllegalStateException("Training ground capacity reached");
        int index=nextPlot++;plots.put(id,index);setDirty();return index;
    }
    public ReturnPoint origin(UUID id){return returns.get(id);}
    public void begin(ServerPlayer p){returns.put(p.getUUID(),ReturnPoint.of(p));setDirty();}
    public void finish(UUID id){if(returns.remove(id)!=null)setDirty();}
    public static TrainingGroundData load(CompoundTag root){
        var d=new TrainingGroundData();var occupied=new HashSet<Integer>();
        for(Tag raw:root.getList("Players",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)raw;if(!t.hasUUID("Id"))continue;UUID id=t.getUUID("Id");int plot=t.getInt("Plot");
            if(plot<0||plot>=TrainingGroundRules.MAX_PLOTS||!occupied.add(plot))continue;
            d.plots.put(id,plot);d.nextPlot=Math.max(d.nextPlot,plot+1);
            if(t.contains("Return",Tag.TAG_COMPOUND)){var point=ReturnPoint.load(t.getCompound("Return"));if(point!=null)d.returns.put(id,point);}
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root){
        var list=new ListTag();plots.forEach((id,plot)->{var t=new CompoundTag();t.putUUID("Id",id);t.putInt("Plot",plot);if(returns.containsKey(id))t.put("Return",returns.get(id).save());list.add(t);});
        root.put("Players",list);return root;
    }
}
