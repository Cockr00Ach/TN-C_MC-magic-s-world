package com.tnc.tnc.life.pasture;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Author's island is excluded; normal creature budgets and a shared eight per chunk cap apply. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class PastureEcology {
    public static boolean canSpawn(EntityType<PastureAnimal> type,LevelAccessor accessor,MobSpawnType reason,BlockPos pos,RandomSource random){
        if(!(accessor instanceof ServerLevelAccessor))return false;
        // WorldGenRegion must read its own generating chunks. Asking its backing
        // ServerLevel for that same chunk waits for the worker currently here.
        if(!accessor.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)||accessor.getRawBrightness(pos,0)<8)return false;
        // Saved property data and live entity counts belong to the server thread.
        // Newly generating wilderness has neither player property nor live mobs.
        if(!(accessor instanceof ServerLevel level))return true;
        if(TownProtection.hazard(level,pos))return false;
        if(level==level.getServer().overworld()){BlockPos town=com.tnc.tnc.world.SkyIslandSavedData.get(level).anchorPos("CENTER");if(town!=null){long dx=(long)pos.getX()-town.getX(),dz=(long)pos.getZ()-town.getZ();if(dx*dx+dz*dz<=900L*900L)return false;}}
        int x=(pos.getX()>>4)<<4,z=(pos.getZ()>>4)<<4;AABB area=new AABB(x,level.getMinBuildHeight(),z,x+16,level.getMaxBuildHeight(),z+16);
        return level.getEntitiesOfClass(Animal.class,area,animal->{var id=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(animal.getType());return id!=null&&id.getNamespace().equals(TNMod.MODID);}).size()<8;
    }
    @SubscribeEvent public static void protectOwnedAnimal(LivingAttackEvent event){if(event.getEntity() instanceof PastureAnimal animal&&animal.owner()!=null&&event.getSource().getEntity() instanceof ServerPlayer player&&!animal.owner().equals(player.getUUID())&&!player.isCreative())event.setCanceled(true);}
    @SubscribeEvent public static void readExistingBellwool(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event){
        if(event.getEntity() instanceof ServerPlayer player&&event.getTarget() instanceof com.tnc.tnc.life.fauna.BellwoolSheepEntity sheep&&event.getItemStack().getItem() instanceof PastureBookItem book){
            event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            if(!TownProtection.denied(player,sheep.blockPosition())&&(sheep.caretaker()==null||sheep.caretaker().equals(player.getUUID())||player.isCreative()))book.showBellwool(player,event.getItemStack(),sheep,event.getHand());
        }
    }
}
