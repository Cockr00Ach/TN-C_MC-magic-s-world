package com.tnc.tnc.production;

import com.tnc.tnc.life.botanical.BotanicalContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class WorkshopUpgradeGameTests {
    private static MagicForgeBlockEntity coil(GameTestHelper h) {
        var forge = MagicForgeGameTests.build(h, h.absolutePos(new BlockPos(3, 3, 3)), Direction.NORTH);
        int index = -1;
        for (int i = 0; i < forge.recipes().size(); i++)
            if (forge.recipes().get(i).getId().getPath().equals("mana_copper_coil_forging")) index = i;
        h.assertTrue(index >= 0 && forge.chooseRecipe(index), "Real coil recipe exists");
        forge.setItem(0, new ItemStack(Items.COPPER_INGOT, 2));
        forge.setItem(1, new ItemStack(Items.AMETHYST_SHARD));
        forge.addCharge(40);
        return forge;
    }
    private static void tick(GameTestHelper h, MagicForgeBlockEntity forge, int count) {
        for (int i = 0; i < count; i++) MagicForgeBlockEntity.serverTick(h.getLevel(), forge.getBlockPos(), forge.getBlockState(), forge);
    }
    @GameTest(template="building_test_empty",batch="workshop_upgrades",timeoutTicks=30)
    public static void oneUpgradeFinishesAtSixtyFourAndCarriesRemainingUses(GameTestHelper h) {
        var forge = coil(h);
        forge.setItem(5, new ItemStack(BotanicalContent.PRODUCTS.get("hearth_oil")));
        tick(h, forge, 63);
        h.assertTrue(forge.getItem(4).isEmpty() && forge.progress() == 63 && MagicForgeBlockEntity.upgradeUses(forge.getItem(5)) == 10,
                "Upgrade never debits before a complete craft");
        tick(h, forge, 1);
        h.assertTrue(!forge.getItem(4).isEmpty() && forge.charge() == 0 && MagicForgeBlockEntity.upgradeUses(forge.getItem(5)) == 9,
                "One real batch completes at 64 ticks, spends exactly 40 mana and one oil use");
        var saved = forge.saveWithoutMetadata();
        var reload = new MagicForgeBlockEntity(forge.getBlockPos(), forge.getBlockState()); reload.load(saved);
        ItemStack removed = reload.removeItem(5, 1);
        h.assertTrue(MagicForgeBlockEntity.upgradeUses(removed) == 9 && reload.getItem(5).isEmpty(),
                "Reload and removal retain the counter on the actual item");
        reload.setItem(5, removed);
        h.assertTrue(MagicForgeBlockEntity.upgradeUses(reload.getItem(5)) == 9, "Reinstallation cannot reset to ten");
        h.succeed();
    }
    @GameTest(template="building_test_empty",batch="workshop_upgrades",timeoutTicks=30)
    public static void brokenStructureNeverSpendsCondensingShell(GameTestHelper h) {
        var forge = coil(h);
        forge.setItem(5, new ItemStack(BotanicalContent.PRODUCTS.get("condensing_shell")));
        tick(h, forge, 63);
        BlockPos missing = ForgeStructure.at(forge.getBlockPos(), forge.front(), 2, 0, 0);
        h.getLevel().setBlockAndUpdate(missing, Blocks.AIR.defaultBlockState());
        tick(h, forge, 3);
        h.assertTrue(forge.getItem(4).isEmpty() && forge.charge() == 40 && forge.getItem(0).getCount() == 2
                        && MagicForgeBlockEntity.upgradeUses(forge.getItem(5)) == 20,
                "A broken final-tick structure preserves mana, inputs and all twenty shell uses");
        h.succeed();
    }
}
