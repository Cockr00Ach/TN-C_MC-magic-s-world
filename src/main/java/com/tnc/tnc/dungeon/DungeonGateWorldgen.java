package com.tnc.tnc.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DungeonGateWorldgen {
    public static StructureType<GateStructure> TYPE;public static StructurePieceType TEMPLATE,PLATFORM;
    @SubscribeEvent public static void register(net.minecraftforge.registries.RegisterEvent e){e.register(Registries.STRUCTURE_TYPE,h->{TYPE=()->GateStructure.CODEC;h.register(DungeonContent.id("dungeon_gate"),TYPE);});e.register(Registries.STRUCTURE_PIECE,h->{TEMPLATE=(StructurePieceType.StructureTemplateType)GatePiece::new;PLATFORM=(StructurePieceType.ContextlessType)Platform::new;h.register(DungeonContent.id("dungeon_gate_template"),TEMPLATE);h.register(DungeonContent.id("dungeon_gate_platform"),PLATFORM);});}
    public static final class GateStructure extends Structure {
        public static final Codec<GateStructure> CODEC=RecordCodecBuilder.create(i->i.group(settingsCodec(i),Codec.STRING.fieldOf("theme").forGetter(s->s.theme.id)).apply(i,GateStructure::new));
        final DungeonTheme theme;
        public GateStructure(StructureSettings s,String theme){super(s);this.theme=DungeonTheme.find(theme);}
        @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext c){var a=DungeonGateAssets.get(theme);int x=c.chunkPos().getMinBlockX(),z=c.chunkPos().getMinBlockZ();var heights=new ArrayList<Integer>();for(int dx=0;dx<a.size().getX();dx+=16)for(int dz=0;dz<a.size().getZ();dz+=16)heights.add(c.chunkGenerator().getFirstOccupiedHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,c.heightAccessor(),c.randomState()));Collections.sort(heights);if(heights.get(heights.size()-1)-heights.get(0)>14)return Optional.empty();int y=Math.max(c.chunkGenerator().getSeaLevel(),heights.get(heights.size()-1))+1;if(y+a.size().getY()>c.heightAccessor().getMaxBuildHeight()-4)return Optional.empty();var origin=new BlockPos(x,y,z);return Optional.of(new GenerationStub(origin.offset(a.portal()),b->{
          for(var p:a.pieces())b.addPiece(new GatePiece(c.structureTemplateManager(),p.resource(),origin.offset(p.offset())));
          for(int dx=0;dx<a.size().getX();dx+=16)for(int dz=0;dz<a.size().getZ();dz+=16)b.addPiece(new Platform(theme,origin.offset(dx,-1,dz),Math.min(16,a.size().getX()-dx),Math.min(16,a.size().getZ()-dz),a.pieces().isEmpty()));
        }));}
        @Override public StructureType<?> type(){return TYPE;}
    }
    static final class GatePiece extends TemplateStructurePiece {
        static StructurePlaceSettings settings(){return new StructurePlaceSettings().setKnownShape(true).setIgnoreEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);}
        GatePiece(StructureTemplateManager m,net.minecraft.resources.ResourceLocation id,BlockPos pos){super(TEMPLATE,0,m,id,id.toString(),settings(),pos);}
        GatePiece(StructureTemplateManager m,CompoundTag t){super(TEMPLATE,t,m,id->settings());}
        @Override protected void handleDataMarker(String text,BlockPos pos,ServerLevelAccessor l,RandomSource r,BoundingBox b){}
    }
    static final class Platform extends StructurePiece {
        final DungeonTheme theme;final boolean fallback;
        Platform(DungeonTheme theme,BlockPos p,int w,int d,boolean fallback){super(PLATFORM,0,new BoundingBox(p.getX(),p.getY()-20,p.getZ(),p.getX()+w-1,p.getY()+14,p.getZ()+d-1));this.theme=theme;this.fallback=fallback;}
        Platform(CompoundTag t){super(PLATFORM,t);theme=DungeonTheme.find(t.getString("Theme"));fallback=t.getBoolean("Fallback");}
        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext c,CompoundTag t){t.putString("Theme",theme.id);t.putBoolean("Fallback",fallback);}
        @Override public void postProcess(WorldGenLevel l,StructureManager manager,net.minecraft.world.level.chunk.ChunkGenerator g,RandomSource random,BoundingBox clip,ChunkPos cp,BlockPos pivot){int y=boundingBox.minY()+20;for(int x=boundingBox.minX();x<=boundingBox.maxX();x++)for(int z=boundingBox.minZ();z<=boundingBox.maxZ();z++){
          var top=new BlockPos(x,y,z);if(!clip.isInside(top))continue;l.setBlock(top,theme.floor(),2);for(int d=1;d<=20;d++){var p=top.below(d);if(!l.getBlockState(p).isAir()&&l.getFluidState(p).isEmpty())break;l.setBlock(p,theme.wall(),2);}
          if(fallback){int dx=x-boundingBox.minX();for(int h=1;h<=14;h++){var p=top.above(h);l.setBlock(p,dx<3||dx>11||h<3||h>12?theme.wall():DungeonContent.GATES.get(theme).defaultBlockState(),2);}}
        }}
    }
}
