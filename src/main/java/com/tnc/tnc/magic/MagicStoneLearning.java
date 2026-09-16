package com.tnc.tnc.magic;

import com.tnc.tnc.Config;
import net.minecraft.network.chat.Component;

/**
 * 「花魔法点数解锁法术」的判定与执行 —— 服务端权威。
 *
 * <p>命令和网络包都走这里，保证规则只有一份：
 * <ol>
 *   <li>没学过（学过的不能再学）</li>
 *   <li>亲和力够（等级 ≤ 该元素亲和力允许的上限）</li>
 *   <li>不能跳级（必须先学上一级）</li>
 *   <li>点数够（点数 = 魔力上限跨过的档位数）</li>
 * </ol>
 */
public final class MagicStoneLearning {

    public enum Result {
        OK,
        ALREADY_LEARNED,
        AFFINITY_TOO_LOW,
        OUT_OF_ORDER,
        NOT_ENOUGH_POINTS
    }

    private MagicStoneLearning() {
    }

    /** 能不能解锁（不改数据），返回不能的原因；能解锁返回 {@link Result#OK}。 */
    public static Result check(MagicStoneData data, SpellCatalog.Entry entry) {
        if (data.hasLearned(entry.id())) {
            return Result.ALREADY_LEARNED;
        }
        if (entry.tier() > data.maxTierFor(entry.element())) {
            return Result.AFFINITY_TOO_LOW;
        }
        // 不能跳级：看的是**同一条链**的进度（三条链各自算）
        if (entry.tier() > data.getProgress(entry.element(), entry.chain()) + 1) {
            return Result.OUT_OF_ORDER;
        }
        if (data.getPointsAvailable(Config.pointThresholds) < Config.learnCostForTier(entry.tier())) {
            return Result.NOT_ENOUGH_POINTS;
        }
        return Result.OK;
    }

    /** 执行解锁。成功返回 OK，失败不改任何数据。 */
    public static Result unlock(MagicStoneData data, SpellCatalog.Entry entry) {
        Result result = check(data, entry);
        if (result != Result.OK) {
            return result;
        }
        data.spendPoints(Config.learnCostForTier(entry.tier()));
        data.learn(entry.id());
        data.setProgress(entry.element(), entry.chain(),
                Math.max(data.getProgress(entry.element(), entry.chain()), entry.tier()));
        return Result.OK;
    }

    /** 给玩家看的一句话说明（成功和失败都有）。 */
    public static Component describe(Result result, SpellCatalog.Entry entry, MagicStoneData data) {
        int cost = Config.learnCostForTier(entry.tier());
        return switch (result) {
            case OK -> Component.literal("§a[TN-C] §r已解锁 §e" + entry.fullName() + "§r（花 " + cost + " 点，还剩 "
                    + data.getPointsAvailable(Config.pointThresholds) + " 点）");
            case ALREADY_LEARNED -> Component.literal("§7[TN-C] " + entry.displayName() + " 已经学过了");
            case AFFINITY_TOO_LOW -> Component.literal("§c[TN-C] " + entry.element().cn() + "系亲和力不足："
                    + entry.displayName() + " 需要" + Element.tierName(entry.tier())
                    + "，你现在只能学到" + Element.tierName(Math.max(1, data.maxTierFor(entry.element()))));
            case OUT_OF_ORDER -> Component.literal("§c[TN-C] 不能跳级：先把"
                    + entry.element().cn() + "系「" + entry.chain().cn() + "」链的上一级学了"
                    + "（这条链当前进度 " + Element.tierName(data.getProgress(entry.element(), entry.chain())) + "）");
            case NOT_ENOUGH_POINTS -> Component.literal("§c[TN-C] 魔法点数不够：需要 " + cost
                    + " 点，当前可用 " + data.getPointsAvailable(Config.pointThresholds) + " 点");
        };
    }
}
