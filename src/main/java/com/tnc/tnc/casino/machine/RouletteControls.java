package com.tnc.tnc.casino.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeMod;

/** Shared physical button positions and authoritative tabletop ray checks. */
public final class RouletteControls {
    public static final int[] BETS={0,6,1};
    public static final String[] LABELS={"红色 · 2倍","绿零 · 36倍","黑色 · 2倍"};
    public static final double TOP=1.025;
    private RouletteControls() {}
    public static int button(double x,double z) {
        if(x<2.8||x>3.7) return -1;
        for(int i=0;i<3;i++) if(z>=0.9+i*0.85&&z<=1.6+i*0.85) return i;
        return -1;
    }
    public static Vec3 local(Vec3 world,BlockPos pos,Direction facing) {
        double x=world.x-pos.getX()-4,z=world.z-pos.getZ()-2;
        Vec3 actual=switch(facing) {
            case EAST -> new Vec3(4+z,world.y-pos.getY(),2-x);
            case SOUTH -> new Vec3(4-x,world.y-pos.getY(),2-z);
            case WEST -> new Vec3(4-z,world.y-pos.getY(),2+x);
            default -> new Vec3(4+x,world.y-pos.getY(),2+z);
        };
        return new Vec3(actual.x/2,actual.y,actual.z);
    }

    public static Vec3 hit(Player player,BlockPos pos,Direction facing) {
        if(!(player.level().getBlockEntity(pos) instanceof TNRouletteTableEntity table))return null;
        Vec3 point=MachineInteraction.hit(player,table);
        return point==null?null:local(point,pos,facing);
    }
}
