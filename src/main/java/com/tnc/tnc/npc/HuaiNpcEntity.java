package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 熙永槐（槐）—— cava 的儿子，勇者小队的<b>战士</b>（不是法师）。
 *
 * <p>剧情里他是个"铁匠的儿子"：第一段第三场被他爹塞了一面**没打完的盾**跟着归出门，
 * 口头禅是"找到人我就回来，回来跟老爹交差"（见 {@code 剧情/开场_分离之后.md}）。
 *
 * <p>行为继承自 {@link TnDialogueNpc}，与 Self / cava 完全一致；区别只有皮肤与剧本。
 *
 * <h2>外观说明（2026-09-22）</h2>
 * 现在用的是<b>皮肤方案</b>（和 Self / cava 一样）：原版人形模型 + 骑士皮肤
 * （银甲 + 蓝战袍）。用户想要"帅气的骑士模型"，但实测：
 * <ul>
 *   <li>TLM 模型库里**没有骑士/男性模型**（全是东方女仆系角色），借不到；</li>
 *   <li>GeckoLib 自定义模型要靠 Blockbench 建模，本环境没有；</li>
 * </ul>
 * 所以先上皮肤版（功能与外观解耦，以后换模型不用动任何逻辑）。
 * 升级路线见 {@code docs/任务系统_交接.md}。
 */
public class HuaiNpcEntity extends TnDialogueNpc {

    /** 剧本：{@code data/tnc/dialogues/huai_first.txt}。 */
    public static final ResourceLocation FIRST_DIALOGUE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "huai_first");

    public HuaiNpcEntity(EntityType<? extends HuaiNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public String skinName() {
        return "huai";
    }

    @Override
    public ResourceLocation dialogueId() {
        return FIRST_DIALOGUE;
    }
}
