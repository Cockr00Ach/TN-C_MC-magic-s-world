package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Alpha-blended, two-sided water geometry; no duplicate faces or opaque depth writes. */
final class WindEarthRenderTypes extends RenderType {
    private WindEarthRenderTypes(){super("tnc_wind_earth_geometry",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    private static final RenderType GEOMETRY=create("tnc_wind_earth_geometry",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,256,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE).setOutputState(MAIN_TARGET).createCompositeState(false));
    /**
     * 叠加发光层（2026-10-07 水球链特效）：同一套 POSITION_COLOR 顶点、同一支闪电着色器，
     * 但用 {@link #LIGHTNING_TRANSPARENCY} ⇒ 混合方式是 <b>SRC_ALPHA + ONE 的加色</b> ✓
     * —— 这才是"光效" ✗：普通 alpha 混合只能把颜色<b>盖</b>上去，越叠越浑 ✗；
     * 加色会把重叠处推向白亮，水滴核心、法阵中心、光束中轴才会"发光" ✓。
     *
     * <p>仍然 {@code COLOR_WRITE}（不写深度 ✗）：否则发光层会挡住它后面的半透明水体 ✗。
     * 输出同样留在 {@code MAIN_TARGET} —— 参考 `water-scale` 那次的教训 ✗：
     * 往天气缓冲写半透明几何会在 Fabulous 模式下出深度合成问题 ✗。
     */
    private static final RenderType GLOW=create("tnc_wind_earth_glow",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,256,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE).setOutputState(MAIN_TARGET).createCompositeState(false));
    static RenderType geometry(){return GEOMETRY;}
    static RenderType glow(){return GLOW;}
}
