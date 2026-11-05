package com.tnc.tnc.client.lightning;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 雷系自用的<b>加色自发光</b>渲染层。
 *
 * <h2>为什么不能用原版闪电那层（项目里已经踩过一次 ✗）</h2>
 * {@code RenderType.lightning()} 的定义里带 {@code .setOutputState(WEATHER_TARGET)} ——
 * 原版闪电是画进<b>天气帧缓冲</b>的，而实体渲染这一趟根本不会合成它 ✗
 * ⇒ 顶点交了、也不报错，就是一个像素都看不见 ✗（详见 {@code TNLightBeamRenderer} 的类注释）。
 * 所以这里照抄一件<b>本仓库已验证能用</b>的东西：{@code LuminousStaffRenderer} 里那套
 * "继承 {@code RenderType} 才能读 protected 常量"的写法 ✓ ——
 * 着色器走实体自发光（{@code RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER}）、
 * 混合走 {@code LIGHTNING_TRANSPARENCY}（{@code SRC_ALPHA, ONE}，按 alpha 加色）、
 * 输出到 {@code MAIN_TARGET} ✓。
 *
 * <p>{@code SRC_ALPHA, ONE} 意味着：顶点 alpha 直接决定"往画面上加多少光" ⇒
 * 同一段几何叠三层（外辉光 / 主色 / 白芯）就会自然过曝成白芯 ＋ 彩色光晕，
 * 这正是"中心白、渐变到边、然后发光"的实现方式 ✓✓。
 *
 * <h2>为什么贴图是纯白</h2>
 * 颜色全部走顶点色（每根闪电、每个主题都不一样），贴图只提供"一个可采样的白点"。
 * 用 {@code beam_white.png}（16×16 纯白，光柱已经在用 ✓）⇒ 不新增任何美术资源 ✓。
 *
 * <p>不写深度（{@code COLOR_WRITE}）＋ 不剔除（{@code NO_CULL}）：
 * 雷是"叠在画面上的光"，写深度会把后面的雷/粒子整段裁掉，反而更假 ✗。
 */
public final class LightningRenderTypes {

    /** 纯白贴图（顶点色上色 ✓）。 */
    public static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/beam_white.png");

    /** 雷系几何唯一用到的渲染层 ✓。 */
    public static final RenderType BOLT = LightningType.BOLT;

    /**
     * ★ <b>法术模型层（半透明 ＋ 自发光）</b> —— 给「雷球 / 三尊神 / 环绕雷球」这些
     * <b>作者做的方块模型</b>用 ✓。
     *
     * <h2>为什么要自己建一层（2026-11-05 作者："神在投篮的神还有球都变得不透明了"）</h2>
     * 这些模型原来走引擎的 {@code SpellModelHelper.LAYERS.get(LightEmission.RADIATE)}。
     * 反编译<b>实际装的那份</b> {@code spell_engine-0.15.12} 可以看到：
     * <pre>
     *   spellObject(LightEmission) → spellObject(atlas, light, <b>false</b>)
     *   spellObject(..., false)    → setTransparencyState(<b>f_110134_</b>)
     * </pre>
     * 而 {@code f_110134_} 就是 {@code NO_TRANSPARENCY} ✗ —— 关掉混合之后，
     * 贴图里 alpha=0 的地方照样按不透明画 ⇒ <b>模型变成实心</b> ✗。
     * 所以这里自己搭一层：{@code TRANSLUCENT_TRANSPARENCY}（{@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}）
     * ＋ 同一个 {@code entity_translucent_emissive} 着色器（全亮度、不吃光照）＋ 方块图集 ✓。
     *
     * <p>不写深度（{@code COLOR_WRITE}）：半透明的实体模型写深度会把后面的东西挡掉、
     * 还会自己和自己打架 ✗ —— 原版半透明实体层也是这么做的 ✓。
     */
    public static final RenderType SPELL_MODEL = LightningType.SPELL_MODEL;

    /** 模型"发光外轮廓"：同一份几何用加色再画一遍（贴图纯白 ⇒ 就是一层白色辉光）✓。 */
    public static final RenderType SPELL_MODEL_GLOW = BOLT;

    private LightningRenderTypes() {
    }

    /**
     * 只为了拿到 {@code RenderType.create} / {@code LIGHTNING_TRANSPARENCY} 这些
     * 包内可见 / protected 的静态成员 —— 在别的包里只能靠"继承"读 ✓。
     */
    private static final class LightningType extends RenderType {

        private LightningType() {
            super("tnc_lightning", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    1048576, false, true, () -> {
                    }, () -> {
                    });
        }

        static final RenderType BOLT = create(
                "tnc_lightning",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                // ★ 2026-11-05：64 KB → 1 MB。一条雷龙 ≈ 2700 个四边形（10800 个顶点 × 36 B ≈ 388 KB），
                //   同一帧里还叠着落雷/雷笼/法阵雷网 —— 64 KB 每帧都要扩容好几次 ✗。
                //   1 MB 一次装下整帧的雷系几何 ✓（ByteBufferBuilder 真的不够时还能自己涨）。
                1048576,
                false,
                true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(WHITE_TEXTURE, false, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(MAIN_TARGET)
                        .createCompositeState(false));

        static final RenderType SPELL_MODEL = create(
                "tnc_spell_model",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                262144,
                false,
                true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        // 方块图集：作者的模型是 Blockbench 的方块模型，UV 全在方块图集里 ✓
                        .setTextureState(new TextureStateShard(
                                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(MAIN_TARGET)
                        .createCompositeState(false));
    }
}
