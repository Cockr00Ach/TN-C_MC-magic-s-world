package com.tnc.tnc.adventure;

import com.google.gson.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual hand-render transform chain, including the inverted model Y axis. */
final class WandHoldTransformTest {
    private static final String[] ELEMENTS={"water","fire","lightning","wind","earth","light","dark"};
    private JsonObject model(String name) throws IOException {
        try(var in=getClass().getResourceAsStream("/assets/tnc/models/item/"+name+".json")) {
            return JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
        }
    }
    private Vector3f sample(BufferedImage image,boolean grip) {
        int low=32,high=-1;
        for(int y=0;y<32;y++)for(int x=0;x<32;x++)if((image.getRGB(x,y)>>>24)>=128){low=Math.min(low,y);high=Math.max(high,y);}
        double xsum=0,ysum=0,count=0;
        for(int y=grip?high-5:low;y<=(grip?high:low+7);y++)for(int x=0;x<32;x++) {
            if(y<0||y>=32||(image.getRGB(x,y)>>>24)<128)continue;
            xsum+=x+.5;ysum+=y+.5;count++;
        }
        return new Vector3f((float)(xsum/count/2-8),(float)(8-ysum/count/2),0);
    }
    private Vector3f pose(Vector3f point,JsonObject display,boolean left) {
        var r=display.getAsJsonArray("rotation");var s=display.getAsJsonArray("scale");var t=display.getAsJsonArray("translation");
        float sign=left?-1:1,rad=(float)Math.PI/180;
        return new Vector3f(point).mul(s.get(0).getAsFloat(),s.get(1).getAsFloat(),s.get(2).getAsFloat())
                .rotate(new Quaternionf().rotationXYZ(r.get(0).getAsFloat()*rad,sign*r.get(1).getAsFloat()*rad,sign*r.get(2).getAsFloat()*rad))
                .add(sign*t.get(0).getAsFloat(),t.get(1).getAsFloat(),t.get(2).getAsFloat());
    }
    @Test void thirdPersonKeepsHeadAboveHandAndPalmOnGripForEveryWand() throws IOException {
        for(String element:ELEMENTS)for(int tier=1;tier<=5;tier++) {
            String name=element+"_wand_"+tier;
            var image=ImageIO.read(getClass().getResourceAsStream("/assets/tnc/textures/item/wands/"+name+".png"));
            var grip=sample(image,true);var tip=sample(image,false);var displays=model(name).getAsJsonObject("display");
            for(boolean left:new boolean[]{false,true}) {
                var d=displays.getAsJsonObject("thirdperson_"+(left?"lefthand":"righthand"));
                var palm=pose(grip,d,left);
                assertTrue(palm.length()<1,name+" must meet the hand at its handle, not its center");
                var axis=pose(tip,d,left).sub(palm).rotateY((float)Math.PI).rotateX(-(float)Math.PI/2);
                assertTrue(-axis.y>axis.length()*.8,name+" head must point up in the player model, hand="+left);
            }
        }
    }
    @Test void firstPersonKeepsGripAtHandAndHeadAboveItInFrontOfCamera() throws IOException {
        for(String element:ELEMENTS)for(int tier=1;tier<=5;tier++) {
            String name=element+"_wand_"+tier;
            var image=ImageIO.read(getClass().getResourceAsStream("/assets/tnc/textures/item/wands/"+name+".png"));
            var grip=sample(image,true);var tip=sample(image,false);var displays=model(name).getAsJsonObject("display");
            for(boolean left:new boolean[]{false,true}) {
                var d=displays.getAsJsonObject("firstperson_"+(left?"lefthand":"righthand"));
                var palm=pose(grip,d,left);
                assertTrue(palm.length()<1,name+" first person grip must meet the hand");
                var axis=pose(tip,d,left).sub(palm).rotateY((left?-1:1)*(float)Math.PI/4);
                assertTrue(axis.y>axis.length()*.4&&axis.z<0,name+" head must rise away from the camera, hand="+left);
            }
        }
    }
}
