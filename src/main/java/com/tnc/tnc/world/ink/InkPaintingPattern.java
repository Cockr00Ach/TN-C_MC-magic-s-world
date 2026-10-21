package com.tnc.tnc.world.ink;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.List;

/** Shared layout and image sampling for a painting ON the floor, never terrain height. */
public final class InkPaintingPattern {
    public static final int CELL = 384;
    public static final String TEXTURE = "textures/environment/ink_shanshui_atlas.png";
    private static final BufferedImage ATLAS = load();
    public record Stamp(double x,double z,double width,double height,int region,float opacity,boolean mirror) {}
    private InkPaintingPattern() {}

    public static List<Stamp> stamps(int cellX,int cellZ) {
        double x=cellX*(double)CELL,z=cellZ*(double)CELL;
        boolean flip=Math.floorMod(cellX*7+cellZ*11,2)==0;
        return List.of(
                new Stamp(x+126,z+112,222,202,0,.88F,flip),
                new Stamp(x+282,z+133,150,181,0,.68F,!flip),
                new Stamp(x+195,z+280,340,150,1,.74F,!flip),
                new Stamp(x+175,z+174,326,123,2,.88F,flip),
                new Stamp(x+202,z+315,334,112,3,.83F,!flip));
    }
    public static int grayAt(int x,int z,int background) {
        double gray=background;
        for(var s:stamps(Math.floorDiv(x,CELL),Math.floorDiv(z,CELL))) {
            double u=(x-s.x)/s.width+.5,v=(z-s.z)/s.height+.5;
            if(u<0||u>=1||v<0||v>=1)continue;
            if(s.mirror)u=1-u;
            int halfW=ATLAS.getWidth()/2,halfH=ATLAS.getHeight()/2;
            int px=s.region%2*halfW+Math.min(halfW-1,(int)(u*halfW));
            int py=s.region/2*halfH+Math.min(halfH-1,(int)(v*halfH));
            int argb=ATLAS.getRGB(px,py);
            double alpha=((argb>>>24)&255)/255.0*s.opacity*edgeOpacity(u,v);
            double value=(((argb>>>16)&255)+((argb>>>8)&255)+(argb&255))/3.0;
            gray=gray*(1-alpha)+value*alpha;
        }
        return (int)Math.round(gray);
    }
    public static int atlasWidth(){return ATLAS.getWidth();}
    public static float edgeOpacity(double u,double v) {
        return (float)Math.max(0,Math.min(1,Math.min(Math.min(u,1-u),Math.min(v,1-v))/.075));
    }
    /** Material normalization: generated washes can contain tiny RGB variations. Keep alpha unchanged. */
    public static int neutralColor(int color) {
        int value=(((color>>>16)&255)+((color>>>8)&255)+(color&255))/3;
        return (color&0xFF000000)|(value<<16)|(value<<8)|value;
    }
    private static BufferedImage load() {
        try(var input=InkPaintingPattern.class.getClassLoader().getResourceAsStream("assets/tnc/"+TEXTURE)) {
            if(input==null)throw new IllegalStateException("Missing shanshui painting atlas");
            var image=ImageIO.read(input);
            if(image==null || image.getWidth()%2!=0 || image.getHeight()%2!=0)
                throw new IllegalStateException("Invalid shanshui quadrant atlas");
            return image;
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load shanshui atlas",e);}
    }
}
