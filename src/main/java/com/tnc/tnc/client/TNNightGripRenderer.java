package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNNightGripEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <b>抓住不放的那只黑手</b>怎么画 —— 暗系第一条链「黑夜之手」重做的抓取端 ✓
 * （作者 2026-10-10：「暗魔法的黑夜之手可能要整个改一下」；并且特别强调
 * 「他名字虽然叫做不可视，但你别游戏里做的啥都看不见，我们还是要做出来要的特效的」✓
 * ⇒ 所以这只手**是画出来的**，不是隐形的 ✗）。
 *
 * <h2>画法与为什么这么画</h2>
 * 直接走<b>引擎画投射物的同一条路</b> ✓（反编译确认：
 * {@code SpellModelHelper.LAYERS.get(light_emission)} 取层 ＋
 * {@code CustomModels.render(layer, itemRenderer, modelId, ...)}）——
 * 和环绕雷球（{@code TNThunderOrbRenderer}）一模一样的做法，那条路已经验证能显示 ✓。
 * 模型就用现成的 {@code tnc:projectile/dark_hand}（已在
 * {@code TNProjectileModels.PROJECTILE_MODELS} 里烘焙 ✓，不需要新模型 ✗）。
 *
 * <p><b>为什么不用普通 Forge 渲染器自己读 JSON 模型</b> ✗：原版 {@code ModelManager} 的自定义
 * 模型要 {@code ModelEvent.RegisterAdditional} 注册再逐面搭，本项目为此专门立了
 * <b>六步配方</b>（{@code docs/spells/投射物模型_配方.md}）—— 走引擎这条路等于白拿 ✓。
 *
 * <h2>朝向：掌心朝着被抓的人 ✓</h2>
 * 模型是"手腕在 −Z、掌面朝 +Z"（见 {@code tools/gen_dark_hand_model.py} 的表头注释 ✓）。
 * 于是只要把模型的 <b>+Z 转到「施法者 → 手」这个方向</b>，读感就是
 * "从他背后伸出来的一只手，掌心按在目标身上" ✓ —— 正好是作者要的姿态。
 * 旋转的推导（把 +Z 转到方向 d）：
 * <pre>
 *   yaw   = atan2(d.x, d.z)      // 先绕 Y
 *   pitch = −asin(d.y)           // 再绕已经转过去的 X（局部轴）
 *   pose  = Ry(yaw) · Rx(pitch)
 * </pre>
 * 校验：d = (0,0,1) ⇒ yaw=0、pitch=0（正前方不动 ✓）；d = (0,0.707,0.707) ⇒ pitch=−45°
 * ⇒ +Z 变成 (0,+0.707,+0.707)（朝前上方 ✓）；d = (1,0,0) ⇒ yaw=90° ⇒ +Z 变成 (1,0,0) ✓。
 *
 * <h2>尺寸：与飞出去的那只手同一张表 ✓</h2>
 * {@link #SCALE} 就是法术 JSON 里那份 {@code scale}
 * （{@code 0.98/1.97/4.92/9.85/19.69} × 模型最大跨度 1.625 格 ⇒ **1.6/3.2/8/16/32 格** ✓）。
 * 两者必须一致 ✗：投射物在命中那一刻就被引擎回收了，接着本实体接手画同一只手 ——
 * 尺寸不一样就会"啪"地跳一下 ✓。
 *
 * <h2>★ 引擎在缩放后的坐标系里平移了 0.5 格（这个坑踩过）</h2>
 * {@code CustomModels.render} 内部先 {@code translate(−0.5,−0.5,−0.5)} ✗，而那是
 * <b>已经缩放过</b>的坐标系 ⇒ 实际偏移 = {@code scale × 0.5} 格。
 * 所以缩放之后要**自己补一个 +0.5** 抵消它 ✓（本模型包围盒本来就居中在原点，
 * 所以补 {@code (0.5,0.5,0.5)} 即回到实体原点 ✓）；
 * 环绕雷球当初就是没补这一下，整整歪了 2 格 ✓（推导见 {@code TNThunderOrbRenderer} 的同名注释）。
 */
public class TNNightGripRenderer extends EntityRenderer<TNNightGripEntity> {

    private static final Logger LOGGER = LogManager.getLogger("TN-C/nighthand");

    /** 给引擎的模型号 —— <b>普通 ResourceLocation</b>，不要加 {@code #standalone} 变体 ✗。 */
    private static final ResourceLocation MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, TNProjectileModels.DARK_HAND);

    /** 贴图（模型自带的那张 ✓；引擎自己会去读，这里只是 {@code getTextureLocation} 的要求）。 */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/spell_projectile/dark_hand.png");

    /**
     * ★ <b>尺寸不在这里了</b> ✗ —— 2026-10-10 挪到实体里（{@code TNNightGripEntity.SIZE_SCALE} /
     * {@link TNNightGripEntity#sizeScale()}）当**唯一来源** ✓：
     * 这样"渲染多大"和"视锥剔除盒子多大"永远一致，不会各说各话 ✗
     * （作者那天要求「把 t4 的手的尺寸换成 t5 那么大」＝ 那张表的索引 3 改成和索引 4 同值 ✓）。
     */

    /** 只在第一次打一行日志，免得每帧刷屏 ✓。 */
    private static boolean logged;

    /**
     * ★ <b>阶段 0b 诊断：每只实体只打一次"我按多大画"</b>（作者 2026-10-08 实机反馈
     * 「t1 到 t5 大小都一样」⇒ 必须证明渲染时读到的 tier/scale 到底是几 ✗）。
     *
     * <p>已经用一次探针证明了：模型烘焙成功（{@code isMissingModel=false}）、贴图无误、
     * 档位数据也对（{@code tier=1..4} 各自生成 1/1/2/3 只手 ✓）。
     * 那么"大小不变"就只可能出在这条链的<b>最后一跳</b>：
     * {@code entity.sizeScale()} 读到的值 / 引擎内部那次 {@code -0.5} 平移 / 锚点抬升。
     * 这里把三个数一次打全，进游戏放一轮五档就能对上表 ✓。
     */
    private static final java.util.Set<Integer> SCALE_LOGGED =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * 模型半高**不在这里** ✗ —— 它是实体的
     * {@code TNNightGripEntity.MODEL_HALF_HEIGHT_UNITS}（唯一来源 ✓）。
     * 2026-10-10 作者手改模型之后 y 跨度从 ±13 变成 ±12.5 单位，
     * 这个数就只能有一个地方能改 ✓（导入脚本 {@code tools/import_dark_hand_bbmodel.py}
     * 会把该填的值打印出来 ✓）。
     */

    public TNNightGripRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;      // 悬空的黑手不投影子 ✓
    }

    @Override
    public ResourceLocation getTextureLocation(TNNightGripEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TNNightGripEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // ★ 尺寸从**实体**读（唯一来源 ✓）—— 2026-10-08 修正：这里以前是
        //   "t1..t5 五档"的线性表（t4==t5），作者要求改成**指数增长**，且
        //   {@code sizeScale()} 以前读的是**只在服务端写的字段** ⇒ 客户端一律按 t1 画 ✗
        //   （实机日志实证 `tier=5 ... scale=0.98`）。两处现在都修在实体里 ✓。
        if (!entity.launched()) return;
        float scale = entity.sizeScale();
        boolean sky = entity.skyfall();
        Vec3 direction = directionOf(entity, partialTick);
        logScaleOnce(entity, scale, sky);

        poseStack.pushPose();
        // ① 把模型的 +Z（掌心）转到目标方向：抓取 ⇒ 朝被抓的人；天降 ⇒ 朝**正下方** ✓
        poseStack.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(direction.x, direction.z)));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotation(
                (float) -Math.asin(Mth.clamp(direction.y, -1.0D, 1.0D))));
        // ② 按档位放大，再补回引擎内部那 −0.5 格 ✓
        poseStack.scale(scale, scale, scale);
        // ③ ★ 锚点（作者 2026-10-10："落地点从中心改到底部"）：
        //    抓取 ⇒ 抬 13/16 格（模型 y 跨度 ±13 单位），**模型底边**落在实体位置上
        //            ⇒ 配合实体的"目标脚下"锚点 ⇒ 一只手掌**贴地立着**按向目标 ✓；
        //    天降 ⇒ **不抬**：此时模型的 +Y（原本的"上"）已经被转成水平了，
        //            再抬就会把整只手平推出去 ✗；掌心（+Z 面）本来就贴着落点 ✓。
        //    两处都在**缩放后的坐标系**里平移 ⇒ 世界偏移 = (13/16)×scale ✓。
        poseStack.translate(0.5F, 0.5F + (sky ? 0.0F : (float) entity.modelHalfHeightUnits()), 0.5F);

        if (!renderWithEngine(poseStack, buffer, entity)) {
            // 引擎不在/渲染失败：**什么都不画**，但绝不崩 ✓
            // （手的主体表现还有服务端撒的黑索与黑雾粒子 ⇒ 即使这条路径失败也不会"啥都看不见" ✓）
            if (!logged) {
                logged = true;
                LOGGER.warn("TN-C: night hand 引擎渲染路径不可用，本帧只靠粒子表现 ✓");
            }
        }
        poseStack.popPose();

        if (!sky) {
            LivingEntity owner = living(entity.ownerEntityId());
            if (owner != null) {
                Vec3 origin = entity.getPosition(partialTick);
                Vec3 back = owner.getPosition(partialTick).add(0, owner.getBbHeight() * 0.65D, 0)
                        .subtract(owner.getLookAngle().multiply(0.55D, 0.0D, 0.55D));
                // Actual imported wrist centre, in the same rotated coordinates as the model.
                double yaw = Math.atan2(direction.x, direction.z);
                double pitch = -Math.asin(Mth.clamp(direction.y, -1.0D, 1.0D));
                Vec3 wrist = new Vec3(-0.8D / 16, entity.modelHalfHeightUnits() - 6.5D / 16, -6.0D / 16)
                        .xRot((float) -pitch).yRot((float) yaw).scale(scale);
                NightHandGeometry.tube(buffer.getBuffer(NightHandRenderTypes.geometry()),poseStack.last().pose(),
                        back.subtract(origin),wrist,Math.min(.8,.12*scale),entity.handSlot(),false,entity.tickCount+partialTick);
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * 手该朝哪个方向 —— 也就是"掌心对着谁" ✓。
     *
     * <p>★ 优先级（作者 2026-10-10：「不是掌心对着别人」就是这里要修的东西）：
     * <ol>
     *   <li><b>被抓的那个人</b> ✓ —— 掌心（模型的 +Z）一直朝着它。这是本渲染器存在的理由：
     *       重做前姿态由引擎那套逻辑决定（对齐**运动矢量**），homing 一拐弯手就跟着转 ✗；</li>
     *   <li>没瞄到人 ⇒ 朝"施法者 → 手"的方向（＝甩出去的方向）✓；</li>
     *   <li>都拿不到 ⇒ 正前方 ✓。绝不抛异常 ✗。</li>
     * </ol>
     *
     * <p>注意方向是 <b>手 → 目标</b>（不是反过来）✗：模型的 +Z 面才是掌心 ✓
     * （见 {@code tools/gen_dark_hand_model.py} 的几何注释：+Z = 掌心、+Y = 上/手指、
     * 手腕沿 −Z）⇒ 把 +Z 指向目标，掌心才朝着他 ✓。
     */
    private static Vec3 directionOf(TNNightGripEntity entity, float partialTick) {
        // ★ 天降（t5）：掌心朝**正下方** ✓ —— 作者 2026-10-10：「t5 变成有很多个大手从天而降
        //   砸向敌人，**大手的掌心向下**」。
        //   传 (0,-1,0) 进来，上面那两步旋转就正好把模型的 +Z（掌心）压到正下方 ✓
        //   （pitch = −asin(−1) = +90°），而手指（+Y）被转成水平 ⇒ 读感是"手掌拍下来" ✓。
        if (entity.skyfall()) {
            return new Vec3(0.0D, -1.0D, 0.0D);
        }
        LivingEntity target = living(entity.targetEntityId());
        if (target != null) {
            Vec3 toTarget = target.getPosition(partialTick)
                    .add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                    .subtract(entity.getPosition(partialTick));
            if (toTarget.lengthSqr() > 1.0E-6D) {
                return toTarget.normalize();
            }
        }
        Entity owner = living(entity.ownerEntityId());
        if (owner != null) {
            Vec3 fromOwner = entity.getPosition(partialTick).subtract(owner.getPosition(partialTick));
            if (fromOwner.lengthSqr() > 1.0E-6D) {
                return fromOwner.normalize();
            }
        }
        Vec3 motion = entity.getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-6D) {
            return motion.normalize();
        }
        return new Vec3(0.0D, 0.0D, 1.0D);
    }

    /** 按 entity id 在客户端世界里找一个活体（拿不到就 null ✓）。 */
    private static LivingEntity living(int entityId) {
        if (entityId < 0 || Minecraft.getInstance().level == null) {
            return null;
        }
        Entity found = Minecraft.getInstance().level.getEntity(entityId);
        return found instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /**
     * 走<b>引擎渲染投射物的同一条路</b> ✓（软依赖：引擎不在时抛 {@code NoClassDefFoundError}，
     * 那也是 {@code Throwable} ✓ ⇒ 外面接住即可，不影响别的渲染 ✓）。
     *
     * @return true ＝ 引擎画成功
     */
    private static boolean renderWithEngine(PoseStack poseStack, MultiBufferSource buffer,
                                            TNNightGripEntity entity) {
        try {
            net.minecraft.client.renderer.RenderType layer =
                    net.spell_engine.client.render.SpellModelHelper.LAYERS
                            .get(net.spell_engine.api.render.LightEmission.RADIATE);
            net.spell_engine.api.render.CustomModels.render(layer,
                    Minecraft.getInstance().getItemRenderer(), MODEL_ID,
                    poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            if (!logged) {
                logged = true;
                LOGGER.info("TN-C: night hand grip <- 引擎渲染路径 {} tier={} layer={} ✓",
                        MODEL_ID, entity.tier(), layer);
                logWhatTheEngineResolved();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 每个"档位＋模式"只打一次：渲染时实际用的 tier / scale / 模型实际世界尺寸 ✓。
     *
     * <p>这张表就是"大小到底变没变"的**直接答案**：
     * t1 应该 ≈ 1.5 格、t2 ≈ 3.4、t3 ≈ 7.4、t4 ≈ 16.3、t5 ≈ 35.9（天降 7 只）✓ ——
     * 指数增长（每档 ×2.2，作者 2026-10-08 的要求 ✓）。
     * 若日志里五档的 {@code scale} 真的不同、而画面就是不变 ⇒ 问题就不在这条链，
     * 而在"你看到的那只手不是这个实体"（那就该去查是别的模组/别的实体在画 ✗）。
     */
    private static void logScaleOnce(TNNightGripEntity entity, float scale, boolean sky) {
        try {
            int key = entity.tier() * 10 + (sky ? 1 : 0);
            if (!SCALE_LOGGED.add(key)) {
                return;
            }
            double half = com.tnc.tnc.magic.TNNightGripEntity.MODEL_HALF_HEIGHT_UNITS * scale;
            LOGGER.warn("TN-C/DIAG: hand render tier={} skyfall={} scale={} -> 模型高 {} 单位 = {} 格"
                            + "（半高 {} 格），锚点抬升 {} 格",
                    entity.tier(), sky, scale, com.tnc.tnc.magic.TNNightGripEntity.MODEL_HALF_HEIGHT_UNITS * 16.0,
                    String.format("%.2f", half * 2.0), String.format("%.2f", half),
                    String.format("%.2f", sky ? 0.0D : half));
        } catch (Throwable ignored) {
            // 诊断不允许影响渲染 ✓
        }
    }

    /**
     * ★ <b>阶段 0 诊断（一次性）：引擎到底拿到了哪个模型？</b>
     *
     * <p>为什么必须问这一句：{@code CustomModels.render} 里
     * {@code getModelManager().getModel(id)} 在"没烘焙"时<b>返回的是缺失模型，而不是 null</b>
     * （javap 实证：{@code if (model == null) { 兜底成物品 } } 那一支根本走不到 ✗）
     * ⇒ 上面那行"引擎渲染路径 … ✓"的日志<b>只证明这条路被调用了，不证明画的是我们的手</b> ✗。
     *
     * <p>所以这里把引擎实际拿到的 {@link net.minecraft.client.resources.model.BakedModel}
     * 与 {@code getMissingModel()} <b>比对象</b>：
     * <ul>
     *   <li>相等 ⇒ 我们的模型<b>压根没被烘焙</b> ⇒ 游戏里看到的永远是紫黑缺失模型
     *       （与贴图无关，调贴图/调模型都没用 ✗）；</li>
     *   <li>不等 ⇒ 模型烘焙成功 ⇒ 画出来的<b>就是</b> {@code dark_hand.json}
     *       ⇒ 观感不对就只能是姿态/尺寸/锚点的问题 ✓。</li>
     * </ul>
     * 顺带把模型自己的粒子贴图（{@code particle} 字段指向的那张图）名字打出来 ——
     * 那是最短的一条"这张模型确实是我们那只手"的证据 ✓。
     *
     * <p>只跑一次（和上面同一次），且全程 try/catch：诊断绝不允许把渲染搞崩 ✗。
     */
    private static void logWhatTheEngineResolved() {
        try {
            net.minecraft.client.resources.model.ModelManager models =
                    Minecraft.getInstance().getModelManager();
            net.minecraft.client.resources.model.BakedModel resolved = models.getModel(MODEL_ID);
            net.minecraft.client.resources.model.BakedModel missing = models.getMissingModel();
            if (resolved == null) {
                LOGGER.warn("TN-C/DIAG: night hand model {} -> getModel() 返回 null（异常情况）", MODEL_ID);
                return;
            }
            boolean isMissing = resolved == missing;
            // 贴图名只是加分项：反射把几种可能的取名方式都试一遍（拿不到不影响主判据 ✓）
            String particle = "(unreadable)";
            try {
                net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = resolved.getParticleIcon();
                if (sprite != null) {
                    particle = spriteName(sprite);
                }
            } catch (Throwable ignored) {
                // 同上
            }
            LOGGER.warn("TN-C/DIAG: night hand model {} -> baked={} isMissingModel={} class={} particleSprite={}",
                    MODEL_ID, !isMissing, isMissing, resolved.getClass().getName(), particle);
            if (isMissing) {
                LOGGER.warn("TN-C/DIAG: 结论 —— 我们的模型没有被烘焙，游戏里画的是紫黑缺失模型 ✗"
                        + "（查 TNModelBaking 的 RegisterAdditional 与 TNProjectileModels 的登记表）");
            } else {
                LOGGER.warn("TN-C/DIAG: 结论 —— 模型已烘焙，画的就是 dark_hand.json ✓"
                        + "（观感问题只可能出在姿态/尺寸/锚点，不在模型文件）");
            }
        } catch (Throwable t) {
            LOGGER.warn("TN-C/DIAG: 模型解析诊断失败 ({})", t.toString());
        }
    }

    /** 尽力问出这张 sprite 指向的贴图名（原版不同小版本的方法名不一样，故多试几种 ✓）。 */
    private static String spriteName(Object sprite) {
        for (String method : new String[]{"getName", "name", "contents"}) {
            try {
                Object value = sprite.getClass().getMethod(method).invoke(sprite);
                if (value == null) {
                    continue;
                }
                if (value instanceof net.minecraft.resources.ResourceLocation id) {
                    return id.toString();
                }
                try {
                    Object inner = value.getClass().getMethod("name").invoke(value);
                    if (inner != null) {
                        return String.valueOf(inner);
                    }
                } catch (Throwable ignored) {
                    // 不是 SpriteContents ⇒ 直接用它的 toString ✓
                }
                return String.valueOf(value);
            } catch (Throwable ignored) {
                // 试下一种
            }
        }
        return "(unreadable)";
    }
}
