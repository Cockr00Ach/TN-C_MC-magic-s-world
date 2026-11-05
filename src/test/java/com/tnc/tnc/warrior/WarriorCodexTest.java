package com.tnc.tnc.warrior;

import com.tnc.tnc.warrior.WarriorCodex.Clause;
import com.tnc.tnc.warrior.WarriorCodex.Entry;
import com.tnc.tnc.warrior.WarriorCodex.Gate;
import com.tnc.tnc.warrior.WarriorCodex.Progress;
import com.tnc.tnc.warrior.WarriorCodex.State;
import com.tnc.tnc.warrior.WarriorCodex.Weapon;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 武器图鉴的目录与判定规则。
 *
 * <p>图鉴是玩家判断"现在能不能练"的唯一依据，所以这里钉的不是排版，而是**目录自洽**：
 * id 不重复、规划中的条目不会冒充已开放、条件文字和判定用的是同一份数据。
 */
class WarriorCodexTest {

    private static Progress progress(int talent, int points, int completions, int best, boolean graduate) {
        return new Progress(talent, points, completions, best, graduate);
    }

    @Test
    void idsAreUniqueAndEveryEntrySitsInOneChain() {
        Set<String> ids = new HashSet<>();
        for (Entry entry : WarriorCodex.all()) {
            assertTrue(ids.add(entry.id()), "重复的条目 id: " + entry.id());
            assertFalse(entry.chain().isEmpty(), entry.id() + " 没有所属链，界面里会掉出列");
            assertTrue(entry.tier() >= 1, entry.id() + " 的级数必须从 1 起");
            assertFalse(entry.name().isEmpty(), entry.id() + " 没有名字");
            assertFalse(entry.effect().isEmpty(), entry.id() + " 没有效果说明");
            assertNotNull(WarriorCodex.byId(entry.id()));
        }
    }

    /**
     * 规划中的条目必须是"干净的提案"：没有教官、永远不是已开放。
     *
     * <p>这条防的是最伤玩家的一种错：把还没实装的东西标成已实装/挂在某位教官名下，
     * 玩家照着去练，练完发现没有那个招式。
     */
    @Test
    void plannedEntriesNeverPretendToBeAvailable() {
        Progress everything = progress(6, TemperingRules.MAX_POINTS, 99, 100, true);
        for (Entry entry : WarriorCodex.all()) {
            if (entry.implemented()) {
                continue;
            }
            assertEquals("", entry.role(), entry.id() + " 是规划条目，不该指向教官");
            assertEquals(State.PLANNED, entry.state(everything),
                entry.id() + " 是规划条目，条件全满也只能是规划中");
        }
    }

    /** 已实装的条目必须写清条件——否则它永远"已开放"，等于白送。 */
    @Test
    void implementedEntriesAllHaveGates() {
        for (Entry entry : WarriorCodex.all()) {
            if (entry.implemented()) {
                assertFalse(entry.gates().isEmpty(), entry.id() + " 已实装却没写条件");
            }
        }
    }

    @Test
    void conditionsAreJudgedAndDisplayedFromTheSameNumbers() {
        Entry entry = WarriorCodex.byId("sword_counter_2");
        assertNotNull(entry);
        Progress partial = progress(2, 1000, 0, 0, false);   // 磨砺够了，训练次数还差
        Progress low = progress(2, 320, 0, 0, false);
        Progress enough = progress(2, 1000, 1, 0, false);

        assertEquals(State.ONGOING, entry.state(partial));
        assertEquals(State.MET, entry.state(enough));

        // 差的是"完成训练 1 次"，不是磨砺：磨砺已经够了
        List<Clause> unmet = entry.unmet(partial);
        assertEquals(1, unmet.size(), "只差训练次数");
        assertEquals(Gate.COMPLETIONS, unmet.get(0).gate());
        assertEquals(2, entry.unmet(low).size(), "磨砺 320 时两条都还没到");
        assertTrue(entry.unmet(enough).isEmpty());

        // 提示文字里的"当前"必须是玩家真实值，不能是写死的文案
        assertTrue(unmet.get(0).text(partial).contains("（当前 0）"),
            "条件提示要带当前值：" + unmet.get(0).text(partial));
        assertTrue(entry.gates().get(0).text(low).contains("1000") && entry.gates().get(0).text(low).contains("320"),
            "磨砺条件提示要带阈值与当前值：" + entry.gates().get(0).text(low));
    }

    /** 进度百分比取所有条件的**最小值**：差一条就是没到，平均值会骗人。 */
    @Test
    void percentIsTheWeakestClause() {
        // 用构造出来的条目测规则本身，不受目录内容改动影响
        Entry entry = new Entry("percent_probe", Weapon.SWORD, "链", 1, "探针", "效果", "来源", true,
            List.of(new Clause(Gate.TEMPER, 3000), new Clause(Gate.TALENT, 3)), "");
        assertEquals(2, entry.gates().size());

        // 磨砺满了、天赋只有 1：整体进度被天赋限制在 1/3
        assertEquals(33, entry.percent(progress(1, 3000, 0, 0, false)));

        // 天赋够了、磨砺一半：被磨砺限制在 50%
        assertEquals(50, entry.percent(progress(3, 1500, 0, 0, false)));

        assertEquals(100, entry.percent(progress(3, 3000, 0, 0, false)));
        assertEquals(0, entry.percent(progress(1, 0, 0, 0, false)));

        // 规划中的条目没有"进度"可言
        Entry planned = new Entry("planned_probe", Weapon.POLEARM, "链", 1, "探针", "效果", "来源", false,
            List.of(new Clause(Gate.TEMPER, 1)), "");
        assertEquals(0, planned.percent(progress(6, 10000, 9, 100, true)));
    }

    @Test
    void noTalentCharacterSeesNothingUnlocked() {
        Progress none = progress(0, 0, 0, 0, false);
        assertEquals(0, WarriorCodex.metCount(none));
        for (Entry entry : WarriorCodex.all()) {
            assertFalse(entry.state(none) == State.MET, entry.id() + " 无天赋角色不该有已开放条目");
        }
    }

    @Test
    void everyWeaponHasAColumnAndTheUnimplementedOnesSaySo() {
        for (Weapon weapon : Weapon.values()) {
            assertFalse(WarriorCodex.chainsOf(weapon).isEmpty(), weapon.cn() + " 没有任何条目，页签点进去会是空白");
            assertFalse(WarriorCodex.of(weapon).isEmpty(), weapon.cn() + " 是空页");
        }
        // 长柄按设计还没开课：页签照样在，但不能有已实装条目；弓已经有连招
        assertTrue(Weapon.POLEARM.anyImplemented(), "长枪已经开课");
        assertTrue(Weapon.BOW.anyImplemented());
        assertTrue(Weapon.SWORD.anyImplemented());
        assertTrue(Weapon.SHIELD.anyImplemented());
        assertTrue(Weapon.WIND.anyImplemented());
    }

    @Test
    void perWeaponCountsOnlyCountImplementedEntries() {
        Progress p = progress(2, 200, 0, 0, false);
        int total = WarriorCodex.implementedCount();
        int sumTotal = 0;
        int sumMet = 0;
        for (Weapon weapon : Weapon.values()) {
            int[] counts = WarriorCodex.metOfImplemented(weapon, p);
            sumTotal += counts[1];
            sumMet += counts[0];
            // 页签上的 "m/t" 不该被规划条目灌水：t 只数已实装
            for (Entry entry : WarriorCodex.of(weapon)) {
                if (!entry.implemented()) {
                    assertEquals(State.PLANNED, entry.state(p));
                }
            }
        }
        assertEquals(total, sumTotal, "各武器已实装数之和应等于总数");
        assertEquals(WarriorCodex.metCount(p), sumMet, "各武器点亮数之和应等于总点亮数");
    }

    @Test
    void unknownIdIsNullNotAnException() {
        assertNull(WarriorCodex.byId("no_such_move"));
    }
}
