package com.tnc.tnc.dungeon.ecology.client;

import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Mana sight exposes an internal organ without turning all the ordinary anatomy emissive. */
final class EcologyRenderTypes extends RenderType {
    private EcologyRenderTypes(){super("tnc_ecology_core",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    private static final Map<ResourceLocation,RenderType> CORES=new HashMap<>();
    static RenderType core(ResourceLocation texture){return CORES.computeIfAbsent(texture,t->create("tnc_ecology_core",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,
        CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER).setTextureState(new TextureStateShard(t,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));}
}
