package com.tnc.tnc.adventure;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class BossCommissionsGameTests {
    private BossCommissionsGameTests() {}

    @GameTest(template = "building_test_empty", batch = "boss_commission", timeoutTicks = 30)
    public static void acceptedRealFightProofPaysOnceAndSurvivesSave(GameTestHelper h) {
        var player = AdventureGameTests.player(h);
        AdventureService.register(player);
        var offer = BossCommissions.OFFERS.get(0);
        var profile = AdventureService.profile(player);
        BossCommissions.recordParticipant(player, offer.entity());
        h.assertTrue(!profile.hasMilestone("special_proof_" + offer.id()),
                "A boss killed before taking the tavern job is not retrospective proof");
        profile.milestones.add("special_accepted_" + offer.id());
        BossCommissions.recordParticipant(player, "minecraft:zombie");
        h.assertTrue(!profile.hasMilestone("special_proof_" + offer.id()),
                "A different enemy cannot complete the named commission");
        BossCommissions.recordParticipant(player, offer.entity());
        h.assertTrue(profile.hasMilestone("special_proof_" + offer.id()),
                "Server-side credited participation creates personal proof");
        h.assertTrue(BossCommissions.settle(player, offer), "The board can pay a completed job");
        long coins = profile.coins();
        long xp = profile.xp();
        h.assertTrue(coins == offer.copper() && xp == offer.xp(),
                "Rewards use the job's specific money and experience");
        h.assertTrue(!BossCommissions.settle(player, offer)
                        && profile.coins() == coins && profile.xp() == xp,
                "Reopening the board cannot duplicate a paid commission");
        var saved = AdventureSavedData.get(player.server);
        var reloaded = AdventureSavedData.load(saved.save(new CompoundTag()));
        h.assertTrue(reloaded.players.get(player.getUUID()).hasMilestone("special_paid_" + offer.id()),
                "Payment remains recorded after a world save/reload");
        h.succeed();
    }
}
