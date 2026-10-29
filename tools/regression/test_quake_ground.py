"""Regression against unchanged production capture bytecode; no Minecraft or GL startup."""
from pathlib import Path
import argparse,json,os,shutil,subprocess,tempfile
p=argparse.ArgumentParser();p.add_argument('input',type=Path);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--output-dir',type=Path);a=p.parse_args()
R=Path(__file__).resolve().parents[2];tmp=tempfile.TemporaryDirectory(prefix='tnc-ground-');out=a.output_dir or Path(tmp.name);out.mkdir(parents=True,exist_ok=True)
classes=out/'classes';classes.mkdir(exist_ok=True)
asm=os.pathsep.join(str(j) for package in ('asm','asm-tree') for j in (R/'.gradle-home/caches/modules-2/files-2.1/org.ow2.asm'/package).glob('*/*/*.jar'))
assert asm,'ASM cache missing'
stubs = {'net/minecraft/core/BlockPos.java': 'package net.minecraft.core;public record BlockPos(int x,int y,int '
                                     'z){public static BlockPos containing(double x,double y,double '
                                     'z){return new '
                                     'BlockPos((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));}public '
                                     'BlockPos above(){return new BlockPos(x,y+1,z);}}',
 'net/minecraft/core/Direction.java': 'package net.minecraft.core;public enum '
                                      'Direction{DOWN,UP,NORTH,SOUTH,WEST,EAST;public enum Axis{X,Y,Z}}',
 'net/minecraft/util/Mth.java': 'package net.minecraft.util;public class Mth{public static int floor(double '
                                'd){return(int)Math.floor(d);}}',
 'net/minecraft/util/RandomSource.java': 'package net.minecraft.util;public interface RandomSource{static '
                                         'RandomSource create(long seed){return s->{};}void setSeed(long '
                                         'seed);}',
 'net/minecraft/world/level/levelgen/Heightmap.java': 'package net.minecraft.world.level.levelgen;public '
                                                      'class Heightmap{public enum '
                                                      'Types{MOTION_BLOCKING_NO_LEAVES}}',
 'net/minecraft/world/level/BlockGetter.java': 'package net.minecraft.world.level;public interface '
                                               'BlockGetter{}',
 'net/minecraft/world/level/BlockAndTintGetter.java': 'package net.minecraft.world.level;public interface '
                                                      'BlockAndTintGetter extends BlockGetter{}',
 'net/minecraft/world/level/Level.java': 'package net.minecraft.world.level;\n'
                                         'import net.minecraft.core.*;import '
                                         'net.minecraft.world.level.block.state.*;import '
                                         'net.minecraft.world.level.levelgen.*;\n'
                                         'public class Level implements BlockAndTintGetter{public boolean '
                                         'loaded=true;public int floorY=-61,heightOffset=0;public double '
                                         'top=1;\n'
                                         'public boolean hasChunkAt(BlockPos p){return loaded;}public int '
                                         'getHeight(Heightmap.Types t,int x,int z){return '
                                         'floorY+1+heightOffset;}\n'
                                         'public int getMinBuildHeight(){return -64;}public BlockState '
                                         'getBlockState(BlockPos p){return new '
                                         'BlockState(p.y()>floorY,top);}\n'
                                         'public float getShade(Direction d,boolean shade){return 1;}}',
 'net/minecraft/client/multiplayer/ClientLevel.java': 'package net.minecraft.client.multiplayer;public class '
                                                      'ClientLevel extends net.minecraft.world.level.Level{}',
 'net/minecraft/world/level/block/RenderShape.java': 'package net.minecraft.world.level.block;public enum '
                                                     'RenderShape{MODEL}',
 'net/minecraft/world/level/material/FluidState.java': 'package net.minecraft.world.level.material;public '
                                                       'class FluidState{public boolean isEmpty(){return '
                                                       'true;}}',
 'net/minecraft/world/phys/shapes/VoxelShape.java': 'package net.minecraft.world.phys.shapes;import '
                                                    'net.minecraft.core.Direction;public class '
                                                    'VoxelShape{final boolean empty;final double top;public '
                                                    'VoxelShape(boolean empty,double '
                                                    'top){this.empty=empty;this.top=top;}public boolean '
                                                    'isEmpty(){return empty;}public double '
                                                    'max(Direction.Axis a){return top;}}',
 'net/minecraft/world/level/block/state/BlockState.java': 'package net.minecraft.world.level.block.state;\n'
                                                          'import net.minecraft.core.*;import '
                                                          'net.minecraft.world.level.*;import '
                                                          'net.minecraft.world.level.block.*;import '
                                                          'net.minecraft.world.level.material.*;import '
                                                          'net.minecraft.world.phys.shapes.*;\n'
                                                          'public class BlockState{final boolean air;final '
                                                          'double top;public BlockState(boolean air,double '
                                                          'top){this.air=air;this.top=top;}\n'
                                                          'public boolean isAir(){return air;}public '
                                                          'FluidState getFluidState(){return new '
                                                          'FluidState();}public boolean '
                                                          'hasBlockEntity(){return false;}\n'
                                                          'public RenderShape getRenderShape(){return '
                                                          'RenderShape.MODEL;}public VoxelShape '
                                                          'getCollisionShape(BlockGetter level,BlockPos '
                                                          'p){return new VoxelShape(air,top);}\n'
                                                          'public long getSeed(BlockPos p){return 42;}}',
 'net/minecraft/client/renderer/texture/TextureAtlasSprite.java': 'package '
                                                                  'net.minecraft.client.renderer.texture;public '
                                                                  'class TextureAtlasSprite{public float '
                                                                  'getU0(){return 0;}public float '
                                                                  'getU1(){return 1;}public float '
                                                                  'getV0(){return 0;}public float '
                                                                  'getV1(){return 1;}}',
 'net/minecraft/client/renderer/block/model/BakedQuad.java': 'package '
                                                             'net.minecraft.client.renderer.block.model;import '
                                                             'net.minecraft.core.Direction;\n'
                                                             'public class BakedQuad{final int[] data=new '
                                                             'int[32];public BakedQuad(float top){float[][] '
                                                             'p={{0,top,0},{0,top,1},{1,top,1},{1,top,0}};for(int '
                                                             'i=0;i<4;i++)for(int '
                                                             'a=0;a<3;a++)data[i*8+a]=Float.floatToIntBits(p[i][a]);}\n'
                                                             'public int[] getVertices(){return data;}public '
                                                             'boolean isTinted(){return false;}public int '
                                                             'getTintIndex(){return 0;}public boolean '
                                                             'isShade(){return true;}public Direction '
                                                             'getDirection(){return Direction.UP;}}',
 'net/minecraft/client/resources/model/BakedModel.java': 'package net.minecraft.client.resources.model;\n'
                                                         'import java.util.*;import '
                                                         'net.minecraft.core.*;import '
                                                         'net.minecraft.util.*;import '
                                                         'net.minecraft.world.level.block.state.*;import '
                                                         'net.minecraft.client.renderer.block.model.*;import '
                                                         'net.minecraft.client.renderer.texture.*;\n'
                                                         'public interface BakedModel{List<BakedQuad> '
                                                         'getQuads(BlockState s,Direction d,RandomSource '
                                                         'r);default TextureAtlasSprite '
                                                         'getParticleIcon(){return new '
                                                         'TextureAtlasSprite();}}',
 'net/minecraft/client/renderer/block/BlockRenderDispatcher.java': 'package '
                                                                   'net.minecraft.client.renderer.block;\n'
                                                                   'import java.util.*;import '
                                                                   'net.minecraft.client.resources.model.*;import '
                                                                   'net.minecraft.client.renderer.block.model.*;import '
                                                                   'net.minecraft.core.*;import '
                                                                   'net.minecraft.world.level.block.state.*;\n'
                                                                   'public class '
                                                                   'BlockRenderDispatcher{public float '
                                                                   'modelTop=1;public boolean '
                                                                   'unculled,empty;\n'
                                                                   'public BakedModel '
                                                                   'getBlockModel(BlockState '
                                                                   's){return(state,d,r)->empty?List.of():((unculled?d==null:d==Direction.UP)?List.of(new '
                                                                   'BakedQuad(modelTop)):List.of());}}',
 'net/minecraft/client/color/block/BlockColors.java': 'package net.minecraft.client.color.block;import '
                                                      'net.minecraft.core.*;import '
                                                      'net.minecraft.world.level.*;import '
                                                      'net.minecraft.world.level.block.state.*;public class '
                                                      'BlockColors{public int getColor(BlockState '
                                                      's,BlockAndTintGetter l,BlockPos p,int t){return '
                                                      '0xffffff;}}',
 'net/minecraft/client/renderer/LevelRenderer.java': 'package net.minecraft.client.renderer;import '
                                                     'net.minecraft.core.*;import '
                                                     'net.minecraft.world.level.*;public class '
                                                     'LevelRenderer{public static int '
                                                     'getLightColor(BlockAndTintGetter l,BlockPos p){return '
                                                     '0xf000f0;}}',
 'net/minecraft/client/Minecraft.java': 'package net.minecraft.client;import '
                                        'net.minecraft.client.multiplayer.*;import '
                                        'net.minecraft.client.renderer.block.*;import '
                                        'net.minecraft.client.color.block.*;\n'
                                        'public class Minecraft{public ClientLevel level=new '
                                        'ClientLevel();public final BlockRenderDispatcher dispatcher=new '
                                        'BlockRenderDispatcher();public BlockRenderDispatcher '
                                        'getBlockRenderer(){return dispatcher;}public BlockColors '
                                        'getBlockColors(){return new BlockColors();}}',
 'QuakeGroundTest.java': 'import java.lang.reflect.*;import net.minecraft.client.Minecraft;\n'
                         'public class QuakeGroundTest {\n'
                         ' static Object capture(Minecraft mc)throws Exception {Class<?> '
                         'c=Class.forName("com.tnc.tnc.boss.fire.client.QuakeBandFx");Method '
                         'm=c.getDeclaredMethod("capture",Minecraft.class,int.class,int.class,double.class);m.setAccessible(true);return '
                         'm.invoke(null,mc,0,0,-60D);}\n'
                         ' static double value(Object s,String n)throws Exception{if(s==null)throw new '
                         'AssertionError("Ground column missing");Method '
                         'm=s.getClass().getDeclaredMethod(n);m.setAccessible(true);return '
                         '((Number)m.invoke(s)).doubleValue();}\n'
                         ' static void near(double a,double b){if(Math.abs(a-b)>1e-6)throw new '
                         'AssertionError("Expected "+b+", got "+a);}\n'
                         ' public static void main(String[] args)throws Exception {\n'
                         '  Minecraft mc=new '
                         'Minecraft();near(value(capture(mc),"groundY"),-60);System.out.println("PASS '
                         'negative Y ground");\n'
                         '  '
                         'if(args[0].equals("height")){mc.level.heightOffset=-1;near(value(capture(mc),"groundY"),-60);System.out.println("PASS '
                         'heightmap hint cannot hide surface");}\n'
                         '  else '
                         'if(args[0].equals("model")){mc.dispatcher.modelTop=0;near(value(capture(mc),"pivot"),0);System.out.println("PASS '
                         'custom model top aligned with collision surface");}\n'
                         '  else '
                         '{mc.level.top=.5;mc.dispatcher.modelTop=.5F;near(value(capture(mc),"groundY"),-60.5);near(value(capture(mc),"pivot"),.5);mc.dispatcher.unculled=true;near(value(capture(mc),"pivot"),.5);mc.dispatcher.empty=true;near(value(capture(mc),"pivot"),.5);mc.level.loaded=false;if(capture(mc)!=null)throw '
                         'new AssertionError("Unloaded chunk sampled");System.out.println("PASS slab, '
                         'unculled model, fallback and unloaded chunk");}\n'
                         ' }\n'
                         '}'}
for name,content in stubs.items():
 f=out/'src'/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(content,encoding='utf-8')
def run(args,name,check=True):
 env=os.environ.copy();env['JAVA_TOOL_OPTIONS']='-Dfile.encoding=UTF-8'
 r=subprocess.run(list(map(str,args)),capture_output=True,env=env);text=(r.stdout+r.stderr).decode('utf-8',errors='replace');(out/(name+'.log')).write_text(text,encoding='utf-8')
 if check and r.returncode:raise RuntimeError(text)
 return r.returncode,text
J=a.java_home/'bin'
run([J/'javac.exe','-encoding','UTF-8','-proc:none','-cp',asm,'-d',classes,R/'tools/regression/ExtractQuakeGround.java',*[out/'src'/n for n in stubs]],'compile')
run([J/'java.exe','-cp',str(classes)+os.pathsep+asm,'ExtractQuakeGround',a.input,classes],'extract')
failed=0
for case in ('height','model','other'):
 code,text=run([J/'java.exe','-cp',classes,'QuakeGroundTest',case],case,False);print(text);failed+=bool(code)
raise SystemExit(bool(failed))
