package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("tncwarriorpractical")
@PrefixGameTestTemplate(false)
public final class WarriorWeaponRankGameTests {
    @GameTest(template = "building_test_empty", timeoutTicks = 60)
    public static void everyBranchCoversAllRanksWithRegisteredWeapons(GameTestHelper helper) {
        var ranks = new EnumMap<WarriorWeaponRanks.Branch, Set<Integer>>(WarriorWeaponRanks.Branch.class);
        for (var weapon : WarriorWeaponRanks.catalogue()) {
            var id = ResourceLocation.fromNamespaceAndPath("tnc", weapon.id());
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id), id + " must be registered");
            var stack = new ItemStack(ForgeRegistries.ITEMS.getValue(id));
            helper.assertTrue(WarriorWeaponRanks.rank(stack) == weapon.rank(), id + " rank must resolve in game");
            helper.assertTrue(WarriorWeaponRanks.branch(stack) == weapon.branch(), id + " branch must resolve in game");
            var family = WarriorWeapons.family(stack);
            helper.assertTrue(family != null && WarriorCombos.forFamily(family).size() == 7,
                    id + " must support all seven ranked slots");
            if (!weapon.id().equals("spear")) {
                helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isPresent(), id + " must have a survival recipe");
            }
            ranks.computeIfAbsent(weapon.branch(), branch -> new HashSet<>()).add(weapon.rank());
        }
        for (var branch : WarriorWeaponRanks.Branch.values()) {
            helper.assertTrue(Set.of(1, 2, 3, 4, 5).equals(ranks.get(branch)), branch + " must cover all five ranks");
        }
        helper.assertTrue(WarriorWeaponRanks.rank(new ItemStack(TNMod.RED_TASSEL_SPEAR.get())) == 3,
                "The red tassel spear is the first spear that can unlock an ultimate");
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void lowRankDoesNotExecuteHigherSlotEvenWithHighTempering(GameTestHelper helper) {
        var player = AdventureGameTests.player(helper);
        try {
            var data = TemperingData.get(player.server);
            data.setTalent(player.getUUID(), 6);
            data.enroll(player.getUUID());
            data.set(player.getUUID(), 10000);
            var slot = WarriorCombos.bySlot(WarriorWeaponFamily.LONGSWORD, 6);
            var state = WarriorComboService.state(player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TNMod.ASHWOOD_JIAN.get()));
            for (var move : slot.sequence()) {
                state.lastInput = Long.MIN_VALUE;
                WarriorComboService.input(player, move);
            }
            helper.assertTrue(!state.cooldowns.containsKey(slot.id()), "Rank 1 cannot execute slot seven");
            state.tracker.clear();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TNMod.DRAGON_SWORD.get()));
            for (var move : slot.sequence()) {
                state.lastInput = Long.MIN_VALUE;
                WarriorComboService.input(player, move);
            }
            helper.assertTrue(state.cooldowns.containsKey(slot.id()), "Rank 5 can execute slot seven");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void ultimateNeedsRankThreeAndStrictlyMoreThanThreeThousand(GameTestHelper helper) {
        var player = AdventureGameTests.player(helper);
        try {
            var data = TemperingData.get(player.server);
            data.setTalent(player.getUUID(), 6);
            data.enroll(player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TNMod.BOW.get()));
            data.set(player.getUUID(), 10000);
            helper.assertTrue(!castUltimate(player, "bow"), "Rank one cannot cast with full tempering");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TNMod.RED_TASSEL_SPEAR.get()));
            data.set(player.getUUID(), 3000);
            helper.assertTrue(!castUltimate(player, "red_tassel_spear"), "Exactly 3000 keeps the ultimate locked");
            data.set(player.getUUID(), 3001);
            helper.assertTrue(castUltimate(player, "red_tassel_spear"), "Rank three and 3001 unlock the ultimate");
            helper.succeed();
        } finally {
            WarriorUltimateService.cancel(player);
            player.discard();
        }
    }

    private static boolean castUltimate(net.minecraft.server.level.ServerPlayer player, String id) {
        long time = player.level().getGameTime();
        for (var move : WarriorMove.parse(WarriorUltimates.forId("tnc:" + id).keys())) {
            if (WarriorUltimateService.input(player, move, time)) return true;
            time += 2;
        }
        return false;
    }
}
