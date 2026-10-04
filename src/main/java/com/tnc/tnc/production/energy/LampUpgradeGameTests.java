package com.tnc.tnc.production.energy;

import com.mojang.authlib.GameProfile;
import com.tnc.tnc.life.botanical.BotanicalContent;
import com.tnc.tnc.life.pasture.PastureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class LampUpgradeGameTests {
    @GameTest(template="building_test_empty",batch="lamp_upgrades",timeoutTicks=30)
    public static void savingCrystalEightLitTicksAndRemovalKeepExactTime(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, EnergyContent.WORK_LAMP.defaultBlockState());
        var lamp = (EnergyBlockEntity)level.getBlockEntity(pos);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "lamp_upgrade"));
        lamp.claim(player.getUUID());
        ItemStack crystal = new ItemStack(BotanicalContent.PRODUCTS.get("energy_saving_crystal"));
        lamp.interact(player, crystal, false);
        h.assertTrue(crystal.isEmpty() && lamp.crystalTicks() == 12000, "Installation moves one actual crystal into the lamp");
        var initial = lamp.saveWithoutMetadata(); initial.putString("Mode", "ALWAYS"); lamp.load(initial); lamp.addEnergy(1);
        for (int i = 0; i < 8; i++) {
            EnergyBlockEntity.serverTick(level, pos, level.getBlockState(pos), lamp);
            h.assertTrue(level.getBlockState(pos).getValue(EnergyBlock.LIT), "One real FE lights all eight upgraded ticks");
        }
        h.assertTrue(lamp.energy() == 0 && lamp.crystalTicks() == 11992, "Eight active ticks debit eight lifetime ticks and exactly one FE");
        EnergyBlockEntity.serverTick(level, pos, level.getBlockState(pos), lamp);
        h.assertTrue(!level.getBlockState(pos).getValue(EnergyBlock.LIT) && lamp.crystalTicks() == 11992,
                "No FE means no light and no lifetime debit");
        lamp.load(lamp.saveWithoutMetadata());
        ItemStack portable = new ItemStack(EnergyContent.WORK_LAMP_ITEM); lamp.writePortableState(portable);
        h.assertTrue(ItemStack.of(portable.getTagElement("BlockEntityTag").getCompound("LampCrystal")).getTag().getInt("LoadedTicks") == 11992,
                "Picked-up lamp retains crystal remaining time");
        lamp.interact(player, ItemStack.EMPTY, true);
        h.assertTrue(lamp.crystalTicks() == 0, "Detachment removes the real upgrade from the machine");
        ItemStack returned = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(BotanicalContent.PRODUCTS.get("energy_saving_crystal"))) returned = s;
        }
        h.assertTrue(EnergyBlockEntity.savingTicks(returned) == 11992, "Returned crystal retains exact remaining loaded ticks");
        h.succeed();
    }
    @GameTest(template="building_test_empty",batch="lamp_upgrades",timeoutTicks=30)
    public static void focusLensActuallyRaisesBlockLightAndCanBeDetached(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, EnergyContent.WORK_LAMP.defaultBlockState());
        var lamp = (EnergyBlockEntity)level.getBlockEntity(pos);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "focus_upgrade"));
        lamp.claim(player.getUUID()); var settings = lamp.saveWithoutMetadata(); settings.putString("Mode", "ALWAYS"); lamp.load(settings); lamp.addEnergy(10);
        EnergyBlockEntity.serverTick(level, pos, level.getBlockState(pos), lamp);
        h.assertTrue(level.getBlockState(pos).getLightEmission(level, pos) == 12, "Base lamp emits real light twelve");
        lamp.interact(player, new ItemStack(PastureRegistry.item("focus_lens")), false);
        h.assertTrue(level.getBlockState(pos).getLightEmission(level, pos) == 15, "Mirror scale lens changes actual block emission to fifteen");
        lamp.load(lamp.saveWithoutMetadata());
        lamp.interact(player, ItemStack.EMPTY, true);
        h.assertTrue(!lamp.hasFocusLens() && level.getBlockState(pos).getLightEmission(level, pos) == 12,
                "Removing the lens restores actual base emission");
        h.succeed();
    }
}
