package com.tnc.tnc.skyacademy;

import com.tnc.tnc.life.routes.RouteContent;
import com.tnc.tnc.life.routes.RouteKind;
import com.tnc.tnc.world.SkyIslandWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Public utilities retain town protection except for the specific circuit leased to a learner. */
@Mod.EventBusSubscriber(modid = "tnc")
public final class AcademyPracticeAccess {
    private AcademyPracticeAccess() {}

    public static boolean circuit(ServerPlayer player, BlockPos pos) {
        if (!AcademyLessons.leased(player,"workshop") || player.serverLevel()!=SkyIslandWorlds.island(player.server)) return false;
        BlockPos b=AcademyStations.base(player.server,"workshop");
        return b!=null && pos.getY()==b.getY() && pos.getZ()==b.getZ()
                && pos.getX()>=b.getX() && pos.getX()<=b.getX()+4;
    }

    private static Block expected(ServerPlayer player, BlockPos pos) {
        if ((!AcademyLessons.leased(player,"farm") && !AcademyLessons.leased(player,"workshop"))
                || player.serverLevel()!=SkyIslandWorlds.island(player.server)) return null;
        var lesson=AcademyLessons.data(player);
        if (!lesson.getBoolean("Practice")) return null;
        String site=lesson.getString("Active");
        boolean farm="farm".equals(site);
        if ((!farm && !"workshop".equals(site)) || !AcademyLessons.leased(player,site)) return null;
        var b=AcademyStations.base(player.server,site);
        if (b==null || !b.east(farm ? 1 : 2).equals(pos)) return null;
        return RouteContent.NODES.get(farm ? RouteKind.COLLECTOR.id : RouteKind.WIRE.id).get();
    }

    public static boolean placement(ServerPlayer player, BlockPos pos, ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock()==expected(player,pos);
    }

    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        Block expected=expected(player,event.getPos());
        if (expected!=null && !event.getPlacedBlock().is(expected)) event.setCanceled(true);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || event.getUseItem()==Event.Result.DENY || !(event.getItemStack().getItem() instanceof BlockItem)) return;
        var stack=event.getItemStack();
        var ctx=new UseOnContext(player,event.getHand(),event.getHitVec());
        var pos=new BlockPlaceContext(ctx).getClickedPos();
        if (!placement(player,pos,stack) || player.getCooldowns().isOnCooldown(stack.getItem())
                || !player.serverLevel().mayInteract(player,pos) || !player.serverLevel().mayInteract(player,event.getPos())) return;
        // Clicking the neighbouring wire should insert the missing segment, not open a machine screen.
        event.setUseBlock(Event.Result.DENY);
        if (player.gameMode.getGameModeForPlayer()!=GameType.ADVENTURE) return;

        // Grant CanPlaceOn for this individual placement only. Forge still snapshots the
        // real block and invokes its placement events, including other mods' protection.
        var support=ForgeRegistries.BLOCKS.getKey(player.serverLevel().getBlockState(event.getPos()).getBlock());
        if (support==null) return;
        // A fresh stack also isolates the vanilla AdventureModeCheck cache. A previous
        // failed click may have cached false, and the original must not retain a true grant.
        ItemStack permitted=stack.copy();
        var list=new ListTag();
        list.add(StringTag.valueOf(support.toString()));
        permitted.getOrCreateTag().put("CanPlaceOn",list);
        player.setItemInHand(event.getHand(),permitted);
        try {
            var result=ForgeHooks.onPlaceItemIntoWorld(new UseOnContext(player,event.getHand(),event.getHitVec()));
            stack.setCount(permitted.getCount());
            event.setCanceled(true);
            event.setCancellationResult(result);
        } finally {
            player.setItemInHand(event.getHand(),stack);
        }
    }
}
