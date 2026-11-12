package com.tnc.tnc.warrior;

import net.minecraft.server.level.ServerPlayer;

/** Phantom attack peak and melee reach share absolute tempering growth, 3x through 10x. */
public final class WarriorPhantom {
    public static final double MAX_GROWTH=WarriorReach.MAX_PEAK-1;
    private WarriorPhantom() {}
    /** Retained API: extra scale above the original weapon, independent of talent cap. */
    public static double growth(int points,int cap){return scale(points,cap)-1;}
    public static double scale(int points,int cap){return WarriorReach.peak(points);}
    public static double scale(ServerPlayer player){
        if(player==null)return 1;
        var data=TemperingData.get(player.server);
        return scale(data.points(player.getUUID()),data.cap(player.getUUID()));
    }
}
