package com.tnc.tnc.home;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class TownProtection {
    public static boolean town(ServerLevel l,BlockPos world){
        if(l!=l.getServer().overworld())return false;
        var origin=HousingService.sourceOrigin(l);if(origin==null)return false;var p=world.subtract(origin);
        return (p.getX()>=120&&p.getX()<=402&&p.getZ()>=92&&p.getZ()<=432&&p.getY()>=80&&p.getY()<=180)
                ||(p.getX()>=410&&p.getX()<=490&&p.getZ()>=260&&p.getZ()<=330&&p.getY()>=80&&p.getY()<=145);
    }
    private static boolean denied(ServerPlayer p,BlockPos pos){return (town(p.serverLevel(),pos)||HousingService.isOwnedPosition(p.serverLevel(),pos))&&!HousingService.mayDecorate(p,pos)&&!p.isCreative();}
    public static boolean hazard(net.minecraft.world.level.BlockGetter world,BlockPos pos){return world instanceof ServerLevel l&&((com.tnc.tnc.npc.SkyIslandAnchors.isComplete(l)&&town(l,pos))||HousingService.isOwnedPosition(l,pos));}
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent e){if(e.getPlayer() instanceof ServerPlayer p&&denied(p,e.getPos())&&!(e.getState().getBlock() instanceof CropBlock&&!HousingService.isOwnedPosition(p.serverLevel(),e.getPos())))e.setCanceled(true);}
    @SubscribeEvent public static void placing(BlockEvent.EntityPlaceEvent e){if(e.getEntity() instanceof ServerPlayer p&&denied(p,e.getPos())&&!(e.getPlacedBlock().getBlock() instanceof CropBlock&&!HousingService.isOwnedPosition(p.serverLevel(),e.getPos())))e.setCanceled(true);}
    @SubscribeEvent public static void using(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p.isCreative())return;
        var item=e.getItemStack().getItem();boolean edits=item instanceof net.minecraft.world.item.BlockItem||item instanceof net.minecraft.world.item.BucketItem||item instanceof net.minecraft.world.item.DiggerItem||item instanceof net.minecraft.world.item.FlintAndSteelItem||item instanceof net.minecraft.world.item.FireChargeItem;
        if((HousingService.isOwnedPosition(p.serverLevel(),e.getPos())&&!HousingService.mayDecorate(p,e.getPos()))||(edits&&denied(p,e.getPos())))e.setCanceled(true);
    }
    @SubscribeEvent public static void tool(BlockEvent.BlockToolModificationEvent e){if(e.getPlayer() instanceof ServerPlayer p&&denied(p,e.getPos()))e.setCanceled(true);}
    @SubscribeEvent public static void fluid(BlockEvent.FluidPlaceBlockEvent e){if(e.getLevel() instanceof ServerLevel l&&(town(l,e.getPos())||HousingService.isOwnedPosition(l,e.getPos()))){e.setNewState(e.getOriginalState());e.setCanceled(true);}}
    @SubscribeEvent public static void piston(net.minecraftforge.event.level.PistonEvent.Pre e){
        if(!(e.getLevel() instanceof ServerLevel l))return;
        for(int i=0;i<=14;i++){var pos=e.getPos().relative(e.getDirection(),i);if(town(l,pos)||HousingService.isOwnedPosition(l,pos)){e.setCanceled(true);return;}}
        var helper=e.getStructureHelper();if(helper==null||!helper.resolve())return;
        for(var pos:helper.getToPush())if(town(l,pos)||HousingService.isOwnedPosition(l,pos)||town(l,pos.relative(helper.getPushDirection()))||HousingService.isOwnedPosition(l,pos.relative(helper.getPushDirection()))){e.setCanceled(true);return;}
        for(var pos:helper.getToDestroy())if(town(l,pos)||HousingService.isOwnedPosition(l,pos)){e.setCanceled(true);return;}
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate e){if(e.getLevel() instanceof ServerLevel l)e.getAffectedBlocks().removeIf(pos->town(l,pos)||HousingService.isOwnedPosition(l,pos));}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null||server.getTickCount()%200!=0)return;
        var t=HousingService.home(server);if(!t.getBoolean("Preparing"))return;
        try{HousingService.finish(server.overworld(),BlockPos.of(t.getLong("Origin")),HousingService.blueprint(server.overworld()),t);}catch(Exception ignored){}
    }
}
