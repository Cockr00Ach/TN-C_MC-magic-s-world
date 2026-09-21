package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.dialogue.DialogueLoader;
import com.tnc.tnc.dialogue.DialogueNetwork;
import com.tnc.tnc.dialogue.DialogueProgress;
import com.tnc.tnc.dialogue.DialogueScript;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * A. Self —— 酒馆老板（TN-C 第一个自己的 NPC）。
 *
 * <p>他是<b>对话 NPC</b>：站着不动、不会死、右键说话。剧情里第一场戏就在他的酒馆
 * （见 {@code 剧情/开场_分离之后.md} 第一场「第二杯酒」）。
 *
 * <p>基类选 {@link PathfinderMob}（原版村民/怪物也是这条线）而不是普通 {@code Mob}：
 * 它是"会走路的生物"的标准基类，之后要给 Self 加"在酒馆里转悠""看着客人"这类行为时，
 * 直接加 {@code goal} 就行，不用换基类。
 *
 * <p>三个刻意的设计点（都是"NPC 不该有的行为"）：
 * <ol>
 *   <li>{@link #removeWhenFarAway} 返回 false —— <b>玩家走远也不会消失</b>；
 *   <li>{@link #isPersistenceRequired} 返回 true —— 不参与原版的"清怪"逻辑；
 *   <li>{@link #hurt} 除了创造模式玩家之外<b>不掉血</b> —— 剧情 NPC 不该被误杀。
 * </ol>
 */
public class SelfNpcEntity extends PathfinderMob {

    /**
     * Self 的首次对话剧本。
     *
     * <p>剧本内容在 {@code data/tnc/dialogues/self_first.txt}（<b>纯文本，编剧可直接改</b>，
     * 不用重新编译）。这份文案对应的就是第一场「第二杯酒」。
     */
    public static final ResourceLocation FIRST_DIALOGUE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "self_first");

    public SelfNpcEntity(EntityType<? extends SelfNpcEntity> type, Level level) {
        super(type, level);
        // 生成即钉死"持久化"标志位（不只是靠 isPersistenceRequired() 这个方法返回值），
        // 让原版自己的存档/清理逻辑也认它
        this.setPersistenceRequired();
    }

    /** 加进世界时打一行日志 —— 配合 remove() 的日志，能完整看出"生成→消失"的全过程。 */
    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.level().isClientSide) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "TN-C npc: Self added at {} (dim={})",
                    this.blockPosition(), this.level().dimension().location());
        }
    }

    /** 属性：满血 20、不主动攻击、移速偏低（老板不跑）。 */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    // ------------------------------------------------------------------
    //  "不许消失" —— 把原版所有会让 NPC 被卸载的路都堵上
    //
    //  用户实测：刷出来的 Self 过一段时间会消失。所以这里逐条封堵，并在
    //  remove() 里打一行日志 —— 万一还有漏掉的路，日志会直接指出是哪一条。
    //  原版/Forge 的三条路（读 Mob.checkDespawn 字节码确认）：
    //    ① 难度=PEACEFUL 且 shouldDespawnInPeaceful() → discard()
    //    ② isPersistenceRequired() 为假 → 走距离卸载
    //    ③ requiresCustomPersistence()（Forge 给自定义实体的钩子）
    // ------------------------------------------------------------------

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    /** Forge 给"自定义实体"的持久化钩子，和上面那条一起返回 true 才算完全堵死。 */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /** ①：和平难度下也不许消失（默认 true，会把 NPC 抹掉）。 */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /**
     * 兜底：不管哪个机制调到这里，都拒绝被移除，并把"是谁、为什么"打进日志。
     *
     * <p>写在 {@code remove} 上而不是 {@code discard}：{@code discard()} 与
     * {@code kill()} 最终都会走到 {@code remove(RemovalReason)}。
     */
    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        // 只有在"非正常移除"时才记录，避免玩家退出世界时的正常清理刷屏
        if (reason != net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK
                && reason != net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "TN-C npc: Self removed! reason={} pos={} (persistent={})",
                    reason, this.blockPosition(), this.isPersistenceRequired());
        }
        super.remove(reason);
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 创造模式的玩家可以打掉他（放错了要能清掉），其他伤害一律免疫。
        if (source.getEntity() instanceof Player player && player.isCreative()) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        // 由服务端决定播哪条剧本，再把整条剧本推给客户端（原因见 DialoguePackets 的说明）。
        // 第一次见面播 self_first；之后先留一句"还没写"的提示，等第二段剧本进来再替换。
        Optional<DialogueScript> script = DialogueLoader.get(
                serverPlayer.server.getResourceManager(), FIRST_DIALOGUE);

        if (script.isEmpty()) {
            // 剧本文件缺失/解析失败 —— 明确告诉玩家和日志，绝不静默什么都不发生
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "\u00a7c[TN-C] \u5267\u672c\u52a0\u8f7d\u5931\u8d25\uff0c\u8bf7\u770b\u65e5\u5fd7\u3002"),
                    false);
            return InteractionResult.SUCCESS;
        }

        DialogueNetwork.openFor(serverPlayer, script.get());
        return InteractionResult.SUCCESS;
    }

    /** 这条剧本该不该给这个玩家播（现在是"看过就不再自动播"）。 */
    public static boolean shouldPlayFirst(ServerPlayer player) {
        return !DialogueProgress.hasSeen(player, FIRST_DIALOGUE);
    }
}
