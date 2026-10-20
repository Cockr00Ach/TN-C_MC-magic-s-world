package com.tnc.tnc.dungeon;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DungeonGateAssets {
    public record Piece(ResourceLocation resource,BlockPos offset){}
    public record Asset(BlockPos size,BlockPos portal,List<Piece> pieces){}
    private static final Map<DungeonTheme,Asset> CACHE=new EnumMap<>(DungeonTheme.class);
    private static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    public static Asset get(DungeonTheme t){return CACHE.computeIfAbsent(t,theme->{try(var stream=DungeonGateAssets.class.getResourceAsStream("/data/tnc/dungeon_gate_assets/"+theme.id+".json")){
        if(stream==null)return new Asset(new BlockPos(15,14,7),new BlockPos(7,2,3),List.of());
        var j=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();var pieces=new ArrayList<Piece>();for(var p:j.getAsJsonArray("pieces")){var v=p.getAsJsonObject();pieces.add(new Piece(ResourceLocation.parse(v.get("resource").getAsString()),pos(v.getAsJsonArray("offset"))));}return new Asset(pos(j.getAsJsonArray("size")),pos(j.getAsJsonArray("portal")),List.copyOf(pieces));
      }catch(IOException e){throw new IllegalStateException("Dungeon gate source unreadable: "+theme.id,e);}});}
    public static void buildHere(ServerPlayer p,DungeonTheme theme){var level=p.serverLevel();var asset=get(theme);var origin=p.blockPosition().offset(-asset.size.getX()/2,0,8);var list=new ArrayList<DungeonRealm.Cell>();
      for(var piece:asset.pieces){var template=level.getStructureManager().getOrCreate(piece.resource);var size=template.getSize();var at=origin.offset(piece.offset);for(var info:template.filterBlocks(at,new StructurePlaceSettings().setKnownShape(true),Blocks.AIR)){/* Air is deliberately not copied into arbitrary existing worlds. */}
        // The command uses the same native template pipeline as worldgen, on an explicitly chosen site.
        template.placeInWorld(level,at,at,new StructurePlaceSettings().setKnownShape(true).setIgnoreEntities(true),level.random,2);
      }
      if(asset.pieces.isEmpty())for(int x=0;x<15;x++)for(int y=0;y<14;y++)if(x<3||x>11||y<2||y>11)level.setBlock(origin.offset(x,y,3),theme.wall(),2);
      for(int x=0;x<asset.size.getX();x++)for(int z=0;z<asset.size.getZ();z++){var at=origin.offset(x,-1,z);level.setBlock(at,theme.floor(),2);for(int d=1;d<16&&level.getBlockState(at.below(d)).isAir();d++)level.setBlock(at.below(d),theme.wall(),2);}
      if(asset.pieces.isEmpty())for(int x=3;x<=11;x++)for(int y=2;y<=11;y++)level.setBlock(origin.offset(x,y,3),DungeonContent.GATES.get(theme).defaultBlockState(),2);
      p.sendSystemMessage(net.minecraft.network.chat.Component.literal(theme.name+"入口已建于 "+origin.offset(asset.portal).toShortString()+"；这是显式测试建造，请在空旷地点使用。"));
    }
}
