package com.tnc.tnc.npc.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * 让 NPC 做一次动作 —— <b>整个 mod 里第二个知道 GeckoLib 存在与否的地方</b> ✓
 * （第一个是 {@link MaidNpcSupport} / {@link GeoSelfSupport}）。
 *
 * <p>为什么要有它：调 {@code triggerAnim} 必须走 {@code GeoEntity} 接口，
 * 而那个类型只有装了 GeckoLib 才存在 ✗。台词动作是**服务端**触发的
 * （见 {@code DialoguePackets.ActionRequest}），服务端上任何一处直接写 GeckoLib 的类型，
 * 没装 GeckoLib 的环境就会 {@code NoClassDefFoundError} ✗ —— 所以这里做三件事：
 * <ol>
 *   <li>{@link #trigger} 自己只用 {@code ModList} 判断（不碰 GeckoLib 的类 ✓）；</li>
 *   <li>真正引用 GeckoLib 的代码关在内部类 {@code Geo} 里，JVM 用到才解析 ✓；</li>
 *   <li>所有失败路径**只记日志、不抛异常** ✓ —— 一个动作播不出来，绝不能把游戏带下水。</li>
 * </ol>
 *
 * <h2>校验（★ 防"客户端能指挥任何生物"）</h2>
 * 触发前会核对：目标实体在玩家附近的**已加载区块**里、而且确实是
 * {@code TnDialogueNpc}（我们自己的剧情 NPC）✓。动作名也必须是纯 ASCII、
 * 不含空格 —— 与剧本里的 {@code @act} 约定一致 ✓。
 */
public final class NpcAnimationTrigger {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 动作名长度上限（防脏数据；正常名字都很短） */
    private static final int MAX_NAME = 48;

    private NpcAnimationTrigger() {
    }

    /**
     * 让 {@code npc} 播一次 {@code action}。
     *
     * @param player 请求方（用来核对距离 —— 只允许让**自己跟前**的 NPC 做动作）
     * @param npc    目标实体（调用方已按 UUID 查出来了）
     * @param action 动画名（必须与 {@code assets/tnc/animations/entity/*.json} 里的名字一致）
     * @return 真的触发了吗（给调用方/日志用）
     */
    public static boolean trigger(net.minecraft.server.level.ServerPlayer player,
                                  Entity npc, String action) {
        if (player == null || npc == null || action == null) {
            return false;
        }
        String name = action.trim();
        if (name.isEmpty() || name.length() > MAX_NAME || !isPlainAscii(name)) {
            LOGGER.warn("TN-C anim: rejected action name '{}' (must be a short plain ASCII word)", action);
            return false;
        }
        // ★ 只认我们自己的剧情 NPC，而且必须在玩家跟前 ——
        //   否则客户端能借这个包指挥世界上的任何生物、或隔着一整个地图触发 ✗。
        if (!(npc instanceof com.tnc.tnc.npc.TnDialogueNpc)) {
            LOGGER.warn("TN-C anim: entity {} is not a TN-C dialogue NPC -> '{}' ignored",
                    npc.getUUID(), name);
            return false;
        }
        double distance = player.distanceTo(npc);
        if (distance > MAX_DISTANCE) {
            LOGGER.warn("TN-C anim: '{}' ignored - NPC {} is {:.1f} blocks away (max {})",
                    name, npc.getUUID(), distance, MAX_DISTANCE);
            return false;
        }
        if (!GeoSelfSupport.available()) {
            // 没装 GeckoLib：自研 NPC 只有皮肤版，没有关键帧可播 —— 只记一行，不算错误
            LOGGER.info("TN-C anim: skipping '{}' (no GeckoLib installed)", name);
            return false;
        }
        return Geo.play(npc, name);
    }

    /** 允许的最大距离（格）。玩家正对着 NPC 说话，正常都在 5 格以内 ✓ */
    private static final double MAX_DISTANCE = 16.0D;

    /** 只允许 a-z A-Z 0-9 _ -（动作名会进日志，也可能进未来的资源路径） */
    private static boolean isPlainAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * 真正碰 GeckoLib 的地方 —— 只有 {@link GeoSelfSupport#available()} 为真时才会被加载 ✓。
     */
    private static final class Geo {
        static boolean play(Entity entity, String action) {
            if (!(entity instanceof software.bernie.geckolib.animatable.GeoEntity geo)) {
                // 皮肤版 NPC（原版人形渲染器）没有关键帧动画 —— 只记一行
                LOGGER.info("TN-C anim: {} has no GeckoLib animatable -> '{}' skipped",
                        entity.getName().getString(), action);
                return false;
            }
            try {
                // 插槽名 "action" 由 NPC 实体自己注册（见 SelfBedrockNpcEntity.registerControllers）
                geo.triggerAnim("action", action);
                LOGGER.info("TN-C anim: triggered '{}' on {}", action, entity.getName().getString());
                return true;
            } catch (Throwable t) {
                // 名字写错 / 该实体没注册这个动作 —— GeckoLib 会抛，我们只报不崩 ✓
                LOGGER.warn("TN-C anim: failed to trigger '{}' on {} -> {}",
                        action, entity.getName().getString(), t.toString());
                return false;
            }
        }
    }
}
