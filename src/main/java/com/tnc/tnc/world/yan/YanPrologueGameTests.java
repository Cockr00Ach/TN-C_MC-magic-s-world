package com.tnc.tnc.world.yan;

import com.tnc.tnc.journey.JourneyData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncyan")
@PrefixGameTestTemplate(false)
public final class YanPrologueGameTests {
    @GameTest(template = "empty", timeoutTicks = 120, batch = "yan_prologue")
    public static void storyArenaAndSecondLaneAreReady(GameTestHelper test) {
        var level = test.getLevel().getServer().getLevel(YanPrologue.WORLD);
        test.assertTrue(level != null, "The prologue dimension loaded");
        test.assertTrue(YanPrologueScript.CARDS.size() == 6, "The prologue contains all six cards");
        test.assertTrue(YanPrologueScript.CARDS.get(1).equals("「看清楚了，我就回来。」"), "The quote is alone");
        test.assertTrue(YanPrologueScript.CARDS.get(5).equals("你握紧法杖，踏上了祭坛。"), "The final card is alone");
        test.assertTrue(YanPrologueScript.REWIND_CARDS.equals(java.util.List.of(
                "你败了，输给了挚友，但身体中的无尽元素不会让你死亡",
                "时间倒流,一切回到酒馆前的那句话，你要再次寻找真相....")), "The rewind uses both requested cards verbatim");
        for (int x : new int[]{0, YanSanctuaryGenerator.LANE_WIDTH}) {
            var arrival = YanPrologue.ENTRY.offset(x, 0, 0);
            level.getChunkAt(arrival);
            test.assertTrue(level.getBlockState(arrival).isAir() && !level.getBlockState(arrival.below()).isAir(),
                    "The fight starts above a solid platform in each lane");
            var altar = YanSanctuaryLayout.ALTAR.offset(x, 0, 0);
            level.getChunkAt(altar);
            test.assertTrue(level.getBlockState(altar).is(YanSanctuary.ALTAR), "Each player sees the same altar");
        }
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 560, batch = "yan_prologue")
    public static void executionHitsThenHoldsDefeatBeforeRewind(GameTestHelper test) {
        var level = test.getLevel().getServer().getLevel(YanPrologue.WORLD);
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "YanPrologueTest")) {
            @Override public boolean hasDisconnected() { return false; }
        };
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override public io.netty.channel.Channel channel() { return channel; }
        };
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(player.server, connection, player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
        };
        var scene = YanPrologue.reserveArena(player, level);
        level.getChunkAt(scene.altar);
        level.getChunkAt(scene.entry);
        var feet = Vec3.atBottomCenterOf(scene.entry);
        player.setPos(feet.x, feet.y, feet.z);
        level.addNewPlayer(player);
        YanPrologue.equip(player);
        YanPrologue.startCombat(scene);
        test.assertTrue(scene.boss.isAlive() && !scene.boss.execUsed(), "No execution in the first phase");

        var hit = new net.minecraftforge.event.entity.living.LivingHurtEvent(player, player.damageSources().magic(), 40);
        YanPrologue.combatDamage(hit);
        test.assertTrue(hit.getAmount() == 4, "Ordinary damage is reduced by 90 percent");
        for (var id : YanPrologueSpells.SPELLS) {
            test.assertTrue(!com.tnc.tnc.magic.ManaGate.shouldBlock(player, id), "The temporary kit can cast " + id);
        }
        scene.boss.hurt(player.damageSources().indirectMagic(player, player), 200);
        test.assertTrue(scene.boss.getPhase() == 2, "The real boss transforms to phase two");

        int start = 160;
        int impact = com.tnc.tnc.boss.YanExecutionRules.AT_IMPACT;
        test.runAtTickTime(25, () -> {
            scene.boss.invulnerableTime = 0;
            scene.boss.hurt(player.damageSources().indirectMagic(player, player), 6);
            test.assertTrue(scene.boss.getHealth() == 62 && !scene.boss.execUsed(), "Phase two takes damage above 75 percent");
        });
        test.runAtTickTime(start, () -> {
            test.assertTrue(scene.phase == YanPrologue.Phase.COMBAT && !scene.boss.execUsed() && scene.boss.execTick() < 0,
                    "Time in phase two cannot start the execution above 75 percent");
            scene.boss.invulnerableTime = 0;
            scene.boss.hurt(player.damageSources().indirectMagic(player, player), 200);
            test.assertTrue(scene.boss.getHealth() == 60 && scene.boss.isAlive(), "A lethal hit stops at exactly 75 percent");
        });
        test.runAtTickTime(start + 7, () -> test.assertTrue(scene.boss.execUsed() && scene.boss.execTick() >= 0
                && !scene.boss.tryExecution(), "Crossing 75 percent starts a single real execution"));
        test.runAtTickTime(start + impact - 3, () -> test.assertTrue(scene.phase == YanPrologue.Phase.COMBAT,
                "The victim survives the slow-motion fall"));
        test.runAtTickTime(start + impact + 12, () -> test.assertTrue(scene.phase == YanPrologue.Phase.DEFEAT
                && player.isAlive() && player.getHealth() == 1 && scene.defeatPosition != null,
                "The actual hit triggers the held death scene without respawning; scene=" + scene.phase
                        + " executionTick=" + scene.boss.execTick() + " health=" + player.getHealth()));
        test.runAtTickTime(start + impact + YanPrologueScript.DEFEAT_TICKS + 12, () -> {
            try {
                test.assertTrue(scene.phase == YanPrologue.Phase.REWIND, "The hold transitions to the two rewind cards");
                YanPrologue.advance(player, scene.token);
                test.assertTrue(YanPrologue.active(player), "A client cannot skip the rewind cards");
                test.succeed();
            } finally {
                YanPrologue.recoverKit(player, JourneyData.get(player.server).player(player.getUUID()));
                YanPrologue.abandon(scene);
                player.discard();
                channel.finishAndReleaseAll();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "yan_prologue")
    public static void temporaryKitRestoresInventoryAndVitals(GameTestHelper test) {
        var level = test.getLevel().getServer().getLevel(YanPrologue.WORLD);
        var player = FakePlayerFactory.getMinecraft(level);
        player.getInventory().clearContent();
        player.getInventory().setItem(6, new ItemStack(Items.AMETHYST_SHARD, 3));
        player.getInventory().selected = 6;
        var stone = com.tnc.tnc.magic.MagicStone.getOrNull(player);
        stone.setMaxMana(210);
        stone.setMana(73);
        var knowledge = java.util.Set.copyOf(stone.getLearned());
        player.setHealth(9);
        player.getFoodData().setFoodLevel(14);
        YanPrologue.equip(player);
        test.assertTrue(YanPrologueSpells.isWand(player.getMainHandItem()), "Prologue wand is in the main hand");
        test.assertTrue(stone.getMaxMana() == 1000 && stone.getMana() == 1000, "Prologue has a full 1000 mana");
        test.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - .9) < .001,
                "Prologue has 90 percent knockback resistance");
        player.setHealth(10);
        YanPrologue.healPulse(player);
        test.assertTrue(player.getHealth() == 12, "A recovery pulse restores two health points");
        test.assertTrue(stone.getLearned().equals(knowledge), "The wand does not teach the spells");
        test.assertTrue(player.getInventory().getItem(6).isEmpty(), "Original inventory is held separately");
        YanPrologue.recoverKit(player, JourneyData.get(player.server).player(player.getUUID()));
        test.assertTrue(player.getInventory().getItem(6).getCount() == 3 && player.getInventory().selected == 6,
                "Inventory and selected slot return");
        test.assertTrue(stone.getMaxMana() == 210 && stone.getMana() == 73
                && player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 0, "The original mana and knockback resistance return");
        test.assertTrue(player.getInventory().getItem(4).isEmpty() && player.getHealth() == 9
                && player.getFoodData().getFoodLevel() == 14, "Kit and encounter health do not follow the player");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "yan_prologue")
    public static void staffContainsTheRequestedEightSpellTiers(GameTestHelper test) {
        var entries = YanPrologueSpells.SPELLS.stream().map(com.tnc.tnc.magic.SpellCatalog::byId).toList();
        var chains = java.util.List.of("SPEED", "FIRE_HAND", "GALE", "WIND_WEAPON",
                "WATER_BIND", "EARTH_MOVE", "LIGHT_GRACE", "DARK_SUMMON");
        var tiers = java.util.List.of(4, 3, 4, 2, 4, 4, 3, 3);
        test.assertTrue(entries.stream().map(e -> e.chain().name()).toList().equals(chains), "All spell chains are in order");
        test.assertTrue(entries.stream().map(e -> e.tier()).toList().equals(tiers), "All spell tiers match");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "yan_prologue")
    public static void yanTransformsAtZeroHealthAndKeepsHisScriptedHealth(GameTestHelper test) {
        var level = test.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var yan = new YanPrologue.Actor(level, player.getUUID());
        var pos = Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(2, 2, 2)));
        yan.moveTo(pos.x, pos.y, pos.z, 0, 0);
        yan.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);
        yan.setHealth(80);
        level.addFreshEntity(yan);
        try {
            yan.hurt(player.damageSources().indirectMagic(player, player), 8);
            test.assertTrue(yan.getHealth() == 56, "The 200 percent bonus triples spell damage");
            yan.invulnerableTime = 0;
            yan.hurt(player.damageSources().playerAttack(player), 200);
            test.assertTrue(yan.getPhase() == 2 && yan.isAlive() && yan.getHealth() == 80,
                    "The true Yan entity revives into phase two at the prologue's health");
            yan.invulnerableTime = 0;
            yan.hurt(player.damageSources().playerAttack(player), 200);
            test.assertTrue(yan.getHealth() == 60 && !yan.shouldBeSaved(), "Phase two can take damage down to the execution threshold");
            test.succeed();
        } finally {
            yan.discard();
        }
    }
}
