package com.tnc.tnc.magic.free;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.MagicStone;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.tnc.tnc.magic.free.FreeMagicNetwork.BEGIN;
import static com.tnc.tnc.magic.free.FreeMagicNetwork.CANCEL;
import static com.tnc.tnc.magic.free.FreeMagicNetwork.FINAL;
import static com.tnc.tnc.magic.free.FreeMagicNetwork.PARTIAL;

@GameTestHolder("tncacademy")
@PrefixGameTestTemplate(false)
public final class FreeMagicGameTests {
    @GameTest(template = "building_test_empty", batch = "free_magic_cast")
    public static void waterWandOnlyCastsOneBoltForTheCurrentFinal(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 100);
        var data = MagicStone.getOrNull(caster);
        var victim = new Target(helper, caster);

        input(caster, BEGIN, 21, "");
        input(caster, PARTIAL, 21, "释放水弹");
        input(caster, FINAL, 20, "水弹");
        helper.assertTrue(data.getMana() == 100, "Partial or stale recognition consumed mana");
        input(caster, FINAL, 21, "释放出水弹！");
        input(caster, FINAL, 21, "水弹");
        helper.assertTrue(data.getMana() == 100, "Accepted FINAL charged before formation ended");

        helper.runAfterDelay(20, () -> helper.assertTrue(data.getMana() == 100 && victim.hits == 0,
                "Formation damaged the aimed target or spent mana early"));
        helper.runAfterDelay(38, () -> {
            cleanup(caster, victim);
            helper.assertTrue(data.getMana() == 100 - FreeMagicIntent.Shape.BALL.mana, "Completed formation did not charge exactly once");
            helper.assertTrue(victim.hits == 1, "Expected one accepted water bolt to hit the aimed target");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_insufficient")
    public static void insufficientManaDoesNotCastOrPartiallyDebit(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 7);
        var victim = new Target(helper, caster);
        input(caster, BEGIN, 11, "");
        input(caster, FINAL, 11, "水弹");

        helper.runAfterDelay(38, () -> {
            cleanup(caster, victim);
            helper.assertTrue(MagicStone.getOrNull(caster).getMana() == 7 && victim.hits == 0,
                    "Insufficient mana still spent energy or fired a bolt");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_burst")
    public static void swappingAwayTheWandCancelsTheRestOfTheBurst(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 100);
        var victim = new Target(helper, caster);
        ItemStack wand = caster.getMainHandItem();
        input(caster, BEGIN, 41, "");
        input(caster, FINAL, 41, "连续水弹");
        helper.runAfterDelay(26, () -> caster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        helper.runAfterDelay(28, () -> caster.setItemInHand(InteractionHand.MAIN_HAND, wand));

        helper.runAfterDelay(60, () -> {
            cleanup(caster, victim);
            helper.assertTrue(victim.hits == 1, "Burst was not permanently canceled when its wand was unequipped");
            helper.assertTrue(MagicStone.getOrNull(caster).getMana() == 92, "Canceled rounds still spent mana");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_composed_volley")
    public static void repeatedVolleyFiresThreeWavesAndChargesAtEachRelease(GameTestHelper helper) {
        ServerPlayer caster = airborneCaster(helper, 100, 3);
        Target[] targets = volleyTargets(helper, caster);
        cast(caster, 131, "连续齐射水弹");
        helper.runAfterDelay(20, () -> assertWave(helper, caster, targets, 100, 0));
        helper.runAfterDelay(31, () -> assertWave(helper, caster, targets, 76, 3));
        helper.runAfterDelay(45, () -> assertWave(helper, caster, targets, 52, 6));
        helper.runAfterDelay(64, () -> {
            cleanup(caster, targets);
            assertWave(helper, caster, targets, 28, 9);
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_partial_affordability")
    public static void aVolleyCanBeginWithManaForOnlyItsFirstWave(GameTestHelper helper) {
        ServerPlayer caster = airborneCaster(helper, 35, 3);
        Target[] targets = volleyTargets(helper, caster);
        cast(caster, 141, "连续齐射水弹");
        helper.runAfterDelay(64, () -> {
            cleanup(caster, targets);
            assertWave(helper, caster, targets, 11, 3);
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_fast_vanilla_damage")
    public static void fastRepeatDamagesAVanillaTargetOnAllThreeWaves(GameTestHelper helper) {
        ServerPlayer caster = airborneCaster(helper, 100, 2);
        Pig victim = EntityType.PIG.create(helper.getLevel());
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        victim.setHealth(100);
        victim.setNoAi(true);
        victim.setNoGravity(true);
        Vec3 anchor = caster.position().add(-.35, 1, 2.6);
        victim.setPos(anchor);
        helper.getLevel().addFreshEntity(victim);
        for (int tick = 1; tick <= 47; tick++) {
            helper.runAfterDelay(tick, () -> {
                victim.setPos(anchor);
                victim.setDeltaMovement(Vec3.ZERO);
            });
        }

        cast(caster, 145, "急速连续水球");
        var data = MagicStone.getOrNull(caster);
        float[] health = new float[3];
        int[] mana = new int[3];
        for (int index = 0; index < 3; index++) {
            int wave = index;
            helper.runAfterDelay(26 + 10 * wave, () -> {
                health[wave] = victim.getHealth();
                mana[wave] = data.getMana();
            });
        }
        helper.runAfterDelay(48, () -> {
            cleanup(caster);
            victim.discard();
            helper.assertTrue(victim.tickCount >= 40, "Vanilla target did not tick normally during the burst");
            for (int wave = 0; wave < 3; wave++) {
                helper.assertTrue(health[wave] == 95 - 5 * wave && mana[wave] == 89 - 11 * wave,
                        "Fast repeat wave " + (wave + 1) + " recorded health/mana " + health[wave] + "/" + mana[wave]);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_giant_collision")
    public static void giantWaterBallHitsBeyondTheOrdinaryCollisionRadius(GameTestHelper helper) {
        ServerPlayer plain = airborneCaster(helper, 100, 2);
        ServerPlayer giant = airborneCaster(helper, 100, 2);
        giant.setPos(plain.position().add(6, 0, 0));
        var plainTarget = new Target(helper, plain, -1.5, 5.6);
        var giantTarget = new Target(helper, giant, -1.5, 5.6);
        cast(plain, 151, "水弹");
        cast(giant, 152, "巨型水弹");
        helper.runAfterDelay(70, () -> {
            cleanup(plain, plainTarget);
            cleanup(giant, giantTarget);
            helper.assertTrue(plainTarget.hits == 0 && giantTarget.hits == 1,
                    "Giant collision does not match the larger water ball: ordinary=" + plainTarget.hits + ", giant=" + giantTarget.hits);
            helper.assertTrue(MagicStone.getOrNull(giant).getMana() == 87, "Giant modifier did not charge its configured cost");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_free_chant")
    public static void chantingRaisesActualDamageWithoutIncreasingManaCost(GameTestHelper helper) {
        ServerPlayer plain = airborneCaster(helper, 100, 1);
        ServerPlayer chanting = airborneCaster(helper, 100, 1);
        chanting.setPos(plain.position().add(6, 0, 0));
        var plainTarget = new Target(helper, plain);
        var chantingTarget = new Target(helper, chanting);
        cast(plain, 161, "水弹");
        cast(chanting, 162, "神明，请回应我的祈祷，赐予我祝福，以契约庇护我的水弹！");
        helper.runAfterDelay(70, () -> {
            cleanup(plain, plainTarget);
            cleanup(chanting, chantingTarget);
            helper.assertTrue(plainTarget.hits == 1 && chantingTarget.hits == 1
                            && Math.abs(chantingTarget.damage - plainTarget.damage * 1.4) < .001,
                    "Additional chant words did not increase actual attack damage: ordinary=" + plainTarget.damage + ", chant=" + chantingTarget.damage);
            helper.assertTrue(MagicStone.getOrNull(plain).getMana() == 92 && MagicStone.getOrNull(chanting).getMana() == 92,
                    "The free chant bonus increased mana cost");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_suppressed")
    public static void suppressionDuringFormationPreventsCasting(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 100);
        var victim = new Target(helper, caster);
        input(caster, BEGIN, 51, "");
        input(caster, PARTIAL, 51, "水弹");
        input(caster, FINAL, 51, "水弹");
        helper.runAfterDelay(10, () -> caster.addEffect(new MobEffectInstance(
                com.tnc.tnc.arcana.ArcanaMealEffects.NO_MAGIC, 100)));
        helper.runAfterDelay(38, () -> {
            helper.assertTrue(MagicStone.getOrNull(caster).getMana() == 100 && victim.hits == 0,
                    "Suppressed caster bypassed the normal magic lock");
            cleanup(caster, victim);
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_inside_target")
    public static void aBoltStartingInsideTheTargetStillHits(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 100);
        var victim = new Target(helper, caster);
        victim.setPos(caster.getEyePosition().add(0, -.25, 0));
        input(caster, BEGIN, 61, "");
        input(caster, FINAL, 61, "水弹");
        helper.runAfterDelay(38, () -> {
            helper.assertTrue(victim.hits == 1, "Point blank bolt missed the overlapping target");
            cleanup(caster, victim);
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_cancel")
    public static void cancelDuringFormationNeverConsumesMana(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 100);
        var victim = new Target(helper, caster);
        input(caster, BEGIN, 71, "");
        input(caster, FINAL, 71, "水弹");
        helper.runAfterDelay(10, () -> input(caster, CANCEL, 71, ""));
        helper.runAfterDelay(12, () -> input(caster, FINAL, 71, "水弹"));
        helper.runAfterDelay(38, () -> {
            cleanup(caster, victim);
            helper.assertTrue(MagicStone.getOrNull(caster).getMana() == 100 && victim.hits == 0,
                    "Canceled formation still fired or spent mana");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_blocked_origin")
    public static void formationCannotLaunchBeyondACloseWall(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 100);
        var victim = new Target(helper, caster);
        wall(helper, 1);
        input(caster, BEGIN, 81, "");
        input(caster, FINAL, 81, "水弹");
        helper.runAfterDelay(38, () -> {
            cleanup(caster, victim);
            helper.assertTrue(victim.hits == 0, "Projectile launched beyond a wall between the player and the array");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_pierce")
    public static void bladePiercesAtMostThreeDistinctTargets(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 100);
        // The short underground template can have natural stone immediately outside it.
        caster.setPos(caster.getX(), helper.getLevel().getMaxBuildHeight() - 16, caster.getZ());
        var first = new Target(helper, caster, -.5, 4.4);
        var second = new Target(helper, caster, -.5, 6.1);
        var third = new Target(helper, caster, -.5, 7.8);
        var fourth = new Target(helper, caster, -.5, 9.5);
        var lane = new ClipContext(caster.getEyePosition(), fourth.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster);
        helper.assertTrue(helper.getLevel().clip(lane).getType() == HitResult.Type.MISS, "Piercing test lane is obstructed");
        input(caster, BEGIN, 91, "");
        input(caster, FINAL, 91, "水刃");
        helper.runAfterDelay(55, () -> {
            cleanup(caster, first, second, third, fourth);
            helper.assertTrue(first.hits == 1 && second.hits == 1 && third.hits == 1 && fourth.hits == 0,
                    "Expected piercing hits [1, 1, 1, 0], got [" + first.hits + ", " + second.hits
                            + ", " + third.hits + ", " + fourth.hits + "]");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_cannon_occlusion")
    public static void cannonAreaRespectsWalls(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 100);
        var near = new Target(helper, caster, 0, .3);
        var behind = new Target(helper, caster, 0, 2.2);
        wall(helper, 1);
        input(caster, BEGIN, 101, "");
        input(caster, FINAL, 101, "水炮");
        helper.runAfterDelay(49, () -> {
            cleanup(caster, near, behind);
            helper.assertTrue(near.hits == 1 && behind.hits == 0, "Cannon failed to splash an exposed target or damaged through stone");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "free_magic_restraint")
    public static void bubbleAppliesItsBoundedRestraint(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 100);
        var victim = new Target(helper, caster);
        victim.acceptsDamage = true;
        input(caster, BEGIN, 111, "");
        input(caster, FINAL, 111, "水泡囚笼");
        helper.runAfterDelay(42, () -> {
            var effect = victim.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            cleanup(caster, victim);
            helper.assertTrue(victim.hits == 1 && effect != null && effect.getAmplifier() == 3
                    && effect.getDuration() > 0 && effect.getDuration() <= 68, "Bubble did not apply its one bounded restraint");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 120, batch = "free_magic_vortex")
    public static void vortexPulsesOnlyAgainstVisibleTargets(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 100, 3);
        var near = new Target(helper, caster, 0, 1.65);
        var behind = new Target(helper, caster, 0, 4.4);
        near.acceptsDamage = true;
        behind.acceptsDamage = true;
        wall(helper, 3);
        input(caster, BEGIN, 121, "");
        input(caster, FINAL, 121, "水龙卷");
        helper.runAfterDelay(100, () -> {
            cleanup(caster, near, behind);
            helper.assertTrue(near.hits > 1 && near.hits <= 6 && behind.hits == 0,
                    "Vortex failed to pulse at a bounded rate or reached through stone");
            helper.succeed();
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=120,batch="free_magic_empower_feedback")
    public static void empoweredSpellHasThreefoldWindup(GameTestHelper helper){
        var player=caster(helper,100);var target=new Target(helper,player);
        cast(player,201,"强化水球");
        helper.runAfterDelay(60,()->helper.assertTrue(target.hits==0&&MagicStone.getOrNull(player).getMana()==100,"Empowered spell released before its threefold windup"));
        helper.runAfterDelay(90,()->{cleanup(player,target);helper.assertTrue(target.hits==1&&target.damage>=12&&MagicStone.getOrNull(player).getMana()==86,"Empowered release did not deliver stronger damage for one charge");helper.succeed();});
    }
    @GameTest(template="building_test_empty",timeoutTicks=140,batch="free_magic_durable_feedback")
    public static void durableProjectileLeavesAnActiveWaterField(GameTestHelper helper){
        var player=airborneCaster(helper,100,2);var target=new Target(helper,player);
        cast(player,202,"持久水球");
        helper.runAfterDelay(100,()->{cleanup(player,target);helper.assertTrue(target.hits>=3,"Durable water vanished after its first hit without persistent field pulses");helper.assertTrue(MagicStone.getOrNull(player).getMana()==89,"Persistent field charged additional mana");helper.succeed();});
    }
    @GameTest(template="building_test_empty",timeoutTicks=80,batch="free_magic_shock_feedback")
    public static void shockLaunchesTheVictimUpward(GameTestHelper helper){
        var player=airborneCaster(helper,100,2);var target=new Target(helper,player);target.acceptsDamage=true;
        // NoAI pigs damp velocity but skip travel; keep AI travel with zero voluntary walking speed.
        target.setNoAi(false);target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        double initialY=target.getY();cast(player,203,"震荡水球");
        helper.runAfterDelay(38,()->{double lift=target.getY()-initialY,impulse=target.getDeltaMovement().y;int hits=target.hits;cleanup(player,target);helper.assertTrue(hits==1&&(lift>2||impulse>.8),"Shock failed to apply an upward launch: hits="+hits+", lift="+lift+", velocity="+impulse);helper.succeed();});
    }

    @GameTest(template="building_test_empty",timeoutTicks=90,batch="free_magic_fixed_formation")
    public static void movingDuringChargeDoesNotSweepBackToTheFormation(GameTestHelper helper){
        var player=airborneCaster(helper,100,2);var between=new Target(helper,player,4,1.6);
        cast(player,204,"水球");
        helper.runAfterDelay(10,()->player.setPos(player.position().add(8,0,0)));
        helper.runAfterDelay(55,()->{cleanup(player,between);helper.assertTrue(between.hits==0,"The release damaged the relocation segment from the moved caster back to the fixed array");helper.assertTrue(MagicStone.getOrNull(player).getMana()==92,"Moving caster failed to launch from the original array");helper.succeed();});
    }
    @GameTest(template="building_test_empty",timeoutTicks=90,batch="free_magic_locked_column")
    public static void turningAfterConfirmationDoesNotRecreateTheColumn(GameTestHelper helper){
        var player=airborneCaster(helper,100,2);var original=new Target(helper,player);var other=new Target(helper,player,-4,0);
        cast(player,205,"水柱");
        helper.runAfterDelay(10,()->player.setYRot(90));
        helper.runAfterDelay(60,()->{cleanup(player,original,other);helper.assertTrue(original.hits==1&&other.hits==0,"Column changed its confirmed formation target after turning");helper.succeed();});
    }

    private static void wall(GameTestHelper helper, int ahead) {
        for (int x = 1; x <= 3; x++) {
            for (int y = 3; y <= 7; y++) helper.setBlock(new BlockPos(x, y, 2 + ahead), Blocks.STONE);
        }
    }

    private static ServerPlayer caster(GameTestHelper helper, int mana) {
        return caster(helper, mana, 2);
    }

    private static ServerPlayer caster(GameTestHelper helper, int mana, int tier) {
        ServerPlayer player = AdventureGameTests.player(helper);
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        player.setYRot(0);
        player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, ElementWands.stack(ElementWands.find("water_wand_" + tier)));
        var data = MagicStone.getOrNull(player);
        data.setAffinity(Element.WATER, tier);
        data.setMaxMana(100);
        data.setMana(mana);
        return player;
    }

    private static ServerPlayer airborneCaster(GameTestHelper helper, int mana, int tier) {
        ServerPlayer player = caster(helper, mana, tier);
        player.setPos(player.getX(), helper.getLevel().getMaxBuildHeight() - 16, player.getZ());
        return player;
    }

    private static Target[] volleyTargets(GameTestHelper helper, ServerPlayer caster) {
        return new Target[]{new Target(helper, caster, -1.05, 5.6), new Target(helper, caster, 0, 5.6),
                new Target(helper, caster, 1.05, 5.6)};
    }

    private static void assertWave(GameTestHelper helper, ServerPlayer caster, Target[] targets, int mana, int hits) {
        int actualMana = MagicStone.getOrNull(caster).getMana();
        int actualHits = 0;
        for (Target target : targets) actualHits += target.hits;
        helper.assertTrue(actualMana == mana && actualHits == hits, "Expected mana/hits " + mana + "/" + hits
                + ", got " + actualMana + "/" + actualHits);
    }

    private static void cast(ServerPlayer caster, long token, String spoken) {
        input(caster, BEGIN, token, "");
        input(caster, FINAL, token, spoken);
    }

    private static void input(ServerPlayer player, int action, long token, String spoken) {
        FreeMagicServer.input(player, new FreeMagicNetwork.Input(action, token, spoken));
    }

    private static void cleanup(ServerPlayer caster, Target... targets) {
        FreeMagicServer.logout(new PlayerEvent.PlayerLoggedOutEvent(caster));
        for (Target target : targets) target.discard();
    }

    private static final class Target extends Pig {
        private final ServerPlayer caster;
        private int hits;
        private float damage;
        private boolean acceptsDamage;

        private Target(GameTestHelper helper, ServerPlayer caster) {
            this(helper, caster, 0, 2.6);
        }

        private Target(GameTestHelper helper, ServerPlayer caster, double right, double forward) {
            super(EntityType.PIG, helper.getLevel());
            this.caster = caster;
            setNoAi(true);
            setNoGravity(true);
            setPos(caster.position().add(right, 1, forward));
            helper.getLevel().addFreshEntity(this);
        }

        @Override public boolean hurt(DamageSource source, float amount) {
            if (source.getEntity() == caster) {
                hits++;
                damage += amount;
            }
            return acceptsDamage;
        }
    }
}
