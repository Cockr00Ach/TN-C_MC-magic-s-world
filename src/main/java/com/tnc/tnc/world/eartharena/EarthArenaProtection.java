package com.tnc.tnc.world.eartharena;

import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc")
public final class EarthArenaProtection {
    private static boolean protectedLevel(net.minecraft.world.level.LevelAccessor level) {
        return level instanceof net.minecraft.world.level.Level real && real.dimension().equals(EarthArena.KEY);
    }
    @SubscribeEvent public static void mining(BlockEvent.BreakEvent event) { if(protectedLevel(event.getLevel())) event.setCanceled(true); }
    @SubscribeEvent public static void clicking(PlayerInteractEvent.LeftClickBlock event) { if(protectedLevel(event.getLevel())) event.setCanceled(true); }
    @SubscribeEvent public static void placement(BlockEvent.EntityPlaceEvent event) { if(protectedLevel(event.getLevel())) event.setCanceled(true); }
    @SubscribeEvent public static void piston(PistonEvent.Pre event) { if(protectedLevel(event.getLevel())) event.setCanceled(true); }
    @SubscribeEvent public static void destruction(LivingDestroyBlockEvent event) { if(protectedLevel(event.getEntity().level())) event.setCanceled(true); }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) { if(protectedLevel(event.getLevel())) event.getAffectedBlocks().clear(); }
    @SubscribeEvent public static void migrationLanding(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player&&protectedLevel(player.level())&&EarthArenaUpgrade.safeLanding(player))event.setDamageMultiplier(0);
    }
}
