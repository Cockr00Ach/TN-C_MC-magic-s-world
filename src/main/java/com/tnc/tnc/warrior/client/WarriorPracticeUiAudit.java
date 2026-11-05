package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.WarriorCodex;
import com.tnc.tnc.warrior.WarriorCodex.Weapon;
import com.tnc.tnc.warrior.TemperingPacket;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 可选的开机渲染审计（只在加 {@code -Dtnc.warriorUiAudit=true} 时跑，顶级界面，不进存档）。
 *
 * <p>它做的事很朴素：把训练面板和武器图鉴在各种数据状态下真的画一遍、截图、顺手断言
 * "该灰的按钮是灰的、六个武器页签都在、点页签真的会换页"。没有这一层，界面里
 * 只有真进游戏才会暴露的排版崩溃（比如某页条目多到画出面板）就只能靠人肉点。
 */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class WarriorPracticeUiAudit {

    private record Case(String file, TemperingPacket packet, boolean codex, Weapon weapon) {}

    private static TemperingPacket practice(boolean enrolled,int points,int talent,boolean graduate,
                                            boolean training,int blocks,int counterTicks,int windTicks,
                                            boolean ground,int mode,int completions,int bestScore,
                                            int bestReaction,String role){
        return new TemperingPacket(enrolled,points,talent,graduate,training,blocks,counterTicks,windTicks,
            /*open=*/false,ground,mode,completions,bestScore,bestReaction,role,
            // 课程进度由服务端现拼；审计只关心排版，用一个真实形状的串就够。
            "guard:1/3|blade:2/4|wind:0/3|counter:1/1","第三课 · 旋转斩击 1/2");
    }

    private static final Case[] CASES={
        // 训练面板（终端）三种状态
        new Case("practice-no-talent.png",new TemperingPacket(false,0,0,false,false,0,0,0,false),false,null),
        new Case("practice-inside-arena.png",
            practice(true,320,2,true,false,0,0,0,true,2,12,95,7,"counter"),false,null),
        new Case("practice-training.png",
            practice(true,320,2,true,true,3,35,0,true,2,12,95,7,"guard"),false,null),
        // 武器图鉴：六个页签各画一次，覆盖"已开放 / 进行中 / 规划中 / 无天赋"
        new Case("codex-sword.png",practice(true,1000,2,false,false,0,0,0,false,0,1,0,0,""),true,Weapon.SWORD),
        new Case("codex-shield.png",practice(true,1000,2,true,false,0,0,0,false,0,1,0,0,""),true,Weapon.SHIELD),
        new Case("codex-axe.png",practice(true,200,1,false,false,0,0,0,false,0,0,0,0,""),true,Weapon.AXE),
        new Case("codex-polearm.png",practice(true,6000,6,true,false,0,0,0,false,0,5,90,0,""),true,Weapon.POLEARM),
        new Case("codex-bow.png",practice(true,6000,6,true,false,0,0,0,false,0,5,90,0,""),true,Weapon.BOW),
        new Case("codex-no-talent.png",new TemperingPacket(false,0,0,false,false,0,0,0,false),true,Weapon.WIND)
    };

    private static int index,ticks,bootTicks;
    private static boolean opened;
    private static volatile boolean saving,saved;

    private WarriorPracticeUiAudit(){}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(!Boolean.getBoolean("tnc.warriorUiAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(++bootTicks>2400){mc.stop();throw new IllegalStateException("Warrior UI audit timed out");}
        if(!opened){
            if(mc.getOverlay()!=null || !(mc.screen instanceof TitleScreen
                    || mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen))return;
            opened=true;mc.options.pauseOnLostFocus=false;show(mc);return;
        }
        if(saving)return;
        if(saved){
            index++;saved=false;ticks=0;
            if(index==CASES.length){
                Path folder=mc.gameDirectory.toPath().resolve("screenshots");
                Files.writeString(folder.resolve("warrior-ui-audit.json"),
                    "{\"cases\":"+CASES.length+",\"buttonStatesVerified\":true,"
                    +"\"hotkeyRegistered\":true,\"codexTabsVerified\":"+Weapon.values().length+","
                    +"\"scope\":\"Actual client render with simulated display state; no world entered\"}");
                mc.stop();return;
            }
            show(mc);return;
        }
        if(++ticks<30)return;
        if(!java.util.Arrays.asList(mc.options.keyMappings).contains(WarriorPracticeKeys.OPEN)){
            mc.stop();throw new IllegalStateException("Warrior practice key was not registered");
        }
        Case current=CASES[index];
        if(current.codex()) verifyCodex(mc,(WarriorCodexScreen)mc.screen,current.weapon());
        else verifyPractice(mc,(WarriorPracticeScreen)mc.screen);
        saving=true;
        Screenshot.grab(mc.gameDirectory,current.file(),mc.getMainRenderTarget(),message->{saved=true;saving=false;});
    }

    /** 训练面板只做三件事，所以断言也就三条：开始/结束/难度，外加"只有一个教官被选中"。 */
    private static void verifyPractice(Minecraft mc,WarriorPracticeScreen screen){
        List<String> titles=new ArrayList<>();
        for(var child:screen.children())if(child instanceof Button b)titles.add(b.getMessage().getString());
        boolean expectStart=TemperingClient.enrolled()&&TemperingClient.ground()&&TemperingClient.activeRole().isEmpty();
        boolean expectEnd=!TemperingClient.activeRole().isEmpty()||TemperingClient.training();
        boolean expectDifficulty=TemperingClient.ground()&&TemperingClient.enrolled()&&!TemperingClient.training();
        int selected=0;
        for(var child:screen.children())if(child instanceof Button b){
            String title=b.getMessage().getString();
            if(title.startsWith("▶ "))selected++;
            // 只校验三个真正的"动作"按钮；教官按钮是单选，选中态由标签上的 ▶ 表达，
            // 再去断言它的 active 就等于把面板内部的置灰规则抄第二遍（迟早抄出分歧）。
            if(!title.equals("开始练习")&&!title.equals("结束练习")&&!title.startsWith("陪练难度"))continue;
            boolean expected=title.equals("开始练习")?expectStart
                :title.equals("结束练习")?expectEnd
                :expectDifficulty;
            if(b.active!=expected){mc.stop();throw new IllegalStateException("Unexpected practice control state: "+title);}
        }
        if(selected!=1){mc.stop();throw new IllegalStateException("Expected exactly one selected instructor, got "+selected);}
        if(titles.size()!=8){mc.stop();throw new IllegalStateException("Practice panel should hold 8 buttons, got "+titles.size());}
        for(Button b:buttons(screen))assertInside(mc,b,screen.panelLeft(),screen.panelTop(),
            WarriorPracticeScreen.panelWidth(),WarriorPracticeScreen.panelHeight());
    }

    /** 控件必须整个落在面板里：条目一多就会被排到面板外，那种"点不到"只能这样抓。 */
    private static void assertInside(Minecraft mc,Button b,int left,int top,int width,int height){
        if(b.getX()<left||b.getY()<top||b.getX()+b.getWidth()>left+width||b.getY()+b.getHeight()>top+height){
            mc.stop();throw new IllegalStateException("Button outside panel: "+b.getMessage().getString()
                +" @"+b.getX()+","+b.getY()+" size "+b.getWidth()+"x"+b.getHeight()
                +" panel "+left+","+top+" "+width+"x"+height);
        }
    }

    private static List<Button> buttons(net.minecraft.client.gui.screens.Screen screen){
        List<Button> out=new ArrayList<>();
        for(var child:screen.children())if(child instanceof Button b)out.add(b);
        return out;
    }

    /**
     * 图鉴：六个页签必须都在，点一下某个页签必须真的换到那一页，且那一页的行数等于目录里的条目数。
     *
     * <p>点页签走的是真实的 {@code mouseClicked} 路径（不是直接改字段），所以顺带验证了
     * 缩放换算没把点击坐标算歪——小窗口下这是最容易坏的一处。
     */
    private static void verifyCodex(Minecraft mc,WarriorCodexScreen screen,Weapon target){
        // 页签一定是先加的那 6 个按钮（init 里先排页签再排条目），按顺序取比按名字猜可靠：
        // 「风刃」既是页签名又是条目名，按前缀匹配会把一条招式当成页签。
        if(buttons(screen).size()<=Weapon.values().length){
            mc.stop();throw new IllegalStateException("Codex should show "+Weapon.values().length+" weapon tabs plus one row per move");
        }
        List<Button> tabs=buttons(screen).subList(0,Weapon.values().length);
        List<Button> rows=buttons(screen).subList(Weapon.values().length,buttons(screen).size());
        int targetIndex=List.of(Weapon.values()).indexOf(target);
        Button targetTab=tabs.get(targetIndex);
        if(target!=Weapon.SWORD){
            float scale=mc.getWindow().getGuiScaledWidth()/(float)screen.width;
            double cx=(targetTab.getX()+targetTab.getWidth()/2.0)*scale;
            double cy=(targetTab.getY()+targetTab.getHeight()/2.0)*scale;
            if(!screen.mouseClicked(cx,cy,0)){mc.stop();throw new IllegalStateException("Tab click was not accepted: "+target.cn());}
            // rebuildWidgets 会换掉全部控件，重新取一份
            tabs=buttons(screen).subList(0,Weapon.values().length);
            rows=buttons(screen).subList(Weapon.values().length,buttons(screen).size());
            targetTab=tabs.get(targetIndex);
        }
        if(targetTab.active){
            mc.stop();throw new IllegalStateException("Tab for "+target.cn()+" should be the current (inactive) one");
        }
        int expected=WarriorCodex.of(target).size();
        if(rows.size()!=expected){
            mc.stop();throw new IllegalStateException(target.cn()+" page should list "+expected+" moves, got "+rows.size());
        }
        for(Button b:buttons(screen))assertInside(mc,b,screen.panelLeft(),screen.panelTop(),
            screen.panelWidthNow(),WarriorCodexScreen.panelHeight());
    }



    private static void show(Minecraft mc){
        Case current=CASES[index];
        TemperingClient.accept(current.packet());
        mc.setScreen(current.codex()?new WarriorCodexScreen():new WarriorPracticeScreen());
    }

    @Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup{
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){
            if(Boolean.getBoolean("tnc.warriorUiAudit"))event.enqueueWork(()->org.lwjgl.glfw.GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow()));
        }
    }
}
