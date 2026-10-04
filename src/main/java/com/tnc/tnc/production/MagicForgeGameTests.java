package com.tnc.tnc.production;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class MagicForgeGameTests {
    private static BlockPos site(GameTestHelper helper) { return helper.absolutePos(new BlockPos(3, 3, 3)); }

    private static void clear(GameTestHelper helper, BlockPos core) {
        var level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(core.offset(-3, -2, -3), core.offset(3, 2, 3)))
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    public static MagicForgeBlockEntity build(GameTestHelper helper, BlockPos core, Direction front) {
        var level = helper.getLevel();
        for (int i = 0; i < ForgeStructure.CELL_COUNT; i++) {
            BlockPos pos = ForgeStructure.at(core, front, i);
            var state = switch (ForgeStructure.expected(i)) {
                case BRICK -> MagicForgeContent.FIREBRICK.defaultBlockState();
                case FRAME -> MagicForgeContent.COPPER_FRAME.defaultBlockState();
                case INPUT -> MagicForgeContent.INPUT_PORT.defaultBlockState();
                case OUTPUT -> MagicForgeContent.OUTPUT_PORT.defaultBlockState();
                case CORE -> MagicForgeContent.FORGE.defaultBlockState().setValue(MagicForgeBlock.FACING, front);
                case HEART -> MagicForgeContent.HEART.defaultBlockState();
                case INJECTOR -> MagicForgeContent.INJECTOR.defaultBlockState()
                        .setValue(ForgeInjectorBlock.FACING, front.getOpposite());
                case EXHAUST -> MagicForgeContent.EXHAUST.defaultBlockState();
                case AIR -> Blocks.AIR.defaultBlockState();
            };
            level.setBlockAndUpdate(pos, state);
        }
        return (MagicForgeBlockEntity) level.getBlockEntity(core);
    }

    private static void coil(MagicForgeBlockEntity forge, GameTestHelper helper) {
        int index = -1;
        for (int i = 0; i < forge.recipes().size(); i++)
            if (forge.recipes().get(i).getId().getPath().equals("mana_copper_coil_forging")) index = i;
        helper.assertTrue(index >= 0 && forge.chooseRecipe(index), "Coil is a real data recipe selectable in the four-slot forge");
        forge.setItem(0, new ItemStack(Items.COPPER_INGOT, 2));
        forge.setItem(1, new ItemStack(Items.AMETHYST_SHARD));
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void fourFacingsAndExactTwentySevenCells(GameTestHelper helper) {
        BlockPos core = site(helper);
        for (Direction front : Direction.Plane.HORIZONTAL) {
            clear(helper, core);
            MagicForgeBlockEntity forge = build(helper, core, front);
            helper.assertTrue(forge.formed(), "Three levels and correct injector face form toward " + front);
            int real = 0;
            for (int i = 0; i < ForgeStructure.CELL_COUNT; i++)
                if (ForgeStructure.expected(i) != ForgeStructure.Part.AIR) real++;
            helper.assertTrue(real == 27, "Four materials fill all twenty-seven cells");
            BlockPos chamber = ForgeStructure.at(core, front, 1, 1, 1);
            helper.assertTrue(helper.getLevel().getBlockState(chamber).is(MagicForgeContent.HEART), "Central cell is the mana heart");
        }
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void missingWrongFacingAndNearbyIncompleteCore(GameTestHelper helper) {
        BlockPos core = site(helper);
        var forge = build(helper, core, Direction.NORTH);
        BlockPos injector = ForgeStructure.at(core, Direction.NORTH, 1, 0, 1);
        helper.getLevel().setBlockAndUpdate(injector,
                MagicForgeContent.INJECTOR.defaultBlockState().setValue(ForgeInjectorBlock.FACING, Direction.NORTH));
        helper.assertTrue(!forge.formed() && forge.report().code(ForgeStructure.index(1, 0, 1))
                == ForgeStructure.WRONG_BLOCK, "Legacy injector cannot replace masonry");
        helper.getLevel().setBlockAndUpdate(injector,
                MagicForgeContent.FIREBRICK.defaultBlockState());
        BlockPos chamber = ForgeStructure.at(core, Direction.NORTH, 1, 1, 1);
        helper.getLevel().setBlockAndUpdate(chamber, Blocks.STONE.defaultBlockState());
        helper.assertTrue(!forge.formed() && forge.report().code(ForgeStructure.index(1, 1, 1))
                == ForgeStructure.WRONG_BLOCK, "Ordinary stone cannot replace the mana heart");
        helper.getLevel().setBlockAndUpdate(chamber, MagicForgeContent.HEART.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(core.offset(3, 0, 0),
                MagicForgeContent.FORGE.defaultBlockState().setValue(MagicForgeBlock.FACING, Direction.WEST));
        helper.assertTrue(forge.formed(), "Incomplete nearby core cannot falsely claim shared parts");
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void brokenFrameJustBeforeCompletionNeverConsumes(GameTestHelper helper) {
        BlockPos core = site(helper);
        var forge = build(helper, core, Direction.NORTH);
        coil(forge, helper);
        forge.addCharge(40);
        for (int i = 0; i < 79; i++) MagicForgeBlockEntity.serverTick(helper.getLevel(), core,
                helper.getLevel().getBlockState(core), forge);
        helper.assertTrue(forge.progress() == 79 && forge.getItem(0).getCount() == 2,
                "The work really reached its last tick with inputs intact");
        BlockPos frame = ForgeStructure.at(core, Direction.NORTH, 1, 0, 0);
        helper.getLevel().setBlockAndUpdate(frame, Blocks.AIR.defaultBlockState());
        MagicForgeBlockEntity.serverTick(helper.getLevel(), core, helper.getLevel().getBlockState(core), forge);
        helper.assertTrue(forge.getItem(0).getCount() == 2 && forge.getItem(1).getCount() == 1
                && forge.charge() == 40 && forge.getItem(4).isEmpty(),
                "Breaking any frame pauses before spending mana or items, even on completion tick");
        helper.getLevel().setBlockAndUpdate(frame, MagicForgeContent.FIREBRICK.defaultBlockState());
        for (int i = 0; i < 2; i++) MagicForgeBlockEntity.serverTick(helper.getLevel(), core,
                helper.getLevel().getBlockState(core), forge);
        helper.assertTrue(forge.getItem(4).is(MagicForgeContent.MANA_COPPER_COIL)
                && forge.charge() == 0 && forge.getItem(0).isEmpty(), "Repair resumes one atomic craft");
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void fullOutputStopsWorkAndOldSaveMigrates(GameTestHelper helper) {
        BlockPos core = site(helper);
        var forge = build(helper, core, Direction.NORTH);
        coil(forge, helper);
        forge.addCharge(40);
        forge.setItem(4, new ItemStack(MagicForgeContent.MANA_COPPER_COIL, 64));
        for (int i = 0; i < 81; i++) MagicForgeBlockEntity.serverTick(helper.getLevel(), core,
                helper.getLevel().getBlockState(core), forge);
        helper.assertTrue(forge.getItem(4).getCount() == 64 && forge.getItem(0).getCount() == 2
                && forge.charge() == 40, "Output overflow pauses rather than consuming ingredients");

        NonNullList<ItemStack> old = NonNullList.withSize(4, ItemStack.EMPTY);
        old.set(0, new ItemStack(Items.COPPER_INGOT, 2));
        old.set(1, new ItemStack(Items.AMETHYST_SHARD));
        old.set(2, new ItemStack(MagicForgeContent.MANA_COPPER_COIL));
        old.set(3, new ItemStack(TNMod.MANA_ROOT_CORE.get()));
        CompoundTag saved = new CompoundTag();
        ContainerHelper.saveAllItems(saved, old);
        saved.putInt("Charge", 73);
        var migrated = new MagicForgeBlockEntity(core, helper.getLevel().getBlockState(core));
        migrated.load(saved);
        helper.assertTrue(migrated.getItem(0).getCount() == 2 && migrated.getItem(1).getCount() == 1
                && migrated.getItem(2).is(TNMod.MANA_ROOT_CORE.get())
                && migrated.getItem(4).is(MagicForgeContent.MANA_COPPER_COIL)
                && migrated.charge() == 73, "Legacy slots 0/1/2 migrate to 0/1/4 and retain Charge");
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void cachedPortHandlesCannotStealAfterBreakOrCoreReplacement(GameTestHelper helper) {
        BlockPos core = site(helper);
        var forge = build(helper, core, Direction.NORTH);
        var player = AdventureGameTests.player(helper);
        forge.setOwner(player.getUUID());
        BlockPos inputPos = ForgeStructure.at(core, Direction.NORTH, 1, 1, 0);
        BlockPos outputPos = ForgeStructure.at(core, Direction.NORTH, 0, 2, 1);
        var inputBE = (ForgePortBlockEntity) helper.getLevel().getBlockEntity(inputPos);
        var outputBE = (ForgePortBlockEntity) helper.getLevel().getBlockEntity(outputPos);
        IItemHandler input = inputBE.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).orElse(null);
        IItemHandler output = outputBE.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
        helper.assertTrue(input != null && output != null, "Two side input and bottom output use real capabilities");
        helper.assertTrue(input.insertItem(0, new ItemStack(Items.COPPER_INGOT, 2), false).isEmpty(),
                "The sided input proxies into the unique core");
        forge.setItem(4, new ItemStack(MagicForgeContent.MANA_COPPER_COIL));
        BlockPos frame = ForgeStructure.at(core, Direction.NORTH, 1, 0, 0);
        helper.getLevel().setBlockAndUpdate(frame, Blocks.AIR.defaultBlockState());
        helper.assertTrue(output.extractItem(0, 1, false).isEmpty()
                && input.insertItem(1, new ItemStack(Items.AMETHYST_SHARD), false).getCount() == 1,
                "Cached capability cannot transfer while any structural block is missing");
        helper.getLevel().setBlockAndUpdate(frame, MagicForgeContent.FIREBRICK.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(core, Blocks.AIR.defaultBlockState());
        var replacement = build(helper, core, Direction.NORTH);
        replacement.setOwner(player.getUUID());
        replacement.setItem(4, new ItemStack(MagicForgeContent.MANA_COPPER_COIL));
        helper.assertTrue(output.extractItem(0, 1, false).isEmpty(),
                "Old captured handler cannot extract from a newly placed core at the same coordinates");
        IItemHandler rebound = outputBE.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
        helper.assertTrue(rebound != null && rebound.extractItem(0, 1, false).is(MagicForgeContent.MANA_COPPER_COIL),
                "Fresh capability binds to the new core and resumes after repair");
        replacement.setItem(4, new ItemStack(MagicForgeContent.MANA_COPPER_COIL));
        helper.getLevel().setBlockAndUpdate(outputPos, Blocks.AIR.defaultBlockState());
        helper.assertTrue(rebound.extractItem(0, 1, false).isEmpty()
                && replacement.getItem(4).getCount() == 1,
                "Old cached handler cannot extract after its port BE was removed");
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 200)
    public static void realHoppersEnterSidePortAndCollectBelow(GameTestHelper helper) {
        BlockPos core = site(helper);
        var level = helper.getLevel();
        var forge = build(helper, core, Direction.NORTH);
        forge.setOwner(AdventureGameTests.player(helper).getUUID());
        coil(forge, helper);
        forge.clearContent();
        BlockPos input = ForgeStructure.at(core, Direction.NORTH, 1, 1, 0);
        BlockPos feederPos = input.east();
        BlockPos collectorPos = ForgeStructure.at(core, Direction.NORTH, 0, 2, 1).below();
        level.setBlockAndUpdate(feederPos, Blocks.HOPPER.defaultBlockState()
                .setValue(HopperBlock.FACING, Direction.WEST));
        level.setBlockAndUpdate(collectorPos, Blocks.HOPPER.defaultBlockState()
                .setValue(HopperBlock.FACING, Direction.DOWN));
        var feeder = (HopperBlockEntity) level.getBlockEntity(feederPos);
        var collector = (HopperBlockEntity) level.getBlockEntity(collectorPos);
        feeder.setItem(0, new ItemStack(Items.COPPER_INGOT, 2));
        feeder.setItem(1, new ItemStack(Items.AMETHYST_SHARD));
        helper.runAfterDelay(55, () -> {
            helper.assertTrue(feeder.isEmpty() && forge.getItem(0).getCount() == 2
                    && forge.getItem(1).getCount() == 1,
                    "Real side hopper routes two different materials into separate generic slots");
            forge.addCharge(40);
            helper.runAfterDelay(100, () -> {
                int coils = 0;
                for (int i = 0; i < collector.getContainerSize(); i++)
                    if (collector.getItem(i).is(MagicForgeContent.MANA_COPPER_COIL))
                        coils += collector.getItem(i).getCount();
                helper.assertTrue(coils == 1 && forge.getItem(4).isEmpty() && forge.charge() == 0,
                        "Bottom hopper collects exactly one finished coil through output port");
                helper.succeed();
            });
        });
    }
}
