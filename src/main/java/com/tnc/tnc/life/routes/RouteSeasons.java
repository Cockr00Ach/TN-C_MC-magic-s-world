package com.tnc.tnc.life.routes;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
/** Explicit registered block IDs; never guesses crops from a furniture name. */
public final class RouteSeasons {
    private static final Map<String,Integer> MASKS=read();
    private static Map<String,Integer> read(){try(var stream=RouteSeasons.class.getResourceAsStream("/data/tnc/life_routes/seasons.json")){var root=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(stream),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();var out=new HashMap<String,Integer>();root.entrySet().forEach(e->out.put(e.getKey(),e.getValue().getAsInt()));return Map.copyOf(out);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    public static int season(long dayTime){return (int)Math.floorMod(Math.floorDiv(dayTime,168000),4);}
    public static String name(long dayTime){return new String[]{"春","夏","秋","冬"}[season(dayTime)];}
    public static double rate(int mask,int season){if((mask&(1<<season))!=0)return 1;return (mask&(1<<Math.floorMod(season-1,4)))!=0||(mask&(1<<((season+1)%4)))!=0?.6:.25;}
    public static boolean allow(BlockState s,ServerLevel l,BlockPos p,net.minecraft.util.RandomSource random){if(!RoutePlanting.grow(s,l,p))return false;if(l!=l.getServer().overworld()||RoutePlanting.magic(s))return true;ResourceLocation id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(s.getBlock());Integer mask=id==null?null:MASKS.get(id.toString());if(mask==null||mask==15)return true;
        for(var q:RouteNetwork.positions(l))if(Math.abs(q.getX()-p.getX())<=4&&Math.abs(q.getZ()-p.getZ())<=4&&Math.abs(q.getY()-p.getY())<=3&&l.hasChunkAt(q)&&l.getBlockEntity(q) instanceof RouteNodeEntity be&&be.kind()==RouteKind.GREENHOUSE&&be.getPersistentData().getLong("ClimateUntil")>=l.getGameTime()&&RouteAccess.allowed(l,p,be.owner))return true;
        return random.nextDouble()<rate(mask,season(l.getDayTime()));}
    public static Map<String,Integer> masks(){return MASKS;}
}
