package com.tnc.tnc.life.routes;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;
public final class RouteAccess {
    public static boolean allowed(ServerLevel level,BlockPos pos,UUID owner){
        if(owner==null||!level.hasChunkAt(pos))return false;
        var player=level.getServer().getPlayerList().getPlayer(owner);
        if(player==null||player.serverLevel()!=level)player=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(owner,"mana_workshop"));
        return !TownProtection.denied(player,pos)&&level.mayInteract(player,pos);
    }
}
