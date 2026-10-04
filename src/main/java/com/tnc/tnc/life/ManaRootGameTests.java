package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.production.MagicForgeBlockEntity;
import com.tnc.tnc.production.MagicForgeContent;
import com.tnc.tnc.production.MagicForgeGameTests;
import com.tnc.tnc.production.ForgeStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class ManaRootGameTests {
    private ManaRootGameTests() {}

    @GameTest(template = "building_test_empty", batch = "mana_root", timeoutTicks = 30)
    public static void playerGrowsRootAndForgeGetsLessManaThanInvested(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos rootPos = h.absolutePos(new BlockPos(1, 2, 2));
        level.setBlockAndUpdate(rootPos.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(rootPos, TNMod.MANA_ROOT.get().defaultBlockState());
        var player = AdventureGameTests.player(h);
        var magic = MagicStone.getOrNull(player);
        magic.assignDefaultAffinities(3);
        magic.setMana(magic.getMaxMana());
        int before = magic.getMana();
        h.assertTrue(before >= 40, "Test player has enough real mana for four feedings");
        var hit = new BlockHitResult(Vec3.atCenterOf(rootPos), Direction.UP, rootPos, false);
        for (int i = 0; i < 4; i++)
            TNMod.MANA_ROOT.get().use(level.getBlockState(rootPos), level, rootPos,
                    player, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(magic.getMana() == before - 40
                        && level.getBlockState(rootPos).getValue(ManaRoot.RootBlock.FED) == 4,
                "Living root consumes exactly forty points from the player's magic stone");
        var random = net.minecraft.util.RandomSource.create(410);
        for (int i = 0; i < 160; i++)
            ((ManaRoot.RootBlock) TNMod.MANA_ROOT.get()).randomTick(level.getBlockState(rootPos), level, rootPos, random);
        var ripe = level.getBlockState(rootPos);
        h.assertTrue(ripe.getValue(ManaRoot.RootBlock.AGE) == 3,
                "Time allows the charged root to become harvestable");
        var drops = Block.getDrops(ripe, level, rootPos, null);
        h.assertTrue(drops.stream().anyMatch(item -> item.is(TNMod.MANA_ROOT_SEED.get()))
                        && drops.stream().anyMatch(item -> item.is(TNMod.MANA_ROOT_CORE.get())),
                "Breaking a ripe root preserves the seed and its core");

        BlockPos forgePos = rootPos.east(2);
        var forge = MagicForgeGameTests.build(h,forgePos,Direction.NORTH);
        forge.setOwner(player.getUUID());
        var inlet=forgePos;
        var core = new ItemStack(TNMod.MANA_ROOT_CORE.get());
        forge.addCharge(190);
        forge.useRootCore(player, core, inlet);
        h.assertTrue(core.getCount() == 1 && forge.charge() == 190,
                "A nearly full forge refuses the core without eating it");
        // Reset only the fixture's charge; a second adjacent core would share
        // structural cells and correctly fail the new multiblock requirement.
        var empty=forge.saveWithoutMetadata();empty.putInt("Charge",0);forge.load(empty);
        forge.useRootCore(player, core, inlet);
        h.assertTrue(core.isEmpty() && forge.charge() == 30,
                "Only thirty of the forty invested mana returns as portable forge power");
        h.succeed();
    }
}
