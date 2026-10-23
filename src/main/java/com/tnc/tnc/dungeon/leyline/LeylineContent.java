package com.tnc.tnc.dungeon.leyline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegisterEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class LeylineContent {
 public static Block SOURCE,NEST,NODE,VEIL,PHASE_MATERIAL,PHASE_AETHER;
 @SubscribeEvent public static void register(RegisterEvent e){e.register(Registries.BLOCK,h->{
 SOURCE=new Block(BlockBehaviour.Properties.copy(Blocks.AMETHYST_BLOCK).strength(-1,3600000));
 NEST=new Block(BlockBehaviour.Properties.copy(Blocks.NETHER_WART_BLOCK).strength(-1,3600000));
 NODE=new Block(BlockBehaviour.Properties.copy(Blocks.CHISELED_DEEPSLATE).strength(-1,3600000));
 VEIL=new Phase(-1);PHASE_MATERIAL=new Phase(0);PHASE_AETHER=new Phase(1);
 h.register(com.tnc.tnc.dungeon.DungeonContent.id("leyline_source"),SOURCE);h.register(com.tnc.tnc.dungeon.DungeonContent.id("leyline_nest"),NEST);h.register(com.tnc.tnc.dungeon.DungeonContent.id("leyline_node"),NODE);
 h.register(com.tnc.tnc.dungeon.DungeonContent.id("leyline_veil"),VEIL);h.register(com.tnc.tnc.dungeon.DungeonContent.id("leyline_phase_material"),PHASE_MATERIAL);h.register(com.tnc.tnc.dungeon.DungeonContent.id("leyline_phase_aether"),PHASE_AETHER);
 });}
 static final class Phase extends Block {
  final int phase;Phase(int p){super(BlockBehaviour.Properties.copy(Blocks.BARRIER).noOcclusion());phase=p;}
  @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
  @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext ctx){
   if(phase<0)return Shapes.block();
   if(ctx instanceof EntityCollisionContext ec){var ent=ec.getEntity();boolean enabled;
    if(ent instanceof ServerPlayer sp)enabled=com.tnc.tnc.mana.sight.ManaSightServer.enabled(sp);
    else if(ent!=null&&ent.level().isClientSide)enabled=Client.enabled();else return Shapes.empty();
    return enabled==(phase==1)?Shapes.block():Shapes.empty();
   }return Shapes.empty();
  }
 }
 private static final class Client {static boolean enabled(){return com.tnc.tnc.mana.sight.client.ManaSightClient.enabled();}}
}
