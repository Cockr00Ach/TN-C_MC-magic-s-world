package com.tnc.tnc.life.routes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
public final class RouteProgress {
    public static void awardOwner(net.minecraft.server.level.ServerLevel l,java.util.UUID owner,String key){if(owner==null)return;var p=l.getServer().getPlayerList().getPlayer(owner);if(p!=null)award(p,key);}
    public static void award(ServerPlayer player,String key){var id=ResourceLocation.fromNamespaceAndPath("tnc","life_routes/"+key);var advancement=player.server.getAdvancements().getAdvancement(id);if(advancement!=null)player.getAdvancements().award(advancement,"performed");}
}
