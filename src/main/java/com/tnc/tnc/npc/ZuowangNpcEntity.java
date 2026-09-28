package com.tnc.tnc.npc;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * 周坐望 —— 「大光魔法师」、最初魔法协会的成立者，后来激流勇退、隐居山林。
 *
 * <p>行为全部继承自 {@link TnDialogueNpc}：站着不动、**不会消失**（四条卸载路径都堵死了）、
 * 打不死、空手右键对话。这个类只声明两件事：<b>用哪张皮肤</b>、<b>播哪条剧本</b>。
 *
 * <h2>他是谁（写台词前先读这段，别写偏）</h2>
 * 出处：{@code 剧情/一周目_第一纪正史.md}、{@code 剧情/第二段_黑暗潮.md}、
 * {@code docs/周坐望_任务设计.md} §一。
 * <ul>
 *   <li><b>就是当年"激活归的魔法石"的那个人</b> —— 他早就看出归与众不同，但**不说**；</li>
 *   <li>衍小时候见过他；后来衍在探寻路上与他交流，发现认知相同，**一起去探寻**；</li>
 *   <li>名字取「**庄周（坐望）**」＝ <b>旁观者</b>；</li>
 *   <li>台词实测（就是他这个人的名牌）：「我不算，我看。」「说了，你们也不改。」
 *       「看了一辈子，总要付一次看钱。」</li>
 *   <li>结局：开门送四人进黑暗深处 → <b>耗尽自己</b>给归换一个瞬间 →
 *       最后**手里留着四块石头**。</li>
 *   <li>元素：<b>光 6 / 暗 0</b>（满档光）。</li>
 * </ul>
 * <p><b>一句话人设</b>：他一辈子在旁边看，看懂了也不说，直到**不说不行**的那一刻 —— 然后他付账。
 *
 * <h2>模型</h2>
 * 装了 GeckoLib（整合包一定有）→ 实体是子类 {@link ZuowangBedrockNpcEntity}，
 * 客户端用 GeckoLib 画 {@code zuowang.geo.json}（作者用 {@code tools/gen_zuowang_model.py} 生成，
 * 银白长发 + 胡须的老法师）✓；
 * 没装 → 就是本类，走原版人形模型 + 64×64 降级皮肤
 * {@code textures/entity/zuowang_humanoid.png} ✓
 * （那张是 {@code tools/gen_zuowang_skin.py} 从 Bedrock 图集的左上 64×64 裁出来的 ——
 * 因为生成器把身体部分**就画在原版 UV 上**，所以裁出来就是一张完整皮肤 ✓）。
 *
 * <p>分流点与 self / cava / huai 同一个：{@code npc/compat/GeoSelfSupport}。
 * 那里才引用 GeckoLib 的类型，避免"没装 GeckoLib 就整个 mod 加载不了" ✗。
 */
public class ZuowangNpcEntity extends TnDialogueNpc {

    /**
     * 首场剧本：{@code data/tnc/dialogues/zuowang.txt}（纯文本，编剧可直接改）。
     *
     * <p>⚠️ 玩家实际看到的是 {@code zuowang_02 / zuowang_03} 里的**某一段** ——
     * 由 {@code DialoguePicker} 按任务进度挑（见 {@code docs/任务系统_交接.md} §6.6）。
     * 这里返回兜底那一段，用于"一条剧本都加载不出来"时的报错信息 ✓。
     */
    public static final ResourceLocation FIRST_DIALOGUE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "zuowang");

    /**
     * 降级皮肤名（= 文件名，{@code textures/entity/zuowang_humanoid.png}，64×64 原版布局）。
     *
     * <p>⚠️ <b>不要写成 {@code zuowang}</b> ✗ —— 那个名字会去要一张 64×64 的
     * {@code zuowang.png}；我们手里的是 128×128 的 {@code zuowang_bedrock.png}（GeckoLib 用），
     * 套到原版人形模型上会糊成一团 ✗。
     */
    public static final String SKIN = "zuowang_humanoid";

    public ZuowangNpcEntity(EntityType<? extends ZuowangNpcEntity> type, Level level) {
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

    /** 属性表（与其它 TN-C NPC 一致；保留静态入口是为了注册处读起来直观）。 */
    public static AttributeSupplier.Builder createAttributes() {
        return TnDialogueNpc.attributes();
    }
}
