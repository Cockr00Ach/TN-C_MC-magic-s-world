package com.tnc.tnc.home;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class PropertyGuidance {
    public static String look(ServerPlayer p,String id){var plot=PlotCatalog.find(id);var o=HousingService.sourceOrigin(p.server.overworld());if(plot==null||o==null)return "房源尚未就绪。";
        p.getPersistentData().putString("TncPropertyRoute",id);String line="米洛："+plot.number()+"号"+plot.name()+"，位置 "+o.offset(plot.entry()).toShortString()+"。到门口后实地看看，再回来决定。";p.sendSystemMessage(Component.literal(line));return line;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%20!=0||p.serverLevel()!=p.server.overworld())return;var o=HousingService.sourceOrigin(p.serverLevel());if(o==null)return;
        for(var plot:PlotCatalog.ALL)if(o.offset(plot.entry()).closerToCenterThan(p.position(),8)){if(!AdventureService.profile(p).hasMilestone("viewed_"+plot.id())){AdventureService.milestone(p,"viewed_"+plot.id());p.sendSystemMessage(Component.literal("你已到达"+plot.number()+"号"+plot.name()+"。门牌与边界已记录；回银行可以办理购买。"));}}
        String id=p.getPersistentData().getString("TncPropertyRoute");var a=PlotCatalog.find(id);if(a==null)return;var target=o.offset(a.entry());double distance=Math.sqrt(target.distToCenterSqr(p.position()));
        if(distance<8){p.getPersistentData().remove("TncPropertyRoute");return;}
        int dx=target.getX()-p.blockPosition().getX(),dz=target.getZ()-p.blockPosition().getZ();String direction=(dz>5?"南":dz<-5?"北":"")+(dx>5?"东":dx<-5?"西":"");p.displayClientMessage(Component.literal("看房指引 · "+a.number()+"号"+a.name()+" · 向"+direction+"约"+(int)distance+"米 · "+target.toShortString()),true);
    }
}
