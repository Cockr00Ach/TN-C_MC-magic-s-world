package com.tnc.tnc.life.pasture.client;

import com.tnc.tnc.life.pasture.PastureMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Four working compartments with readable water/egg/stock counters from the server. */
public final class PastureFacilityScreen extends AbstractContainerScreen<PastureMenu> {
    private static final int DARK = 0xff354b45, PAPER = 0xffd4d0b6, WOOD = 0xff927e58, INK = 0xff354d43;
    public PastureFacilityScreen(PastureMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 176; imageHeight = 171;
        titleLabelY = 7; inventoryLabelY = 78;
    }
    private void box(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(leftPos+x, topPos+y, leftPos+x+w, topPos+y+h, DARK);
        g.fill(leftPos+x+1, topPos+y+1, leftPos+x+w-1, topPos+y+h-1, fill);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        box(g, -2, -2, imageWidth+4, imageHeight+4, WOOD);
        g.fill(leftPos, topPos, leftPos+imageWidth, topPos+imageHeight, PAPER);
        g.fill(leftPos, topPos, leftPos+imageWidth, topPos+22, DARK);
        box(g, 8, 28, 160, 45, 0xffc4c5a6);
        for (int i=0; i<menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            box(g, slot.x-1, slot.y-1, 18, 18, i<4 ? 0xffb2bda0 : 0xffb9b396);
        }
        if (menu.data(0) == 3) {
            box(g, 16, 64, 144, 4, DARK);
            g.fill(leftPos+17, topPos+65, leftPos+17+142*Math.min(1000, Math.max(0, menu.data(2)))/1000, topPos+67, 0xffa5cf98);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        String[] names={"牧场食槽", "栖居标记", "授权托盘", "牧卵架", "采露架", "充电栖架", "魔瓶座", "行旅微光"};
        int kind=Math.max(0, Math.min(names.length-1, menu.data(0)));
        g.drawString(font, names[kind], 8, 7, 0xfff0e6c1, false);
        g.drawString(font, "库存 " + menu.data(3) + "份", 12, 58, INK, false);
        String status=switch(kind) {
            case 0 -> "清水 " + menu.data(1) + "/16份";
            case 3 -> "孵化 " + menu.data(2)/10 + "%";
            case 6 -> "脉冲注魔 · 最多5魔力";
            default -> "四格 · 每格最多16";
        };
        if (kind==6) status=font.plainSubstrByWidth(status, 88);
        g.drawString(font, status, kind==6 ? 80 : 90, 58, INK, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, INK, false);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g); super.render(g, mouseX, mouseY, partial); renderTooltip(g, mouseX, mouseY);
    }
}
