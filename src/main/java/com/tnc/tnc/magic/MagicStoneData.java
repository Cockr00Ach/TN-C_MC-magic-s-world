package com.tnc.tnc.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 魔法石的全部玩家数据（设计文档第三、四节）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>魔法石本身是<b>隐藏数据</b>，不是背包物品 —— 所以这里就是一个纯数据对象。</li>
 *   <li>亲和力 1–6，<b>天生固定</b>，每人每元素一个值。</li>
 *   <li>魔力值上限 = f(亲和力总和) + 原版等级成长；魔力值在上限内随时间恢复。</li>
 *   <li>魔法点数 = 按魔力上限的档位给（100→1 / 300→2 / 500→3 …），用来<b>解锁法术</b>。</li>
 *   <li>每元素有独立进度（学到第几级），学习<b>不可撤回</b>，只能靠遗忘药水重置。</li>
 * </ul>
 *
 * <p>数值都放在 {@link com.tnc.tnc.Config} 里，方便按设计文档第十三节的待确认清单调。
 */
public class MagicStoneData {

    public static final int ELEMENT_COUNT = Element.values().length;

    /** 亲和力下限 / 上限（设计文档第二节：1–6）。 */
    public static final int MIN_AFFINITY = 1;
    public static final int MAX_AFFINITY = 6;

    // ---------------- 持久化字段 ----------------

    private final int[] affinity = new int[ELEMENT_COUNT];
    /** 每元素已学到的等级 0–5（0 = 还没学）。 */
    private final int[] progress = new int[ELEMENT_COUNT];
    private final Set<ResourceLocation> learned = new LinkedHashSet<>();

    private int mana;
    private int maxMana;
    private int pointsSpent;

    /**
     * 额外赠送的魔法点数（调试/剧情奖励用）。
     *
     * <p>为什么单独一个字段而不是去改 {@link #pointsSpent}：{@code pointsSpent} 是"花掉了多少"，
     * 把它改成负数只是让账面对不上；单独记"额外给了多少"账目才是清楚的。
     * 可用点数 = 档位总点数 + 额外赠送 − 已花掉。
     */
    private int bonusPoints;

    /**
     * 亲和力是否已经分配过。亲和力天生固定，只在第一次进游戏时掷一次，
     * 之后永久不变（除非用遗忘药水之类的道具重置）。
     */
    private boolean initialized;

    public MagicStoneData() {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            affinity[i] = 0;
            progress[i] = 0;
        }
        this.mana = 0;
        this.maxMana = 0;
        this.pointsSpent = 0;
        this.initialized = false;
    }

    // ------------------------------------------------------------------
    //  亲和力
    // ------------------------------------------------------------------

    public boolean isInitialized() {
        return initialized;
    }

    public int getAffinity(Element element) {
        return affinity[element.ordinal()];
    }

    public void setAffinity(Element element, int value) {
        affinity[element.ordinal()] = clamp(value, MIN_AFFINITY, MAX_AFFINITY);
    }

    public int affinitySum() {
        int sum = 0;
        for (int value : affinity) {
            sum += value;
        }
        return sum;
    }

    /**
     * 第一次进游戏时分配亲和力。目前就是按配置给所有人同一个值（主角 S/M 七元素全为 3），
     * 以后要做"天生随机/剧情决定"就改这里。
     */
    public void assignDefaultAffinities(int value) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            affinity[i] = clamp(value, MIN_AFFINITY, MAX_AFFINITY);
        }
        initialized = true;
        recomputeMaxMana(0, 10, 10, 0);
        mana = maxMana;
    }

    /** 亲和力 → 该元素能学到的最高等级（设计文档第二节的对照表）。 */
    public int maxTierFor(Element element) {
        int a = getAffinity(element);
        if (a <= 0) {
            return 0;
        }
        // 亲和力 5 与 6 都是神级；6 额外解锁"神级奇遇任务"（那部分以后单独做）
        return Math.min(a, 5);
    }

    // ------------------------------------------------------------------
    //  魔力值 / 魔法点数
    // ------------------------------------------------------------------

    public int getMana() {
        return mana;
    }

    public void setMana(int value) {
        this.mana = clamp(value, 0, Math.max(0, maxMana));
    }

    public void addMana(int delta) {
        setMana(this.mana + delta);
    }

    /** 施法消耗；不够就返回 false（调用方决定怎么提示）。 */
    public boolean spendMana(int cost) {
        if (cost < 0 || mana < cost) {
            return false;
        }
        mana -= cost;
        return true;
    }

    public int getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(int value) {
        this.maxMana = Math.max(0, value);
        if (mana > maxMana) {
            mana = maxMana;
        }
    }

    /**
     * 重算魔力上限：亲和力总和 × manaPerAffinity + 原版等级 × manaPerVanillaLevel。
     * 亲和力总和是天生固定的，所以成长只来自原版等级。
     */
    public void recomputeMaxMana(int vanillaLevel, int manaPerAffinity, int manaPerVanillaLevel, int flatBonus) {
        int computed = affinitySum() * manaPerAffinity + Math.max(0, vanillaLevel) * manaPerVanillaLevel + flatBonus;
        setMaxMana(computed);
    }

    /** 已经花掉的点数。 */
    public int getPointsSpent() {
        return pointsSpent;
    }

    /**
     * 总点数 = 魔力上限跨过了几个档位。例如档位 [100,300,500]：
     * 上限 99 → 0 点；120 → 1 点；310 → 2 点；520 → 3 点。
     */
    public int getPointsTotal(List<? extends Integer> thresholds) {
        int total = 0;
        for (Integer threshold : thresholds) {
            if (threshold != null && maxMana >= threshold) {
                total++;
            }
        }
        return total;
    }

    public int getPointsAvailable(List<? extends Integer> thresholds) {
        return Math.max(0, getPointsTotal(thresholds) + bonusPoints - pointsSpent);
    }

    /** 额外赠送的点数（调试/奖励）。 */
    public int getBonusPoints() {
        return bonusPoints;
    }

    public void setBonusPoints(int value) {
        this.bonusPoints = Math.max(0, value);
    }

    public void addBonusPoints(int delta) {
        setBonusPoints(this.bonusPoints + delta);
    }

    /** 投点数解锁法术。点数不够返回 false。 */
    public boolean spendPoints(int cost) {
        if (cost < 0) {
            return false;
        }
        pointsSpent += cost;
        return true;
    }

    /** 遗忘药水：退还该元素所有点数并清空该元素进度与已学法术。 */
    public int resetElement(Element element) {
        int refund = 0;
        List<ResourceLocation> toRemove = new ArrayList<>();
        for (ResourceLocation spell : learned) {
            if (isSpellOfElement(spell, element)) {
                toRemove.add(spell);
            }
        }
        for (ResourceLocation spell : toRemove) {
            learned.remove(spell);
            refund += 1; // 具体退多少等做遗忘药水时再按真实花费算
        }
        progress[element.ordinal()] = 0;
        pointsSpent = Math.max(0, pointsSpent - refund);
        return refund;
    }

    /**
     * 现阶段只按法术 id 的前缀猜元素（tnc:lightning_xxx 之类）。
     * 等做学习系统时应该改成读法术数据里的学派字段。
     */
    private static boolean isSpellOfElement(ResourceLocation spell, Element element) {
        return spell.getPath().contains(element.id());
    }

    // ------------------------------------------------------------------
    //  进度与已学法术
    // ------------------------------------------------------------------

    public int getProgress(Element element) {
        return progress[element.ordinal()];
    }

    public void setProgress(Element element, int value) {
        progress[element.ordinal()] = clamp(value, 0, 5);
    }

    /** 能不能学这个元素这个等级的法术：等级 ≤ 亲和力允许的上限，且不能跳级。 */
    public boolean canLearnTier(Element element, int tier) {
        return tier >= 1 && tier <= maxTierFor(element) && tier <= getProgress(element) + 1;
    }

    public Set<ResourceLocation> getLearned() {
        return Collections.unmodifiableSet(learned);
    }

    public boolean hasLearned(ResourceLocation spell) {
        return learned.contains(spell);
    }

    public boolean learn(ResourceLocation spell) {
        return learned.add(spell);
    }

    public boolean forget(ResourceLocation spell) {
        return learned.remove(spell);
    }

    // ------------------------------------------------------------------
    //  复制（死亡重生 / 换维度时用）
    // ------------------------------------------------------------------

    public void copyFrom(MagicStoneData other) {
        System.arraycopy(other.affinity, 0, this.affinity, 0, ELEMENT_COUNT);
        System.arraycopy(other.progress, 0, this.progress, 0, ELEMENT_COUNT);
        this.learned.clear();
        this.learned.addAll(other.learned);
        this.mana = other.mana;
        this.maxMana = other.maxMana;
        this.pointsSpent = other.pointsSpent;
        this.bonusPoints = other.bonusPoints;
        this.initialized = other.initialized;
    }

    // ------------------------------------------------------------------
    //  NBT
    // ------------------------------------------------------------------

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        ListTag affinityList = new ListTag();
        for (int value : affinity) {
            affinityList.add(net.minecraft.nbt.IntTag.valueOf(value));
        }
        tag.put("Affinity", affinityList);

        ListTag progressList = new ListTag();
        for (int value : progress) {
            progressList.add(net.minecraft.nbt.IntTag.valueOf(value));
        }
        tag.put("Progress", progressList);

        ListTag learnedList = new ListTag();
        for (ResourceLocation spell : learned) {
            learnedList.add(StringTag.valueOf(spell.toString()));
        }
        tag.put("Learned", learnedList);

        tag.putInt("Mana", mana);
        tag.putInt("MaxMana", maxMana);
        tag.putInt("PointsSpent", pointsSpent);
        tag.putInt("BonusPoints", bonusPoints);
        tag.putBoolean("Initialized", initialized);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        readIntArray(tag.getList("Affinity", Tag.TAG_INT), affinity);
        readIntArray(tag.getList("Progress", Tag.TAG_INT), progress);

        learned.clear();
        ListTag learnedList = tag.getList("Learned", Tag.TAG_STRING);
        for (int i = 0; i < learnedList.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(learnedList.getString(i));
            if (id != null) {
                learned.add(id);
            }
        }

        mana = tag.getInt("Mana");
        maxMana = tag.getInt("MaxMana");
        pointsSpent = tag.getInt("PointsSpent");
        bonusPoints = Math.max(0, tag.getInt("BonusPoints"));
        initialized = tag.getBoolean("Initialized");
    }

    private static void readIntArray(ListTag list, int[] target) {
        for (int i = 0; i < target.length && i < list.size(); i++) {
            target[i] = list.getInt(i);
        }
    }

    // ------------------------------------------------------------------
    //  工具
    // ------------------------------------------------------------------

    /** 玩家原版等级（0 起）。 */
    public static int vanillaLevelOf(Player player) {
        return player.experienceLevel;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 给命令/调试用的一行摘要。 */
    public List<String> describe(List<? extends Integer> thresholds) {
        List<String> lines = new ArrayList<>();
        StringBuilder aff = new StringBuilder();
        StringBuilder prog = new StringBuilder();
        for (Element e : Element.values()) {
            if (aff.length() > 0) {
                aff.append(" ");
                prog.append(" ");
            }
            aff.append(e.cn()).append(":").append(getAffinity(e));
            prog.append(e.cn()).append(":").append(getProgress(e));
        }
        lines.add("亲和力   " + aff);
        lines.add("元素进度 " + prog);
        lines.add("魔力     " + mana + " / " + maxMana);
        lines.add("点数     " + getPointsAvailable(thresholds) + " 可用（总 " + getPointsTotal(thresholds)
                + (bonusPoints > 0 ? " + 赠送 " + bonusPoints : "")
                + "，已投 " + pointsSpent + "）");
        lines.add("已学法术 " + (learned.isEmpty() ? "（无）" : joinSpells()));
        return lines;
    }

    private String joinSpells() {
        StringBuilder sb = new StringBuilder();
        for (ResourceLocation spell : learned) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(spell);
        }
        return sb.toString();
    }

    public Collection<ResourceLocation> learnedView() {
        return Collections.unmodifiableCollection(learned);
    }
}
