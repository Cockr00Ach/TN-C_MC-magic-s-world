package com.tnc.tnc.npc;

import com.tnc.tnc.dialogue.DialogueLoader;
import com.tnc.tnc.dialogue.DialogueNetwork;
import com.tnc.tnc.dialogue.DialogueProgress;
import com.tnc.tnc.dialogue.DialogueScript;
import net.minecraft.network.chat.Component;
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
 * 剧情 NPC 的公共基类 —— 站着不动、不会消失、杀不死、右键对话。
 *
 * <p>把 Self / cava / riggen 这些**只有皮肤和剧本不同**的 NPC 共用的部分收在这里，
 * 每个 NPC 子类只要给两样东西：
 * <ul>
 *   <li>{@link #skinName()} —— 贴图文件名（{@code textures/entity/<名字>.png}）</li>
 *   <li>{@link #dialogueId()} —— 剧本 id（{@code data/tnc/dialogues/<名字>.txt}）</li>
 * </ul>
 *
 * <h2>"不许消失"的四条路（都是踩过才知道的）</h2>
 * 原版/Forge 会让生物消失的路径有四条，这里**逐条堵死**：
 * <ol>
 *   <li>{@link #removeWhenFarAway} —— 玩家走远</li>
 *   <li>{@link #isPersistenceRequired} —— 距离卸载的总开关</li>
 *   <li>{@link #requiresCustomPersistence} —— Forge 给自定义实体的钩子</li>
 *   <li>{@link #shouldDespawnInPeaceful} —— ★ 和平难度下会被 discard()，最容易漏</li>
 * </ol>
 */
public abstract class TnDialogueNpc extends PathfinderMob {

    protected TnDialogueNpc(EntityType<? extends TnDialogueNpc> type, Level level) {
        super(type, level);
        // 生成即钉死持久化标志位（不只靠方法返回值）
        this.setPersistenceRequired();
    }

    /** 贴图文件名（不含路径与扩展名）：{@code assets/tnc/textures/entity/<名字>.png}。 */
    public abstract String skinName();

    /** 剧本 id：{@code data/tnc/dialogues/<名字>.txt}。 */
    public abstract ResourceLocation dialogueId();

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    // ------------------------------------------------------------------ 不许消失

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (!this.level().isClientSide
                && reason != RemovalReason.UNLOADED_TO_CHUNK
                && reason != RemovalReason.CHANGED_DIMENSION) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "TN-C npc: {} removed! reason={} pos={} (persistent={})",
                    skinName(), reason, this.blockPosition(), this.isPersistenceRequired());
        }
        super.remove(reason);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.level().isClientSide) {
            // 穿上这个 NPC 该有的装备（盔甲层已在 TnNpcRenderer 里挂好，穿上就看得见）。
            // 只做一次：原版实体加入世界只调用一次，读档不会重穿。
            TnNpcEquipment.equip(this);
            com.mojang.logging.LogUtils.getLogger().info(
                    "TN-C npc: {} added at {} (dim={})",
                    skinName(), this.blockPosition(), this.level().dimension().location());
        }
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 创造模式可以打掉（放错了要能清），其他伤害免疫
        if (source.getEntity() instanceof Player player && player.isCreative()) {
            return super.hurt(source, amount);
        }
        return false;
    }

    // ------------------------------------------------------------------ 对话

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        // 服务端决定播哪条剧本，再把整条推给客户端（原因见 DialoguePackets 的注释）
        Optional<DialogueScript> script = DialogueLoader.get(
                serverPlayer.server.getResourceManager(), dialogueId());
        if (script.isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "\u00a7c[TN-C] \u5267\u672c\u52a0\u8f7d\u5931\u8d25\uff1a" + dialogueId()
                            + "\uff08\u770b\u65e5\u5fd7\uff09"), false);
            return InteractionResult.SUCCESS;
        }
        DialogueNetwork.openFor(serverPlayer, script.get());
        return InteractionResult.SUCCESS;
    }

    /** 这个玩家是否已经看过本条剧本（给任务/条件判断用）。 */
    public boolean playerHasSeen(ServerPlayer player) {
        return DialogueProgress.hasSeen(player, dialogueId());
    }
}
