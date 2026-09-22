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
 * <p><b>皮肤</b>：{@code assets/tnc/textures/entity/zhuangquerang_humanoid.png}
 * （64×64 原版人形布局，由 {@code tools/gen_zhuangquerang_skin.ps1} 生成 —— 藏青裙 + 白围裙的
 * 蓝白女仆配色）。
 *
 * <p>⚠️ <b>这只是"降级皮肤"</b> ✗：正常情况（装了 GeckoLib）她的实体是子类
 * {@link ZhuangquerangMaidNpcEntity}，客户端用 GeckoLib 直接画<b>女仆模型</b> ✓
 * （模型/贴图/动画见那个类的说明）。本类的 64×64 皮肤只在没有 GeckoLib 时兜底 ✓。
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

    /**
     * 降级皮肤名 = 文件名（{@code textures/entity/<skinName>.png}）。
     *
     * <p>注意是 {@code zhuangquerang_humanoid} 而**不是** {@code zhuangquerang} ——
     * 后者是 128×128 的女仆图集，套在原版人形模型上会糊成一团 ✗。
     */
    @Override
    public String skinName() {
        return "zhuangquerang_humanoid";
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
