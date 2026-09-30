package com.tnc.tnc.mixin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InventoryScreen.class)
public abstract class TwoSlotInventoryScreenMixin {
    @Inject(method="renderBg",at=@At("TAIL"),require=1)
    private void tnc$panels(GuiGraphics g,float partial,int mx,int my,CallbackInfo ci){
        var s=(InventoryScreen)(Object)this;int x=s.getGuiLeft()+6,y=s.getGuiTop()+6;
        g.fill(x,y,x+21,y+74,0xFFC6C6C6);
        for(int row=0;row<2;row++){
            int py=y+row*36;g.fill(x,py,x+21,py+33,0xFF8A8A8A);g.fill(x+1,py+1,x+21,py+33,0xFFFFFFFF);
            g.fill(x+1,py+1,x+20,py+32,0xFFB8B8B8);
            int sy=s.getGuiTop()+(row==0?16:52);g.fill(x+1,sy,x+19,sy+18,0xFF373737);g.fill(x+2,sy+1,x+19,sy+18,0xFFFFFFFF);g.fill(x+2,sy+1,x+18,sy+17,0xFF8B8B8B);
            g.pose().pushPose();g.pose().translate(x+4,py+2,0);g.pose().scale(.55f,.55f,1);
            g.drawString(Minecraft.getInstance().font,row==0?"帽子":"全身",0,0,0xFF394250,false);g.pose().popPose();
        }
    }
}
