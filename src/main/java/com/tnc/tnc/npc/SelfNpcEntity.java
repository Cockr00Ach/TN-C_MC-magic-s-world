package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.dialogue.DialogueProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * A. Self —— 酒馆老板（中立派系）。TN-C 第一个自己的 NPC。
 *
 * <p>剧情第一场「第二杯酒」就在他的酒馆里（见 {@code 剧情/开场_分离之后.md}）。
 *
 * <p>行为全部继承自 {@link TnDialogueNpc} —— 站着不动、<b>不会消失</b>（四条卸载路径都堵死了）、
 * 打不死、右键对话。这里只声明两件事：<b>用哪张皮肤</b>、<b>播哪条剧本</b>。
 */
public class SelfNpcEntity extends TnDialogueNpc {

    /** 首场剧本：{@code data/tnc/dialogues/self_first.txt}（纯文本，编剧可直接改）。 */
    public static final ResourceLocation FIRST_DIALOGUE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "self_first");

    public SelfNpcEntity(EntityType<? extends SelfNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public String skinName() {
        return "self";
    }

    @Override
    public ResourceLocation dialogueId() {
        return FIRST_DIALOGUE;
    }

    /** 属性表（与其它 TN-C NPC 一致；这里保留静态入口是为了注册处读起来直观）。 */
    public static AttributeSupplier.Builder createAttributes() {
        return TnDialogueNpc.attributes();
    }

    /** 这个玩家是否还没看过第一场（以后接任务条件时会用到）。 */
    public static boolean shouldPlayFirst(ServerPlayer player) {
        return !DialogueProgress.hasSeen(player, FIRST_DIALOGUE);
    }
}
