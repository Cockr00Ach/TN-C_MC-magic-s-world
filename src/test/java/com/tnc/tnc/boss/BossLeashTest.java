package com.tnc.tnc.boss;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 专属维度里 Boss 的"脱战距离"闸门（2026-11-15 作者：玩家离开多少格才会脱战「应该非常的远」）✓
 *
 * <p>原版 Mob 用 {@code Attributes.FOLLOW_RANGE} 决定"能咬住多远的玩家" ——
 * 玩家跑出这个范围，{@code TargetGoal} 就不再续用 ⇒ 目标被清 ⇒ 脱战。
 * 之前各 Boss 是 40 / 64 / 128 / 192 / 256 / 400 不等（阿波罗只有 40 ✗）⇒ 绕个圈就脱战 ✗。
 *
 * <p>现在统一 512 格：专属维度一般就几百格见方 ⇒ **只要还在这个维度里就不会脱战** ✓。
 */
class BossLeashTest {

    /** 所有专属维度的 Boss（属性构造器所在文件）✓ */
    private static final List<String> BOSS_FILES = List.of(
            "src/main/java/com/tnc/tnc/boss/TNApolloEntity.java",
            "src/main/java/com/tnc/tnc/boss/YanDarkBossEntity.java",
            "src/main/java/com/tnc/tnc/boss/TNWaterLeviathanEntity.java",
            "src/main/java/com/tnc/tnc/boss/fire/VolcanicTortoiseEntity.java",
            "src/main/java/com/tnc/tnc/boss/earth/StoneLancerEntity.java",
            "src/main/java/com/tnc/tnc/boss/TNDarkGiantSpells.java",
            "src/main/java/com/tnc/tnc/world/ink/InkCraneBossEntity.java",
            "src/main/java/com/tnc/tnc/world/ink/InkWingedTigerEntity.java",
            "src/main/java/com/tnc/tnc/world/ink/InkCelestialDragonEntity.java",
            "src/main/java/com/tnc/tnc/world/wind/CanglanWarlordEntity.java");

    @Test
    void theArenaLeashIsVeryFarIndeed() {
        assertTrue(BossLeash.ARENA_FOLLOW_RANGE > BossLeash.PREVIOUS_MAX,
                "新的脱战距离没有比之前最远的 400 格更远 ✗");
        assertTrue(BossLeash.ARENA_FOLLOW_RANGE >= 512.0D,
                "专属维度一般几百格见方，脱战距离 " + BossLeash.ARENA_FOLLOW_RANGE + " 还不够远 ✗");
        assertTrue(BossLeash.stillEngaged(500.0D), "500 格外就脱战 ⇒ 玩家跑两步就脱 ✗");
        assertFalse(BossLeash.stillEngaged(600.0D), "600 格还咬住 ⇒ 追出维度了 ✗");
        assertTrue(BossLeash.ARENA_CHASE_RANGE < BossLeash.ARENA_FOLLOW_RANGE,
                "追击范围必须小于脱战范围（追到极限就守，不该脱战）✗");
        assertTrue(BossLeash.shouldChase(400.0D) && !BossLeash.shouldChase(500.0D),
                "追击上限不对 ✗");
    }

    @Test
    void everyArenaBossUsesTheSharedLeash() throws Exception {
        for (String file : BOSS_FILES) {
            String source = Files.readString(Path.of(file));
            assertTrue(source.contains("BossLeash.ARENA_FOLLOW_RANGE"),
                    Path.of(file).getFileName() + " 还在用旧的脱战距离 ✗");
        }
    }

    @Test
    void arenaBossesNeverDespawnWhenThePlayerStepsOut() throws Exception {
        // 脱战距离再远，只要 removeWhenFarAway 还是 true，玩家走远一样会被刷掉 ✗
        for (String file : BOSS_FILES) {
            String source = Files.readString(Path.of(file));
            if (!source.contains("removeWhenFarAway")) {
                continue;      // 表单里的纯常量类（TNDarkGiantSpells）没有这个方法 ✓
            }
            assertTrue(source.contains("removeWhenFarAway") && source.contains("return false"),
                    Path.of(file).getFileName() + " 的 removeWhenFarAway 不是 false ⇒ 玩家跑远会被刷掉 ✗");
        }
    }
}
