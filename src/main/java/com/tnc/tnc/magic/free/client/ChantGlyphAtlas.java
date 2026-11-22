package com.tnc.tnc.magic.free.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Bounded, lazy glyph atlas drawn from the bundled OFL brush font; no platform font dependency. */
final class ChantGlyphAtlas {
    record Glyph(float u,float v,float width){}
    private static final int SIZE=1024,CELL=48,COLS=SIZE/CELL;
    private static final Map<Integer,Glyph> GLYPHS=new HashMap<>();
    private static DynamicTexture texture;
    private static ResourceLocation location;
    private static RenderType pass;
    private static Font font;
    private static boolean dirty;
    static void clear(){
        if(location!=null)Minecraft.getInstance().getTextureManager().release(location);
        texture=null;location=null;pass=null;font=null;GLYPHS.clear();
    }
    static void prepare(String text){
        long missing=text.codePoints().distinct().filter(c->!GLYPHS.containsKey(c)).count();
        if(GLYPHS.size()+missing>COLS*COLS)clear();
        text.codePoints().forEach(ChantGlyphAtlas::glyph);
        if(dirty){texture.upload();dirty=false;}
    }
    static Glyph glyph(int codepoint){
        var cached=GLYPHS.get(codepoint);if(cached!=null)return cached;
        if(texture==null){
            try(var input=Minecraft.getInstance().getResourceManager().open(ResourceLocation.fromNamespaceAndPath("tnc","font/chant-brush.ttf"))){
                font=Font.createFont(Font.TRUETYPE_FONT,input).deriveFont(32f);
            }catch(Exception e){throw new IllegalStateException("Cannot load bundled chant font",e);}
            texture=new DynamicTexture(new NativeImage(SIZE,SIZE,true));
            location=Minecraft.getInstance().getTextureManager().register("tnc_chant_ink",texture);pass=WaterEffectPass.create(location);
        }
        int slot=GLYPHS.size(),x=slot%COLS*CELL,y=slot/COLS*CELL;
        var tile=new BufferedImage(CELL,CELL,BufferedImage.TYPE_INT_ARGB);var g=tile.createGraphics();
        g.setComposite(AlphaComposite.Src);g.setColor(Color.WHITE);g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        var text=new String(Character.toChars(codepoint));var metrics=g.getFontMetrics();
        int advance=metrics.stringWidth(text);g.drawString(text,5,5+metrics.getAscent());g.dispose();
        var pixels=texture.getPixels();
        for(int iy=0;iy<CELL;iy++)for(int ix=0;ix<CELL;ix++){
            int argb=tile.getRGB(ix,iy);pixels.setPixelRGBA(x+ix,y+iy,(argb&0xff00ff00)|((argb>>>16)&255)|((argb&255)<<16));
        }
        var glyph=new Glyph((float)x/SIZE,(float)y/SIZE,advance*.5f);GLYPHS.put(codepoint,glyph);dirty=true;return glyph;
    }
    static RenderType pass(){return pass;}
    static float cellUv(){return (float)CELL/SIZE;}
    private ChantGlyphAtlas(){}
}
