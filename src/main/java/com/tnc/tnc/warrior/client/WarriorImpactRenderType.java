package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Coloured solid-volume effects: alpha blending preserves the authored colour. */
final class WarriorImpactRenderType extends RenderType {
    private WarriorImpactRenderType() { super("tnc_hit_geometry", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, true, () -> {}, () -> {}); }
    static final RenderType TYPE = create("tnc_hit_geometry", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, true, CompositeState.builder()
                .setShaderState(RENDERTYPE_LIGHTNING_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                .setOutputState(MAIN_TARGET).createCompositeState(false));
}
