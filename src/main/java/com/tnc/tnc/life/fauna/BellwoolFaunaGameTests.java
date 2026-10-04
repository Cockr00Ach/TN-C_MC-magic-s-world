package com.tnc.tnc.life.fauna;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class BellwoolFaunaGameTests {
    private BellwoolFaunaGameTests() {}

    @GameTest(template = "building_test_empty", batch = "bellwool_fauna", timeoutTicks = 30)
    public static void independentSpeciesHarvestAndSavedCooldown(GameTestHelper h) {
        var level = h.getLevel();
        BellwoolSheepEntity sheep = FaunaRegistry.BELLWOOL_SHEEP.create(level);
        h.assertTrue(sheep != null, "Independent bellwool entity can be created");
        h.assertTrue(!((Object) sheep instanceof Sheep), "Bellwool is a new species, not vanilla sheep metadata");
        h.assertTrue(FaunaRegistry.BELLWOOL_SHEEP != (Object) EntityType.SHEEP,
                "Bellwool has its own registered entity type");
        h.assertTrue("tnc:bellwool_sheep".equals(
                String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(FaunaRegistry.BELLWOOL_SHEEP))),
                "Saved entity type has the intended stable id");
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        sheep.setPos(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
        level.addFreshEntity(sheep);
        BellwoolSheepEntity loaded = null;
        try {
            var player = AdventureGameTests.player(h);
            h.assertTrue(sheep.readyForHarvest(), "Adult living animal begins with harvestable fleece");
            h.assertTrue(sheep.harvest(player), "Shearing living bellwool succeeds");
            h.assertTrue(sheep.isSheared(), "Sheared silhouette syncs to clients");
            h.assertTrue(!sheep.harvest(player), "Second shear does not duplicate fleece");
            int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
                    .filter(entity -> entity.getItem().is(TNMod.RESONANT_FLEECE.get()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            h.assertTrue(drops == 2, "One shear drops precisely two real resonant fleece items");

            CompoundTag saved = new CompoundTag();
            sheep.addAdditionalSaveData(saved);
            loaded = FaunaRegistry.BELLWOOL_SHEEP.create(level);
            h.assertTrue(loaded != null, "Reload fixture exists");
            loaded.readAdditionalSaveData(saved);
            h.assertTrue(loaded.isSheared() && !loaded.readyForHarvest(),
                    "Relog keeps sheared state and two-day cooldown");
            h.assertTrue(loaded.nextHarvestTick() == sheep.nextHarvestTick(),
                    "Persisted cooldown cannot be bypassed by unloading the animal");
            h.succeed();
        } finally {
            sheep.discard();
            if (loaded != null) loaded.discard();
        }
    }

    @GameTest(template = "building_test_empty", batch = "bellwool_fauna", timeoutTicks = 30)
    public static void newSpeciesBreedsTwoDayYoungAndFeltPreventsFreezing(GameTestHelper h) {
        var level = h.getLevel();
        BellwoolSheepEntity parent = FaunaRegistry.BELLWOOL_SHEEP.create(level);
        BellwoolSheepEntity mate = FaunaRegistry.BELLWOOL_SHEEP.create(level);
        h.assertTrue(parent != null && mate != null, "Breeding fixtures exist");
        BellwoolSheepEntity child = null;
        try {
            child = parent.getBreedOffspring(level, mate);
            h.assertTrue(child != null && child.getType() == FaunaRegistry.BELLWOOL_SHEEP,
                    "Offspring is the same independent species");
            parent.finalizeSpawnChildFromBreeding(level, mate, child);
            h.assertTrue(child.getAge() <= -48_000,
                    "Juvenile must grow for two loaded game days");
            h.assertTrue(!parent.canFallInLove() && !mate.canFallInLove(),
                    "Both parents respect a breeding cooldown");

            var player = AdventureGameTests.player(h);
            ItemStack felt = new ItemStack(FaunaRegistry.RESONANT_FELT);
            player.getInventory().offhand.set(0, felt);
            player.setTicksFrozen(100);
            FaunaRegistry.RESONANT_FELT.inventoryTick(felt, level, player, -1, false);
            h.assertTrue(player.getTicksFrozen() == 0,
                    "Two harvested fleeces craft a useful offhand cold wrap");
            h.succeed();
        } finally {
            parent.discard();
            mate.discard();
            if (child != null) child.discard();
        }
    }
}
