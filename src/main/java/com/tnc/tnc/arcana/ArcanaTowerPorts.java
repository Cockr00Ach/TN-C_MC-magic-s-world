package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
/** The twenty-four shell bricks are physical mana sockets, not blind decorative walls. */
public final class ArcanaTowerPorts {
 public static boolean socket(BlockPos core,BlockPos q){var d=q.subtract(core);int x=Math.abs(d.getX()),z=Math.abs(d.getZ()),y=d.getY();return y>=0&&y<=2&&(x==2&&z<=1||z==2&&x<=1)||x==0&&z==0&&(y==-1||y==3);}
 public static List<BlockPos> links(ServerLevel l,BlockPos at,UUID owner){var result=new ArrayList<BlockPos>();if(owner==null)return result;
  for(var core:RouteNetwork.positions(l)){if(core.distSqr(at)>22||!l.hasChunkAt(core)||!(l.getBlockEntity(core) instanceof RouteNodeEntity tower)||tower.kind()!=RouteKind.ABYSS_TOWER||!owner.equals(tower.owner)||!ArcanaDevices.formed(l,core))continue;
   if(core.equals(at)){for(var q:BlockPos.betweenClosed(core.offset(-2,-1,-2),core.offset(2,3,2)))if(socket(core,q)&&allowedWire(l,q,core))result.add(q.immutable());}
   else if(socket(core,at)&&allowedWire(l,at,core))result.add(core);
  }return result;
 }
 private static boolean allowedWire(ServerLevel l,BlockPos p,BlockPos core){if(!l.hasChunkAt(p))return false;var state=l.getBlockState(p);if(!(state.getBlock() instanceof RouteWireBlock))return true;var d=core.subtract(p);Direction direction=d.getX()!=0&&Math.abs(d.getX())==2?Direction.getNearest(d.getX(),0,0):d.getZ()!=0?Direction.getNearest(0,0,d.getZ()):Direction.getNearest(0,d.getY(),0);return state.getValue(RouteWireBlock.ARMS.get(direction));}
 public static boolean brickSocket(net.minecraft.world.level.BlockGetter l,BlockPos a,BlockPos brick,UUID owner){if(!(l instanceof ServerLevel level)||!l.getBlockState(brick).is(ArcanaContent.TOWER_BRICK))return false;for(var core:RouteNetwork.positions(level))if(socket(core,a)&&level.hasChunkAt(core)&&level.getBlockEntity(core) instanceof RouteNodeEntity n&&n.kind()==RouteKind.ABYSS_TOWER&&Objects.equals(owner,n.owner)&&ArcanaDevices.formed(level,core))return true;return false;}
}
