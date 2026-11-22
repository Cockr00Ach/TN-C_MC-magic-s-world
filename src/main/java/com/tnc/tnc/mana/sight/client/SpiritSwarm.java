package com.tnc.tnc.mana.sight.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Random;

import static com.tnc.tnc.mana.sight.client.SpiritShape.Kind;

/** One culled native particle per habitat. Its body grains move together between game ticks. */
final class SpiritSwarm extends TextureSheetParticle {
    enum Flight { DRIFT, SCHOOL, RING, STREAM, PASSAGE }
    private record Swimmer(double u,double v,double w,double beat,double scale,int palette) {}
    // Deep skin, pale underside, coloured fins, iris, shadowed back.
    private static final int[][] PALETTES={
            {0x458f9e,0x9ebdb6,0x8f82b3,0xc6a5ce,0x405f84}, // blue glass, lilac fins
            {0x8476ad,0xb6adc8,0x72a5b6,0xd9a1a7,0x5d507f}, // mauve with a cool green edge
            {0xb6965c,0xd2c29b,0xb68183,0xc6ac9b,0x917055}, // honey and peach
            {0x59a394,0xb1c4a6,0x7897bb,0xdfbc8f,0x427a81}, // sea glass
            {0xb97b99,0xd1acb6,0x9585b3,0xd6b08f,0x845d82}, // dusty rose
            {0x5c88ad,0x9ebbbd,0x6ba99f,0xd6b99b,0x4c5d87}  // cornflower and pale mint
    };
    private static final EnumMap<Kind,SpiritShape.Grain[]> SHAPES=new EnumMap<>(Kind.class);
    static {
        for(var kind:Kind.values()) SHAPES.put(kind,SpiritShape.make(kind));
        // Shader packs bloom emissive texels even at low alpha. Limit the pigment's RGB
        // before drawing, so many overlapping grains keep their hue and the edges stay soft.
        for(int[] palette:PALETTES)for(int i=0;i<palette.length;i++){
            int rgb=palette[i];
            double exposure=i==SpiritShape.Ink.BELLY.ordinal()?.48:.56;
            int r=(int)((rgb>>16&255)*exposure),g=(int)((rgb>>8&255)*exposure),b=(int)((rgb&255)*exposure);
            palette[i]=r<<16|g<<8|b;
        }
    }
    private final Kind kind;
    private final Flight flight;
    private final double phase,bearing,size,extent,range;
    private final Swimmer[] swimmers;
    private final long expires;
    private final double[] backWave=new double[65];
    private final double[] curl=new double[65];
    private final Vector3f right=new Vector3f(), up=new Vector3f();
    private float u0,u1,v0,v1;

    SpiritSwarm(ClientLevel level, Vec3 at, long seed, Kind kind, Flight flight, int number,
                double size, double extent, double range, long expires, SpriteSet sprites) {
        super(level,at.x,at.y,at.z);
        this.kind=kind;this.flight=flight;this.size=size;this.extent=extent;
        this.range=range;this.expires=expires;
        var random=new Random(seed);
        phase=random.nextDouble()*Math.PI*2;
        bearing=random.nextDouble()*Math.PI*2;
        int family=random.nextInt(PALETTES.length);
        swimmers=new Swimmer[number];
        for(int i=0;i<number;i++){
            int colour=switch(kind){
                case WHALE,FANG -> family%2==0?0:5;
                case JELLY -> new int[]{1,0,4,5}[(family+i)%4];
                case LANTERN -> (family+i*5+i/PALETTES.length)%PALETTES.length;
                default -> family;
            };
            swimmers[i]=new Swimmer(random.nextDouble(),random.nextDouble(),random.nextDouble(),
                    random.nextDouble()*6.28,.78+random.nextDouble()*.42,colour);
        }
        setSprite(sprites.get(3,3));hasPhysics=false;
        setBoundingBox(new AABB(at,at).inflate(extent+size+3));
    }

    @Override public ParticleRenderType getRenderType(){return SpiritVeil.SHEET;}
    @Override public boolean shouldCull(){return flight!=Flight.PASSAGE;}
    @Override public void tick(){
        if(!ManaSightClient.enabled()||Minecraft.getInstance().level!=level||level.getGameTime()>=expires){remove();return;}
        if(age<90)age++;
    }

    @Override public void render(VertexConsumer out, Camera camera, float partial){
        if(SpiritVeil.skipOpaque || !ManaSightClient.enabled() || Minecraft.getInstance().level!=level)return;
        var eye=camera.getPosition();
        double dx=x-eye.x,dy=y-eye.y,dz=z-eye.z;
        double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        boolean passage=flight==Flight.PASSAGE;
        double opacity=Mth.clamp((range-distance)/(passage?240:14),0,1)*Math.min(1,(age+partial)/(passage?80.:14.));
        long clock=level.getGameTime();
        if(expires!=Long.MAX_VALUE)opacity*=Mth.clamp((expires-clock-partial)/55.,0,1);
        if(opacity<.015)return;
        right.set(1,0,0).rotate(camera.rotation());
        up.set(0,1,0).rotate(camera.rotation());
        u0=getU0();u1=getU1();v0=getV0();v1=getV1();
        double t=clock+partial;
        double ca=Math.cos(bearing),sa=Math.sin(bearing);
        for(int i=0;i<swimmers.length;i++){
            Swimmer fish=swimmers[i];
            double path=t*(flight==Flight.PASSAGE?.0022:.009)+phase;
            double px,py,pz,vx,vy,vz;
            if(flight==Flight.RING){
                // A tilted, living torus. Different lanes overtake and weave in and out of the shoal.
                double a=path*(.70+.045*fish.v)+fish.u*Math.PI*2;
                double r=extent*(.72+.22*fish.w+.06*Math.sin(3*a+fish.beat));
                px=r*Math.cos(a);py=.77*r*Math.sin(a)+(fish.v-.5)*extent*.22;
                pz=.29*r*Math.sin(a)+(fish.w-.5)*extent*.52+Math.sin(a*2+fish.beat)*.65;
                vx=-Math.sin(a);vy=.77*Math.cos(a);vz=.29*Math.cos(a);
                if(expires!=Long.MAX_VALUE){
                    double remaining=expires-t;
                    double gather=Mth.clamp(Math.min(820-remaining,remaining)/165.,0,1);
                    double fan=1-gather*gather*(3-2*gather);
                    // Incoming lanes braid into a ring, then peel away at the ebb.
                    double lane=fish.u*2-1;
                    px+=fan*extent*lane*.62;py-=fan*extent*Math.sin(a)*.55;
                    pz+=fan*extent*(fish.v-.5)*.72;
                    vy*=1-.72*fan;
                }
            }else if(flight==Flight.STREAM){
                // Two interlaced travelling currents, made up of individual fish.
                double a=path*.62+fish.u*Math.PI*2;
                double b=2*a+(i%2)*Math.PI;
                px=extent*.83*Math.sin(a); py=extent*(.17*Math.sin(b)+(fish.v-.5)*.20);
                pz=extent*(.28*Math.cos(a)+.12*Math.cos(b)+(fish.w-.5)*.18);
                vx=.83*Math.cos(a);vy=.34*Math.cos(b);vz=-.28*Math.sin(a)-.24*Math.sin(b);
            }else{
                double spread=flight==Flight.SCHOOL||swimmers.length>1?1:.40;
                double a=path+(fish.u-.5)*spread;
                if(kind==Kind.JELLY || kind==Kind.OCTOPUS)a=path*.42+fish.u*2.4;
                px=extent*(.65*Math.cos(a)+(fish.w-.5)*spread*.52);
                pz=extent*(.34*Math.sin(a)+(fish.v-.5)*spread*.62);
                py=extent*(.07*Math.sin(2*a)+(fish.v-.5)*spread*.22);
                if(kind==Kind.JELLY || kind==Kind.OCTOPUS)py+=size*.17*Math.sin(t*.080+fish.beat);
                vx=-.65*Math.sin(a);vz=.34*Math.cos(a);vy=.14*Math.cos(2*a);
            }
            double xx=dx+px*ca-pz*sa, yy=dy+py, zz=dz+px*sa+pz*ca;
            double length=passage?size:size*fish.scale;
            double d=Math.sqrt(xx*xx+yy*yy+zz*zz);
            if(d<.6 || xx*camera.getLookVector().x+yy*camera.getLookVector().y+zz*camera.getLookVector().z < -length)continue;
            double horizontal=Math.hypot(vx,vz);
            double cosYaw=(vx*ca-vz*sa)/horizontal,sinYaw=(vx*sa+vz*ca)/horizontal;
            double pitch=Math.atan2(vy,horizontal);
            Kind shape=kind;
            if(kind==Kind.LANTERN&&(length/d<.060||swimmers.length>18))shape=Kind.FRY;
            draw(out,shape,fish,xx,yy,zz,length,cosYaw,sinYaw,Math.cos(pitch),Math.sin(pitch),
                    t,opacity*Mth.clamp((d-.6)/1.8,0,1),d,camera);
        }
    }

    private void draw(VertexConsumer out,Kind shape,Swimmer fish,double x,double y,double z,double scale,
                      double cy,double sy,double cp,double sp,double t,double opacity,double distance,Camera camera){
        boolean bell=shape==Kind.JELLY||shape==Kind.OCTOPUS;
        boolean whale=shape==Kind.WHALE;
        double beat=t*(whale?.036:shape==Kind.RIBBON?.11:bell?.083:.16)+fish.beat;
        double pulse=Math.sin(beat);
        for(int j=0;j<backWave.length;j++){
            double u=j/64.;
            backWave[j]=Math.sin(beat-u*(shape==Kind.RIBBON?8:4))*u*u*(whale?.13:shape==Kind.RIBBON?.23:.185);
            if(bell)curl[j]=Math.cos(beat-4*u)*u*u*.14;
        }
        int[] palette=PALETTES[fish.palette];
        double roll=whale ? .17+.115*Math.sin(t*.017+fish.beat) : 0;
        double cr=Math.cos(roll),sr=Math.sin(roll);
        SpiritShape.Grain[] body=SHAPES.get(shape);
        int stride=body.length>400 && scale/distance<.10 ? 2:1;
        float terrainDepth=Minecraft.getInstance().options.getEffectiveRenderDistance()*16f;
        var look=camera.getLookVector();
        for(int n=0;n<body.length;n+=stride){
            var p=body[n];
            double lx=p.x(), ly=p.y(), lz=p.z();
            if(bell){
                if(ly>=0){
                    double squeeze=1+.105*pulse;
                    lx*=squeeze;lz*=squeeze;ly*=1-.16*pulse;
                }else{
                    int j=Mth.clamp((int)(-ly*84),0,64);
                    lx+=backWave[j];lz+=curl[j];ly*=1-.045*pulse;
                }
            }else{
                int j=Mth.clamp((int)((.41-lx)*64),0,64);
                if(whale){
                    ly+=backWave[j];
                    double raised=ly*cr-lz*sr;
                    lz=lz*cr+ly*sr;ly=raised;
                }else lz+=backWave[j];
            }
            double forward=(lx*cp-ly*sp)*scale,side=lz*scale;
            float px=(float)(x+forward*cy-side*sy),py=(float)(y+(lx*sp+ly*cp)*scale),
                    pz=(float)(z+forward*sy+side*cy);
            int rgb=palette[p.ink().ordinal()];
            float radius=(float)Math.max(distance*.0008,p.radius()*scale*(stride==2?1.30:1));
            if(whale){
                // Preserve angular size and parallax beyond the terrain projection. Compress
                // only the depth behind the chunk horizon; foreground terrain still occludes it.
                float depth=px*look.x+py*look.y+pz*look.z;
                if(depth>terrainDepth){
                    float beyond=depth-terrainDepth;
                    float factor=(terrainDepth+terrainDepth*beyond/(terrainDepth+beyond))/depth;
                    px*=factor;py*=factor;pz*=factor;radius*=factor;
                }
            }
            float alpha=(float)(opacity*p.opacity()*.57);
            bead(out,px,py,pz,radius,rgb,alpha);
        }
    }

    private void bead(VertexConsumer out,float x,float y,float z,float r,int rgb,float alpha){
        float ax=(right.x+up.x)*r,ay=(right.y+up.y)*r,az=(right.z+up.z)*r;
        float bx=(right.x-up.x)*r,by=(right.y-up.y)*r,bz=(right.z-up.z)*r;
        vertex(out,x-ax,y-ay,z-az,u1,v1,rgb,alpha);
        vertex(out,x-bx,y-by,z-bz,u1,v0,rgb,alpha);
        vertex(out,x+ax,y+ay,z+az,u0,v0,rgb,alpha);
        vertex(out,x+bx,y+by,z+bz,u0,v1,rgb,alpha);
    }

    private static void vertex(VertexConsumer out,float x,float y,float z,float u,float v,int rgb,float alpha){
        out.vertex(x,y,z).color(rgb>>16&255,rgb>>8&255,rgb&255,(int)(alpha*255)).uv(u,v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0,1,0).endVertex();
    }
}

