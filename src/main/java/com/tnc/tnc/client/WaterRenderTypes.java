package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Alpha-blended, two-sided water geometry; no duplicate faces or opaque depth writes. */
final class WaterRenderTypes extends RenderType {
    private WaterRenderTypes(){super("tnc_water_geometry",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    private static final RenderType GEOMETRY=create("tnc_water_geometry",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,256,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE).setOutputState(MAIN_TARGET).createCompositeState(false));
    static RenderType geometry(){return GEOMETRY;}
}
