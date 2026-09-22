package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * B. cava —— 铁匠老板（中立派系）。TN-C 第二个自己的 NPC。
 *
 * <p>剧情里他是槐的父亲：第一段第三场「一面没打完的盾」就在他的铁匠铺。
 * （见 {@code 剧情/开场_分离之后.md}；槐要出门历练，是他爹让去的。）
 *
 * <p>行为继承自 {@link TnDialogueNpc}，与 Self 完全一致；区别只有<b>皮肤</b>和<b>剧本</b>。
 */
public class CavaNpcEntity extends TnDialogueNpc {

    /** 剧本：{@code data/tnc/dialogues/cava_first.txt}（纯文本，编剧可直接改）。 */
    public static final ResourceLocation FIRST_DIALOGUE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "cava_first");

    public CavaNpcEntity(EntityType<? extends CavaNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public String skinName() {
        return "cava";
    }

    @Override
    public ResourceLocation dialogueId() {
        return FIRST_DIALOGUE;
    }
}
