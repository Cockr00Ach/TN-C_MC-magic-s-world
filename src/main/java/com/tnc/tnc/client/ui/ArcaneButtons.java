package com.tnc.tnc.client.ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Native buttons retain callbacks, focus, disabled state, tooltips and narration. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class ArcaneButtons {
 @SubscribeEvent public static void init(ScreenEvent.Init.Post e){var screen=e.getScreen();if(!screen.getClass().getName().startsWith("com.tnc.tnc.")||screen instanceof com.tnc.tnc.dialogue.client.DialogueScreen)return;for(var listener:java.util.List.copyOf(e.getListenersList()))if(listener instanceof Button old&&!(old instanceof Themed)){e.removeListener(old);e.addListener(new Themed(old));}}
 public static final class Themed extends Button {
  private final Button delegate;
  public Themed(Button original){super(original.getX(),original.getY(),original.getWidth(),original.getHeight(),original.getMessage(),b->original.onPress(),DEFAULT_NARRATION);delegate=original;active=original.active;visible=original.visible;setTooltip(original.getTooltip());}
  @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){active=delegate.active;setMessage(delegate.getMessage());boolean hover=active&&(isHoveredOrFocused());int x=getX(),y=getY(),w=getWidth(),h=getHeight();int border=!active?0xff514b42:hover?0xffd9bc8b:0xff8e7551;
   g.fill(x,y,x+w,y+h,0xff0c0e12);g.fill(x+1,y+1,x+w-1,y+h-1,border);g.fill(x+2,y+2,x+w-2,y+h-2,hover?0xff29343a:0xff1b2027);g.fill(x+3,y+3,x+w-3,y+4,hover?0xff9dbaae:0xff655b49);
   for(int side:new int[]{x,x+w-3}){g.fill(side,y+1,side+3,y+2,border);g.fill(side,y+h-2,side+3,y+h-1,border);}
   if(hover){g.fill(x+3,y+h-4,x+w-3,y+h-3,0xff72a9a8);}
   var font=Minecraft.getInstance().font;g.drawCenteredString(font,font.plainSubstrByWidth(getMessage().getString(),Math.max(1,w-8)),x+w/2,y+(h-8)/2,!active?0xff817a6e:hover?0xfff4e8d1:0xffd9c9af);
  }
 }
}
