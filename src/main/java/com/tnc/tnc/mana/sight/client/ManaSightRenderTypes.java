package com.tnc.tnc.mana.sight.client;
import net.minecraft.client.renderer.*;
import com.mojang.blaze3d.vertex.*;
/** Saturated alpha-blended body preserves element hues; additive light is a separate halo. */
public final class ManaSightRenderTypes extends RenderType{
 private ManaSightRenderTypes(String n,VertexFormat f,VertexFormat.Mode m,int b,boolean c,boolean s,Runnable a,Runnable z){super(n,f,m,b,c,s,a,z);}
 public static final RenderType BODY=create("tnc_mana_sight",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,524288,false,true,CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).createCompositeState(false));
}
