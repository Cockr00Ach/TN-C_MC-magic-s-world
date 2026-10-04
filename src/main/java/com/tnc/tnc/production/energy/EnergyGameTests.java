package com.tnc.tnc.production.energy;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.life.ecology.EcologyContent;
import com.tnc.tnc.life.OriginalCrops;
import com.tnc.tnc.life.ecology.VerdantVeinBlock;
import com.tnc.tnc.life.ecology.VerdantVeinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class EnergyGameTests {
    private static EnergyBlockEntity at(GameTestHelper h, BlockPos relative) {
        return (EnergyBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(relative));
    }

    @GameTest(template="building_test_empty",batch="energy",timeoutTicks=30)
    public static void networkAndExternalExtractionShareOneBudget(GameTestHelper h) {
        var level = h.getLevel();
        var owner = UUID.randomUUID();
        var source = h.absolutePos(new BlockPos(1, 2, 1));
        var wire = h.absolutePos(new BlockPos(2, 2, 1));
        var lamp = h.absolutePos(new BlockPos(3, 2, 1));
        level.setBlockAndUpdate(source, EnergyContent.GENERATOR.defaultBlockState());
        level.setBlockAndUpdate(wire, EnergyContent.CABLE.defaultBlockState());
        level.setBlockAndUpdate(lamp, EnergyContent.WORK_LAMP.defaultBlockState());
        var generator = (EnergyBlockEntity) level.getBlockEntity(source);
        generator.claim(owner); at(h, new BlockPos(2, 2, 1)).claim(owner);
        at(h, new BlockPos(3, 2, 1)).claim(owner);
        generator.addEnergy(100);
        var side = generator.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(IllegalStateException::new);
        h.assertTrue(side.extractEnergy(50, true) == 20 && generator.energy() == 100,
                "Simulated extraction cannot debit energy or the output budget");
        var result = EnergyNetwork.distribute(level, generator, 100);
        h.assertTrue(result.moved() == 20 && generator.energy() == 80
                        && at(h, new BlockPos(3, 2, 1)).energy() == 20,
                "One cable route transfers exactly 20 FE without creating energy");
        h.assertTrue(side.extractEnergy(20, true) == 0 && side.extractEnergy(20, false) == 0,
                "External FE output cannot bypass the network's shared 20 FE/t budget");
        var saved = generator.saveWithoutMetadata();
        h.assertTrue(saved.getInt("Energy") == 80 && saved.getInt("OutputThisTick") == 20,
                "Saved state retains both the actual FE and this tick's output budget");
        h.succeed();
    }

    @GameTest(template="building_test_empty",batch="energy",timeoutTicks=30)
    public static void foreignNeighborCannotUseOrCacheEnergyCapability(GameTestHelper h) {
        var level = h.getLevel();
        var first = h.absolutePos(new BlockPos(1, 2, 1));
        var neighbor = first.east();
        level.setBlockAndUpdate(first, EnergyContent.GENERATOR.defaultBlockState());
        level.setBlockAndUpdate(neighbor, EnergyContent.BATTERY.defaultBlockState());
        var source = (EnergyBlockEntity) level.getBlockEntity(first);
        var other = (EnergyBlockEntity) level.getBlockEntity(neighbor);
        var mine = UUID.randomUUID(); source.claim(mine); other.claim(UUID.randomUUID());
        source.addEnergy(100);
        h.assertTrue(!source.getCapability(ForgeCapabilities.ENERGY, Direction.EAST).isPresent(),
                "A different owner's adjacent device cannot see an FE interface");
        level.setBlockAndUpdate(neighbor, Blocks.AIR.defaultBlockState());
        var cached = source.getCapability(ForgeCapabilities.ENERGY, Direction.EAST).orElseThrow(IllegalStateException::new);
        level.setBlockAndUpdate(neighbor, EnergyContent.BATTERY.defaultBlockState());
        ((EnergyBlockEntity) level.getBlockEntity(neighbor)).claim(UUID.randomUUID());
        h.assertTrue(cached.extractEnergy(20, false) == 0 && source.energy() == 100,
                "A previously cached FE handle is rechecked after its neighbor changes");
        h.assertTrue(!source.getCapability(ForgeCapabilities.ENERGY, null).isPresent(),
                "Unsided FE access cannot bypass neighbor ownership checks");
        h.succeed();
    }

    @GameTest(template="building_test_empty",batch="energy",timeoutTicks=30)
    public static void movingDeviceKeepsChargeButNotPriorOwner(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().setBlockAndUpdate(pos, EnergyContent.GENERATOR.defaultBlockState());
        var source = (EnergyBlockEntity) h.getLevel().getBlockEntity(pos);
        source.claim(UUID.randomUUID()); source.addMana(37); source.addEnergy(123);
        var dropped = new ItemStack(EnergyContent.GENERATOR_ITEM);
        source.writePortableState(dropped);
        var moved = new EnergyBlockEntity(pos, EnergyContent.GENERATOR.defaultBlockState());
        moved.load(dropped.getTagElement("BlockEntityTag"));
        h.assertTrue(moved.mana() == 37 && moved.energy() == 123 && moved.owner() == null,
                "Pickup carries the stored power but releases the old landowner claim");
        moved.claim(AdventureGameTests.player(h).getUUID());
        h.assertTrue(moved.owner() != null && moved.energy() == 123,
                "Legal placement assigns a new owner without minting or losing stored power");
        h.succeed();
    }

    @GameTest(template="building_test_empty",batch="energy",timeoutTicks=50)
    public static void matureOwnedPlantFeedsRealGeneratorStorage(GameTestHelper h) {
        var level = h.getLevel();
        var generatorPos = h.absolutePos(new BlockPos(2, 2, 2));
        var plantPos = generatorPos.east();
        level.setBlockAndUpdate(plantPos.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(generatorPos, EnergyContent.GENERATOR.defaultBlockState());
        level.setBlockAndUpdate(plantPos, EcologyContent.VERDANT.defaultBlockState()
                .setValue(VerdantVeinBlock.AGE, 3));
        level.setBlockAndUpdate(plantPos.above(), Blocks.STONE.defaultBlockState());
        var owner = UUID.randomUUID();
        var generator = (EnergyBlockEntity) level.getBlockEntity(generatorPos);
        generator.claim(owner);
        var plant = (VerdantVeinBlockEntity) level.getBlockEntity(plantPos);
        var seeded = plant.saveWithoutMetadata();
        seeded.putUUID("Owner", owner); seeded.putInt("Mana", 40);
        plant.load(seeded);
        h.runAfterDelay(21, () -> {
            int drawn = generator.energy() / 5 + generator.mana();
            h.assertTrue(drawn >= 1 && drawn <= 2 && generator.energy() > 0,
                    "The mature owned plant sends only real debited mana once per second");
            int newMana = plant.storedMana() + drawn - 40;
            h.assertTrue(newMana >= 0 && newMana <= 2,
                    "Plant storage plus converted mana differs only by at most two actual one-second generation ticks");
            h.succeed();
        });
    }

    @GameTest(template="building_test_empty",batch="energy",timeoutTicks=30)
    public static void lampSpendsExactlyOneFeForFourLitTicks(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, EnergyContent.WORK_LAMP.defaultBlockState());
        var lamp = (EnergyBlockEntity) level.getBlockEntity(pos);
        lamp.claim(UUID.randomUUID());
        lamp.addEnergy(1);
        var settings = lamp.saveWithoutMetadata();
        settings.putString("Mode", "ALWAYS");
        lamp.load(settings);
        for (int i = 0; i < 4; i++) {
            EnergyBlockEntity.serverTick(level, pos, level.getBlockState(pos), lamp);
            h.assertTrue(level.getBlockState(pos).getValue(EnergyBlock.LIT),
                    "One FE must illuminate the full four-tick work cycle");
        }
        h.assertTrue(lamp.energy() == 0, "The FE must be debited on activation");
        EnergyBlockEntity.serverTick(level, pos, level.getBlockState(pos), lamp);
        h.assertTrue(!level.getBlockState(pos).getValue(EnergyBlock.LIT),
                "The lamp must turn dark after the paid cycle ends");
        h.succeed();
    }

    @GameTest(template="building_test_empty",batch="energy",timeoutTicks=30)
    public static void cutCableStopsDeliveryWithoutDeletingCharge(GameTestHelper h) {
        var level = h.getLevel();
        var generatorPos = h.absolutePos(new BlockPos(1, 2, 1));
        var cablePos = generatorPos.east();
        var lampPos = cablePos.east();
        level.setBlockAndUpdate(generatorPos, EnergyContent.GENERATOR.defaultBlockState());
        level.setBlockAndUpdate(cablePos, EnergyContent.CABLE.defaultBlockState());
        level.setBlockAndUpdate(lampPos, EnergyContent.WORK_LAMP.defaultBlockState());
        var owner = UUID.randomUUID();
        var source = (EnergyBlockEntity) level.getBlockEntity(generatorPos);
        source.claim(owner);
        ((EnergyBlockEntity) level.getBlockEntity(cablePos)).claim(owner);
        ((EnergyBlockEntity) level.getBlockEntity(lampPos)).claim(owner);
        source.addEnergy(100);
        level.setBlockAndUpdate(cablePos, Blocks.AIR.defaultBlockState());
        h.assertTrue(EnergyNetwork.distribute(level, source, 20).moved() == 0
                        && source.energy() == 100
                        && ((EnergyBlockEntity) level.getBlockEntity(lampPos)).energy() == 0,
                "Breaking the loaded wire leaves all stored FE in place and turns off delivery");
        h.succeed();
    }

    @GameTest(template="building_test_empty",batch="energy",timeoutTicks=40)
    public static void paperPressRequiresAndConsumesRealReedsWaterAndFe(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, EnergyContent.PAPER_PRESS.defaultBlockState());
        var press = (EnergyBlockEntity) level.getBlockEntity(pos);
        press.claim(UUID.randomUUID());
        press.addEnergy(80);
        var input = press.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        var reeds = new ItemStack(OriginalCrops.TIDE_REED_STEM, 2);
        h.assertTrue(input.insertItem(0, reeds, true).isEmpty()
                        && press.saveWithoutMetadata().getInt("Reeds") == 0,
                "A simulated hopper delivery cannot create real reeds");
        h.assertTrue(input.insertItem(0, reeds, false).isEmpty(),
                "Top item input accepts two real reeds");
        for (int tick = 0; tick < 80; tick++)
            EnergyBlockEntity.serverTick(level, pos, level.getBlockState(pos), press);
        h.assertTrue(press.energy() == 80 && press.saveWithoutMetadata().getInt("Reeds") == 2
                        && press.saveWithoutMetadata().getInt("Paper") == 0,
                "No water means no paper, no lost reeds, and no FE charge");
        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
        press.interact(AdventureGameTests.player(h), waterBucket, false);
        h.assertTrue(waterBucket.isEmpty() && press.saveWithoutMetadata().getInt("WaterUnits") == 8,
                "A real water bucket fills exactly eight measured units");
        for (int tick = 0; tick < 80; tick++)
            EnergyBlockEntity.serverTick(level, pos, level.getBlockState(pos), press);
        var finished = press.saveWithoutMetadata();
        h.assertTrue(press.energy() == 0 && finished.getInt("Reeds") == 0
                        && finished.getInt("WaterUnits") == 7 && finished.getInt("Paper") == 3,
                "One finished batch exchanges 2 reeds, 1/8 bucket, and 80 FE for 3 paper");
        var output = press.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(IllegalStateException::new);
        h.assertTrue(output.extractItem(0, 3, true).getCount() == 3
                        && press.saveWithoutMetadata().getInt("Paper") == 3,
                "Simulated extraction leaves all actual paper in the machine");
        h.assertTrue(output.extractItem(0, 3, false).getCount() == 3
                        && press.saveWithoutMetadata().getInt("Paper") == 0,
                "Bottom automation exports only real finished paper");
        h.succeed();
    }

    @GameTest(template="building_test_empty",batch="energy",timeoutTicks=40)
    public static void fullPressOutputStopsWithoutConsumingMaterials(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, EnergyContent.PAPER_PRESS.defaultBlockState());
        var press = (EnergyBlockEntity) level.getBlockEntity(pos);
        press.claim(UUID.randomUUID()); press.addEnergy(80);
        var packed = press.saveWithoutMetadata();
        packed.putInt("Reeds", 2); packed.putInt("WaterUnits", 1); packed.putInt("Paper", 63);
        press.load(packed);
        for (int tick = 0; tick < 90; tick++)
            EnergyBlockEntity.serverTick(level, pos, level.getBlockState(pos), press);
        var still = press.saveWithoutMetadata();
        h.assertTrue(press.energy() == 80 && still.getInt("Reeds") == 2
                        && still.getInt("WaterUnits") == 1 && still.getInt("Paper") == 63,
                "No batch starts if three paper cannot fit in the real output inventory");
        var carry = new ItemStack(EnergyContent.PAPER_PRESS_ITEM);
        press.writePortableState(carry);
        var moved = new EnergyBlockEntity(pos, EnergyContent.PAPER_PRESS.defaultBlockState());
        moved.load(carry.getTagElement("BlockEntityTag"));
        var recovered = moved.saveWithoutMetadata();
        h.assertTrue(moved.owner() == null && moved.energy() == 80
                        && recovered.getInt("Reeds") == 2 && recovered.getInt("WaterUnits") == 1
                        && recovered.getInt("Paper") == 63,
                "Carried press preserves FE, water, raw stock, and finished stock without old land rights");
        h.succeed();
    }

    @GameTest(template="building_test_empty",batch="energy",timeoutTicks=30)
    public static void dismantledMachineRejectsCachedEnergyAndItemHandles(GameTestHelper h) {
        var level = h.getLevel();
        var generatorPos = h.absolutePos(new BlockPos(1, 2, 1));
        var pressPos = h.absolutePos(new BlockPos(3, 2, 1));
        level.setBlockAndUpdate(generatorPos, EnergyContent.GENERATOR.defaultBlockState());
        level.setBlockAndUpdate(pressPos, EnergyContent.PAPER_PRESS.defaultBlockState());
        var owner = UUID.randomUUID();
        var oldGenerator = (EnergyBlockEntity) level.getBlockEntity(generatorPos);
        var oldPress = (EnergyBlockEntity) level.getBlockEntity(pressPos);
        oldGenerator.claim(owner); oldPress.claim(owner);
        oldGenerator.addEnergy(100);
        var stock = oldPress.saveWithoutMetadata();
        stock.putInt("Paper", 3);
        oldPress.load(stock);
        var cachedFe = oldGenerator.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(IllegalStateException::new);
        var cachedPaper = oldPress.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(IllegalStateException::new);
        EnergyContent.GENERATOR.playerWillDestroy(level, generatorPos,
                level.getBlockState(generatorPos), AdventureGameTests.player(h));
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(generatorPos).inflate(1.0))
                .stream().filter(entity -> entity.getItem().is(EnergyContent.GENERATOR_ITEM)).toList();
        h.assertTrue(drops.size() == 1 && drops.get(0).getItem()
                        .getTagElement("BlockEntityTag").getInt("Energy") == 100,
                "Survival dismantling drops one portable copy of the real stored FE");
        level.setBlockAndUpdate(generatorPos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pressPos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(generatorPos, EnergyContent.GENERATOR.defaultBlockState());
        level.setBlockAndUpdate(pressPos, EnergyContent.PAPER_PRESS.defaultBlockState());
        ((EnergyBlockEntity) level.getBlockEntity(generatorPos)).claim(owner);
        ((EnergyBlockEntity) level.getBlockEntity(pressPos)).claim(owner);
        h.assertTrue(cachedFe.extractEnergy(20, false) == 0
                        && cachedPaper.extractItem(0, 3, false).isEmpty(),
                "Old cached capabilities die when the block is removed, even at the same position");
        h.assertTrue(((EnergyBlockEntity) level.getBlockEntity(generatorPos)).energy() == 0,
                "The replacement machine cannot inherit the dismantled machine's FE");
        h.succeed();
    }
}
