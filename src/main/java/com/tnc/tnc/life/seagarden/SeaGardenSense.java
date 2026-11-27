package com.tnc.tnc.life.seagarden;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc")
public final class SeaGardenSense {
    @SubscribeEvent public static void swimmingPast(TickEvent.PlayerTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !player.isAlive() || player.isSpectator() || !player.isUnderWater()) return;
        int phase=player.tickCount+player.getId();
        if(phase%4!=0) return;
        var level=player.serverLevel();
        var center=player.blockPosition();
        // The two-cell breath plume is narrow. Check it more frequently than fan proximity
        // so a swimming player can pass through it, but never spend a charge at almost full air.
        if(player.getAirSupply()+45<=player.getMaxAirSupply()) {
            double dx=player.getX()-(center.getX()+.5), dz=player.getZ()-(center.getZ()+.5);
            if(dx*dx+dz*dz<=.24) {
                var lantern=SeaGarden.Kind.LANTERN.block();
                for(int down=1;down<=2;down++) {
                    var p=center.below(down);
                    if(!level.getFluidState(p.above()).is(net.minecraft.tags.FluidTags.WATER)) break;
                    if(level.getBlockState(p).is(lantern) && lantern.takeBreath(level,p,player)) break;
                }
            }
        }
        if(phase%12!=0) return;
        var fan=SeaGarden.Kind.FAN.block();
        var cursor=new BlockPos.MutableBlockPos();
        for (int dx=-2;dx<=2;dx++) for (int dz=-2;dz<=2;dz++) for (int dy=-1;dy<=1;dy++) {
            if(dx*dx+dz*dz+dy*dy>5) continue;
            cursor.setWithOffset(center,dx,dy,dz);
            if (!level.hasChunkAt(cursor)) continue;
            var state=level.getBlockState(cursor);
            if (state.is(fan) && state.getValue(SeaPlantBlock.STAGE)==0) fan.prime(level,cursor.immutable(),state);
        }
    }
    private SeaGardenSense() {}
}
