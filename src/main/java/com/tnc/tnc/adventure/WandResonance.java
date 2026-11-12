package com.tnc.tnc.adventure;

import com.tnc.tnc.magic.SpellCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 法杖<b>共鸣</b>：用<b>同元素</b>的法杖放该元素的法术，伤害 +25%。
 *
 * <h2>为什么要有这条</h2>
 * 2026-11-09 作者拍板：法杖不再限制元素（知识在魔法石，任何杖都能放已学的），
 * 但"拿对了元素"要有回报 —— 否则 7 系 ×5 阶共 35 根元素杖就只剩外观了。
 * 所以：<b>能用</b>（任何杖）≠ <b>好用</b>（同元素杖）。
 *
 * <h2>怎么判定"这次伤害是那个法术打的"</h2>
 * SpellEngine 的伤害走 {@code spell_power} 的 DamageType（{@code spell_power:player} /
 * {@code spell_power:mob}），所以看伤害来源的 registry 命名空间就够，不需要追踪法术 id ✓
 * （同 {@code ArcanaBeacon} 的做法：在 {@code LivingHurtEvent} 里直接乘 ✓）
 *
 * <h2>时间窗</h2>
 * 施放的<b>放行</b>时刻打一个窗口（{@value #WINDOW_TICKS} tick），命中基本都落在这个窗口里；
 * 持续伤害类法术每一跳都会重新施放 → 每跳都会重新打窗口 ✓
 */
@Mod.EventBusSubscriber(modid = com.tnc.tnc.TNMod.MODID)
public final class WandResonance {

    private static final Logger LOGGER = LogManager.getLogger("TN-C/resonance");

    /** 同元素伤害倍率。 */
    public static final float DAMAGE_MULTIPLIER = 1.25F;

    /** 窗口时长：3 秒（读条 + 飞行 + 命中都够）。 */
    private static final int WINDOW_TICKS = 60;

    private static final String UNTIL_TAG = "TncWandResonanceUntil";

    /** 只在第一次见到某种伤害类型时打一行日志，方便实机核对判定对不对。 */
    private static final Set<String> SEEN_KEYS = ConcurrentHashMap.newKeySet();

    private WandResonance() {
    }

    /** 玩家手上的法杖（主手优先，其次副手）。 */
    public static ElementWands.Wand heldWand(ServerPlayer player) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (!stack.isEmpty() && stack.getItem() instanceof ElementWands.Wand wand) {
                return wand;
            }
        }
        return null;
    }

    /** 这根杖是不是和这个法术同元素（独立法术不参与共鸣）。 */
    public static boolean matches(ServerPlayer player, SpellCatalog.Entry entry) {
        if (entry == null || entry.element() == null || entry.independent()) {
            return false;
        }
        ElementWands.Wand wand = heldWand(player);
        return wand != null && wand.design.element() == entry.element();
    }

    /** 施放放行时调用：同元素就开窗，不同元素就把窗关掉（避免拿错杖还吃加成）。 */
    public static void mark(ServerPlayer player, SpellCatalog.Entry entry) {
        if (player.getServer() == null) {
            return;
        }
        if (matches(player, entry)) {
            player.getPersistentData().putLong(UNTIL_TAG, player.getServer().getTickCount() + WINDOW_TICKS);
        } else if (player.getPersistentData().contains(UNTIL_TAG)) {
            player.getPersistentData().remove(UNTIL_TAG);
        }
    }

    /** 玩家现在有没有共鸣窗口。 */
    public static boolean active(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        long until = player.getPersistentData().getLong(UNTIL_TAG);
        return until > 0 && server.getTickCount() <= until;
    }

    /**
     * 同元素法术的伤害 +25%。
     *
     * <p>只认 {@code spell_power} 命名空间的伤害 —— 近战、弓、其他 mod 的伤害都不会被放大 ✓
     */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!active(player)) {
            return;
        }
        ResourceLocation key = event.getSource().typeHolder().unwrapKey()
                .map(k -> k.location()).orElse(null);
        if (key == null) {
            return;
        }
        if (SEEN_KEYS.add(key.toString())) {
            // 一次性日志：实机里看一眼就知道共鸣判定有没有踩到正确的伤害类型
            LOGGER.info("TN-C: resonance saw damage type {}", key);
        }
        if (!"spell_power".equals(key.getNamespace())) {
            return;
        }
        event.setAmount(event.getAmount() * DAMAGE_MULTIPLIER);
    }
}
