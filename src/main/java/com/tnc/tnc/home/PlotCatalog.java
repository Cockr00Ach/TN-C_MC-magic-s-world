package com.tnc.tnc.home;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One source of truth for bank listings, permissions, signs and authored plot boundaries. */
public final class PlotCatalog {
    public enum Kind {HOME,FIELD,PASTURE}
    public record Plot(String id,String number,String name,Kind kind,long price,BlockPos min,BlockPos max,BlockPos entry,String detail){
        public long down(){return price*3/10;}
        public long mortgageTotal(){return (price-down())*11/10;}
        public boolean contains(BlockPos p){return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
        public boolean intersects(BlockPos lo,BlockPos hi){return lo.getX()<=max.getX()&&hi.getX()>=min.getX()&&lo.getY()<=max.getY()&&hi.getY()>=min.getY()&&lo.getZ()<=max.getZ()&&hi.getZ()>=min.getZ();}
    }
    public static final List<Plot> ALL=read();
    private static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static List<Plot> read(){try(var stream=Objects.requireNonNull(PlotCatalog.class.getResourceAsStream("/data/tnc/housing/catalog.json"));var r=new InputStreamReader(stream,StandardCharsets.UTF_8)){
        var root=JsonParser.parseReader(r).getAsJsonObject();var list=new ArrayList<Plot>();var ids=new HashSet<String>();
        for(var value:root.getAsJsonArray("plots")){var o=value.getAsJsonObject();var p=new Plot(o.get("id").getAsString(),o.get("number").getAsString(),o.get("name").getAsString(),Kind.valueOf(o.get("kind").getAsString()),o.get("price").getAsLong(),pos(o.getAsJsonArray("min")),pos(o.getAsJsonArray("max")),pos(o.getAsJsonArray("entry")),o.get("detail").getAsString());if(!ids.add(p.id())||p.price()<=0||list.stream().anyMatch(a->a.intersects(p.min(),p.max())))throw new IllegalArgumentException("Overlapping or invalid town property");list.add(p);}return List.copyOf(list);
    }catch(IOException e){throw new ExceptionInInitializerError(e);}}
    public static Plot find(String id){return ALL.stream().filter(p->p.id().equals(id)).findFirst().orElse(null);}
    public static Plot at(BlockPos local){return ALL.stream().filter(p->p.contains(local)).findFirst().orElse(null);}
}
