package com.tnc.tnc.life.routes;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import com.tnc.tnc.production.MagicForgeBlockEntity;
/** The displayed socket and the actual network share the same endpoints. */
public final class RouteConnections {
 public static boolean compatible(BlockGetter l,BlockPos a,BlockPos b){if(l instanceof net.minecraft.world.level.LevelReader reader&&!reader.hasChunkAt(b))return false;var x=l.getBlockEntity(a);var y=l.getBlockEntity(b);if(!(y instanceof RouteNodeEntity)&&!(y instanceof MagicForgeBlockEntity)){return x instanceof RouteNodeEntity n&&com.tnc.tnc.arcana.ArcanaTowerPorts.brickSocket(l,a,b,n.owner);}
  java.util.UUID owner=x instanceof RouteNodeEntity n?n.owner:x instanceof MagicForgeBlockEntity f?f.owner():null;
  java.util.UUID other=y instanceof RouteNodeEntity n?n.owner:y instanceof MagicForgeBlockEntity f?f.owner():null;
  return owner==null||other==null||owner.equals(other);
 }
 public static boolean connects(BlockGetter l,BlockPos a,BlockPos b){if(!compatible(l,a,b))return false;var difference=b.subtract(a);var direction=Direction.getNearest(difference.getX(),difference.getY(),difference.getZ());
  if(l.getBlockState(a).getBlock() instanceof RouteWireBlock&&!l.getBlockState(a).getValue(RouteWireBlock.ARMS.get(direction)))return false;
  return !(l.getBlockState(b).getBlock() instanceof RouteWireBlock)||l.getBlockState(b).getValue(RouteWireBlock.ARMS.get(direction.getOpposite()));
 }
 public static boolean relay(RouteNodeEntity n){return n.kind().wire()||n.kind().supply();}
}
