package com.tnc.tnc.warrior.client;

import com.tnc.tnc.client.ui.ArcaneGui;
import com.tnc.tnc.warrior.*;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A read-only character view with explicit server-validated practice actions. */
public final class WarriorPracticeScreen extends Screen {
    private static final int W=360,H=282,INK=0xffe6dccb,MUTED=0xffb9b1a3;
    private int left,top;private float fit=1;private Button start,cancel,ground;
    public WarriorPracticeScreen(){super(Component.literal("战士修习"));}
    @Override protected void init(){
        fit=Math.min(1,Math.min((width-16f)/(W+4),(height-16f)/(H+4)));
        fit=Math.max(.25f,fit);width=(int)Math.ceil(width/fit);height=(int)Math.ceil(height/fit);
        left=(width-W)/2;top=(height-H)/2;
        start=addRenderableWidget(Button.builder(Component.literal("开始训练"),b->{
            TemperingActionPacket.send(TemperingActionPacket.Action.START);minecraft.setScreen(null);
        }).bounds(left+18,top+245,100,20).build());
        cancel=addRenderableWidget(Button.builder(Component.literal("取消训练"),b->TemperingActionPacket.send(TemperingActionPacket.Action.CANCEL))
                .bounds(left+130,top+245,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(left+242,top+245,100,20).build());
        ground=addRenderableWidget(Button.builder(Component.literal(groundLabel()),b->{
            TemperingActionPacket.send(TemperingClient.ground()?TemperingActionPacket.Action.LEAVE_GROUND:TemperingActionPacket.Action.ENTER_GROUND);minecraft.setScreen(null);
        }).bounds(left+18,top+217,324,20).build());
        buttons();
    }
    private static String groundLabel(){return TemperingClient.ground()?"离开训练场":"进入个人训练场 · 请先到协会附近";}
    private void buttons(){
        boolean canStart=TemperingClient.enrolled()&&!TemperingClient.training(),canCancel=TemperingClient.training();
        boolean canTravel=TemperingClient.ground()||TemperingClient.enrolled();
        if(start!=null){start.active=canStart;cancel.active=canCancel;}
        if(ground!=null){ground.active=canTravel;ground.setMessage(Component.literal(groundLabel()));}
        // Screen init hooks may wrap the original buttons; update the live controls as well.
        for(var child:children())if(child instanceof Button b){
            if(b.getMessage().getString().equals("开始训练"))b.active=canStart;
            if(b.getMessage().getString().equals("取消训练"))b.active=canCancel;
            if(b.getMessage().getString().startsWith("进入个人训练场")||b.getMessage().getString().equals("离开训练场")){b.active=canTravel;b.setMessage(Component.literal(groundLabel()));}
        }
    }
    @Override public void tick(){buttons();}
    private void text(GuiGraphics g,String text,int x,int y,int color){g.drawString(font,text,left+x,top+y,color,false);}
    public static String trainingStatus(){
        if(!TemperingClient.training())return "未在训练";
        if(TemperingClient.blocks()<3)return "格挡 "+TemperingClient.blocks()+" / 3 · 持盾应对敌人";
        int ticks=TemperingClient.counterTicks();
        return ticks>0?"反击窗口 "+String.format(Locale.ROOT,"%.1f",ticks/20.0)+" 秒 · 反击最后一个敌人":"反击窗口已过 · 再次格挡刷新窗口";
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        buttons();
        renderBackground(g);g.pose().pushPose();g.pose().scale(fit,fit,1);
        ArcaneGui.frame(g,left,top,W,H);
        int talent=TemperingClient.talent(),points=TemperingClient.points(),cap=TemperingClient.cap();
        text(g,"战士修习",18,15,0xffe8c68a);
        text(g,font.plainSubstrByWidth("快捷键 "+WarriorPracticeKeys.OPEN.getTranslatedKeyMessage().getString(),122),220,15,MUTED);
        text(g,"天赋 "+talent+" / 6",18,39,INK);
        text(g,"成长 "+WarriorTalentRules.multiplier(talent)+" 倍",220,39,INK);
        text(g,(talent==0?"未修习":TemperingRules.rankName(points))+" · 磨砺 "+points+" / "+cap,18,60,INK);
        text(g,"额外减伤 "+String.format(Locale.ROOT,"%.1f%%",TemperingRules.reduction(points)*100),220,60,INK);
        g.fill(left+18,top+79,left+342,top+85,0xff2b3033);
        if(cap>0)g.fill(left+18,top+79,left+18+(int)(324L*points/cap),top+85,0xff87bfac);
        String goal=talent==0?"此角色尚无战士天赋，无法积累磨砺。":points>=cap?"已达到当前天赋上限，修习成果永久保留。":
            "下一目标 "+TemperingRules.nextMilestone(points,cap)+" 磨砺 · 天赋不会随训练提升";
        text(g,goal,18,96,MUTED);
        text(g,"守势反击",18,120,0xffe8c68a);
        int reward=Math.min(Math.max(0,cap-points),WarriorTalentRules.gain(GuardDrill.REWARD,talent));
        text(g,TemperingClient.graduate()?"首次训练已完成":"首次完成预计 +"+reward+" 磨砺",170,120,MUTED);
        text(g,"三次盾挡后，在四秒内持剑或斧反击最后一个敌人。",18,139,MUTED);
        text(g,trainingStatus(),18,158,INK);
        String wind=points<200?"风刃 · 需要 200 磨砺及风灌注武器":TemperingClient.windTicks()>0?
            "风刃 · 冷却 "+String.format(Locale.ROOT,"%.1f",TemperingClient.windTicks()/20.0)+" 秒":"风刃 · 已就绪，持风灌注武器潜行对空右键";
        text(g,wind,18,180,INK);
        text(g,TemperingClient.ground()?"训练场内保护生命，武器攻击仍会消耗耐久。":"野外训练照常受伤；可到协会进入个人训练场。",18,195,MUTED);
        super.render(g,(int)(mx/fit),(int)(my/fit),partial);g.pose().popPose();
    }
    @Override public boolean mouseClicked(double x,double y,int b){return super.mouseClicked(x/fit,y/fit,b);}
    @Override public boolean mouseReleased(double x,double y,int b){return super.mouseReleased(x/fit,y/fit,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return super.mouseDragged(x/fit,y/fit,b,dx/fit,dy/fit);}
    @Override public void mouseMoved(double x,double y){super.mouseMoved(x/fit,y/fit);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(WarriorPracticeKeys.OPEN.matches(key,scan)){onClose();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean isPauseScreen(){return false;}
}
