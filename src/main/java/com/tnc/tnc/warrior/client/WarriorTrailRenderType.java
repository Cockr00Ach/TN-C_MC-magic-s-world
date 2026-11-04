package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Single two-sided submission: saturated shell colours, depth-tested, no opaque depth writes. */
final class WarriorTrailRenderType extends RenderType {
    private WarriorTrailRenderType(){super("tnc_swept_blade",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,32768,false,true,()->{},()->{});}
    static final RenderType TYPE=create("tnc_swept_blade",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,32768,false,true,
        CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
            .setOutputState(MAIN_TARGET).createCompositeState(false));
}
