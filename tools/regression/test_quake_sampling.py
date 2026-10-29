"""Run real Mojmap sampling bytecode against negative-height, culled and unculled model fixtures."""
from pathlib import Path
import argparse,os,subprocess,tempfile,sys

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input',type=Path,help='Mojmap patch jar')
    parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--output-dir',type=Path)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[2]
    temporary=tempfile.TemporaryDirectory(prefix='tnc-quake-sampling-')
    stage=args.output_dir or Path(temporary.name);stage.mkdir(parents=True,exist_ok=True)
    classes=stage/'classes';classes.mkdir(exist_ok=True)
    asm=os.pathsep.join(str(p) for package in ('asm','asm-tree')
        for p in (root/'.gradle-home/caches/modules-2/files-2.1/org.ow2.asm'/package).glob('*/*/*.jar'))
    stubs={
    'net/minecraft/core/Direction.java':'''package net.minecraft.core; public enum Direction {DOWN,UP,NORTH,SOUTH,WEST,EAST;public enum Axis{X,Y,Z;}}''',
    'net/minecraft/core/BlockPos.java':'''package net.minecraft.core; public class BlockPos{public final int x,y,z;public BlockPos(int a,int b,int c){x=a;y=b;z=c;}public static BlockPos containing(double x,double y,double z){return new BlockPos((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));}public BlockPos above(){return new BlockPos(x,y+1,z);}public int getY(){return y;}}''',
    'net/minecraft/util/Mth.java':'''package net.minecraft.util;public class Mth{public static int floor(double x){return(int)Math.floor(x);}}''',
    'net/minecraft/util/RandomSource.java':'''package net.minecraft.util;public interface RandomSource{static RandomSource create(long seed){return new RandomSource(){};}default void setSeed(long seed){}}''',
    'net/minecraft/world/phys/AABB.java':'''package net.minecraft.world.phys;public class AABB{public double minX=0,minZ=0,maxX=1,maxZ=1;}''',
    'net/minecraft/world/phys/shapes/VoxelShape.java':'''package net.minecraft.world.phys.shapes;import java.util.*;import net.minecraft.world.phys.AABB;import net.minecraft.core.Direction;public class VoxelShape{final boolean empty;public VoxelShape(boolean e){empty=e;}public boolean isEmpty(){return empty;}public List<AABB> toAabbs(){return empty?List.of():List.of(new AABB());}public double max(Direction.Axis axis){return 1;}}''',
    'net/minecraft/world/level/BlockGetter.java':'''package net.minecraft.world.level;public interface BlockGetter{}''',
    'net/minecraft/world/level/BlockAndTintGetter.java':'''package net.minecraft.world.level;public interface BlockAndTintGetter extends BlockGetter{}''',
    'net/minecraft/world/level/material/FluidState.java':'''package net.minecraft.world.level.material;public class FluidState{public boolean isEmpty(){return true;}}''',
    'net/minecraft/world/level/block/RenderShape.java':'''package net.minecraft.world.level.block;public enum RenderShape{INVISIBLE,MODEL,ENTITYBLOCK_ANIMATED;}''',
    'net/minecraft/world/level/block/state/BlockBehaviour.java':'''package net.minecraft.world.level.block.state;import net.minecraft.core.*;import net.minecraft.world.level.*;import net.minecraft.world.level.material.*;import net.minecraft.world.level.block.*;import net.minecraft.world.phys.shapes.*;public class BlockBehaviour{public static class BlockStateBase{public boolean air;public boolean isAir(){return air;}public FluidState getFluidState(){return new FluidState();}public boolean hasBlockEntity(){return false;}public RenderShape getRenderShape(){return RenderShape.MODEL;}public VoxelShape getCollisionShape(BlockGetter level,BlockPos pos){return new VoxelShape(air);}}}''',
    'net/minecraft/world/level/block/state/BlockState.java':'''package net.minecraft.world.level.block.state;public class BlockState extends BlockBehaviour.BlockStateBase{public BlockState(boolean air){this.air=air;}}''',
    'net/minecraft/world/level/levelgen/Heightmap.java':'''package net.minecraft.world.level.levelgen;public class Heightmap{public enum Types{MOTION_BLOCKING_NO_LEAVES;}}''',
    'net/minecraft/world/level/Level.java':'''package net.minecraft.world.level;import net.minecraft.core.*;import net.minecraft.world.level.block.state.*;import net.minecraft.world.level.levelgen.*;public class Level implements BlockAndTintGetter{public boolean loaded=true;public int floorY=-61;public boolean hasChunkAt(BlockPos p){return loaded;}public int getHeight(Heightmap.Types t,int x,int z){return floorY+1;}public int getMinBuildHeight(){return -64;}public BlockState getBlockState(BlockPos p){return new BlockState(p.y!=floorY);}}''',
    'net/minecraft/client/multiplayer/ClientLevel.java':'''package net.minecraft.client.multiplayer;public class ClientLevel extends net.minecraft.world.level.Level{}''',
    'net/minecraft/client/renderer/texture/TextureAtlasSprite.java':'''package net.minecraft.client.renderer.texture;public class TextureAtlasSprite{}''',
    'net/minecraft/client/renderer/texture/OverlayTexture.java':'''package net.minecraft.client.renderer.texture;public class OverlayTexture{public static final int NO_OVERLAY=655360;}''',
    'net/minecraft/client/renderer/block/model/BakedQuad.java':'''package net.minecraft.client.renderer.block.model;import net.minecraft.client.renderer.texture.*;import net.minecraft.core.*;public class BakedQuad{public TextureAtlasSprite getSprite(){return new TextureAtlasSprite();}public boolean isTinted(){return true;}public int getTintIndex(){return 0;}public Direction getDirection(){return Direction.UP;}}''',
    'net/minecraft/client/resources/model/BakedModel.java':'''package net.minecraft.client.resources.model;import java.util.*;import net.minecraft.core.*;import net.minecraft.util.*;import net.minecraft.world.level.block.state.*;import net.minecraft.client.renderer.block.model.*;public interface BakedModel{List<BakedQuad> getQuads(BlockState state,Direction face,RandomSource random);}''',
    'net/minecraft/client/renderer/block/BlockRenderDispatcher.java':'''package net.minecraft.client.renderer.block;import java.util.*;import net.minecraft.client.resources.model.*;import net.minecraft.client.renderer.block.model.*;import net.minecraft.world.level.block.state.*;import com.mojang.blaze3d.vertex.PoseStack;import net.minecraft.client.renderer.MultiBufferSource;public class BlockRenderDispatcher{public boolean unculled;public int draws;public double drawY;public BakedModel getBlockModel(BlockState state){return(s,d,r)->unculled?(d==null?List.of(new BakedQuad()):List.of()):(d!=null?List.of(new BakedQuad()):List.of());}public void renderSingleBlock(BlockState state,PoseStack poses,MultiBufferSource buffers,int light,int overlay){if(poses.depth!=1||buffers==null||light!=0x00f000f0||overlay!=655360)throw new AssertionError("Invalid native block draw context");draws++;drawY=poses.y;}}''',
    'net/minecraft/client/color/block/BlockColors.java':'''package net.minecraft.client.color.block;import net.minecraft.core.*;import net.minecraft.world.level.*;import net.minecraft.world.level.block.state.*;public class BlockColors{public int getColor(BlockState s,BlockAndTintGetter l,BlockPos p,int i){return 0x87aa55;}}''',
    'net/minecraft/client/renderer/LevelRenderer.java':'''package net.minecraft.client.renderer;import net.minecraft.core.*;import net.minecraft.world.level.*;public class LevelRenderer{public static int getLightColor(BlockAndTintGetter l,BlockPos p){return 0x00f000f0;}}''',
    'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;import net.minecraft.client.multiplayer.*;import net.minecraft.client.renderer.block.*;import net.minecraft.client.color.block.*;public class Minecraft{public ClientLevel level=new ClientLevel();public final BlockRenderDispatcher dispatcher=new BlockRenderDispatcher();public static final Minecraft INSTANCE=new Minecraft();public static Minecraft getInstance(){return INSTANCE;}public BlockRenderDispatcher getBlockRenderer(){return dispatcher;}public BlockColors getBlockColors(){return new BlockColors();}}''',
    'net/minecraft/client/renderer/MultiBufferSource.java':'''package net.minecraft.client.renderer;public interface MultiBufferSource{public static class BufferSource implements MultiBufferSource{}}''',
    'com/mojang/blaze3d/vertex/PoseStack.java':'''package com.mojang.blaze3d.vertex;import org.joml.Quaternionf;public class PoseStack{public int depth;public double y;public void pushPose(){depth++;}public void popPose(){depth--;}public void translate(double x,double y,double z){this.y+=y;}public void mulPose(Quaternionf q){}}''',
    'org/joml/Quaternionf.java':'''package org.joml;public class Quaternionf{}''',
    'com/mojang/math/Axis.java':'''package com.mojang.math;import org.joml.Quaternionf;public interface Axis{Axis XP=new Axis(){};Axis ZP=new Axis(){};default Quaternionf rotationDegrees(float d){return new Quaternionf();}}''',
    'net/minecraft/world/phys/Vec3.java':'''package net.minecraft.world.phys;public class Vec3{public final double x,y,z;public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}}''',
    'com/tnc/tnc/boss/fire/TortoiseQuake.java':'''package com.tnc.tnc.boss.fire;public class TortoiseQuake{public record Cell(int x,int z,double distance){}}''',
    'QuakeSamplingTest.java':'''import net.minecraft.client.*;import net.minecraft.world.level.*;
public class QuakeSamplingTest{
 public static void main(String[] args)throws Exception{
  var mc=Minecraft.getInstance();var type=Class.forName("com.tnc.tnc.boss.fire.client.SlamGroundWave");
  var method=type.getDeclaredMethod("sample",Level.class,int.class,int.class,double.class);method.setAccessible(true);
  mc.dispatcher.unculled=false;
  if(method.invoke(null,mc.level,50,-876,-60D)==null)throw new AssertionError("Negative-height grass floor rejected");
  System.out.println("PASS negative-height ordinary grass model");
  mc.dispatcher.unculled=true;
  if(method.invoke(null,mc.level,50,-876,-60D)==null)throw new AssertionError("Solid ground vanished when its model uses unculled quads");
  System.out.println("PASS ground with unculled model quads");
  var patch=method.invoke(null,mc.level,50,-876,-60D);
  var draw=java.util.Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals("drawPatch")).findFirst().orElseThrow();draw.setAccessible(true);
  var poses=new com.mojang.blaze3d.vertex.PoseStack();
  draw.invoke(null,mc,poses,new net.minecraft.world.phys.Vec3(40,-48,-868),new net.minecraft.world.phys.Vec3(50.5,-60,-876),new com.tnc.tnc.boss.fire.TortoiseQuake.Cell(50,-876,2),patch,0.75D);
  if(mc.dispatcher.draws!=1||poses.depth!=0||Math.abs(mc.dispatcher.drawY-(-12.247D))>1E-8)throw new AssertionError("Native block rendering or camera-relative placement missing");
  System.out.println("PASS actual block draw uses dispatcher, overlay, light and camera-relative pose");
  mc.level.loaded=false;
  if(method.invoke(null,mc.level,50,-876,-60D)!=null)throw new AssertionError("Unloaded chunk sampled");
  System.out.println("PASS unloaded chunks skipped");
 }
}'''
    }
    sources=[]
    for name,data in stubs.items():
        p=stage/'src'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(data,encoding='utf-8');sources.append(p)
    java=args.java_home/'bin'
    def run(command,name):
        env=os.environ.copy();env['JAVA_TOOL_OPTIONS']='-Dfile.encoding=UTF-8'
        p=subprocess.run(list(map(str,command)),capture_output=True,env=env)
        output=(p.stdout+p.stderr).decode('utf-8',errors='replace');(stage/(name+'.log')).write_text(output,encoding='utf-8')
        if p.returncode:print(output);raise SystemExit(p.returncode)
        if name in ('extract','test'):print(output)
    run([java/'javac.exe','-encoding','UTF-8','-proc:none','-cp',asm,'-d',classes,Path(__file__).with_name('ExtractQuakeSampling.java'),*sources],'compile')
    run([java/'java.exe','-cp',str(classes)+os.pathsep+asm,'ExtractQuakeSampling',args.input,classes],'extract')
    run([java/'java.exe','-cp',classes,'QuakeSamplingTest'],'test')

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');main()
