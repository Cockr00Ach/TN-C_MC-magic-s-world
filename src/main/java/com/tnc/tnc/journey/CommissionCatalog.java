package com.tnc.tnc.journey;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class CommissionCatalog extends SimpleJsonResourceReloadListener {
    public record Offer(String id,String title,String story,String kind,String target,int count,int level,int coins,int xp,int cooldown){
        public Offer {if(!Set.of("kill","floor").contains(kind)||count<1||count>1000||level<1||coins<0||coins>100000||xp<0||xp>100000||cooldown<60)throw new IllegalArgumentException("Invalid commission: "+id);}
    }
    private static Map<String,Offer> all=Map.of();
    public CommissionCatalog(){super(new Gson(),"commissions");}
    public static Collection<Offer> all(){return all.values();}
    public static Offer find(String id){return all.get(id);}
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager resources,ProfilerFiller profiler){
        var next=new TreeMap<String,Offer>();files.forEach((id,value)->{try{var t=value.getAsJsonObject();var o=new Offer(id.toString(),t.get("title").getAsString(),t.get("story").getAsString(),t.get("kind").getAsString(),t.get("target").getAsString(),t.get("count").getAsInt(),t.get("level").getAsInt(),t.get("coins").getAsInt(),t.get("xp").getAsInt(),t.get("cooldown_seconds").getAsInt());next.put(o.id(),o);}catch(RuntimeException ex){com.mojang.logging.LogUtils.getLogger().error("Invalid commission {}",id,ex);}});all=Collections.unmodifiableMap(next);
    }
    @SubscribeEvent public static void reload(AddReloadListenerEvent e){e.addListener(new CommissionCatalog());}
}
