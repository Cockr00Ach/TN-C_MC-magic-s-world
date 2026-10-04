package com.tnc.tnc.tavern;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Data-driven seats and indoor rooms, relative to each world's existing island. */
public record TavernAtmosphere(List<Seat> seats,List<AABB> rooms,int totalSeats) {
    public record Seat(String id,BlockPos local,String block,double height,float yaw,int persona,String name,List<String> lines) {}
    private static final ResourceLocation RESOURCE=ResourceLocation.fromNamespaceAndPath("tnc","tavern/atmosphere_v1.json");
    public static TavernAtmosphere load(MinecraftServer server) throws Exception {
        var resource=server.getResourceManager().getResource(RESOURCE).orElseThrow();
        try(var in=new InputStreamReader(resource.open(),StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(in).getAsJsonObject());
        }
    }
    static TavernAtmosphere parse(JsonObject root) {
        if(root.get("revision").getAsInt()!=1)throw new IllegalArgumentException("酒馆氛围版本无效");
        var seats=new ArrayList<Seat>();var ids=new HashSet<String>();var positions=new HashSet<BlockPos>();
        for(var raw:root.getAsJsonArray("guests")) {
            var t=raw.getAsJsonObject();var p=t.getAsJsonArray("local");
            var pos=new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());
            if(!TavernUpgrade.inside(pos)||!ids.add(t.get("id").getAsString())||!positions.add(pos))throw new IllegalArgumentException("重复或越界的酒馆坐席");
            var lines=new ArrayList<String>();t.getAsJsonArray("lines").forEach(line->lines.add(line.getAsString()));
            if(lines.isEmpty()||lines.size()>2)throw new IllegalArgumentException("酒馆台词应为一至两句");
            seats.add(new Seat(t.get("id").getAsString(),pos,t.get("block").getAsString(),t.get("height").getAsDouble(),t.get("yaw").getAsFloat(),t.get("persona").getAsInt(),t.get("name").getAsString(),List.copyOf(lines)));
        }
        int total=root.get("total_seats").getAsInt();var empty=new HashSet<BlockPos>();
        if(root.has("empty_seats"))for(var raw:root.getAsJsonArray("empty_seats")){var p=raw.getAsJsonArray();var pos=new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());if(!TavernUpgrade.inside(pos)||!empty.add(pos)||positions.contains(pos))throw new IllegalArgumentException("指定空座越界、重复或仍有客人");}
        int available=total-empty.size();
        if(available<=0||seats.size()<available*.60||seats.size()>available*.70)throw new IllegalArgumentException("除指定空座外的酒馆坐席比例应为60%—70%");
        var rooms=new ArrayList<AABB>();
        for(var raw:root.getAsJsonArray("rooms")) {
            var a=raw.getAsJsonArray();if(a.size()!=6)throw new IllegalArgumentException("无效的酒馆室内范围");
            var box=new AABB(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble());
            if(box.getSize()<=0||!insideRoom(BlockPos.containing(box.minX,box.minY,box.minZ))||!insideRoom(BlockPos.containing(box.maxX,box.maxY,box.maxZ)))throw new IllegalArgumentException("酒馆音乐范围越界");
            rooms.add(box);
        }
        if(rooms.isEmpty()||rooms.size()>8)throw new IllegalArgumentException("酒馆音乐范围数量无效");
        return new TavernAtmosphere(List.copyOf(seats),List.copyOf(rooms),total);
    }
    private static boolean insideRoom(BlockPos p){return TavernUpgrade.inside(p)||(p.getX()>=425&&p.getX()<=441&&p.getY()>=80&&p.getY()<=90&&p.getZ()>=287&&p.getZ()<=299);}
}
