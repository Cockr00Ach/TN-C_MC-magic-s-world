package com.tnc.tnc.life.routes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
/** Direct bucket placement follows the same non-destructive rule as flowing mana. */
public final class ManaBucketItem extends BucketItem {
 public ManaBucketItem(){super(RouteContent.MANA,new Properties().craftRemainder(Items.BUCKET).stacksTo(1));}
 @Override public boolean emptyContents(Player player,Level l,BlockPos p,BlockHitResult hit){var s=l.getBlockState(p);if(!s.isAir()&&!s.is(RouteContent.MANA_LIQUID.get()))return false;if(l instanceof net.minecraft.server.level.ServerLevel server){if(player instanceof net.minecraft.server.level.ServerPlayer sp){if(!RouteAccess.allowed(server,p,sp.getUUID()))return false;}else if(com.tnc.tnc.home.TownProtection.town(server,p)||com.tnc.tnc.home.HousingService.plotAt(server,p)!=null)return false;}return super.emptyContents(player,l,p,hit);}
}
