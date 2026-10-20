import com.tnc.tnc.world.ink.InkLandscapePattern;
import com.tnc.tnc.world.ink.InkFloorPattern;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Orthographic voxel preview of the exact Java landscape sampler, not a game screenshot. */
class PreviewInkLandscape {
    static Graphics2D g;
    static int sx(int x,int z){return 960+(x-z)*3;}
    static int sy(int x,int y,int z){return 210+(x+z)*2-y*5;}
    static void polygon(int color,int... p) {
        int n=p.length/2;int[] xs=new int[n],ys=new int[n];
        for(int i=0;i<n;i++){xs[i]=p[i*2];ys[i]=p[i*2+1];}
        g.setColor(new Color(color,color,color));g.fillPolygon(xs,ys,n);
    }
    public static void main(String[] args)throws Exception {
        var image=new BufferedImage(1920,1320,BufferedImage.TYPE_INT_RGB);
        g=image.createGraphics();g.setColor(new Color(224,224,224));g.fillRect(0,0,1920,1320);
        int lo=-40,hi=240;
        var columns=new InkLandscapePattern.Column[hi-lo+2][hi-lo+2];
        for(int x=lo;x<=hi+1;x++)for(int z=lo;z<=hi+1;z++)columns[x-lo][z-lo]=InkLandscapePattern.column(x,z);
        for(int d=lo*2;d<=hi*2;d++)for(int x=lo;x<=hi;x++) {
            int z=d-x;if(z<lo||z>hi)continue;
            int X=sx(x,z),Y=sy(x,0,z);
            int floor=switch(InkFloorPattern.shadeAt(x,z)){case BLACK->22;case GRAY->92;case LIGHT_GRAY->170;case WHITE->239;};
            polygon(floor,X,Y,X+3,Y+2,X,Y+4,X-3,Y+2);
            var c=columns[x-lo][z-lo];
            for(int y=1;y<=InkLandscapePattern.MAX_HEIGHT;y++) {
                var m=c.at(y);if(m==null)continue;
                int color=switch(m){case INK->17;case CHARCOAL->82;case WASH->166;case PAPER->233;case NEEDLES->23;case PALE_NEEDLES->100;};
                Y=sy(x,y,z);
                if(columns[x-lo+1][z-lo].at(y)==null)polygon(Math.max(0,color-28),X+3,Y+2,X,Y+4,X,Y+9,X+3,Y+7);
                if(columns[x-lo][z-lo+1].at(y)==null)polygon(Math.max(0,color-12),X,Y+4,X-3,Y+2,X-3,Y+7,X,Y+9);
                if(c.at(y+1)==null)polygon(color,X,Y,X+3,Y+2,X,Y+4,X-3,Y+2);
            }
        }
        g.setColor(Color.DARK_GRAY);g.setFont(new Font("SansSerif",Font.PLAIN,24));
        g.drawString("INK REALM / CLIFFS, BENT PINES & CALLIGRAPHIC GROUND",40,45);
        g.setFont(new Font("SansSerif",Font.PLAIN,16));
        g.drawString("Exact Java voxel sampler preview / not an in-game screenshot",40,76);
        g.dispose();ImageIO.write(image,"png",new File("docs/previews/ink_landscape_v5.png"));
    }
}
