package com.tnc.tnc.adventure;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * First playable high-rank tavern investigations. Normal bounties still use the
 * Bountiful board; sneaking at the same board opens these once-per-player jobs.
 * Proof is server-side participation in a real kill, never a tradeable drop.
 */
public final class BossCommissions {
    public record Offer(String id, String name, String entity, String place,
                        int requiredLevel, long copper, long xp, int reputation) {}

    public static final List<Offer> OFFERS = List.of(
            new Offer("wroughtnaut", "炉门里的人", "mowziesmobs:ferrous_wroughtnaut",
                    "寻找锻铁密室，调查仍在守炉的铁甲巨人。", 15, 600, 1200, 60),
            new Offer("naga", "庭院里的蜕皮", "twilightforest:naga",
                    "前往暮色森林的纳迦庭院，带回亲身战斗的记录。", 20, 800, 1600, 80),
            new Offer("harbinger", "没有下班的工厂", "cataclysm:the_harbinger",
                    "深入远古工厂，确认先驱者的失控装置已经停止。", 35, 1500, 2500, 120));

    private BossCommissions() {}

    public static boolean isTarget(String entity) {
        return OFFERS.stream().anyMatch(offer -> offer.entity().equals(entity));
    }

    private static String accepted(Offer offer) { return "special_accepted_" + offer.id(); }
    private static String proof(Offer offer) { return "special_proof_" + offer.id(); }
    private static String paid(Offer offer) { return "special_paid_" + offer.id(); }

    /** Called for each credited participant by AdventureEvents' existing hit ledger. */
    public static void recordParticipant(ServerPlayer player, String entity) {
        var profile = AdventureService.profile(player);
        if (!profile.registered()) return;
        for (var offer : OFFERS) {
            if (!offer.entity().equals(entity) || !profile.hasMilestone(accepted(offer))
                    || profile.hasMilestone(paid(offer)) || profile.hasMilestone(proof(offer))) continue;
            profile.milestones.add(proof(offer));
            AdventureSavedData.get(player.server).setDirty();
            player.sendSystemMessage(Component.literal("特别委托「" + offer.name()
                    + "」已有真实参战记录。回酒馆委托栏潜行右键交差。"));
        }
    }

    static boolean settle(ServerPlayer player, Offer offer) {
        var profile = AdventureService.profile(player);
        if (!profile.hasMilestone(proof(offer)) || profile.hasMilestone(paid(offer))) return false;
        long credited = Math.min(offer.copper(), AdventureRules.MAX_COINS - profile.coins());
        profile.credit(credited, "酒馆特别委托 · " + offer.name());
        profile.addXp(offer.xp());
        profile.reputation = Math.min(1_000_000, profile.reputation + offer.reputation());
        profile.milestones.add(paid(offer));
        AdventureSavedData.get(player.server).setDirty();
        AdventureService.refreshGrowth(player);
        player.sendSystemMessage(Component.literal("✓ 「" + offer.name() + "」结案：+"
                + credited + "铜、+" + offer.xp() + "经验、+" + offer.reputation() + "声望。"));
        return true;
    }

    /** Opened only at the real tavern board by a registered adventure player. */
    public static void visitBoard(ServerPlayer player) {
        var profile = AdventureService.profile(player);
        player.sendSystemMessage(Component.literal("酒馆特别委托 · 潜行右键查看与结算；普通纸张仍直接右键委托栏。"));
        boolean shown = false;
        for (var offer : OFFERS) {
            // Optional mod packs should not advertise an impossible target.
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.parse(offer.entity()))
                    && !profile.hasMilestone(accepted(offer))
                    && !profile.hasMilestone(proof(offer))
                    && !profile.hasMilestone(paid(offer))) continue;
            shown = true;
            if (profile.hasMilestone(paid(offer))) {
                player.sendSystemMessage(Component.literal("✓ 「" + offer.name() + "」已归档。"));
            } else if (profile.hasMilestone(proof(offer))) {
                settle(player, offer);
            } else if (profile.level() < offer.requiredLevel()) {
                player.sendSystemMessage(Component.literal("· 「" + offer.name() + "」需要冒险等级 "
                        + offer.requiredLevel() + "；" + offer.place()));
            } else if (profile.milestones.add(accepted(offer))) {
                AdventureSavedData.get(player.server).setDirty();
                player.sendSystemMessage(Component.literal("◆ 已备案「" + offer.name() + "」：" + offer.place()
                        + "击败目标后回到此处交差；无需上交唯一战利品。"));
            } else {
                player.sendSystemMessage(Component.literal("◆ 进行中「" + offer.name() + "」：" + offer.place()));
            }
        }
        if (!shown) player.sendSystemMessage(Component.literal("当前整合包没有装入这批特别委托的目标首领。"));
    }
}
