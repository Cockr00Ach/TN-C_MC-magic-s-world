package com.tnc.tnc.life.fauna;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Registers the first genuinely separate magical livestock species. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FaunaRegistry {
    public static EntityType<BellwoolSheepEntity> BELLWOOL_SHEEP;
    public static Item BELLWOOL_SPAWN_EGG;
    public static Item RESONANT_FELT;

    private FaunaRegistry() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            if (BELLWOOL_SHEEP != null) return;
            BELLWOOL_SHEEP = EntityType.Builder.of(BellwoolSheepEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.2F).clientTrackingRange(10).build("tnc:bellwool_sheep");
            helper.register(id("bellwool_sheep"), BELLWOOL_SHEEP);
        });
        event.register(Registries.ITEM, helper -> {
            if (BELLWOOL_SPAWN_EGG != null) return;
            BELLWOOL_SPAWN_EGG = new net.minecraftforge.common.ForgeSpawnEggItem(
                    () -> BELLWOOL_SHEEP, 0xD8C9AB, 0x2B6870, new Item.Properties());
            RESONANT_FELT = new ResonantFeltItem();
            helper.register(id("bellwool_sheep_spawn_egg"), BELLWOOL_SPAWN_EGG);
            helper.register(id("resonant_felt"), RESONANT_FELT);
        });
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(BELLWOOL_SHEEP, BellwoolSheepEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void spawnPlacement(SpawnPlacementRegisterEvent event) {
        event.register(BELLWOOL_SHEEP, SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                FaunaRegistry::canSpawnBellwool, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private static boolean canSpawnBellwool(EntityType<BellwoolSheepEntity> type,
                                            LevelAccessor accessor, MobSpawnType reason,
                                            BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!Animal.checkAnimalSpawnRules(type, accessor, reason, pos, random)) return false;
        if (accessor instanceof net.minecraft.world.level.ServerLevelAccessor serverAccessor) {
            ServerLevel level = serverAccessor.getLevel();
            // The authored town and partner interiors never become a breeding-ground.
            if (com.tnc.tnc.home.TownProtection.hazard(level, pos)) return false;
            if (level == level.getServer().overworld()) {
                BlockPos town = com.tnc.tnc.world.SkyIslandSavedData.get(level).anchorPos("CENTER");
                if (town != null) {
                    long dx = (long) pos.getX() - town.getX();
                    long dz = (long) pos.getZ() - town.getZ();
                    if (dx * dx + dz * dz <= 900L * 900L) return false;
                }
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(TNMod.TNC_TAB.getKey())) return;
        event.accept(BELLWOOL_SPAWN_EGG);
        event.accept(RESONANT_FELT);
    }
}
