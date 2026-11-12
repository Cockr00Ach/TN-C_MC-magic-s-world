package com.tnc.tnc.magic.thunderseal.client;

import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
import com.tnc.tnc.magic.thunderseal.ThunderSealContent;
import com.tnc.tnc.magic.thunderseal.ThunderSealEntity;
import com.tnc.tnc.magic.thunderseal.ThunderSealRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * ★ 霆狱（雷锲链 1~5）的<b>全新</b>表现 —— 2026-11-05 作者："雷锲的雷牢跟雷电的效果都很一般，
 * 我建议你打破过去的所有，重新做"。
 *
 * <h2>旧版为什么"一般"</h2>
 * 旧版是<b>自己另写的一套</b>：19 根立柱 ＋ 3 条扁带 ＋ 一层顶冠，全是<b>扁片子</b>
 * （两片三角形拼的"带子"），外加一根 {@code beacon_beam} 贴图的细线当落雷 ✗。
 * 它和我们新做的落雷（四层加色、带分叉、白芯）根本不是一种东西，
 * 所以放在一起就显得"纸片笼子 ＋ 一根线"✗。
 *
 * <h2>新版思路（全部用 {@link LightningBoltGeometry} 真雷画）</h2>
 * <ol>
 *   <li><b>地</b>：两层反向旋转的蛛网雷网 ＋ 贴地雷光；</li>
 *   <li><b>牢</b>：{@code bars} 根<b>竖雷</b>从地面环往上收拢到顶（不是柱子，是<b>电</b>）✓</li>
 *   <li><b>箍</b>：两道横环，每道由 12 段<b>短雷</b>首尾相接，一圈顺转一圈逆转 ✓</li>
 *   <li><b>冠</b>：顶上 8 道雷向中心汇聚 ＋ 白闪；</li>
 *   <li><b>楔</b>：每个雷楔节点自己一根竖雷 ＋ 脚下小雷网（模型留着，它才是"楔"✓）；</li>
 *   <li><b>落</b>：立顶那一下 —— 中心一根 40 格大雷 ＋ 每个节点一根天雷 ＋ 12 道径向爆雷
 *       ＋ 扩散冲击环（不是原来那根细线 ✗）✓</li>
 * </ol>
 *
 * <p><b>和伤害同步</b>：{@code ThunderSealEntity} 在 {@code (age-charge)%12==0} 时打一次脉冲，
 * 这里同样按 12 tick 做一次"整笼变亮" ⇒ 看得见的节奏就是掉血的节奏 ✓。
 */
@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ThunderSealRenderer extends EntityRenderer<ThunderSealEntity> {

    /** 雷楔模型（雷楔节点上的那根"楔子"，留着 —— 它是这套法术的形 ✓） */
    private static final ResourceLocation MODEL = ThunderSealContent.id("thunder_seal_pylon");

    public ThunderSealRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ThunderSealContent.SEAL, ThunderSealRenderer::new);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(new net.minecraft.client.resources.model.ModelResourceLocation(MODEL, "inventory"));
    }

    @Override
    public ResourceLocation getTextureLocation(ThunderSealEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    /** 每档一种雷色：1 金 / 2 蓝 / 3 紫 / 4 蓝 / 5 暗紫（"九霄天刑"最凶）✓ */
    private static int themeFor(int tier) {
        return switch (ThunderSealRules.tier(tier)) {
            case 1 -> LightningTheme.GOLD;
            case 2 -> LightningTheme.BLUE;
            case 3 -> LightningTheme.VIOLET;
            case 4 -> LightningTheme.BLUE;
            default -> LightningTheme.DARK;
        };
    }

    @Override
    public void render(ThunderSealEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        double age = entity.age(partialTick);
        int tier = ThunderSealRules.tier(entity.tier());
        int theme = themeFor(tier);
        int count = ThunderSealRules.count(tier);
        double radius = ThunderSealRules.radius(tier);
        double height = ThunderSealRules.height(tier);
        int charge = ThunderSealRules.charge(tier);
        int finish = ThunderSealRules.finish(tier);
        int life = ThunderSealRules.lifetime(tier);

        // 总体淡入/淡出（前 4 tick、最后 16 tick）
        float fade = (float) Mth.clamp(age / 4.0D, 0.0D, 1.0D)
                * (float) Mth.clamp((life - age) / 16.0D, 0.0D, 1.0D);
        if (fade <= 0.004F) {
            return;
        }
        // 笼子长起来的过程（charge 结束前长满）
        double growth = Mth.clamp(age / Math.max(6.0D, charge * 0.7D), 0.0D, 1.0D);
        // 立顶之后 16 tick 的冲击包络
        boolean impact = age >= finish;
        double sinceImpact = impact ? age - finish : 0.0D;
        float burst = impact ? (float) Mth.clamp(1.0D - sinceImpact / 16.0D, 0.0D, 1.0D) : 0.0F;
        // 12 tick 一次"整笼变亮"，和实体打伤害的节奏一致 ✓
        boolean pulseOn = age >= charge && age < finish && ((int) (age - charge)) % 12 < 4;
        float pulse = age < charge
                ? (float) (0.35D + 0.65D * growth)
                : (pulseOn ? 1.0F : 0.55F);

        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double camX = cam.x - entity.getX();
        double camY = cam.y - entity.getY();
        double camZ = cam.z - entity.getZ();
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(LightningRenderTypes.BOLT);
        long baseSeed = entity.getId() * 1315423911L;
        // 笼骨每 3 tick 重新抖一次（每 tick 换会太吵 ✗）
        long flick = (long) (age / 3.0D);

        float a = fade * pulse;
        float ground = (float) (radius * growth);

        // ---------------- 1. 地面：反向双蛛网 ＋ 雷光 ----------------
        LightningBoltGeometry.groundWeb(vc, m, 0.0D, 0.06D, 0.0D, ground, baseSeed + 1L, theme,
                0.55F * a, (float) (age * 0.055D), camX, camY, camZ, true);
        LightningBoltGeometry.groundWeb(vc, m, 0.0D, 0.10D, 0.0D, ground * 0.58F, baseSeed + 2L, theme,
                0.40F * a * pulse, -(float) (age * 0.085D), camX, camY, camZ, true);
        LightningBoltGeometry.groundFlash(vc, m, 0.0D, 0.03D, 0.0D, ground * 0.92F, theme,
                0.30F * a * (0.5F + 0.5F * pulse));

        // ---------------- 2. 雷牢：竖雷（从地面环收拢到顶） ----------------
        if (growth > 0.02D) {
            int bars = Math.min(18, Math.max(6, count * 2 + 4));
            double topRadius = radius * 0.40D * growth;
            double topY = height * growth;
            for (int i = 0; i < bars; i++) {
                double ang = i * (Math.PI * 2.0D / bars) + Math.toRadians(age * 0.9D);
                double bx = Math.cos(ang) * ground;
                double bz = Math.sin(ang) * ground;
                // 顶上收拢并扭半个身位 ⇒ 看着像"往中间拧"的笼子，不是一圈平行柱子 ✓
                double tx = Math.cos(ang + 0.55D) * topRadius;
                double tz = Math.sin(ang + 0.55D) * topRadius;
                float w = (float) (0.085D + 0.02D * count / 9.0D);
                LightningBoltGeometry.bolt(vc, m, bx, 0.06D, bz, tx, topY, tz,
                        baseSeed + 11L * i + flick, w, 0.62F * a, theme, i % 3 == 0 ? 2 : 1,
                        camX, camY, camZ);
            }
        }

        // ---------------- 3. 横箍：两道，正反转动 ----------------
        if (age > charge * 0.65D) {
            float ringIn = (float) Mth.clamp((age - charge * 0.65D) / Math.max(4.0D, charge * 0.35D), 0.0D, 1.0D);
            for (int r = 0; r < 2; r++) {
                double ry = height * (0.36D + 0.36D * r) * growth;
                double rr = radius * (1.0D - 0.18D * r) * growth;
                int segments = 12;
                double spin = Math.toRadians(age * (r == 0 ? 2.2D : -2.8D));
                for (int s = 0; s < segments; s++) {
                    double a0 = spin + s * (Math.PI * 2.0D / segments);
                    double a1 = a0 + Math.PI * 2.0D / segments;
                    LightningBoltGeometry.bolt(vc, m,
                            Math.cos(a0) * rr, ry, Math.sin(a0) * rr,
                            Math.cos(a1) * rr, ry, Math.sin(a1) * rr,
                            baseSeed + 101L * r + s + flick * 7L, 0.055F, 0.55F * a * ringIn,
                            theme, 0, camX, camY, camZ);
                }
            }
        }

        // ---------------- 4. 顶冠：向中心汇聚 ＋ 白闪 ----------------
        if (growth > 0.35D) {
            double crownR = radius * 0.42D * growth;
            double crownY = height * growth;
            for (int i = 0; i < 8; i++) {
                double ang = i * (Math.PI / 4.0D) - Math.toRadians(age * 1.4D);
                LightningBoltGeometry.bolt(vc, m,
                        Math.cos(ang) * crownR, crownY, Math.sin(ang) * crownR,
                        0.0D, crownY + 0.9D * growth, 0.0D,
                        baseSeed + 200L + i + flick, 0.07F, 0.6F * a, theme, 1, camX, camY, camZ);
            }
            LightningBoltGeometry.billboardFlash(vc, m, 0.0D, crownY + 0.55D, 0.0D,
                    (float) (radius * 0.42D), theme, 0.40F * a, camX, camY, camZ);
        }

        // ---------------- 5. 雷楔节点：天雷砸下，楔子**直接闪出** ----------------
        // ★ 2026-11-08 作者："雷锲的出现直接闪出就行，你不要雷劈了之后还从地里长出来"
        //   ⇒ 把"长"的痕迹全删掉：
        //     ✗ 模型不再从 0.02 缩放到 1（整根一起变大 = 从土里顶出来 ✗）
        //     ✗ 不再有从地面往楔轴上收的 6 道短雷（那就是"从地里长出来"的读法 ✗）
        //   ✓ 天雷落地那一 tick，楔子**满尺寸**已经在那儿，雷环是**向外**炸的：
        //     30 格天雷砸下 → 落点白闪 → 雷环向外炸开 → 楔子直接闪出（1 楔 1 道雷 ✓）
        var model = mc.getModelManager().getModel(
                new net.minecraft.client.resources.model.ModelResourceLocation(MODEL, "inventory"));
        double birthTicks = ThunderSealRules.birthTicks();
        for (int slot = 0; slot < count; slot++) {
            double since = age - ThunderSealRules.strikeTick(slot);   // 距离"这道天雷落地"多久
            if (since < 0.0D) {
                continue;                        // 还没轮到这一楔：一根雷都不画（劈到才有 ✓）
            }
            Vec3 node = entity.node(slot, age);
            float k = (float) Mth.clamp(1.0D - since / 10.0D, 0.0D, 1.0D);

            if (since < 10.0D) {
                // ① 天雷：从 30 格高砸到落点（落地 1 tick 最亮，之后 10 tick 收细）
                LightningBoltGeometry.bolt(vc, m, node.x, 30.0D, node.z, node.x, node.y + 0.08D, node.z,
                        baseSeed + 600L + slot * 37L + (long) since,
                        tier >= 4 ? 0.34F : 0.24F, 0.95F * k, theme, tier >= 4 ? 3 : 2,
                        camX, camY, camZ);
                // ② 落地白闪
                LightningBoltGeometry.groundFlash(vc, m, node.x, node.y + 0.04D, node.z,
                        (float) (2.1D + since * 0.35D), theme, 0.90F * k);
            }
            // ③ 雷环：**向外**炸开（越炸越大、离楔轴越远 ⇒ 不会读成"往中间长"✓）
            if (since < birthTicks) {
                float g = (float) (1.0D - since / birthTicks);
                LightningBoltGeometry.groundWeb(vc, m, node.x, node.y + 0.05D, node.z,
                        (float) (0.9D + since * 0.55D), baseSeed + 900L + slot, theme,
                        0.55F * g, (float) (since * 0.22D), camX, camY, camZ, false);
                for (int s = 0; s < 5; s++) {
                    double ang = s * (Math.PI * 2.0D / 5.0D) + slot * 0.9D;
                    double yy = node.y + 0.30D + 0.10D * (s % 2);
                    LightningBoltGeometry.bolt(vc, m,
                            node.x + Math.cos(ang) * 0.22D, yy, node.z + Math.sin(ang) * 0.22D,
                            node.x + Math.cos(ang) * (1.35D + since * 0.30D), yy,
                            node.z + Math.sin(ang) * (1.35D + since * 0.30D),
                            baseSeed + 950L + slot * 13L + s, 0.06F, 0.70F * g, theme, 1,
                            camX, camY, camZ);
                }
            }

            // ④ 楔子立起来的那根竖雷（骨架）：落地即满亮度
            double nodeTop = Math.max(node.y, 0.0D) + height * 0.88D;
            LightningBoltGeometry.bolt(vc, m, node.x, 0.05D, node.z, node.x, nodeTop, node.z,
                    baseSeed + 300L + slot * 71L + flick, 0.10F, 0.66F * a, theme, 2,
                    camX, camY, camZ);
            LightningBoltGeometry.groundWeb(vc, m, node.x, node.y + 0.07D, node.z,
                    1.7F, baseSeed + 400L + slot, theme, 0.34F * a * pulse,
                    (float) (age * 0.09D), camX, camY, camZ, false);

            // ⑤ 楔子本体：**落地即满尺寸**（不缩放、不过冲 ⇒ 直接闪出来 ✓）
            float scale = (float) (height / 1.9D);
            poseStack.pushPose();
            poseStack.translate(node.x, node.y, node.z);
            poseStack.mulPose(Axis.YP.rotationDegrees((float) (slot * 360.0D / count)));
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-0.25D, 0.0D, -0.25D);
            mc.getItemRenderer().renderModelLists(model, net.minecraft.world.item.ItemStack.EMPTY,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack,
                    buffers.getBuffer(net.minecraft.client.renderer.RenderType
                            .entityCutout(TextureAtlas.LOCATION_BLOCKS)));
            poseStack.popPose();
        }

        // ---------------- 6. 立顶：中心大雷 ＋ 节点天雷 ＋ 径向爆雷 ＋ 冲击环 ----------------
        if (impact && burst > 0.004F) {
            long hitSeed = baseSeed + 500L + (long) (sinceImpact / 2.0D);
            // 中心一根 40 格大雷（t5 更粗、分叉更多）
            float mainWidth = tier >= 5 ? 0.62F : (tier >= 4 ? 0.44F : 0.30F);
            LightningBoltGeometry.bolt(vc, m, 0.0D, 40.0D, 0.0D, 0.0D, 0.10D, 0.0D,
                    hitSeed, mainWidth, 0.95F * fade * burst, theme, tier >= 4 ? 4 : 2,
                    camX, camY, camZ);
            // 每个节点一根天雷（"九霄"就是九根 ✓）
            for (int slot = 0; slot < count; slot++) {
                Vec3 node = entity.node(slot, age);
                double from = 26.0D + slot * 1.5D;
                LightningBoltGeometry.bolt(vc, m, node.x, from, node.z, node.x, node.y + 0.1D, node.z,
                        hitSeed + 21L * slot, tier >= 4 ? 0.30F : 0.20F, 0.80F * fade * burst, theme, 2,
                        camX, camY, camZ);
            }
            // 12 道径向爆雷（从封印中心往外炸）
            for (int i = 0; i < 12; i++) {
                double ang = i * (Math.PI / 6.0D) + sinceImpact * 0.01D;
                double len = radius * 1.45D;
                LightningBoltGeometry.bolt(vc, m,
                        0.0D, 0.25D, 0.0D,
                        Math.cos(ang) * len, 0.35D + 0.55D * Math.sin(i * 1.7D), Math.sin(ang) * len,
                        hitSeed + 300L + i, 0.11F, 0.70F * fade * burst, theme, 1, camX, camY, camZ);
            }
            // 扩散冲击环 ＋ 地面白光
            double ringR = radius * (1.0D + sinceImpact * 0.11D);
            LightningBoltGeometry.groundWeb(vc, m, 0.0D, 0.14D, 0.0D, (float) ringR, hitSeed + 7L, theme,
                    0.75F * fade * burst, (float) (sinceImpact * 0.02D), camX, camY, camZ, false);
            LightningBoltGeometry.groundFlash(vc, m, 0.0D, 0.05D, 0.0D, (float) (ringR * 1.05D), theme,
                    0.65F * fade * burst);
            LightningBoltGeometry.billboardFlash(vc, m, 0.0D, height * 0.5D, 0.0D,
                    (float) (radius * 0.8D), theme, 0.60F * fade * burst, camX, camY, camZ);
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }
}
