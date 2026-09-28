package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.*;

/** Only called after a successful server unlock. Finite converging streams, no blocks or entities. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class LearningVisuals {
    private record Flow(Element element,int age,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    private static final Map<UUID,Flow> FLOWS=new HashMap<>();
    private LearningVisuals() {}
    public static void start(ServerPlayer p,Element element) {FLOWS.put(p.getUUID(),new Flow(element,0,p.level().dimension()));}
    public static Vector3f color(Element element) {
        if(element==null)return new Vector3f(.68F,.25F,.9F);
        return switch(element) {
            case WATER->new Vector3f(.06F,.4F,1);
            case LIGHTNING->new Vector3f(.66F,.36F,1);
            case FIRE->new Vector3f(1,.2F,.025F);
            case WIND->new Vector3f(.12F,1,.55F);
            case EARTH->new Vector3f(.6F,.3F,.07F);
            case LIGHT->new Vector3f(1,.95F,.62F);
            case DARK->new Vector3f(.4F,.06F,.6F);
        };
    }
    public static Vec3 point(Vec3 chest,int age,int stream,int sample) {
        double t=Math.min(1,(age+sample*.7)/40.0),r=3.2*(1-t),angle=stream*Math.PI/3+age*.14+sample*.12;
        return chest.add(Math.cos(angle)*r,Math.sin(angle*.7)*r*.45,Math.sin(angle)*r);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        var iterator=FLOWS.entrySet().iterator();
        while(iterator.hasNext()) {
            var flow=iterator.next();var p=server.getPlayerList().getPlayer(flow.getKey());var f=flow.getValue();
            if(p==null||!p.isAlive()||f.age>=40||!p.level().dimension().equals(f.dimension)
                    ||com.tnc.tnc.combat.DownedCombat.isDowned(p)) {iterator.remove();continue;}
            if(f.age%2==0) {
                Vec3 chest=p.position().add(0,1.1,0);var dust=new DustParticleOptions(color(f.element),1.2F);
                for(int stream=0;stream<6;stream++)for(int sample=0;sample<4;sample++) {
                    Vec3 at=point(chest,f.age,stream,sample);
                    p.serverLevel().sendParticles(dust,at.x,at.y,at.z,1,0,0,0,0);
                }
            }
            flow.setValue(new Flow(f.element,f.age+1,f.dimension));
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {FLOWS.clear();}
}
