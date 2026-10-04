package com.tnc.tnc.adventure;

import com.tnc.tnc.life.OriginalCrops;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class WildSampleResearchGameTests {
    private WildSampleResearchGameTests() {}

    @GameTest(template = "building_test_empty", timeoutTicks = 30)
    public static void firstWildResearchConsumesOnlySampleOnce(GameTestHelper helper) {
        var player = AdventureGameTests.player(helper);
        var profile = AdventureService.profile(player);
        profile.registered = true;
        long before = profile.coins();
        var samples = new ItemStack(OriginalCrops.ROAD_BELL_WILD_SAMPLE, 2);
        helper.assertTrue(WildSampleResearch.claim(player, samples)
                        && samples.getCount() == 1 && profile.coins() == before + 120
                        && profile.hasMilestone("wild_research_road_bell"),
                "First sample is delivered, pays once and preserves other collected samples");
        helper.assertTrue(!WildSampleResearch.claim(player, samples)
                        && samples.getCount() == 1 && profile.coins() == before + 120,
                "Repeating the same research neither consumes a sample nor pays twice");
        var seed = new ItemStack(OriginalCrops.ROAD_BELL_SEED);
        helper.assertTrue(!WildSampleResearch.claim(player, seed) && seed.getCount() == 1,
                "Research never takes the player's only seed");
        helper.succeed();
    }
}
