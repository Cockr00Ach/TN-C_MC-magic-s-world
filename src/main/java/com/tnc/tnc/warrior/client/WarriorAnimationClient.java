package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.WarriorAnimation;
import java.util.HashMap;
import java.util.Map;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端动作镜像：只记"谁在播哪个动作、从第几 tick 开始"，姿势由
 * {@code WarriorPoseMixin} 在玩家模型 {@code setupAnim} 末尾套上去。
 *
 * <p>服务端只广播 {@link WarriorAnimation} + 时长；角度、缓动、骨骼选择全在客户端算，
 * 所以调动作不需要动网络，也不需要重启服务端。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WarriorAnimationClient {

    /** 播放中的动作。{@code startTick} 是客户端全局 tick。 */
    public record Active(WarriorAnimation animation, long startTick, int durationTicks, boolean hold, long refreshedAt) {
        public Active(WarriorAnimation animation,long startTick,int durationTicks,boolean hold){this(animation,startTick,durationTicks,hold,startTick);}

        /** 长按架势的安全线：客户端万一没收到收势包，也不能永远定住。 */
        static final int HOLD_TIMEOUT = 200;

        /** 0..1 的播放进度（含帧间插值）。长按架势永远停在 1。 */
        public float progress(float partial) {
            double raw = (WarriorAnimationClient.ticks - startTick + partial) / (double) durationTicks;
            // 长按架势不需要额外的式子：时间一直往前走，进度自然裁在 1
            // （也就是“停在最后一帧”）；差别只在于 hold 不会过期。
            return (float) Math.max(0, Math.min(1, raw));
        }

        public float weight(float partial) {
            return WarriorMotionBlend.weight(ticks-startTick+partial,durationTicks,hold);
        }

        /** 演完再多留几 tick，避免最后一帧突然回正。 */
        boolean expired(long now) {
            long lived = now - startTick;
            if (hold) {
                return now - refreshedAt > durationTicks + HOLD_TIMEOUT;
            }
            return lived > durationTicks + 3;
        }

        /** 这一帧还在“挥”吗（长按停住的架势不算挥，不该继续拖刀光）。 */
        public boolean swinging(long now) {
            return !hold || now - startTick <= durationTicks;
        }
    }

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static final java.util.Set<WarriorAnimation> reported = java.util.EnumSet.noneOf(WarriorAnimation.class);
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static boolean loggedClips;
    private static long ticks;

    static {
        // 正式客户端里，动画文件是从 Forge 挂上去的 mod 文件里读的：classpath 读不到时
        // 再走一次 ModFile#findResource（jar 和开发目录都支持），避免"文件在包里、
        // 游戏里却只有程序化姿势"这种最难查的情况。
        WarriorComboAnimations.setFallback(path -> {
            net.minecraftforge.fml.ModList modList = net.minecraftforge.fml.ModList.get();
            if (modList == null) {
                return null;
            }
            net.minecraftforge.fml.ModContainer container = modList.getModContainerById(TNMod.MODID).orElse(null);
            if (container == null) {
                return null;
            }
            java.nio.file.Path resolved = container.getModInfo().getOwningFile().getFile()
                    .findResource(path.startsWith("/") ? path.substring(1) : path);
            return resolved == null ? null : java.nio.file.Files.newInputStream(resolved);
        });
    }

    private WarriorAnimationClient() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        if (!loggedClips) {
            // 一次性把"到底读到几条关键帧"写进日志：这样"动画没生效"到底是
            // 文件没打进包、还是注入点没生效，一眼能分开（见 docs/warrior/连招动画制作说明）。
            loggedClips = true;
            LOGGER.info("TN-C: 连招关键帧 {} 条 -> {}", WarriorComboAnimations.all().size(),
                    WarriorComboAnimations.all().keySet());
        }
        if (ticks % 20 == 0) {
            long now = ticks;
            ACTIVE.entrySet().removeIf(entry -> entry.getValue().expired(now));
        }
    }

    public static void accept(int entityId, WarriorAnimation animation, int durationTicks) {
        accept(entityId, animation, durationTicks, false);
    }

    public static void accept(int entityId, WarriorAnimation animation, int durationTicks, boolean hold) {
        if (animation == null || durationTicks <= 0) {
            return;
        }
        if (reported.add(animation) && WarriorComboAnimations.clip(animation) == null) {
            // 这条动作没有骨骼关键帧，客户端会退回程序化姿势。日志里留一句，
            // 免得"动作文件没打进 jar / 名字对不上"再被当成"动画没做进去"。
            LOGGER.warn("TN-C: 动作 {} 没有骨骼关键帧（{}），改用程序化姿势",
                    animation, animation.cn());
        }
        // 分量低的动作不许顶掉正在演的分量高的动作：放完终结技再按 Q/E，不会被自己的
        // 基础挥砍打断；但新连招（同为 1）会直接接管，符合"输一个新连招就紧跟下一套"。
        Active current = ACTIVE.get(entityId);
        if (current != null && !current.expired(ticks)
                && current.animation().priority() > animation.priority()) {
            return;
        }
        if(hold && current!=null && current.hold() && current.animation()==animation){
            ACTIVE.put(entityId,new Active(animation,current.startTick(),current.durationTicks(),true,ticks));
        }else ACTIVE.put(entityId, new Active(animation, ticks, durationTicks, hold));
        // 龙之剑的终结技：动作一到，客户端自己起一条龙（龙本体是真实几何体，不是粒子）。
        // 只入队一个实体 id，解析放到客户端 tick —— 网络包回调不保证在主线程。
        if (animation.finisher()) {
            WarriorDragonRenderer.request(entityId);
        }
    }

    /**
     * 收势：把正在长按的架势换成“已经演完、再停 3 tick”，
     * 这样松手时是软落地而不是硬跳回站姿。
     */
    public static void release(int entityId) {
        Active current = ACTIVE.get(entityId);
        if (current == null || !current.hold()) {
            return;
        }
        ACTIVE.put(entityId, new Active(current.animation(),
                ticks - current.durationTicks(), current.durationTicks(), false));
    }

    /** 立刻停掉某个实体正在演的动作（Shift 中断 / 被僵直打断）。 */
    public static void cancel(int entityId) {
        ACTIVE.remove(entityId);
    }

    /**
     * 根骨骼（{@code bone}）当前绕竖轴的角度，度。
     *
     * <p>这个量不能像别的骨骼那样只套到 {@code body} 上 —— 它要整只模型一起转（回旋斩），
     * 所以由 {@link WarriorPoseRender} 在渲染前旋转 PoseStack；这里只负责按当前进度取值。
     * 没有动作、或者这条动作没有 {@code bone} 轨道时返回 0。
     */
    public static float rootYawDegrees(net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return 0f;
        }
        Active active = active(player.getId());
        if (active == null) {
            return 0f;
        }
        if (active.animation() == WarriorAnimation.SPIN_SLASH) {
            float turn = 360f * active.progress(net.minecraft.client.Minecraft.getInstance().getFrameTime());
            turn=WarriorMotionBlend.root(turn,active.weight(net.minecraft.client.Minecraft.getInstance().getFrameTime()));
            return player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? -turn : turn;
        }
        WarriorComboAnimations.Clip clip = WarriorComboAnimations.clip(active.animation());
        if (clip == null || !clip.moves("bone")) {
            return 0f;
        }
        float t = active.progress(net.minecraft.client.Minecraft.getInstance().getFrameTime());
        float seconds = Math.max(0f, Math.min(1f, t)) * clip.lengthSeconds();
        return WarriorMotionBlend.root(WarriorPose.rootYaw(clip, seconds,
                player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT),active.weight(net.minecraft.client.Minecraft.getInstance().getFrameTime()));
    }

    /** 该实体当前该演的动作；没有/已演完返回 {@code null}。 */
    /** 这个实体这一帧是不是“真的在挥”（长按架势不算）。 */
    public static boolean swinging(int entityId) {
        Active active = active(entityId);
        return active != null && active.swinging(ticks);
    }

    public static Active active(int entityId) {
        Active active = ACTIVE.get(entityId);
        return active == null || active.expired(ticks) ? null : active;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        WarriorTrail.clear();
        WarriorDash.clear();
    }

    @SubscribeEvent
    public static void clone(ClientPlayerNetworkEvent.Clone event) {
        // 重生/换维度换的是新的客户端实体 id，旧动画不该跟过去
        ACTIVE.clear();
        WarriorTrail.clear();
        WarriorDash.clear();
    }
}
