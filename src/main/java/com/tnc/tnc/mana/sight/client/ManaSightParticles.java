package com.tnc.tnc.mana.sight.client;

import com.tnc.tnc.mana.sight.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Native particle-atlas effects: no line meshes, lightning render pass, or screen wallpaper. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ManaSightParticles {
    private static final int[] COLORS={0x13d5ef,0xff6933,0x9d63ff,0x29dc88,0xf5b339,0x7abaff,0xff62b1};
    private static final RandomSource RANDOM=RandomSource.create(0x544e435349474854L);
    private static final List<ManaSightParticle> OWNED=new ArrayList<>();
    private static final List<ManaSightParticle> EFFECTS=new ArrayList<>();
    private static ClientLevel effectLevel;private static long lastPrune=Long.MIN_VALUE;
    private static SpriteSet sprites;
    private static ClientLevel level;
    private static int ticks, cursor, emitted;
    private static float fade;

    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event){
        event.registerSpriteSet(ManaSightParticleContent.TYPE,set->{
            sprites=set;
            return (type,l,x,y,z,vx,vy,vz)->null; // This effect is owned by the local toggle, never /particle spam.
        });
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)resources->Minecraft.getInstance().execute(ManaSightParticles::reloadClear));
    }
    private static void pruneEffects(){var mc=Minecraft.getInstance();if(mc.level!=effectLevel){EFFECTS.forEach(ManaSightParticle::remove);EFFECTS.clear();effectLevel=mc.level;lastPrune=Long.MIN_VALUE;}if(effectLevel==null)return;long time=effectLevel.getGameTime();if(time==lastPrune)return;lastPrune=time;EFFECTS.removeIf(p->{boolean expired=!p.isAlive()||mc.player==null||p.distanceToSqr(mc.player.getEyePosition())>110*110;if(expired)p.remove();return expired;});}
    public static void effect(Vec3 from,Vec3 to,int color,int sprite,int motion,float size,int life){var mc=Minecraft.getInstance();pruneEffects();if(mc.level==null||mc.player==null||sprites==null||Math.min(from.distanceToSqr(mc.player.getEyePosition()),to.distanceToSqr(mc.player.getEyePosition()))>64*64||count()>=limit()||EFFECTS.size()>=limit()/3)return;var p=new ManaSightParticle(mc.level,from,to,color,sprite,motion,size,1,life,unit()*30,sprites,false);EFFECTS.add(p);mc.particleEngine.add(p);}
    public static int count(){pruneEffects();return OWNED.size()+EFFECTS.size();}
    public static int sightCount(){return OWNED.size();}
    public static void reloadClear(){clear();EFFECTS.forEach(ManaSightParticle::remove);EFFECTS.clear();effectLevel=null;}
    public static int emittedTransfers(){return emitted;}
    static float fade(){return fade;}
    public static void clear(){OWNED.forEach(ManaSightParticle::remove);OWNED.clear();level=null;ticks=0;cursor=0;fade=0;}
    public static int limit(){var p=Minecraft.getInstance().options.particles().get();return p==ParticleStatus.MINIMAL?6000:p==ParticleStatus.DECREASED?14400:24000;}
    public static int range(){var p=Minecraft.getInstance().options.particles().get();return p==ParticleStatus.MINIMAL?64:p==ParticleStatus.DECREASED?80:96;}
    public static double farthest(){var mc=Minecraft.getInstance();if(mc.player==null)return 0;return Math.sqrt(OWNED.stream().mapToDouble(p->p.distanceToSqr(mc.player.getEyePosition())).max().orElse(0));}
    private static double unit(){return RANDOM.nextDouble();}
    private static int tint(int color){ // Individual grains vary in warmth without rainbow bands.
        double blend=unit()*.08;int r=color>>16&255,g=color>>8&255,b=color&255;
        return ((int)(r+(255-r)*blend)<<16)|((int)(g+(255-g)*blend)<<8)|(int)(b+(255-b)*blend);
    }
    private static void add(Vec3 at,Vec3 target,int color,int texture,int motion,float size,float opacity,int lifetime,double phase){
        if(sprites==null||count()>=limit()||!level.hasChunkAt(BlockPos.containing(at))||level.getBlockState(BlockPos.containing(at)).isSolidRender(level,BlockPos.containing(at)))return;
        var p=new ManaSightParticle(level,at,target,tint(color),texture,motion,size,opacity,lifetime,phase,sprites);
        OWNED.add(p);Minecraft.getInstance().particleEngine.add(p);
    }
    public static void update(ClientLevel world,Vec3 eye,Collection<ManaFlow> flows,float entrance){
        if(level!=world){clear();level=world;}fade=entrance;while(OWNED.size()+EFFECTS.size()>limit()){var list=OWNED.isEmpty()?EFFECTS:OWNED;list.remove(0).remove();}
        OWNED.removeIf(p->{boolean expired=!p.isAlive()||world.getGameTime()-p.born>200||p.distanceToSqr(eye)>110*110;if(expired)p.remove();return expired;});
        if(sprites==null)return;
        int quality=limit(), fill=ticks++==0?1800:0;double reach=range()-6;
        // Reserve a quarter of the budget for real, server-confirmed interaction.
        int ambientBudget=Math.max(0,quality*3/4-EFFECTS.size());
        for(int i=0;i<fill+85&&OWNED.size()<ambientBudget;i++){
            double a=unit()*Math.PI*2,r=3+Math.sqrt(unit())*reach;
            Vec3 at=eye.add(Math.cos(a)*r,-10+unit()*48,Math.sin(a)*r);
            int color=COLORS[Math.floorMod(Mth.floor(at.x/16)*31+Mth.floor(at.z/16)*17,7)];
            boolean star=unit()<.09;
            add(at,Vec3.ZERO,color,star?1:0,0,(float)(star?.20+unit()*.16:.065+unit()*.11),.9f,85+RANDOM.nextInt(75),unit()*30);
        }
        int cx=Mth.floor(eye.x/20),cz=Mth.floor(eye.z/20),cy=Mth.floor(eye.y/24),cells=(int)Math.ceil(reach/20);
        record Cell(Vec3 heart,int hash){}
        var field=new ArrayList<Cell>();
        // Stable world-space cells; rotate traversal so a full budget does not favor the west.
        for(int dx=-cells;dx<=cells;dx++)for(int dz=-cells;dz<=cells;dz++)for(int dy=-1;dy<=1;dy++){
            int gx=cx+dx,gz=cz+dz,gy=cy+dy,hash=gx*73428767^gz*912931^gy*19349663;
            if(Math.floorMod(hash,3)!=0)continue;
            Vec3 heart=new Vec3(gx*20+10,gy*24+12,gz*20+10);
            if(heart.distanceToSqr(eye)<36||heart.distanceToSqr(eye)>reach*reach)continue;
            field.add(new Cell(heart,hash));
        }
        if(!field.isEmpty())Collections.rotate(field,Math.floorMod(ticks*137,field.size()));
        for(var cell:field){if(OWNED.size()>=ambientBudget)break;var heart=cell.heart();int hash=cell.hash();
            int color=COLORS[Math.floorMod(hash,7)];int grains=fill>0?35:3;
            for(int n=0;n<grains&&OWNED.size()<ambientBudget;n++){
                double angle=unit()*Math.PI*2,r=.8+Math.sqrt(unit())*6;
                Vec3 at=heart.add(Math.cos(angle)*r,(unit()-.5)*12,Math.sin(angle)*r);
                add(at,heart,color,unit()<.065?1:0,1,(float)(.075+unit()*.14),1,90+RANDOM.nextInt(70),angle);
            }
            if((ticks+hash)%11==0||fill>0)add(heart,heart,color,0,1,(float)(.3+unit()*.35),.9f,90+RANDOM.nextInt(35),unit()*30);
            if((ticks+hash)%17==0||fill>0){Vec3 at=heart.add((unit()-.5)*8,(unit()-.5)*8,(unit()-.5)*8);add(at,heart,color,2,0,(float)(2.0+unit()*2.8),.65f,95+RANDOM.nextInt(45),unit()*30);}
        }
        // Flow packets describe accepted energy only. Discrete grains always move source -> sink.
        var visible=flows.stream().filter(f->Math.min(eye.distanceToSqr(f.from()),eye.distanceToSqr(f.to()))<range()*range()).toList();
        int budget=quality/75;
        for(int i=0;i<visible.size()&&budget>0;i++){
            var f=visible.get(Math.floorMod(cursor+i,visible.size()));
            int n=Math.min(budget,f.kind()==ManaFlow.EMISSION?8:Math.min(22,7+f.amount()/3));
            for(int j=0;j<n;j++){
                if(f.kind()==ManaFlow.EMISSION){double a=unit()*Math.PI*2;Vec3 end=f.from().add(Math.cos(a)*(1+unit()),.5+unit()*1.2,Math.sin(a)*(1+unit()));add(f.from(),end,f.color(),j==0?1:0,3,(float)(.10+unit()*.08),.95f,22+RANDOM.nextInt(18),unit()*30);}
                else {double length=f.from().distanceTo(f.to());int life=Mth.clamp((int)(length*2.5),12,36);add(f.from(),f.to(),f.color(),j==0&&ticks%3==0?1:0,2,(float)(.12+unit()*.10),1f,life,unit()*30);}
                emitted++;
            }budget-=n;
        }
        cursor++;
    }
}
