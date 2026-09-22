package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * 庄鹊让 —— 卷五《代》的角色，TN-C 第四个皮肤 NPC（作者 2026-09-22 要求"先当 NPC 做"）。
 *
 * <p>行为全部继承自 {@link TnDialogueNpc}：站着不动、**不会消失**（四条卸载路径都堵死了）、
 * 打不死、空手右键对话。这个类只声明两件事：<b>用哪张皮肤</b>、<b>播哪条剧本</b>。
 *
 * <p><b>皮肤</b>：{@code assets/tnc/textures/entity/zhuangquerang.png}
 * （64×64 原版人形布局，由 {@code tools/gen_zhuangquerang_skin.ps1} 生成 —— 藏青裙 + 白围裙的
 * 蓝白女仆配色，与她选定的"蓝色调"外形一致，见 {@code docs/当前状态.md} 第四节）。
 * <b>这是占位皮肤</b> ✗，等美术或将来的 UV 重映射工具出正式版。
 *
 * <p><b>剧本</b>：{@code data/tnc/dialogues/zhuangquerang_first.txt}
 * （纯文本，<b>编剧系统 AAA 可直接改</b> ✓；当前是明确标注的占位内容 ✗）。
 *
 * <p><b>二周目</b>：作者的设计是她二周目可以变成"可选可玩角色"，<b>没被选就仍然是这个 NPC</b> ✓。
 * 那条路线（周目状态 + 角色选择）还没实装 ✗，设计记在 {@code docs/当前状态.md} 第四节。
 */
public class ZhuangquerangNpcEntity extends TnDialogueNpc {

    /** 首场剧本：{@code data/tnc/dialogues/zhuangquerang_first.txt}。 */
    public static final ResourceLocation FIRST_DIALOGUE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "zhuangquerang_first");

    public ZhuangquerangNpcEntity(EntityType<? extends ZhuangquerangNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public String skinName() {
        return "zhuangquerang";
    }

    @Override
    public ResourceLocation dialogueId() {
        return FIRST_DIALOGUE;
    }

    /** 属性表（与其它 TN-C NPC 一致；保留静态入口是为了注册处读起来直观）。 */
    public static AttributeSupplier.Builder createAttributes() {
        return TnDialogueNpc.attributes();
    }
}
