package com.tnc.tnc.dungeon.leyline;
import com.tnc.tnc.dungeon.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class LeylineEvents {
 private static DungeonGraph.Room room(ServerPlayer p){return DungeonService.recovering(p)||com.tnc.tnc.combat.DownedCombat.isDowned(p)?null:DungeonService.room(p);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void use(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e){
  if(!(e.getEntity() instanceof ServerPlayer p))return;var room=room(p);if(room==null)return;var r=DungeonService.run(p);var s=LeylineService.state(r,room);var at=e.getPos();
  if(LeylinePuzzles.interact(p,r,room,at,e.getHand())){e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);return;}
  if(LeylineService.nodes(s,"source").stream().anyMatch(n->n.getLong("Pos")==at.asLong())){
   String id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem()).getPath();
   if(id.equals("strange_waste")||id.equals("overdraft_elixir")){if(!s.getBoolean("SourceCut")&&s.getInt("Poison")==0){s.putInt("Poison",id.equals("strange_waste")?1:2);LeylineService.consumeHeld(p,e.getHand());LeylineService.markDirty(p.server);DungeonService.message(p,"污染沿魔脉流向母巢");}e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);return;}
  }
  // Placement is allowed only on a designated engineering plinth, scoped to this operation.
  var stack=e.getItemStack();if(stack.getItem() instanceof BlockItem bi){
   String id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(bi.getBlock()).getPath();
   boolean allowed=id.contains("mirror")||id.equals("carved_mana_platter");
   if(allowed&&LeylineService.nodes(s,"mirror_base").stream().anyMatch(n->n.getLong("Pos")==at.asLong())){
    final InteractionResult[] result={InteractionResult.PASS};DungeonRealm.write(()->result[0]=stack.useOn(new net.minecraft.world.item.context.UseOnContext(p,e.getHand(),e.getHitVec())));
    e.setCanceled(true);e.setCancellationResult(result[0]);
   }
  }
 }
 @SubscribeEvent public static void hit(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock e){
  if(!(e.getEntity() instanceof ServerPlayer p))return;var room=room(p);if(room==null||p.distanceToSqr(Vec3.atCenterOf(e.getPos()))>36)return;var r=DungeonService.run(p);
  var state=LeylineService.state(r,room);var at=e.getPos();
  if(p.isShiftKeyDown()&&p.getMainHandItem().isEmpty()&&LeylineService.nodes(state,"mirror_base").stream().anyMatch(n->BlockPos.of(n.getLong("Pos")).above().equals(at))
     &&p.serverLevel().getBlockEntity(at) instanceof com.tnc.tnc.life.routes.RouteNodeEntity mirror&&mirror.mayUse(p)){
    DungeonRealm.write(()->p.serverLevel().destroyBlock(at,true,p));e.setCanceled(true);return;
  }
  if(LeylineService.cutSource(p,r,room,e.getPos())||LeylinePuzzles.breakBlock(p,r,room,e.getPos()))e.setCanceled(true);
 }
 public static void potion(ServerPlayer p,BlockPos at,String id){var room=room(p);if(room==null)return;var r=DungeonService.run(p);var s=LeylineService.state(r,room);
  for(var n:LeylineService.nodes(s,"source"))if(BlockPos.of(n.getLong("Pos")).distSqr(at)<=4&&!s.getBoolean("SourceCut")&&s.getInt("Poison")==0){if(id.equals("strange_waste")||id.equals("overdraft_elixir")){s.putInt("Poison",id.equals("strange_waste")?1:2);LeylineService.markDirty(p.server);}}
  for(var n:s.getList("Nodes",10)){var pos=BlockPos.of(((net.minecraft.nbt.CompoundTag)n).getLong("Pos"));if(pos.distSqr(at)<=4&&LeylinePuzzles.potion(p,r,room,pos,id))break;}
 }
 public static void spell(ServerPlayer p,BlockPos at,String element){var room=room(p);if(room==null)return;var r=DungeonService.run(p);
  if(element.equals("fire")||element.equals("ice"))LeylineService.cutSource(p,r,room,at);
  LeylinePuzzles.spell(p,r,room,at,element);
 }
 @SubscribeEvent public static void projectile(net.minecraftforge.event.entity.ProjectileImpactEvent e){
  if(!(e.getProjectile().getOwner() instanceof ServerPlayer p)||!(e.getRayTraceResult() instanceof BlockHitResult b))return;
  String id=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getProjectile().getType()).getPath();
  String element=e.getProjectile() instanceof net.minecraft.world.entity.projectile.AbstractArrow?"arrow":id.contains("fire")?"fire":id.contains("water")?"water":id.contains("ice")||id.contains("frost")?"ice":id.contains("wind")?"wind":"";
  if(!element.isEmpty())spell(p,b.getBlockPos(),element);
 }
}

