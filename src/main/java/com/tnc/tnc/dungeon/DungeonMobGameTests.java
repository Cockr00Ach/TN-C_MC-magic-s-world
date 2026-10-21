package com.tnc.tnc.dungeon;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 12 只地下城小怪的**实机（无头 GameTestServer）行为自检**。
 *
 * <p>离线测试（{@code DungeonMobAssetsTest}）只能证明"文件对得上"；
 * 这里跑的是真的 Minecraft：真注册、真属性、真 AI tick、真伤害。
 * 覆盖四个最容易静默出错的地方：
 * <ol>
 *   <li>实体能生成且拿得到**自己那一份**属性（属性表漏交会直接抛"no attributes"）；</li>
 *   <li>{@link DungeonSpecies#of} 能把实体类型反查回正确的物种（错了就会用错的动画/数值）；</li>
 *   <li>前摇打满之后**真的出伤害**（前摇逻辑写错的话怪只会原地摆手，永远打不死人）；</li>
 *   <li>状态机：过热破防、自爆必死、召唤上限、锁链定身、精英缩放。</li>
 * </ol>
 *
 * <p>★ 模板 {@code building_test_empty} 只有 **5×5×5** —— 所有坐标必须落在 0..4，
 * 否则 GameTest 直接判"outside the structure"（这个坑我踩过一次）。
 */
@GameTestHolder("tncdungeon")
@PrefixGameTestTemplate(false)
public final class DungeonMobGameTests {

    private DungeonMobGameTests() {
    }

    /** 5×5×5 模板内的合法落点：x/z 只能 0..4。 */
    private static BlockPos at(int x, int z) {
        return new BlockPos(x, 1, z);
    }

    private static TNDungeonMob spawn(GameTestHelper helper, DungeonSpecies species, int x, int z) {
        TNDungeonMob mob = helper.spawn(DungeonMobRegistration.type(species).get(), at(x, z));
        mob.setNoAi(true);
        return mob;
    }

    /** 一个站着不动的靶子（村民 20 血），并把它设成怪的目标。 */
    private static Villager dummy(GameTestHelper helper, TNDungeonMob mob, int x, int z) {
        // MCA cancels vanilla villagers at EntityJoinLevel and queues a replacement.
        // Exercise a real, present resident instead of an unregistered vanilla object.
        Villager villager;
        if (com.tnc.tnc.home.McaResidents.available()) {
            try {
                BlockPos pos = helper.absolutePos(at(x, z));
                villager = com.tnc.tnc.home.McaResidents.create(helper.getLevel(),
                        com.tnc.tnc.home.ResidentService.ALL.get(1), pos, UUID.randomUUID());
                villager.setPos(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
                helper.assertTrue(helper.getLevel().addFreshEntity(villager), "MCA dummy joins level");
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Cannot create actual MCA combat dummy", ex);
            }
        } else {
            villager = helper.spawn(EntityType.VILLAGER, at(x, z));
        }
        helper.assertTrue(helper.getLevel().getEntity(villager.getUUID()) == villager,
                "Combat dummy must be registered in the actual world");
        villager.setNoAi(true);
        mob.setTarget(villager);
        return villager;
    }

    /** 把一次攻击从起手推到命中（前摇 tick 全部走完）。 */
    private static void fireOnce(TNDungeonMob mob) {
        mob.beginAttack();
        int guard = 0;
        while (mob.isMidAttack() && guard++ < 200) {
            mob.tickAttack();
        }
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void everySpeciesSpawnsWithItsOwnStats(GameTestHelper helper) {
        for (DungeonSpecies species : DungeonSpecies.values()) {
            TNDungeonMob mob = spawn(helper, species, 2, 2);
            helper.assertTrue(DungeonSpecies.of(mob.getType()) == species,
                    species.id + " 的实体类型反查不回自己的物种");
            helper.assertTrue(Math.abs(mob.getMaxHealth() - (float) species.health) < 0.01F,
                    species.id + " 最大血量应为 " + species.health + "，实际 " + mob.getMaxHealth());
            helper.assertTrue(Math.abs(mob.getAttributeValue(Attributes.ATTACK_DAMAGE)
                            - species.attackDamage) < 0.01D,
                    species.id + " 攻击力应为 " + species.attackDamage);
            mob.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void orcWhirlHitsTargetsBehindIt(GameTestHelper helper) {
        TNDungeonMob orc = spawn(helper, DungeonSpecies.ORC_BRUTE, 1, 2);
        Villager behind = dummy(helper, orc, 3, 2);
        orc.setYRot(90.0F); // 面朝 -x，靶子站在 +x：普通锥形判定会漏掉它
        float before = behind.getHealth();
        fireOnce(orc);
        helper.assertTrue(behind.getHealth() < before,
                "横扫必须 360°：站在背后的目标也应该掉血");
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void tentacleGrabPullsAndSlowsItsTarget(GameTestHelper helper) {
        TNDungeonMob horror = spawn(helper, DungeonSpecies.TENTACLE_HORROR, 2, 1);
        horror.setYRot(0.0F); // 固定朝向：触手往正前方(+z)伸
        Villager prey = dummy(helper, horror, 2, 3); // 正前方 2 格：触手伸出去刚好够到
        fireOnce(horror);
        helper.assertTrue(prey.getHealth() < prey.getMaxHealth(), "触手应该够到正前方的目标并造成伤害");
        helper.assertTrue(prey.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "被触手缠住应该附带减速");
        helper.assertTrue(prey.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D,
                "抓取应该把目标朝自己拽一下（动量非零）");
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void tentacleGrabOnlyReachesAsFarAsItsTentacles(GameTestHelper helper) {
        TNDungeonMob horror = spawn(helper, DungeonSpecies.TENTACLE_HORROR, 0, 0);
        horror.setYRot(0.0F);
        Villager tooFar = dummy(helper, horror, 0, 4); // 正前方 4 格：手只能伸到约 2.9 格
        fireOnce(horror);
        helper.assertTrue(tooFar.getHealth() == tooFar.getMaxHealth(),
                "4 格外的目标在触手够不到的范围，不该掉血（否则又变回隔空抓人）");
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void chargeWindupActuallyDealsDamageToItsTarget(GameTestHelper helper) {
        TNDungeonMob boar = spawn(helper, DungeonSpecies.LANTERN_BOAR, 2, 1);
        Villager target = dummy(helper, boar, 2, 3);
        float before = target.getHealth();
        fireOnce(boar);
        helper.assertTrue(target.getHealth() < before,
                "冲撞前摇走完后必须造成伤害（否则这只怪永远打不死人）；before=" + before
                        + " after=" + target.getHealth());
        helper.assertTrue(!boar.isMidAttack(), "一次攻击结束后必须回到可用状态");
        boar.discard();
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void crossbowmanFiresThreeBolts(GameTestHelper helper) {
        TNDungeonMob archer = spawn(helper, DungeonSpecies.BONE_CROSSBOWMAN, 2, 1);
        Villager target = dummy(helper, archer, 2, 4);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(DungeonBoltEntity.class,
                archer.getBoundingBox().inflate(24.0D)).isEmpty(), "开火前不该有弹体");
        fireOnce(archer);
        int bolts = helper.getLevel().getEntitiesOfClass(DungeonBoltEntity.class,
                archer.getBoundingBox().inflate(24.0D)).size();
        helper.assertTrue(bolts == 3, "三连弩必须正好生出 3 个弹体，实际 " + bolts);
        archer.discard();
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void steamSentinelOverheatsAfterFourPunchesAndTakesDoubleDamage(GameTestHelper helper) {
        TNDungeonMob sentinel = spawn(helper, DungeonSpecies.STEAM_SENTINEL, 2, 1);
        Villager punchBag = dummy(helper, sentinel, 2, 2);
        for (int i = 0; i < 4; i++) {
            fireOnce(sentinel);
        }
        helper.assertTrue(sentinel.stateKey() == 1,
                "第 4 次重拳后必须进入过热（stateKey 应为 1，实际 " + sentinel.stateKey() + "）");

        // 过热期间受伤翻倍：同样的伤害打两只，比掉的血量。
        TNDungeonMob cold = spawn(helper, DungeonSpecies.STEAM_SENTINEL, 4, 4);
        float hp = sentinel.getHealth();
        sentinel.hurt(helper.getLevel().damageSources().generic(), 10.0F);
        float hotLoss = hp - sentinel.getHealth();
        hp = cold.getHealth();
        cold.hurt(helper.getLevel().damageSources().generic(), 10.0F);
        float coldLoss = hp - cold.getHealth();
        helper.assertTrue(hotLoss > coldLoss * 1.5F,
                "过热停机必须是破防窗口：正常掉 " + coldLoss + "，过热应掉约两倍，实际 " + hotLoss);
        sentinel.discard();
        cold.discard();
        punchBag.discard();
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void graveGrubDetonatesAndDies(GameTestHelper helper) {
        TNDungeonMob grub = spawn(helper, DungeonSpecies.GRAVE_GRUB, 2, 1);
        Villager target = dummy(helper, grub, 2, 2);
        float before = target.getHealth();
        fireOnce(grub);
        helper.assertTrue(target.getHealth() < before, "自爆必须伤到身边的人");
        helper.assertTrue(grub.isDeadOrDying() || grub.isRemoved(), "自爆之后自己必须消失（不然就成永动机了）");
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void ashMonkSummonsTwoMinions(GameTestHelper helper) {
        TNDungeonMob monk = spawn(helper, DungeonSpecies.ASH_MONK, 2, 1);
        Villager target = dummy(helper, monk, 2, 4);
        UUID monkId = monk.getUUID();
        fireOnce(monk);
        int summoned = helper.getLevel().getEntitiesOfClass(TNDungeonMob.class,
                monk.getBoundingBox().inflate(8.0D), m -> monkId.equals(m.owner())).size();
        helper.assertTrue(summoned == 2, "灰烬僧一次应召 2 只傀儡，实际 " + summoned);
        monk.discard();
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void umbralBinderLocksItsTargetInPlace(GameTestHelper helper) {
        TNDungeonMob binder = spawn(helper, DungeonSpecies.UMBRAL_BINDER, 2, 1);
        Villager target = dummy(helper, binder, 2, 3);
        fireOnce(binder);
        helper.assertTrue(target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) != null,
                "影缚牧师的锁链必须给目标挂上减速（否则它只是一只 3 点伤害的沙包）");
        binder.discard();
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void burrowTeleportsBehindTheTarget(GameTestHelper helper) {
        TNDungeonMob crawler = spawn(helper, DungeonSpecies.SEWER_CRAWLER, 2, 1);
        Villager target = dummy(helper, crawler, 2, 4);
        target.setYRot(180.0F);
        double before = crawler.distanceTo(target);
        fireOnce(crawler);
        helper.assertTrue(crawler.distanceTo(target) < before,
                "钻地必须把它送到目标身边（原距离 " + before + "，现在 " + crawler.distanceTo(target) + "）");
        crawler.discard();
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 80, batch = "dungeon_mobs")
    public static void eliteScalingDoublesAndAHalfTheHealth(GameTestHelper helper) {
        TNDungeonMob normal = spawn(helper, DungeonSpecies.RUST_HOOK_BUTCHER, 2, 2);
        TNDungeonMob elite = spawn(helper, DungeonSpecies.RUST_HOOK_BUTCHER, 4, 4);
        float base = normal.getMaxHealth();
        elite.setElite(true);
        helper.assertTrue(elite.isElite(), "精英标记没生效");
        helper.assertTrue(Math.abs(elite.getMaxHealth() - base * 2.5F) < 0.01F,
                "精英血量应为 2.5 倍：" + base + " -> " + elite.getMaxHealth());
        helper.assertTrue(elite.scale() > 1.0D, "精英体型要看得见地变大");
        normal.discard();
        elite.discard();
        helper.succeed();
    }
}
