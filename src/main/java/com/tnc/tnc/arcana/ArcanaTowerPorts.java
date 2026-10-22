package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.production.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import java.util.*;
/** Every exposed structure face is a port. Virtual edges still debit the controller and wire budgets. */
public final class ArcanaTowerPorts {
 public static boolean socket(BlockPos core,BlockPos q){var d=q.subtract(core);int x=Math.abs(d.getX()),z=Math.abs(d.getZ()),y=d.getY();return y>=0&&y<=2&&(x==2&&z<=1||z==2&&x<=1)||x<=1&&z<=1&&(y==-1||y==3);}
 private static boolean contains(ServerLevel l,BlockPos core,BlockPos q){var be=l.getBlockEntity(core);if(be instanceof MagicForgeBlockEntity f){var center=core.relative(f.front().getOpposite());var d=q.subtract(center);return Math.abs(d.getX())<=1&&Math.abs(d.getY())<=1&&Math.abs(d.getZ())<=1;}var d=q.subtract(core);return Math.abs(d.getX())<=1&&Math.abs(d.getZ())<=1&&d.getY()>=0&&d.getY()<=2;}
 private static boolean valid(ServerLevel l,BlockPos core,UUID owner){if(!l.hasChunkAt(core))return false;var be=l.getBlockEntity(core);return be instanceof MagicForgeBlockEntity f&&Objects.equals(owner,f.owner())&&f.formedForTransfer()||be instanceof RouteNodeEntity n&&n.kind()==RouteKind.ABYSS_TOWER&&Objects.equals(owner,n.owner)&&ArcanaDevices.formed(l,core);}
 private static boolean contact(ServerLevel l,BlockPos core,BlockPos q,boolean arms){if(!l.hasChunkAt(q)||contains(l,core,q))return false;var state=l.getBlockState(q);for(var d:Direction.values())if(contains(l,core,q.relative(d))&&(!arms||!(state.getBlock() instanceof RouteWireBlock)||state.getValue(RouteWireBlock.ARMS.get(d))))return true;return false;}
 public static List<BlockPos> links(ServerLevel l,BlockPos at,UUID owner){var result=new ArrayList<BlockPos>();if(owner==null)return result;
  for(var core:RouteNetwork.positions(l)){if(core.distSqr(at)>30||!valid(l,core,owner))continue;
   if(core.equals(at)){for(var q:BlockPos.betweenClosed(core.offset(-3,-2,-3),core.offset(3,3,3)))if(contact(l,core,q,true))result.add(q.immutable());}
   else if(contact(l,core,at,true))result.add(core);
  }return result;
 }
 public static boolean brickSocket(BlockGetter l,BlockPos a,BlockPos brick,UUID owner){if(!(l instanceof ServerLevel level))return false;for(var core:RouteNetwork.positions(level))if(core.distSqr(a)<=30&&valid(level,core,owner)&&contact(level,core,a,false)&&contains(level,core,brick))return true;return false;}
 public static void refresh(Level l,BlockPos core){if(l.isClientSide)return;for(var at:BlockPos.betweenClosed(core.offset(-3,-2,-3),core.offset(3,3,3)))if(l.hasChunkAt(at)&&l.getBlockState(at).getBlock() instanceof RouteWireBlock wire)wire.refresh(l,at);}
}
