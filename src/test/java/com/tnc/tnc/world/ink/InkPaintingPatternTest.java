package com.tnc.tnc.world.ink;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class InkPaintingPatternTest {
    @Test void cutoutBordersFeatherWithoutFadingTheCenter() {
        assertEquals(0,InkPaintingPattern.edgeOpacity(0,.5));
        assertEquals(0,InkPaintingPattern.edgeOpacity(1,.5));
        assertEquals(0,InkPaintingPattern.edgeOpacity(.5,0));
        assertEquals(.5F,InkPaintingPattern.edgeOpacity(.0375,.5),.001);
        assertEquals(1,InkPaintingPattern.edgeOpacity(.5,.5));
    }
    @Test void atlasHasFourDistinctTransparentMountainAndCloudCutouts() throws Exception {
        var image=ImageIO.read(Path.of("src/main/resources/assets/tnc/"+InkPaintingPattern.TEXTURE).toFile());
        assertTrue(image.getColorModel().hasAlpha());
        assertTrue(image.getWidth()>=1024 && image.getHeight()>=1024);
        for(int region=0;region<4;region++) {
            int visible=0,clear=0,dark=0;
            for(int x=region%2*image.getWidth()/2;x<(region%2+1)*image.getWidth()/2;x+=3)
                for(int y=region/2*image.getHeight()/2;y<(region/2+1)*image.getHeight()/2;y+=3) {
                    int pixel=image.getRGB(x,y),alpha=pixel>>>24;
                    if(alpha<10)clear++;
                    if(alpha>120){visible++;if((pixel&255)<150)dark++;}
                    if(alpha>80) {
                        int neutral=InkPaintingPattern.neutralColor(pixel);
                        int r=(neutral>>>16)&255,g=(neutral>>>8)&255,b=neutral&255;
                        assertTrue(r==g && g==b,"Runtime painting material must be strictly neutral black/white");
                        assertEquals(alpha,neutral>>>24,"Material normalization preserves transparency");
                    }
                }
            assertTrue(visible>500 && clear>100 && dark>30,"Each quadrant needs an isolated ink cutout");
        }
    }
    @Test void layoutIsFlatCrossChunkDeterministicAndHasMountainAndCloudRegions() {
        var stamps=InkPaintingPattern.stamps(-1,2);
        assertEquals(5,stamps.size());assertEquals(stamps,InkPaintingPattern.stamps(-1,2));
        for(int region=0;region<4;region++) {final int r=region;assertTrue(stamps.stream().anyMatch(s->s.region()==r));}
        int changed=0;
        for(int x=0;x<384;x+=3)for(int z=0;z<384;z+=3) {
            int gray=InkPaintingPattern.grayAt(x,z,255);
            assertTrue(gray>=0 && gray<=255);if(gray<220)changed++;
        }
        assertTrue(changed>1000,"Mountain illustration covers a large ground area, not a block-sized icon");
    }
}
