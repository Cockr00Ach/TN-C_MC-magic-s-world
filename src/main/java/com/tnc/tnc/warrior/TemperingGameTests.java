package com.tnc.tnc.warrior;

import com.tnc.tnc.adventure.AdventureGameTests;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class TemperingGameTests {
    @GameTest(template="building_test_empty", timeoutTicks=40)
    public static void ledgerAndPacketRoundTrip(GameTestHelper h) {
        var p = AdventureGameTests.player(h); var d = new TemperingData(); var id = p.getUUID();
        d.setTalent(id,6); d.enroll(id); d.set(id, 3000);
        h.assertTrue(d.completeGuardDrill(id), "First completion claims reward");
        var loaded = TemperingData.load(d.save(new CompoundTag())); loaded.enroll(id);
        h.assertTrue(loaded.points(id) == 3000 && !loaded.completeGuardDrill(id), "Reload/enroll retains points and first reward claim");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new TemperingPacket(true, 3000,6).encode(buffer);
            var packet = TemperingPacket.decode(buffer);
            h.assertTrue(packet.enrolled() && packet.points() == 3000 && packet.talent()==6, "Packet retains enrollment, talent and points");
        } finally { buffer.release(); }
        h.succeed();
    }
    @GameTest(template="building_test_empty", timeoutTicks=40)
    public static void realDamageUsesReductionButFallDoesNot(GameTestHelper h) {
        var p = AdventureGameTests.player(h); var enemy = EntityType.ZOMBIE.create(h.getLevel());
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(net.minecraft.server.level.ServerPlayer.class, p, 0, "f_8921_");
        p.setHealth(20); p.invulnerableTime = 0;
        p.hurt(h.getLevel().damageSources().mobAttack(enemy), 8);
        float baseline = 20 - p.getHealth();
        h.assertTrue(baseline > 0, "Control attack must deal actual damage");
        TemperingData.get(p.server).setTalent(p.getUUID(),6);
        TemperingData.get(p.server).set(p.getUUID(), 10000);
        p.setHealth(20); p.invulnerableTime = 0;
        p.hurt(h.getLevel().damageSources().mobAttack(enemy), 8);
        h.assertTrue(Math.abs((20-p.getHealth()) - baseline*.75) < .001,
            "Tempering removes 25 percent after difficulty scaling; control=" + baseline + ", health=" + p.getHealth());
        float beforeFall = p.getHealth();
        p.invulnerableTime = 0;
        p.hurt(h.getLevel().damageSources().fall(), 4);
        h.assertTrue(Math.abs(p.getHealth() - (beforeFall-4)) < .001, "Fall damage remains four");
        h.succeed();
    }
    @GameTest(template="building_test_empty", timeoutTicks=100)
    public static void shieldEventTrainingRewardsOnlyOnce(GameTestHelper h) {
        var p = AdventureGameTests.player(h); var enemy = EntityType.ZOMBIE.create(h.getLevel());
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        var data = TemperingData.get(p.server); data.setTalent(p.getUUID(),2); data.enroll(p.getUUID());
        h.assertTrue(TemperingTraining.start(p), "Enrolled survival player can train");
        Runnable block = () -> MinecraftForge.EVENT_BUS.post(new ShieldBlockEvent(p, h.getLevel().damageSources().mobAttack(enemy), 4));
        Runnable counter = () -> MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(enemy, h.getLevel().damageSources().playerAttack(p), 2));
        block.run();
        h.assertTrue(TemperingTraining.blockCount(p) == 1, "Initial shield event counts");
        h.runAfterDelay(15, () -> { block.run(); h.assertTrue(TemperingTraining.blockCount(p) == 2, "Second spaced shield event counts; time=" + p.server.overworld().getGameTime()); });
        h.runAfterDelay(30, () -> { block.run(); h.assertTrue(TemperingTraining.blockCount(p) == 3, "Third shield event counts"); });
        h.runAfterDelay(31, () -> {
            counter.run();
            h.assertTrue(data.points(p.getUUID()) == 120, "First training awards 120");
            h.assertTrue(TemperingTraining.start(p), "Repeat practice allowed");
        });
        h.runAfterDelay(45, block); h.runAfterDelay(60, block); h.runAfterDelay(75, block);
        h.runAfterDelay(76, () -> {
            counter.run();
            h.assertTrue(data.points(p.getUUID()) == 120, "Repeat practice grants no additional points");
            h.succeed();
        });
    }
}
