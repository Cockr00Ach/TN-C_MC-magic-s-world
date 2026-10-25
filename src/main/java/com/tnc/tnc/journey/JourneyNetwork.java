package com.tnc.tnc.journey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class JourneyNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.parse("tnc:journeys"),()->"1","1"::equals,"1"::equals);
    private record Session(String page,long token,long until){}
    private static final Map<UUID,Session> sessions=new HashMap<>();private static long sequence;
    public record View(CompoundTag data){}public record Action(long token,String action,String value){}
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e){e.enqueueWork(()->{
        CHANNEL.messageBuilder(View.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder((m,b)->b.writeNbt(m.data)).decoder(b->new View(b.readNbt())).consumerMainThread((m,c)->{Client.open(m.data);c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(Action.class,1,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{b.writeLong(m.token);b.writeUtf(m.action,24);b.writeUtf(m.value,128);}).decoder(b->new Action(b.readLong(),b.readUtf(24),b.readUtf(128))).consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)act(p,m);c.get().setPacketHandled(true);}).add();
    });}
    private static final class Client{static void open(CompoundTag t){com.tnc.tnc.journey.client.JourneyScreen.open(t);}}
    public static void open(ServerPlayer p,String page,CompoundTag data){long token=++sequence;if(!page.equals("greeting"))sessions.put(p.getUUID(),new Session(page,token,p.server.overworld().getGameTime()+2400));data.putString("Page",page);data.putLong("Token",token);CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new View(data));}
    public static void send(long token,String action,String value){CHANNEL.sendToServer(new Action(token,action,value));}
    private static void namingResult(ServerPlayer p,long token,boolean success,String message){var t=new CompoundTag();t.putString("Page","name_result");t.putLong("Token",token);t.putBoolean("Success",success);t.putString("Message",message);CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new View(t));}
    private static void act(ServerPlayer p,Action m){
        if(m.action.equals("name")){
            var session=sessions.get(p.getUUID());
            if(session==null||!session.page.equals("profile")||session.token!=m.token){namingResult(p,m.token,false,"页面已失效，请关闭后重新打开人物档案。");return;}
            // Naming changes no inventory or combat state. Its matching session
            // remains valid while the player considers a name, even past 2 min.
            boolean saved=FamilyLife.name(p,m.value);
            namingResult(p,m.token,saved,saved?"姓名已保存":"名字请使用1—20个字符。");return;
        }
        if(!p.isAlive()||p.isSpectator()||com.tnc.tnc.combat.DownedCombat.isDowned(p))return;
        if(m.action.equals("welcome")){if(!JourneyData.get(p.server).player(p.getUUID()).getBoolean("Named")){FamilyLife.initialize(p);var data=FamilyLife.profile(p);data.putBoolean("InitialWelcome",true);var existing=sessions.get(p.getUUID());if(existing!=null&&existing.page.equals("profile")&&existing.until>=p.server.overworld().getGameTime()){data.putString("Page","profile");data.putLong("Token",existing.token);CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new View(data));}else open(p,"profile",data);}return;}
        if(m.action.equals("welcome_seen")){
            var pending=sessions.get(p.getUUID());
            if(pending!=null&&pending.page.equals("profile")&&pending.token==m.token
                    &&!JourneyData.get(p.server).player(p.getUUID()).getBoolean("Named")){
                // Loading a large pack may outlast the initial session. Start the
                // naming window when the client actually displays the panel.
                sessions.put(p.getUUID(),new Session("profile",pending.token,p.server.overworld().getGameTime()+2400));
                JourneyData.get(p.server).player(p.getUUID()).putBoolean("WelcomeShown",true);
                JourneyData.get(p.server).setDirty();
            }
            return;
        }
        if(m.action.equals("sip")){Preparation.sip(p);return;}
        if(m.action.equals("profile")){FamilyLife.openProfile(p);return;}
        var s=sessions.get(p.getUUID());if(s==null||s.token!=m.token||s.until<p.server.overworld().getGameTime())return;
        if(s.page.equals("board"))CommissionService.act(p,m.action,m.value);
        else if(s.page.equals("family")&&m.action.equals("dinner"))FamilyLife.dinner(p);
        else if(s.page.equals("family")&&m.action.equals("home")){CommissionService.message(p,com.tnc.tnc.home.McaResidents.moveFamily(p));FamilyLife.family(p);}
    }
    public static void clear(){sessions.clear();}
}
