package com.tnc.tnc.world.ink.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** The active renderer owns shader, output target, texture and blend state. */
final class InkPaintingRenderTypes {
    static RenderType paint(){return RenderType.entityTranslucent(ResourceLocation.fromNamespaceAndPath("tnc","dynamic/ink_shanshui_neutral"));}
}
