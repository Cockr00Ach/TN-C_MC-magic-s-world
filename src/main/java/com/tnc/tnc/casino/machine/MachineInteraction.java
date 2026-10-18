package com.tnc.tnc.casino.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeMod;

/** Every visible machine side is clickable; reach and walls are verified on both ends. */
public final class MachineInteraction {
    private MachineInteraction() {}
    public static Vec3 hit(Player player,TNMachineEntity machine) {
        var pos=machine.getBlockPos();var eye=player.getEyePosition();
        double reach=Math.min(8,player.getAttributeValue(ForgeMod.BLOCK_REACH.get()));
        var end=eye.add(player.getLookAngle().scale(reach));
        var shape=machine.getBlockState().getShape(player.level(),pos);
        var hit=shape.clip(eye,end,pos);
        if(hit==null)return null;
        var wall=player.level().clip(new ClipContext(eye,hit.getLocation(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(wall.getType()!=HitResult.Type.MISS&&!wall.getBlockPos().equals(pos)
                &&wall.getLocation().distanceToSqr(eye)+.0001<hit.getLocation().distanceToSqr(eye))return null;
        return hit.getLocation();
    }
}
