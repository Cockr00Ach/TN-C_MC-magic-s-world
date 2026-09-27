package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * 雷速链的<b>第一人称表现</b>：速度越快，视野越拉 —— 让"快"这件事在屏幕上看得见。
 *
 * <h2>为什么这个最划算</h2>
 * 自身增益法术（SELF）在引擎里没有任何模型/投射物，光靠粒子很难传达"我现在很快"。
 * 而原版本身就用 FOV 表达速度（冲刺时会轻微拉伸视野）✓ ——
 * 我们只要在 Forge 的 {@link ComputeFovModifierEvent} 里再乘一个系数，
 * 不写 mixin、不画 UI，几行代码就有明显手感 ✓。
 *
 * <h2>数值</h2>
 * 取<b>最高的那一档</b>而不是相乘 ✗（否则三样叠加会拉到头晕）：
 * 雷速 +3%、极速雷风 +8%、登神 +5%。基础值来自 {@code event.getFovModifier()}
 * （原版已经把冲刺/弓弩蓄力等算进去了 ✓）。
 *
 * <p>挂在 <b>FORGE 总线 + Dist.CLIENT</b>：这是游戏事件、且只在客户端存在 ✓
 * （挂错总线会静默不生效，项目里踩过 —— 见 MagicStoneKeys 的注释）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNSpellClientVisuals {

    /** 雷速（t1）：轻微 */
    private static final float FOV_LIGHTNING_HASTE = 1.03F;
    /** 极速雷风（t3）：明显 */
    private static final float FOV_LIGHTNING_WIND = 1.08F;
    /** 闪电登神（t5）：中等（它已经有满身电弧了，FOV 不用太夸张） */
    private static final float FOV_LIGHTNING_ASCENSION = 1.05F;

    private TNSpellClientVisuals() {
    }

    // ---- 爆炸震屏（2026-09-22）：附近出现冲击波实体时给镜头加抖动 ----
    // 为什么走这条路：冲击波实体是服务端在爆炸点生成的 ✓ 而客户端本来就能看到它 ✓，
    // 所以"震屏"不需要任何网络包 —— 客户端自己每 tick 就近侦测一次即可 ✓。
    private static float shake = 0.0F;
    /** 白闪强度（0~1，每 tick ×0.72）✓ */
    private static float flash = 0.0F;
    /** 冲击波震屏：贴脸时多大（度）＋ 影响半径（格）✓ 作者要求"参考核弹" → 拉到 14 / 40 ✓ */
    private static final double SHAKE_MAX = 9.0D;
    private static final double SHAKE_RANGE = 40.0D;
    private static final net.minecraft.util.RandomSource SHAKE_RANDOM = net.minecraft.util.RandomSource.create();

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            shake = 0.0F;
            return;
        }
        // 20 格内有冲击波 => 抖一下（越近越猛）
        for (com.tnc.tnc.magic.TNShockwaveEntity wave : minecraft.level.getEntitiesOfClass(
                com.tnc.tnc.magic.TNShockwaveEntity.class, minecraft.player.getBoundingBox().inflate(20.0D))) {
            double distance = wave.position().distanceTo(minecraft.player.position());
            // 2026-09-27 作者："爆炸特效感觉好拉，我感受不到那种爆炸的感觉，多加一点镜头晃动吧，参考一下核弹"
            //   → 震幅 4.0→14.0、作用半径 20→40 格、衰减 0.80→0.88（摇得更久 ✓）
            float strength = (float) (SHAKE_MAX * Math.max(0.0D, 1.0D - distance / SHAKE_RANGE));
            if (strength > shake) {
                shake = strength;
            }
            // 同一发冲击波顺手点一下白闪（越近越白 ✓）——"核弹感"主要靠它 ＋ 抖 ✓
            float f = (float) Math.max(0.0D, 1.0D - distance / SHAKE_RANGE);
            if (f > flash) {
                flash = f;
            }
        }
        // 落雷（主链雷场/雷暴/雷击）：劈下来时也有轻微晃动 ✓（作者指定）
        if (minecraft.level != null && minecraft.player != null) {
            for (com.tnc.tnc.magic.TNLightningStrikeEntity bolt : minecraft.level.getEntitiesOfClass(
                    com.tnc.tnc.magic.TNLightningStrikeEntity.class,
                    minecraft.player.getBoundingBox().inflate(SHAKE_RANGE))) {
                double d = bolt.position().distanceTo(minecraft.player.position());
                float s = (float) (bolt.shake() * Math.max(0.0D, 1.0D - d / SHAKE_RANGE));
                if (s > shake) {
                    shake = s;
                }
            }
        }
        shake *= 0.88F;         // 衰减
        if (shake < 0.02F) {
            shake = 0.0F;
        }
        flash *= 0.72F;
        if (flash < 0.01F) {
            flash = 0.0F;
        }
    }

    /**
     * 爆炸白闪：整屏一层白光，随 tick 快速淡掉 ✓。
     *
     * <p>为什么要有它：只有镜头晃动的话，"爆炸"还是会显得单薄 ✗ ——
     * 一记盖住屏幕的白闪是"能量在脸上炸开"最直接的信号 ✓（核弹的观感就是这么来的 ✓）。
     */
    @SubscribeEvent
    public static void onRenderGui(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        if (flash <= 0.0F) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        int a = (int) Math.min(200.0F, flash * 200.0F);
        if (a <= 2) {
            return;
        }
        int argb = (a << 24) | 0xFFFFFF;
        event.getGuiGraphics().fill(0, 0, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), argb);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0.0F) {
            return;
        }
        event.setYaw(event.getYaw() + (SHAKE_RANDOM.nextFloat() - 0.5F) * shake * 1.6F);
        event.setPitch(event.getPitch() + (SHAKE_RANDOM.nextFloat() - 0.5F) * shake * 1.2F);
        event.setRoll(event.getRoll() + (SHAKE_RANDOM.nextFloat() - 0.5F) * shake * 2.4F);
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        float mult = 1.0F;
        mult = Math.max(mult, factor(player, TNEffects.LIGHTNING_HASTE, FOV_LIGHTNING_HASTE));
        mult = Math.max(mult, factor(player, TNEffects.LIGHTNING_WIND, FOV_LIGHTNING_WIND));
        mult = Math.max(mult, factor(player, TNEffects.LIGHTNING_ASCENSION, FOV_LIGHTNING_ASCENSION));
        if (mult != 1.0F) {
            event.setNewFovModifier(event.getFovModifier() * mult);
        }
    }

    /** 有该效果就返回它的系数，否则返回 1（不动原版数值）。 */
    private static float factor(Player player, RegistryObject<MobEffect> effect, float value) {
        return (effect.isPresent() && player.hasEffect(effect.get())) ? value : 1.0F;
    }
}
