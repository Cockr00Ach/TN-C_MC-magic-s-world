package com.tnc.tnc.warrior;

import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncwarrior")
@PrefixGameTestTemplate(false)
public final class WeaponChampionGameTests {
    @GameTest(template = "building_test_empty", batch = "field_champion_ultimates")
    public static void highRanksCastAndReleaseTheirOwnUltimate(GameTestHelper helper) {
        var pad = field(helper);
        var player = AdventureGameTests.player(helper);
        player.getAbilities().invulnerable = true; // Observe multiple complete attack rotations.
        var finishes = java.util.EnumSet.noneOf(WeaponChampion.Strike.class);
        for (var branch : WarriorWeaponRanks.Branch.values()) {
            var base = WeaponChampion.forBranch(branch, 2);
            var elite = WeaponChampion.forBranch(branch, 3);
            helper.assertTrue(!base.ultimateReady(100, true) && !elite.ultimateReady(100, true),
                "Early tiers have no ultimate");
            for (int rank = 3; rank <= 5; rank++) {
                var previous = WeaponChampion.forBranch(branch, rank - 1);
                var style = WeaponChampion.forBranch(branch, rank);
                helper.assertTrue(style.approachTicks(false) < previous.approachTicks(false)
                    && style.windupTicks(style.strike(false)) < previous.windupTicks(style.strike(false)),
                    "Higher ranks resume pressure and release a normal attack sooner: " + style);
            }
            for (int rank = 4; rank <= 5; rank++) {
                var style = WeaponChampion.forBranch(branch, rank);
                helper.assertTrue(style.ultimateReady(7 - rank, false) && !style.ultimateReady(6 - rank, false)
                    && style.ultimateReady(6 - rank, true), "Half health shortens the rotation: " + style);
                var boss = startAt(helper, player, pad, style);
                try {
                    for (int i = 0; i < 1000 && (boss.warning() == null || !boss.warning().strike().isUltimate()); i++) boss.tick();
                    var cue = boss.warning();
                    helper.assertTrue(cue != null && cue.strike() == style.ultimate()
                        && cue.ticks() == 38 && cue.wavesLeft() == cue.strike().waves(), "Ultimate windup in a real duel: " + style);
                    finishes.add(cue.strike());
                    if (style == WeaponChampion.BOW_LEGEND) {
                        boss.hurtBySkill(player.damageSources().playerAttack(player), 8,
                            WarriorCombos.bySlot(WarriorWeaponFamily.HAMMER, 0), true);
                    } else {
                        player.setPos(cue.origin().add(9 * cue.scale(), 0, 9 * cue.scale()));
                        for (int wave = cue.wavesLeft(); wave >= 1; wave--) {
                            var current = boss.warning();
                            helper.assertTrue(current != null && current.origin().equals(cue.origin()) && current.wavesLeft() == wave,
                                "Locked ultimate telegraphs every wave: " + style);
                            if (style == WeaponChampion.BOW_MASTER && wave == 1) {
                                player.setPos(cue.origin());
                                player.getAbilities().invulnerable = false;
                                for (int t = 0; t < 65; t++) player.tick();
                                player.setHealth(player.getMaxHealth());
                                player.invulnerableTime = 0;
                            }
                            for (int t = 0; t < current.ticks(); t++) boss.tick();
                            helper.assertTrue(boss.impactCue().strike() == cue.strike(), "The warned ultimate resolves");
                        }
                    }
                    if (style == WeaponChampion.BOW_MASTER) {
                        helper.assertTrue(player.getHealth() < player.getMaxHealth() && boss.weaponPose() != 3,
                            "Staying in the marked impact actually damages the challenger");
                        player.getAbilities().invulnerable = true;
                        player.setHealth(player.getMaxHealth());
                    } else {
                        helper.assertTrue(boss.weaponPose() == 3 && boss.warning() == null, "Counter window after ultimate: " + style);
                    }
                } finally {
                    WeaponChampionDuel.cancel(player);
                }
            }
        }
        helper.assertTrue(finishes.size() == 7, "One distinct ultimate per weapon branch");
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", batch = "field_champion_roster")
    public static void everyChallengeReachesItsOpeningAttack(GameTestHelper helper) {
        var pad = field(helper);
        var player = AdventureGameTests.player(helper);
        player.getAbilities().invulnerable = true;
        for (var style : WeaponChampion.values()) {
            try {
                var boss = startAt(helper, player, pad, style);
                for (int t = 0; t < 100 && boss.warning() == null; t++) boss.tick();
                var cue = boss.warning();
                helper.assertTrue(cue != null && cue.strike() == style.strikeForRound(0, false),
                    "Field opening must telegraph the selected champion's move: " + style);
                if (style.rank() > 2 && style != WeaponChampion.GREATSWORD_MASTER)
                    helper.assertTrue(cue.strike() == style.strikeForRound(2, false), "First contact shows the signature: " + style);
                for (int t = 0; t < cue.ticks(); t++) boss.tick();
                helper.assertTrue(boss.impactCue() != null && boss.impactCue().strike() == cue.strike(),
                    "The first warning must actually resolve: " + style);
                var skill = WarriorCombos.forRank(WarriorWeapons.family(boss.getMainHandItem()), style.rank()).get(0);
                skill = WarriorPerformance.forWeapon(skill, boss.getMainHandItem());
                for (int t = 0; t < 300 && boss.weaponPose() < 6; t++) {
                    player.setPos(boss.getX(), pad.getY(), boss.getZ() + 4);
                    boss.tick();
                }
                for (int beat = 0; beat <= skill.length(); beat++) {
                    var next = boss.warning();
                    helper.assertTrue(next != null && next.wavesLeft() == skill.length() + 1 - beat,
                        "Every weapon beat winds up in order: " + style + " beat " + beat);
                    var synced = WeaponChampionCue.load(next.save());
                    helper.assertTrue(synced.scale() == next.scale() && synced.arc() == next.arc()
                        && synced.origin().distanceToSqr(next.origin()) < 1e-12
                        && synced.forward().distanceToSqr(next.forward()) < 1e-12
                        && synced.animation() == next.animation() && synced.strike() == next.strike()
                        && synced.wavesLeft() == next.wavesLeft() && synced.start() == next.start() && synced.ticks() == next.ticks(),
                        "Range and animation survive client sync: " + style + " " + next + " vs " + synced);
                    if (beat == skill.length()) {
                        helper.assertTrue(next.strike() == WeaponChampion.Strike.COMBO_FINISH, "Finisher reached: " + style);
                        double radius = skill.radius() * style.attackScale(next.strike());
                        var within = next.origin().add(next.forward().scale(radius - .02));
                        var outside = next.origin().add(next.forward().scale(radius + .02));
                        helper.assertTrue(next.hits(within.x, within.z) && !next.hits(outside.x, outside.z),
                            "Finisher uses equipped weapon and tier reach: " + style);
                    }
                    for (int t = 0; t < next.ticks(); t++) boss.tick();
                    helper.assertTrue(boss.impactCue() != null && boss.impactCue().wavesLeft() == next.wavesLeft()
                        && boss.impactCue().strike() == next.strike(), "Every warned beat resolves: " + style);
                }
            } finally {
                WeaponChampionDuel.cancel(player);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", batch = "field_champion_counters")
    public static void fieldCountersAlterTheFight(GameTestHelper helper) {
        var pad = field(helper);
        var player = AdventureGameTests.player(helper);
        try {
            var swordsman = startAt(helper, player, pad, WeaponChampion.LONGSWORD_ELITE);
            var cue = firstWarning(swordsman, helper);
            player.setPos(cue.origin().add(cue.forward().scale(2)));
            var attack = player.damageSources().playerAttack(player);
            helper.assertTrue(!swordsman.hurt(attack, 5) && swordsman.warning().strike() == WeaponChampion.Strike.SWORD_GUARD,
                "Baiting the first cut changes the telegraphed move");
            var breaker = WarriorCombos.bySlot(WarriorWeaponFamily.LONGSWORD, 2);
            swordsman.hurtBySkill(attack, 5, breaker, false);
            helper.assertTrue(swordsman.weaponPose() == 3 && swordsman.warning() == null, "Breaker punishes the feint");
            WeaponChampionDuel.cancel(player);

            var iron = startAt(helper, player, pad, WeaponChampion.HALBERD_MASTER);
            cue = firstWarning(iron, helper);
            player.setPos(iron.position().add(cue.forward().scale(3)));
            var swing = WarriorCombos.bySlot(WarriorWeaponFamily.HAMMER, 0);
            iron.hurtBySkill(attack, 12, swing, true);
            helper.assertTrue(iron.weaponPose() != 3, "One plate still holds");
            iron.invulnerableTime = 0;
            iron.hurtBySkill(attack, 12, swing, true);
            helper.assertTrue(iron.weaponPose() == 3, "Second charged strike shatters the cuirass");
            for (int i = 0; i < 80; i++) iron.tick();
            float before = iron.getHealth();
            iron.hurt(attack, 12);
            helper.assertTrue(iron.getHealth() < before - 2, "Frontal armor stays broken after the stagger");
            WeaponChampionDuel.cancel(player);

            var gate = startAt(helper, player, pad, WeaponChampion.HALBERD_LEGEND);
            cue = firstWarning(gate, helper);
            player.setPos(cue.origin().add(3.5, 0, 0));
            for (int i = 0; i < cue.ticks(); i++) gate.tick();
            helper.assertTrue(gate.warning().wavesLeft() == 1, "Second gate is armed");
            player.setPos(cue.origin());
            for (int i = 0; i < 5; i++) gate.tick();
            player.setPos(cue.origin().add(3.5, 0, 0));
            gate.tick();
            player.setPos(cue.origin());
            for (int i = 0; i < 5; i++) gate.tick();
            helper.assertTrue(gate.warning() != null, "Leaving the seal resets the hold");
            gate.tick();
            helper.assertTrue(gate.warning() == null && gate.weaponPose() == 3, "Holding the eye cancels the second gate");
            WeaponChampionDuel.cancel(player);

            var reaper = startAt(helper, player, pad, WeaponChampion.SCYTHE_MASTER);
            cue = firstWarning(reaper, helper);
            reaper.setHealth(reaper.getMaxHealth() - 20);
            before = reaper.getHealth();
            player.setPos(cue.origin().add(-cue.forward().z * 4 * cue.scale(), 0, cue.forward().x * 4 * cue.scale()));
            for (int i = 0; i < 65; i++) player.tick();
            player.invulnerableTime = 0;
            for (int i = 0; i < cue.ticks(); i++) reaper.tick();
            helper.assertTrue(reaper.getHealth() == before + 5, "An actual orbit hit restores five boss health");
        } finally {
            WeaponChampionDuel.cancel(player);
        }
        helper.succeed();
    }

    private static WeaponChampionCue firstWarning(WeaponChampionEntity boss, GameTestHelper helper) {
        for (int i = 0; i < 100 && boss.warning() == null; i++) boss.tick();
        helper.assertTrue(boss.warning() != null, "Missing opening warning for " + boss.champion());
        return boss.warning();
    }

    @GameTest(template = "building_test_empty", batch = "animal_champion_counters")
    public static void craneTigerAndTortoiseRewardDifferentCounters(GameTestHelper helper) {
        var player = AdventureGameTests.player(helper);
        var pad = field(helper);
        TemperingData.get(player.server).setTalent(player.getUUID(), 3);
        try {
            var crane = startAt(helper, player, pad, WeaponChampion.SPEAR_ELITE);
            for (int i = 0; i < 20; i++) crane.tick();
            helper.assertTrue(crane.getY() > pad.getY() + 4, "Crane must rise above the pad: pad=" + pad
                + ", crane=" + crane.position() + ", player=" + player.position() + ", removed=" + crane.isRemoved());
            helper.assertTrue(!crane.hurt(player.damageSources().playerAttack(player), 20), "Circling crane evades melee");
            for (int i = 0; i < 29; i++) crane.tick();
            helper.assertTrue(crane.warning() != null && crane.getY() < pad.getY() + .5, "Crane descends into its marked thrust");
            player.setPos(crane.position().add(0, 0, 1.8));
            helper.assertTrue(WarriorComboService.smallCounterTarget(player, crane),
                "Landed crane must be a visible parry target: distance=" + crane.horizontalReachSquared(player)
                    + ", visible=" + player.hasLineOfSight(crane) + ", owner in ring=" + crane.belongsTo(player));
            WarriorComboService.state(player.getUUID()).stanceUntil = WarriorComboService.gameTime(player) + 30;
            helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(player, crane.damageSources().mobAttack(crane), 8))
                && crane.warning() == null && crane.weaponPose() == 3, "A real parry cancels the crane's combo");
            for (int i = 0; i < 90; i++) crane.tick();
            helper.assertTrue(crane.weaponPose() == 3 && crane.getY() < pad.getY() + .5, "Long stun stays grounded");
            for (int i = 0; i < 25; i++) crane.tick();
            helper.assertTrue(crane.getY() > pad.getY() + 3, "Crane can take off after its stun");
            WeaponChampionDuel.cancel(player);

            var tiger = startAt(helper, player, pad, WeaponChampion.GREATSWORD_MASTER);
            for (int i = 0; i < 30; i++) tiger.tick();
            var cue = tiger.warning();
            helper.assertTrue(cue != null, "Tiger commits to an attack");
            player.setPos(cue.origin().add(cue.forward().scale(2)));
            for (int i = 0; i < 65; i++) player.tick(); // Expire vanilla login protection on the mock player.
            float health = player.getHealth();
            player.invulnerableTime = 0;
            for (int i = 0; i < 110; i++) tiger.tick();
            helper.assertTrue(player.getHealth() < health, "An unblocked tiger cleave removes real player health");
            helper.assertTrue(tiger.weaponPose() == 5 && tiger.warning() == null, "Tiger is still pulling its blade free");
            WeaponChampionDuel.cancel(player);

            var turtle = startAt(helper, player, pad, WeaponChampion.HAMMER_LEGEND);
            float before = turtle.getHealth();
            turtle.hurt(player.damageSources().playerAttack(player), 40);
            helper.assertTrue(before - turtle.getHealth() <= .35f + .001f, "Closed shell caps ordinary damage");
            for (int i = 0; i < 30; i++) turtle.tick();
            player.setPos(turtle.position().add(0, 0, 2));
            var breaker = WarriorCombos.bySlot(WarriorWeaponFamily.HAMMER, 2);
            turtle.hurtBySkill(player.damageSources().playerAttack(player), 24, breaker, false);
            helper.assertTrue(turtle.weaponPose() == 3 && turtle.getHealth() < before - 15,
                "A close breaker interrupts the thunder charge and bypasses shell armor");
        } finally {
            WeaponChampionDuel.cancel(player);
        }
        helper.succeed();
    }

    private static BlockPos field(GameTestHelper helper) {
        var pad = helper.absolutePos(new BlockPos(7, 155, 7));
        var level = helper.getLevel();
        for (var tile : BlockPos.betweenClosed(pad.offset(-13, -1, -13), pad.offset(13, -1, 13)))
            level.setBlock(tile, Blocks.STONE.defaultBlockState(), 2);
        for (var tile : BlockPos.betweenClosed(pad.offset(-13, 0, -13), pad.offset(13, 19, 13)))
            if (!level.isEmptyBlock(tile)) level.setBlock(tile, Blocks.AIR.defaultBlockState(), 2);
        return pad;
    }

    private static WeaponChampionEntity startAt(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player,
                                                BlockPos pad, WeaponChampion style) {
        player.setPos(Vec3.atBottomCenterOf(pad));
        var weapon = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", style.weapon));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
        helper.assertTrue(WeaponChampionDuel.invite(player, style) && WeaponChampionDuel.accept(player, style),
            "A field invitation starts " + style);
        return helper.getLevel().getEntitiesOfClass(WeaponChampionEntity.class, player.getBoundingBox().inflate(16),
            boss -> boss.belongsTo(player) && boss.champion() == style).get(0);
    }

    @GameTest(template = "building_test_empty", batch = "weapon_champions")
    public static void sevenBranchesHaveDistinctFinishersAndReadableHazards(GameTestHelper helper) {
        for (var branch : WarriorWeaponRanks.Branch.values()) {
            var champion = WeaponChampion.valueOf(branch.name());
            helper.assertTrue(WeaponChampionDuel.type(champion) != null, "Registered champion for " + branch);
            var equipment = WarriorWeaponRanks.entry(champion.weapon);
            helper.assertTrue(equipment != null && equipment.branch() == branch && equipment.rank() == 2,
                "Champion equips its branch's second rank");
            helper.assertTrue(champion.strike(false) != champion.strike(true), "Branch has a second attack");
            var elite = WeaponChampion.forBranch(branch, 3);
            var eliteWeapon = WarriorWeaponRanks.entry(elite.weapon);
            helper.assertTrue(WeaponChampionDuel.type(elite) != null && elite.rank() == 3
                && eliteWeapon != null && eliteWeapon.branch() == branch && eliteWeapon.rank() == 3,
                "Elite equips the third rank and has a separate registered entity");
            var signature = elite.strikeForRound(2, false);
            helper.assertTrue(signature.waves() >= 2 && signature != champion.strike(false)
                && signature != champion.strike(true)
                && WeaponChampion.fromSelection(elite.selectionKey()) == elite && elite.selectionKey().length() <= 16,
                "Elite has its own selectable multi-stage attack");
            var master = WeaponChampion.forBranch(branch, 4);
            var masterWeapon = WarriorWeaponRanks.entry(master.weapon);
            helper.assertTrue(masterWeapon != null && masterWeapon.rank() == 4 && masterWeapon.branch() == branch
                && WeaponChampion.fromSelection(master.selectionKey()) == master && master.selectionKey().length() <= 16,
                "Fourth-rank encounter can travel through the terminal and equips the correct weapon");
            for (var fighter : new WeaponChampion[] {champion, elite, master}) {
                var type = WeaponChampionDuel.type(fighter);
                helper.assertTrue(type.getHeight() == fighter.height() && type.getWidth() == fighter.width(),
                    "The registered hitbox matches the fighter's build: " + fighter);
            }
            helper.assertTrue(master.strikeForRound(2, false).waves() >= 2 && master.strikeForRound(2, false) != signature,
                "Each master has an attack distinct from the matching elite");
            var legend = WeaponChampion.forBranch(branch, 5);
            var legendaryWeapon = WarriorWeaponRanks.entry(legend.weapon);
            helper.assertTrue(WeaponChampionDuel.type(legend).getHeight() == legend.height()
                && legendaryWeapon != null && legendaryWeapon.rank() == 5 && legendaryWeapon.branch() == branch
                && WeaponChampion.fromSelection(legend.selectionKey()) == legend && legend.selectionKey().length() <= 16
                && legend.strikeForRound(2, false).waves() >= 2
                && legend.strikeForRound(2, false) != master.strikeForRound(2, false),
                "Each legendary encounter has a selectable matching weapon, full-sized hitbox and signature");
        }
        var swordBreaker = WarriorCombos.bySlot(WarriorWeaponFamily.LONGSWORD, 2);
        var hammerQuake = WarriorCombos.bySlot(WarriorWeaponFamily.HAMMER, 0);
        var bowShot = WarriorCombos.bySlot(WarriorWeaponFamily.BOW, 0);
        helper.assertTrue(WeaponChampion.LONGSWORD.opens(swordBreaker, true, false, false, 2, 1),
            "Breaker catches sword guard");
        helper.assertTrue(WeaponChampion.HAMMER.opens(hammerQuake, true, false, false, 3, 1)
            && !WeaponChampion.HAMMER.opens(hammerQuake, true, false, false, 4, 1), "Quake must be interrupted inside the ring");
        helper.assertTrue(WeaponChampion.BOW.opens(bowShot, true, false, false, 6, 1)
            && !WeaponChampion.BOW.opens(bowShot, true, false, false, 2, 1), "Bow opening rewards ranged combo aim");
        Vec3 forward = new Vec3(0, 0, 1);
        helper.assertTrue(WeaponChampion.Strike.THRUST.hits(Vec3.ZERO, forward, new Vec3(0, 0, 6))
            && !WeaponChampion.Strike.THRUST.hits(Vec3.ZERO, forward, new Vec3(2, 0, 6)), "Spear lane can be sidestepped");
        helper.assertTrue(WeaponChampion.Strike.CROSS.hits(Vec3.ZERO, forward, new Vec3(4, 0, 0))
            && !WeaponChampion.Strike.CROSS.hits(Vec3.ZERO, forward, new Vec3(3, 0, 3)), "Halberd cross leaves diagonal gaps");
        helper.assertTrue(WeaponChampion.Strike.REAP.hits(Vec3.ZERO, forward, new Vec3(0, 0, 4))
            && !WeaponChampion.Strike.REAP.hits(Vec3.ZERO, forward, new Vec3(0, 0, 2)), "Scythe ring has an inner safe lane");
        var firstWave = new WeaponChampionCue(WeaponChampion.Strike.QUAKE, Vec3.ZERO, Vec3.ZERO, forward, 100, 18, 3);
        var secondWave = new WeaponChampionCue(WeaponChampion.Strike.QUAKE, Vec3.ZERO, Vec3.ZERO, forward, 118, 18, 2);
        var lastWave = new WeaponChampionCue(WeaponChampion.Strike.QUAKE, Vec3.ZERO, Vec3.ZERO, forward, 136, 18, 1);
        helper.assertTrue(firstWave.hits(0, 5.5) && !firstWave.hits(0, 1)
            && secondWave.hits(0, 1) && !secondWave.hits(0, 5.5)
            && lastWave.hits(0, 4) && !lastWave.hits(0, 1) && !lastWave.hits(0, 5.5),
            "Hammer waves move across the arena instead of repeating the same ring");
        helper.assertTrue(firstWave.equals(WeaponChampionCue.load(firstWave.save()))
            && firstWave.hits(0, 6.7) && !firstWave.hits(0, 7) && firstWave.progress(109) == 0.5,
            "The synced warning retains timing and the outer edge of the shockwave");
        var rolling = WeaponChampion.Strike.ROLLING_QUAKE;
        helper.assertTrue(rolling.hits(Vec3.ZERO, forward, new Vec3(0, 0, 1), 3)
            && !rolling.hits(Vec3.ZERO, forward, new Vec3(0, 0, 1), 1)
            && rolling.hits(Vec3.ZERO, forward, new Vec3(0, 0, 5), 1),
            "The elite hammer pressure travels outward");
        var cross = WeaponChampion.Strike.PIVOT_CROSS;
        var diagonal = forward.yRot((float) Math.PI / 4);
        helper.assertTrue(!cross.hits(Vec3.ZERO, forward, new Vec3(3, 0, 3))
            && cross.hits(Vec3.ZERO, diagonal, new Vec3(3, 0, 3)),
            "The elite halberd's second cross covers the first cross's gap");
        var curtain = WeaponChampion.Strike.ARROW_CURTAIN;
        helper.assertTrue(curtain.hits(Vec3.ZERO, forward, new Vec3(5, 0, 0))
            && !curtain.hits(Vec3.ZERO, forward, new Vec3(0, 0, 5)),
            "The elite archer warns and hits a lane");
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", batch = "weapon_champions", timeoutTicks = 120)
    public static void bossRequiresAnInvitationAndVictoryReleasesTheDuel(GameTestHelper helper) {
        var player = AdventureGameTests.player(helper);
        var visitor = AdventureGameTests.player(helper);
        var pad = field(helper);
        player.setPos(Vec3.atBottomCenterOf(pad));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        visitor.setPos(player.position());
        var bossType = WeaponChampion.LONGSWORD;
        helper.assertTrue(!WeaponChampionDuel.accept(player, bossType), "No battle without a current invitation");
        helper.assertTrue(WeaponChampionDuel.invite(player, bossType), "A challenger can issue an invitation in the field");
        helper.assertTrue(!WeaponChampionDuel.accept(player, WeaponChampion.SCYTHE)
            && !WeaponChampionDuel.accept(visitor, bossType), "Only the owner and the named opponent can accept");
        helper.assertTrue(WeaponChampionDuel.accept(player, bossType), "An axe wielder can challenge a swordsman");
        var boss = helper.getLevel().getEntitiesOfClass(WeaponChampionEntity.class, player.getBoundingBox().inflate(16),
            who -> who.belongsTo(player)).get(0);
        helper.assertTrue(!WeaponChampionDuel.accept(player, bossType) && boss.getType().is(
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "bosses"))), "Registered boss starts exactly once");
        helper.assertTrue(!boss.hurt(visitor.damageSources().playerAttack(visitor), 60), "Spectators cannot steal this fight");
        float before = boss.getHealth();
        helper.assertTrue(boss.hurt(player.damageSources().playerAttack(player), 16) && boss.getHealth() < before - 2,
            "Ordinary weapons damage a boss outside a counter window");
        boss.setHealth(1);
        boss.invulnerableTime = 0;
        boss.hurt(player.damageSources().playerAttack(player), 24);
        helper.assertTrue(!boss.isAlive() && !WeaponChampionDuel.active(boss, player), "Killing the boss settles the fight");
        var data = TrainingGroundData.get(player.server);
        var restored = TrainingGroundData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(restored.recordBossVictory(player.getUUID(), bossType, 10000) < 10000,
            "Victory time persists independently of practice records");
        var next = startAt(helper, player, pad, WeaponChampion.SPEAR);
        player.setPos(pad.getX() + 65, pad.getY(), pad.getZ());
        next.tick();
        helper.assertTrue(next.isRemoved() && !WeaponChampionDuel.active(next, player), "Retreat clears the field boss");
        helper.succeed();
    }
}
