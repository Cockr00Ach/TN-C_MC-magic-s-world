import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Analytic particle sprites, not painted scene textures. RGB falloff softens native alpha cutout. */
public final class GenerateManaSightSprites {
    static double smooth(double t){return t*t*(3-2*t);}
    static double hash(int x,int y){int h=x*374761393+y*668265263;h=(h^(h>>>13))*1274126177;return (h&0x7fffffff)/(double)0x7fffffff;}
    static double noise(double x,double y){int a=(int)Math.floor(x),b=(int)Math.floor(y);double u=smooth(x-a),v=smooth(y-b);return (hash(a,b)*(1-u)+hash(a+1,b)*u)*(1-v)+(hash(a,b+1)*(1-u)+hash(a+1,b+1)*u)*v;}
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);
        for(int kind=0;kind<3;kind++){
            int n=kind==2?128:64;BufferedImage img=new BufferedImage(n,n,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<n;y++)for(int x=0;x<n;x++){
                double u=(x+.5)/n*2-1,v=(y+.5)/n*2-1,r=u*u+v*v;
                double fall=Math.max(0,1-r),a,c;
                if(kind==0){a=Math.exp(-r*6)*fall*fall;c=Math.min(1,Math.exp(-r*12)*1.6+Math.exp(-r*3)*.14);}
                else if(kind==1){double rays=Math.exp(-Math.abs(u)*42)*Math.exp(-Math.abs(v)*5)+Math.exp(-Math.abs(v)*42)*Math.exp(-Math.abs(u)*5);a=Math.min(1,(Math.exp(-r*30)+rays*.65)*fall);c=Math.min(1,a*1.5);}
                else {double f=.58*noise(u*3+13,v*3+7)+.27*noise(u*7+19,v*7+29)+.15*noise(u*16+31,v*16+41);a=r<.99?1:0;c=fall*fall*fall*(.35+f*.65)*.45;}
                int alpha=(int)Math.round(Math.min(1,a)*255),rgb=(int)Math.round(Math.min(1,c)*255);
                img.setRGB(x,y,(alpha<<24)|(rgb<<16)|(rgb<<8)|rgb);
            }
            ImageIO.write(img,"png",out.resolve(new String[]{"mana_glow","mana_star","mana_mist"}[kind]+".png").toFile());
        }
    }
}
