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
 *
 * <h2>模型：两套并存（2026-09-22，动画系统MMM 加）</h2>
 * <ul>
 *   <li>装了 GeckoLib（整合包一定有，TLM 依赖它）→ 实体是子类
 *       {@link SelfBedrockNpcEntity}，客户端用 GeckoLib 画
 *       {@code assets/tnc/geo/entity/self.geo.json} ✓ ——
 *       <b>只有这条路能播 Blockbench 录的关键帧动作</b>（剧情演出要的擦手/放抹布/看酒杯）✓；</li>
 *   <li>没装 → 就是本类，走原版人形模型（{@code TnHumanoidNpcModel}）+ {@code self.png}（64×64 经典皮肤）✓。</li>
 * </ul>
 * 分流点：{@code com.tnc.tnc.npc.compat.GeoSelfSupport}（那里才引用 GeckoLib 的类型，
 * 避免"没装 GeckoLib 就整个 mod 加载不了" ✗，与庄鹊让那条路同一套写法）。
 *
 * <p>⚠️ 之所以拆成两个类、而不是"一个类按需实现 GeoEntity"：
 * GeckoLib 的 {@code GeoEntityRenderer} 用 {@code instanceof GeoEntity} 判断能不能画 ✗ ——
 * 想在 GeckoLib 环境下用它来画，<b>实体实例本身必须实现那个接口</b> ✓，
 * 所以只能在"注册哪个实体类型"这一层分叉。
 */
public class SelfNpcEntity extends TnDialogueNpc {

    /** 首场剧本：{@code data/tnc/dialogues/self_first.txt}（纯文本，编剧可直接改）。 */
    public static final ResourceLocation FIRST_DIALOGUE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "self_first");

    /**
     * 降级皮肤名（= 文件名，{@code textures/entity/self.png}，64×64 经典布局）。
     *
     * <p>⚠️ <b>不要改成 {@code self_bedrock}</b> ✗ —— 那张 128×128 是给 GeckoLib 的 Bedrock
     * 模型用的（见 {@link SelfBedrockNpcEntity#textureResource()}）；套在原版人形模型上会糊成一团 ✗。
     */
    public static final String SKIN = "self";

    public SelfNpcEntity(EntityType<? extends SelfNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public String skinName() {
        return SKIN;
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
