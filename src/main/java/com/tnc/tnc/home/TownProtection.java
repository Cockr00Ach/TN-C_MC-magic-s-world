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
    public static boolean denied(ServerPlayer p,BlockPos pos){return (town(p.serverLevel(),pos)||HousingService.plotAt(p.serverLevel(),pos)!=null||HousingService.isOwnedPosition(p.serverLevel(),pos))&&!HousingService.mayDecorate(p,pos)&&!p.isCreative();}
    public static boolean hazard(net.minecraft.world.level.BlockGetter world,BlockPos pos){return world instanceof ServerLevel l&&((com.tnc.tnc.npc.SkyIslandAnchors.isComplete(l)&&town(l,pos))||HousingService.isOwnedPosition(l,pos));}
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent e){if(e.getPlayer() instanceof ServerPlayer p&&denied(p,e.getPos()))e.setCanceled(true);}
    @SubscribeEvent public static void placing(BlockEvent.EntityPlaceEvent e){if(e.getEntity() instanceof ServerPlayer p&&denied(p,e.getPos()))e.setCanceled(true);}
    @SubscribeEvent public static void using(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p.isCreative())return;
        var id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(e.getLevel().getBlockState(e.getPos()).getBlock());
        if(id!=null&&id.getNamespace().equals("immersive_furniture")&&id.getPath().equals("artisans_workstation")){e.setCanceled(true);p.displayClientMessage(net.minecraft.network.chat.Component.literal("家具请到32号朝夕商行购买。"),true);return;}
        var item=e.getItemStack().getItem();boolean edits=item instanceof net.minecraft.world.item.BlockItem||item instanceof net.minecraft.world.item.BucketItem||item instanceof net.minecraft.world.item.DiggerItem||item instanceof net.minecraft.world.item.FlintAndSteelItem||item instanceof net.minecraft.world.item.FireChargeItem||item instanceof net.minecraft.world.item.BoneMealItem;
        // Check both the clicked support and final placement position; a seed must
        // not bypass a plot boundary by clicking its neighbour. Wilderness remains
        // editable without any purchased town property.
        boolean plants=e.getLevel().getBlockState(e.getPos()).getBlock() instanceof net.minecraft.world.level.block.BushBlock;
        var placement=new net.minecraft.world.item.context.BlockPlaceContext(p,e.getHand(),e.getItemStack(),e.getHitVec());
        if((HousingService.isOwnedPosition(p.serverLevel(),e.getPos())&&!HousingService.mayDecorate(p,e.getPos()))||((edits||plants)&&denied(p,e.getPos()))||(item instanceof net.minecraft.world.item.BlockItem&&denied(p,placement.getClickedPos())))e.setCanceled(true);
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
        for(var plot:PlotCatalog.ALL){var t=HousingService.home(server,plot.id());if(!t.getBoolean("Preparing"))continue;
        try{HousingService.finish(server.overworld(),BlockPos.of(t.getLong("Origin")),HousingService.blueprint(server.overworld(),plot.id()),t);}catch(Exception ignored){}}
    }
    @SubscribeEvent public static void animalUse(PlayerInteractEvent.EntityInteract e){if(e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof net.minecraft.world.entity.animal.Animal&&HousingService.ownedAt(p.serverLevel(),e.getTarget().blockPosition())!=null&&denied(p,e.getTarget().blockPosition()))e.setCanceled(true);}
    @SubscribeEvent public static void preciseAnimalUse(PlayerInteractEvent.EntityInteractSpecific e){if(e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof net.minecraft.world.entity.animal.Animal&&HousingService.ownedAt(p.serverLevel(),e.getTarget().blockPosition())!=null&&denied(p,e.getTarget().blockPosition()))e.setCanceled(true);}
    @SubscribeEvent public static void mobGrief(net.minecraftforge.event.entity.EntityMobGriefingEvent e){if(e.getEntity().level() instanceof ServerLevel l&&(town(l,e.getEntity().blockPosition())||HousingService.isOwnedPosition(l,e.getEntity().blockPosition())))e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);}
    @SubscribeEvent public static void attackDecor(net.minecraftforge.event.entity.player.AttackEntityEvent e){if(e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof net.minecraft.world.entity.decoration.HangingEntity&&denied(p,e.getTarget().blockPosition()))e.setCanceled(true);}
    @SubscribeEvent public static void animalAttack(net.minecraftforge.event.entity.living.LivingAttackEvent e){if(e.getEntity() instanceof net.minecraft.world.entity.animal.Animal&&e.getEntity().level() instanceof ServerLevel l&&HousingService.ownedAt(l,e.getEntity().blockPosition())!=null){var attacker=e.getSource().getEntity();if(!(attacker instanceof ServerPlayer p)||denied(p,e.getEntity().blockPosition()))e.setCanceled(true);}}
    @SubscribeEvent public static void pickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent e){if(e.getEntity() instanceof ServerPlayer p&&HousingService.isOwnedPosition(p.serverLevel(),e.getItem().blockPosition())&&!HousingService.mayDecorate(p,e.getItem().blockPosition())&&!p.isCreative())e.setCanceled(true);}
}
