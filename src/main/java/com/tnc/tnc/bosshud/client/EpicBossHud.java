package com.tnc.tnc.bosshud.client;

import com.tnc.tnc.bosshud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Only replaces an identified TN-C bar. Foreign bosses, build-progress bars and vanilla music remain untouched. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class EpicBossHud {
    private static final class Entry {BossHudNetwork.Snapshot snapshot;int seen;final BossHudRules.Trail trail=new BossHudRules.Trail();Entry(BossHudNetwork.Snapshot s,int t){snapshot=s;seen=t;}}
    private static final Map<UUID,Entry> BARS=new LinkedHashMap<>();
    private static ClientLevel level;private static int ticks;
    private static final boolean AUDIT=Boolean.getBoolean("tnc.epicBossHudAudit");
    private static long draws,foreign;private static final Set<String> DRAWN=new HashSet<>();
    private static void session(){var current=Minecraft.getInstance().level;if(current!=level){BARS.clear();level=current;ticks=0;}}
    public static void accept(BossHudNetwork.Snapshot packet){
        session();if(level==null||BossHudTheme.forType(packet.type())==null)return;
        var theme=BossHudTheme.forType(packet.type());
        var clean=new BossHudNetwork.Snapshot(packet.bar(),packet.entity(),packet.type(),BossHudRules.health(packet.health(),packet.maximum()),BossHudRules.maximum(packet.maximum()),BossHudRules.phase(packet.phase(),theme.phases),theme.phases);
        var entry=BARS.get(packet.bar());
        if(entry==null||!entry.snapshot.entity().equals(packet.entity())){
            if(BARS.size()>=BossHudRules.CACHE_LIMIT)BARS.remove(BARS.keySet().iterator().next());BARS.put(packet.bar(),new Entry(clean,ticks));
        }else{entry.snapshot=clean;entry.seen=ticks;}
    }
    public static void remove(UUID bar){BARS.remove(bar);}
    public static int size(){return BARS.size();}public static long draws(){return draws;}public static long foreign(){return foreign;}public static Set<String> drawnThemes(){return Set.copyOf(DRAWN);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;session();if(level==null||Minecraft.getInstance().isPaused())return;ticks++;
        BARS.values().removeIf(entry->ticks-entry.seen>BossHudRules.LEASE_TICKS);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void boss(CustomizeGuiOverlayEvent.BossEventProgress event){
        session();var entry=BARS.get(event.getBossEvent().getId());if(entry==null){if(AUDIT)foreign++;return;}
        if(com.tnc.tnc.world.ink.client.InkEncounterCamera.active()||com.tnc.tnc.world.wind.client.CanglanCinematicCamera.active()){event.setCanceled(true);event.setIncrement(0);return;}
        var mc=Minecraft.getInstance();boolean compact=BARS.size()>1;int width=BossHudRules.width(event.getWindow().getGuiScaledWidth());
        long now=net.minecraft.Util.getMillis();var p=entry.snapshot;entry.trail.update(BossHudRules.progress(p.health(),p.maximum()),p.phase(),now);
        EpicBossBarArt.draw(event.getGuiGraphics(),BossHudTheme.forType(p.type()),event.getBossEvent().getName(),p,
                event.getWindow().getGuiScaledWidth()/2-width/2,event.getY(),width,compact,entry.trail.shown(),entry.trail.ghost(),now/1000.0);
        event.setCanceled(true);event.setIncrement(BossHudRules.rowHeight(compact));
        if(AUDIT){draws++;DRAWN.add(p.type());}
    }
}
