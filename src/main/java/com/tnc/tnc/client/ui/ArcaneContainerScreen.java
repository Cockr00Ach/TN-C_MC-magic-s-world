package com.tnc.tnc.client.ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
/** Fits the existing slot coordinates without changing the user's GUI-scale setting. */
public abstract class ArcaneContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M>{
 protected float fit=1;
 protected ArcaneContainerScreen(M menu,Inventory inv,Component title){super(menu,inv,title);}
 @Override protected void init(){int actualW=minecraft.getWindow().getGuiScaledWidth(),actualH=minecraft.getWindow().getGuiScaledHeight();fit=Math.min(1,Math.min((actualW-12f)/(imageWidth+4),(actualH-12f)/(imageHeight+4)));fit=Math.max(.25f,fit);width=(int)Math.ceil(actualW/fit);height=(int)Math.ceil(actualH/fit);super.init();}
 @Override public int getGuiLeft(){return Math.round(leftPos*fit);}@Override public int getGuiTop(){return Math.round(topPos*fit);}@Override public int getXSize(){return Math.round(imageWidth*fit);}@Override public int getYSize(){return Math.round(imageHeight*fit);}
 protected final double mouseX(double x){return x/fit;}protected final double mouseY(double y){return y/fit;}
 @Override public void render(GuiGraphics g,int x,int y,float pt){renderBackground(g);g.pose().pushPose();g.pose().scale(fit,fit,1);int mx=(int)mouseX(x),my=(int)mouseY(y);super.render(g,mx,my,pt);g.pose().popPose();afterContainer(g,x,y,pt);}
 protected void afterContainer(GuiGraphics g,int mx,int my,float pt){renderTooltip(g,mx,my);}
 @Override public boolean mouseClicked(double x,double y,int b){return super.mouseClicked(mouseX(x),mouseY(y),b);}
 @Override public boolean mouseReleased(double x,double y,int b){return super.mouseReleased(mouseX(x),mouseY(y),b);}
 @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return super.mouseDragged(mouseX(x),mouseY(y),b,dx/fit,dy/fit);}
 @Override public boolean mouseScrolled(double x,double y,double d){return super.mouseScrolled(mouseX(x),mouseY(y),d);}
 @Override public void mouseMoved(double x,double y){super.mouseMoved(mouseX(x),mouseY(y));}
}
