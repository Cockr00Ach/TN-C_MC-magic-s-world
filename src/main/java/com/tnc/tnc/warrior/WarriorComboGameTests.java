package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 连招的服务端闭环：输入顺序 → 命中判定 → 终结技伤害/耐久 → 缓冲清空。
 *
 * <p>僵尸放在 Q/E 轻击够不到、终结技够得到的位置（2.9 格），这样掉血只可能来自
 * 终结技，能钉住"第五段不放、第六段才放"的时序。
 */
@GameTestHolder("tncwarrior")
@PrefixGameTestTemplate(false)
public final class WarriorComboGameTests {

    private static final int SPIN_TICKS = 3;

    private WarriorComboGameTests() {
    }

    private static void clearArena(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(3, 3, 2), new BlockPos(9, 5, 12))) {
            helper.setBlock(pos, Blocks.AIR);
        }
    }

    private static ServerPlayer warrior(GameTestHelper helper, int points) {
        ServerPlayer player = AdventureGameTests.player(helper);
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 3, 3))));
        player.setYRot(0);
        player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        TemperingData data = TemperingData.get(player.server);
        data.setTalent(player.getUUID(), 3);
        data.set(player.getUUID(), points);
        return player;
    }

    /** 2.9 格：Q/E 轻击（2.7）够不到，破锋式（3.0）与旋转斩击（3.6）够得到。 */
    private static Zombie dummy(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 3, 7));
        // spawn 会落在方块中心（相对坐标 6.5, 7.5）；往玩家方向挪 1.1 格，卡在两个半径之间
        zombie.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 3, 7))).add(0, 0, -1.1));
        zombie.setNoAi(true);
        zombie.setInvulnerable(false);
        return zombie;
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 120)
    public static void sixInputSpinFiresOnlyOnTheLastInput(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 1000);
        ItemStack sword = player.getMainHandItem();
        Zombie zombie = dummy(helper);
        float before = zombie.getHealth();
        // 虚影：磨砺 1000/3000 → 倍率 1.30（2026-11-03 成长倍率加倍，原来 1.15），手比基础值长一截
        helper.assertTrue(Math.abs(WarriorPhantom.scale(player) - 1.30) < 1.0E-6,
                "磨砺 1000 时虚影倍率应为 1.30，实际 " + WarriorPhantom.scale(player));

        helper.runAfterDelay(1, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(4, () -> {
            WarriorComboService.input(player, WarriorMove.RIGHT);
            helper.assertTrue(zombie.getHealth()==before,"Two inputs must not trigger spin");
        });
        // 远处木桩：5.5 格——旋转斩击基础半径 3.6 够不到，磨砺 1.30 倍后 4.68 还是不够，
        // 所以这一条应该不中；真正要验证的是「判定半径确实被乘了倍率」
        Zombie far = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 3, 9));
        far.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 3, 9))).add(0, 0, -0.5));
        far.setNoAi(true);
        far.setInvulnerable(false);
        float farBefore = far.getHealth();
        helper.runAfterDelay(7, () -> {
            WarriorComboService.input(player, WarriorMove.HORIZONTAL);
            helper.assertTrue(zombie.getHealth() < before,
                    "第六段应命中旋转斩击；before=" + before + ", now=" + zombie.getHealth());
            helper.assertTrue(far.getHealth() == farBefore,
                    "5.5 格外的木桩连倍率后的 4.68 也够不到，不该被扫到");
            helper.assertTrue(sword.getDamageValue() >= SPIN_TICKS, "终结技要消耗 3 点耐久");
            helper.assertTrue(WarriorComboService.state(player.getUUID()).tracker.isEmpty(), "命中后缓冲清空");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 140)
    public static void lockedChainIsSilentUntilUnlocked(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 0);
        Zombie zombie = dummy(helper);
        float before = zombie.getHealth();

        // 磨砺 0：能接成破锋式，但没解锁 → 不放终结技、不扣耐久
        helper.runAfterDelay(1, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(4, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(7, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(zombie.getHealth() == before, "未解锁的连招不该造成伤害");
            helper.assertTrue(player.getMainHandItem().getDamageValue() == 0, "未解锁的连招不该扣耐久");
            helper.assertTrue(WarriorComboService.state(player.getUUID()).tracker.isEmpty(), "接成后缓冲仍要清空");
        });

        // 补到 200 磨砺：同一串输入立刻能放
        helper.runAfterDelay(20, () -> {
            TemperingData.get(player.server).set(player.getUUID(), 200);
            WarriorComboService.input(player, WarriorMove.LEFT);
        });
        helper.runAfterDelay(23, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(26, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(zombie.getHealth() < before, "解锁后破锋式应造成伤害");
            helper.assertTrue(player.getMainHandItem().getDamageValue() >= 2, "破锋式消耗 2 点耐久");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 120)
    public static void finisherAndSlashesRespectWalls(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 1000);
        Zombie zombie = dummy(helper);
        // 玩家 (6.5, 3.5) 与僵尸 (6.5, 6.4) 之间立一堵 1 格厚的墙，视线被挡
        for (int y = 3; y <= 5; y++) {
            helper.setBlock(new BlockPos(6, y, 5), Blocks.STONE);
        }
        float before = zombie.getHealth();
        helper.runAfterDelay(1, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(4, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        helper.runAfterDelay(7, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(10, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(13, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(16, () -> {
            WarriorComboService.input(player, WarriorMove.HORIZONTAL);
            helper.assertTrue(zombie.getHealth() == before, "隔墙的旋转斩击不该造成伤害");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 100)
    public static void assaultChainActuallyDisplacesThePlayer(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 1000);
        // 测试用的假玩家不在世界实体表里，位置不会被 tick 积分；这里验证"位移已经写进速度"，
        // 方向必须是玩家面朝的方向（yaw=0 时是 +Z）。
        helper.runAfterDelay(1, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(4, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        helper.runAfterDelay(7, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(17, () -> {
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            double forward = player.getDeltaMovement().dot(flat);
            helper.assertTrue(forward > 0.25,
                    "突击链（旋转斩击）必须给玩家一个朝前的速度；forward=" + forward
                            + ", velocity=" + player.getDeltaMovement());
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 140)
    public static void defenseChainDamagesAndStaggers(GameTestHelper helper) {
        clearArena(helper);
        // 逆鳞斩的门槛是 3000 磨砺
        ServerPlayer player = warrior(helper, 3000);
        Zombie zombie = dummy(helper);
        float before = zombie.getHealth();

        helper.runAfterDelay(1, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        helper.runAfterDelay(4, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(16, () -> {
            WarriorComboService.input(player, WarriorMove.HORIZONTAL);
            helper.assertTrue(zombie.getHealth() < before, "逆鳞斩应造成伤害");
            helper.assertTrue(zombie.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                    "防御链必须给目标挂上僵直");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80)
    public static void defenseInterruptClearsAnotherPlayersCombo(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 1000);
        ServerPlayer other = AdventureGameTests.player(helper);
        other.setPos(player.position().add(0, 0, 2.2));
        other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        TemperingData data = TemperingData.get(other.server);
        data.setTalent(other.getUUID(), 3);
        data.set(other.getUUID(), 1000);
        WarriorComboService.input(other, WarriorMove.HORIZONTAL);
        helper.assertTrue(!WarriorComboService.state(other.getUUID()).tracker.isEmpty(),
                "测试准备：目标应先有连招缓冲");
        WarriorComboService.clearCombo(other);
        helper.assertTrue(WarriorComboService.state(other.getUUID()).tracker.isEmpty(),
                "打断必须清空对方正在接的连招");
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 120)
    public static void longPressZTriggersTheStanceAndGuard(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 2000);
        Zombie zombie = dummy(helper);
        float before = zombie.getHealth();

        helper.runAfterDelay(1, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        helper.runAfterDelay(4, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(7, () -> {
            // 先按一下（客户端按下瞬间发的轻点），再补长按包 —— 服务端应该把轻点替换成长按
            WarriorComboService.input(player, WarriorMove.RIGHT);
            helper.assertTrue(WarriorComboService.state(player.getUUID()).tracker.size() == 3,
                    "E Q E 轻点应作为长按的前缀留在缓冲里");
        });
        helper.runAfterDelay(9, () -> {
            WarriorComboService.input(player, WarriorMove.RIGHT, true);
            helper.assertTrue(zombie.getHealth() == before, "架势起手不应主动伤害远处目标");
            helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE),
                    "振刀要给自己短时抗性");
            helper.assertTrue(player.getMainHandItem().getDamageValue() >= 2, "振刀要消耗 2 点耐久");
            helper.assertTrue(WarriorComboService.state(player.getUUID()).tracker.isEmpty(),
                    "命中后缓冲清空");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80)
    public static void unfinishedComboIsCancelledByTakingDamage(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 1000);
        Zombie zombie = dummy(helper);
        // 首领档血量：同一套连招在首领的攻击下不该被打断（2026-11-02 用户指定）
        Zombie boss = dummy(helper);
        boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        boss.setHealth(200);
        helper.assertTrue(WarriorComboService.boss(boss), "测试准备：这只僵尸要被判定为首领");
        WarriorComboService.input(player, WarriorMove.LEFT);
        WarriorComboService.input(player, WarriorMove.RIGHT);
        helper.assertTrue(!WarriorComboService.state(player.getUUID()).tracker.isEmpty(),
                "测试准备：先接两段连招");
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new net.minecraftforge.event.entity.living.LivingDamageEvent(
                        player, player.damageSources().mobAttack(boss), 8.0F));
        helper.assertTrue(!WarriorComboService.state(player.getUUID()).tracker.isEmpty(),
                "首领的攻击不该打断玩家的连招");

        // 普通小怪：前摇里被敌人打一下，连招应当整段作废（还没架起振刀）。
        // 假玩家不在实体表里，走不到完整的 hurt 结算，这里直接投递原版会投递的伤害事件。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new net.minecraftforge.event.entity.living.LivingDamageEvent(
                        player, player.damageSources().mobAttack(zombie), 1.0F));
        helper.assertTrue(WarriorComboService.state(player.getUUID()).tracker.isEmpty(),
                "未完成的连招应该在挨打时被打断");
        helper.succeed();
    }
    @GameTest(template = "building_test_empty", timeoutTicks = 80)
    public static void eachWeaponFamilyAcceptsItsOwnItems(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 3000);
        TemperingData data = TemperingData.get(player.server);
        data.setTalent(player.getUUID(), 3);
        data.set(player.getUUID(), 3000);

        record Case(ItemStack stack, String first, String family) {
        }
        Case[] cases = {
                new Case(new ItemStack(Items.IRON_SWORD), "q", "长剑"),
                new Case(new ItemStack(TNMod.GREATSWORD.get()), "z", "重剑"),
                new Case(new ItemStack(TNMod.HAMMER.get()), "q", "战锤"),
                new Case(new ItemStack(TNMod.SPEAR.get()), "q", "长枪"),
                new Case(new ItemStack(TNMod.BOW.get()), "q", "长弓")
        };
        for (Case c : cases) {
            var st = WarriorComboService.state(player.getUUID());
            st.tracker.clear();
            st.lastInput = 0;
            st.lastSlash = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, c.stack());
            WarriorMove move = WarriorMove.byKey(c.first().charAt(0));
            WarriorComboService.input(player, move);
            helper.assertTrue(!WarriorComboService.state(player.getUUID()).tracker.isEmpty(),
                    c.family() + " 的首段输入应能进入缓冲");
        }
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 120)
    public static void greatswordAssaultHitsWideWithoutDisplacement(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 3000);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TNMod.GREATSWORD.get()));
        Zombie zombie = dummy(helper);
        float before = zombie.getHealth();
        helper.runAfterDelay(1, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(4, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        helper.runAfterDelay(7, () -> {
            WarriorComboService.input(player, WarriorMove.HORIZONTAL);
            helper.assertTrue(zombie.getHealth() < before, "重剑破阵斩应命中身前的僵尸");
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            // 2026-11-02 用户要求"每个连招都能有位移"：重剑现在会垫一小步，
            // 但不能像长剑那样冲出去（0.62 × 步长）
            helper.assertTrue(player.getDeltaMovement().dot(flat) < 0.6,
                    "重剑的突击链只该垫一小步，不能像长剑那样冲出去");
            helper.assertTrue(player.getDeltaMovement().dot(flat) > 0.0,
                    "每一招都应该有位移（用户 2026-11-02）");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 120)
    public static void bowAttackFiresAnArrowAndEvadeBacksteps(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 3000);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TNMod.BOW.get()));
        player.getInventory().add(new ItemStack(Items.ARROW, 5));
        dummy(helper);
        helper.runAfterDelay(1, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(4, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(7, () -> {
            WarriorComboService.input(player, WarriorMove.RIGHT);
            helper.assertTrue(!player.level().getEntitiesOfClass(
                            net.minecraft.world.entity.projectile.Arrow.class,
                            player.getBoundingBox().inflate(16), arrow -> arrow.getOwner()==player).isEmpty(),
                    "长弓穿云箭应真的射出箭");
            helper.assertTrue(arrows(player) == 4, "穿云箭要消耗一支箭，背包应剩 4，实际 " + arrows(player));
        });
        helper.runAfterDelay(20, () -> {
            WarriorComboService.state(player.getUUID()).tracker.clear();
            WarriorComboService.state(player.getUUID()).lastInput = 0;
            WarriorComboService.input(player, WarriorMove.HORIZONTAL);
        });
        helper.runAfterDelay(23, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(26, () -> {
            WarriorComboService.input(player, WarriorMove.RIGHT);
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            helper.assertTrue(player.getDeltaMovement().dot(flat) < -0.2, "后跃射应把玩家向后推开");
            helper.assertTrue(player.invulnerableTime > 0, "后跃射应给短暂无敌");
            helper.assertTrue(arrows(player) == 3, "后跃射也要消耗一支箭，背包应剩 3，实际 " + arrows(player));
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 120)
    public static void bowWithoutArrowsFiresNothingAndStaysOffCooldown(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 3000);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TNMod.BOW.get()));
        dummy(helper);
        helper.runAfterDelay(1, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(4, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(7, () -> {
            WarriorComboService.input(player, WarriorMove.RIGHT);
            helper.assertTrue(player.level().getEntitiesOfClass(
                            net.minecraft.world.entity.projectile.Arrow.class,
                            player.getBoundingBox().inflate(16), arrow -> arrow.getOwner()==player).isEmpty(),
                    "没箭的时候不该凭空射出一支箭");
            helper.assertTrue(WarriorComboService.state(player.getUUID()).cooldowns.isEmpty(),
                    "没箭失败不该占用冷却，补箭后应能立刻再放");
        });
        helper.runAfterDelay(12, helper::succeed);
    }

    /** 背包 + 副手里所有"箭"的数量，用来验证消耗。 */
    private static int arrows(ServerPlayer player) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.ArrowItem) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80)
    public static void stepCancelsTheComboAndPushesForward(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 1000);
        WarriorComboService.input(player, WarriorMove.LEFT);
        helper.assertTrue(!WarriorComboService.state(player.getUUID()).tracker.isEmpty(),
                "测试准备：先接一段连招");
        WarriorComboService.step(player, true);
        helper.assertTrue(WarriorComboService.state(player.getUUID()).tracker.isEmpty(),
                "按 Shift 必须立刻停下自己的连招");
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        helper.assertTrue(player.getDeltaMovement().dot(flat) > 0.2, "短按/长按 Shift 都要给一个向前的冲刺");
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED),
                "长按 Shift 还要给加速跑");
        helper.succeed();
    }

    /** 用户口述：短按冲一大段、长按只挪一小段 + 加速跑。 */
    @GameTest(template = "building_test_empty", timeoutTicks = 80)
    public static void shiftTapTravelsFurtherThanHold(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 1000);
        WarriorComboService.step(player, false);
        double tap = player.getDeltaMovement().horizontalDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
        WarriorComboService.step(player, true);
        double hold = player.getDeltaMovement().horizontalDistance();
        helper.assertTrue(tap > hold * 1.5,
                "短按要冲得比长按远得多：tap=" + tap + ", hold=" + hold);
        helper.assertTrue(hold > 0.1, "长按也要动，只是距离小：" + hold);
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED),
                "长按要带加速跑");
        helper.succeed();
    }

    /**
     * 放完终结技的这段时间里，短按不能顶掉动作（{@code performing} 为真），
     * 演完自动解锁；解锁后新连招照常能接。
     */
    @GameTest(template = "building_test_empty", timeoutTicks = 140)
    public static void finisherKeepsItsPoseUntilItEnds(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 1000);
        Zombie zombie = dummy(helper);
        float before = zombie.getHealth();

        helper.runAfterDelay(1, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(4, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(7, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(zombie.getHealth() < before, "测试准备：破锋式要放出来");
            long now = WarriorComboService.gameTime(player);
            helper.assertTrue(WarriorComboService.performing(player, now),
                    "终结技动作还在演，这段时间短按不该广播基础挥砍");
            helper.assertTrue(!WarriorComboService.performing(player, now + 60),
                    "动作演完必须自动解锁，否则后面的连招全被挡住");
        });
        // 动作演完之后马上还能再放一套（"再输一个连招就能紧跟下一套"）
        helper.runAfterDelay(60, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(63, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(66, () -> {
            helper.assertTrue(!WarriorComboService.performing(player, WarriorComboService.gameTime(player)),
                    "没接满时不该有锁");
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 200)
    public static void staggerLocksTheComboUntilItExpires(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 3000);
        Zombie zombie = dummy(helper);
        float before = zombie.getHealth();
        ItemStack sword = player.getMainHandItem();

        // 被架住/被破架后的僵直：这段时间里接成了连招也放不出去，而且不占冷却
        helper.runAfterDelay(1, () -> WarriorComboService.stagger(player, WarriorDuel.PARRY_STAGGER));
        helper.runAfterDelay(2, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(5, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(8, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(zombie.getHealth() == before, "僵直期间连招不该造成伤害");
            helper.assertTrue(WarriorComboService.state(player.getUUID()).cooldowns.isEmpty(),
                    "僵直挡下的连招不该占冷却");
            helper.assertTrue(sword.getDamageValue() == 0, "没放出去就不该扣耐久");
            // 假玩家不在实体表里、不会自己 tick，僵直挂的 debuff 永远不会过期，
            // 攻击力会被虚弱压到 0 —— 这里手动清掉，才能验证"僵直结束后真的能放"。
            player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            player.removeEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
        });

        // 僵直结束（20 tick 后）同一串输入立刻能放
        helper.runAfterDelay(60, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(63, () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        helper.runAfterDelay(66, () -> {
            WarriorComboService.input(player, WarriorMove.RIGHT);
            helper.assertTrue(zombie.getHealth() < before, "僵直结束后应能正常放出破锋式；hp=" + zombie.getHealth()
                    + " before=" + before + " 攻击力=" + player.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(sword.getDamageValue() >= 2, "放出去就要扣 2 点耐久");
        });
        helper.runAfterDelay(80, helper::succeed);
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 120)
    public static void stanceWindowOpensWhenTheParryFires(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 3000);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TNMod.GREATSWORD.get()));
        // 重剑 QEE = 架势·壁立：放出去就该同时拿到"架势窗口"和抗性，
        // 窗口没了但抗性还在（或反过来）都会让"架住"永远触发不了。
        helper.runAfterDelay(1, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        helper.runAfterDelay(4, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        helper.runAfterDelay(7, () -> {
            WarriorComboService.input(player, WarriorMove.RIGHT, true);
            var state = WarriorComboService.state(player.getUUID());
            long now = WarriorComboService.gameTime(player);
            helper.assertTrue(state.stanceUntil > now, "壁立放出去要开一个架势窗口");
            helper.assertTrue(state.stanceUntil - now >= 30, "架势窗口至少 1.5 秒才有意义");
            helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE),
                    "架势窗口里要带抗性，和振刀一致");
        });
        helper.runAfterDelay(12, helper::succeed);
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80)
    public static void nonSwordAndUnenrolledPlayersAreIgnored(GameTestHelper helper) {
        clearArena(helper);
        ServerPlayer player = warrior(helper, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        WarriorComboService.input(player, WarriorMove.LEFT);
        helper.assertTrue(WarriorComboService.state(player.getUUID()).tracker.isEmpty(),
                "拿着木棍不该吃长剑连招输入");

        ServerPlayer stranger = AdventureGameTests.player(helper);
        stranger.setPos(player.position());
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        WarriorComboService.input(stranger, WarriorMove.LEFT);
        helper.assertTrue(WarriorComboService.state(stranger.getUUID()).tracker.isEmpty(),
                "没有战士天赋的玩家不该有连招缓冲");
        helper.succeed();
    }
}
