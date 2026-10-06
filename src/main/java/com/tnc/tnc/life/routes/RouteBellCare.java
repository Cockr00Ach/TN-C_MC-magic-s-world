package com.tnc.tnc.life.routes;
import com.tnc.tnc.life.fauna.BellwoolSheepEntity;
import com.tnc.tnc.life.pasture.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
public final class RouteBellCare {
 public static InteractionResult setHome(ServerPlayer p,BellwoolSheepEntity sheep,BlockPos marker){if(!p.getUUID().equals(sheep.caretaker())||p.distanceToSqr(sheep)>4096||!RouteAccess.allowed(p.serverLevel(),marker,p.getUUID())||!p.serverLevel().getBlockState(marker).is(PastureRegistry.block("habitat_marker")))return InteractionResult.FAIL;sheep.getPersistentData().putLong("RouteNest",marker.above().asLong());RouteProgress.award(p,"nest/bellwool_sheep");return InteractionResult.SUCCESS;}
 public static void tick(BellwoolSheepEntity sheep){if(!(sheep.level() instanceof ServerLevel l)||sheep.caretaker()==null||!sheep.getPersistentData().contains("RouteNest"))return;var home=BlockPos.of(sheep.getPersistentData().getLong("RouteNest"));double radius=sheep.getBbWidth()/2.;double x=net.minecraft.util.Mth.clamp(sheep.getX(),home.getX()-4+radius,home.getX()+5-radius),z=net.minecraft.util.Mth.clamp(sheep.getZ(),home.getZ()-4+radius,home.getZ()+5-radius);if(x!=sheep.getX()||z!=sheep.getZ()){sheep.getNavigation().stop();sheep.setPos(x,sheep.getY(),z);}if(sheep.tickCount%200!=0||sheep.isBaby()||!sheep.canFallInLove())return;for(var at:BlockPos.betweenClosed(home.offset(-4,-1,-4),home.offset(4,1,4)))if(RouteAccess.allowed(l,at,sheep.caretaker())&&l.getBlockEntity(at) instanceof PastureFacilityEntity trough&&trough.kind()==PastureFacilityBlock.Kind.TROUGH&&sheep.caretaker().equals(trough.owner())){if(sheep.distanceToSqr(at.getX()+.5,at.getY()+.5,at.getZ()+.5)>4){sheep.getNavigation().moveTo(at.getX()+.5,at.getY()+1,at.getZ()+.5,1);return;}if(trough.takeFood(com.tnc.tnc.TNMod.BELLWOOL_FODDER.get()))sheep.setInLove(null);return;}}
}
