package com.tnc.tnc.production.client;

import com.tnc.tnc.production.ForgeStructure;
import com.tnc.tnc.production.ForgeRecipeNetwork;
import com.tnc.tnc.production.MagicForgeBlockEntity;
import com.tnc.tnc.production.MagicForgeMenu;
import com.tnc.tnc.production.ManaForgeRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A compact copper-and-slate workshop, with actual slots and three live structure layers. */
public final class MagicForgeScreen extends AbstractContainerScreen<MagicForgeMenu> {
    private static final int EDGE = 0xff201c1e;
    private static final int SLATE = 0xff39383d;
    private static final int PARCHMENT = 0xffd4c5a5;
    private static final int COPPER = 0xffbe824c;
    private static final String[] FILTERS = {"all", "materials", "utility", "workshop"};
    private int filter;
    private int scroll;

    public MagicForgeScreen(MagicForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 230;
        imageHeight = 242;
        inventoryLabelY = 142;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("工匠札记"),
                b -> ForgeGuideScreen.open(this)).bounds(leftPos + 173, topPos + 4, 52, 16).build());
        addRenderableWidget(Button.builder(Component.literal("注魔 +25"),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0))
                .bounds(leftPos + 142, topPos + 111, 82, 18).build());
    }

    private List<Integer> filteredRecipes() {
        List<Integer> result = new ArrayList<>();
        List<ManaForgeRecipe> recipes = menu.recipes();
        for (int i = 0; i < recipes.size(); i++)
            if (filter == 0 || recipes.get(i).category().equals(FILTERS[filter])) result.add(i);
        return result;
    }

    private String issue() {
        if (menu.formed()) return "结构完整 · 可以锻造";
        int index = Math.max(0, Math.min(26, menu.firstBad()));
        return switch (menu.cell(index)) {
            case ForgeStructure.UNLOADED -> "区块未加载 · 炉台暂停";
            case ForgeStructure.FILLED_CHAMBER -> "中层中央请保持空膛";
            case ForgeStructure.WRONG_DIRECTION -> "注能口应向后朝外";
            case ForgeStructure.SHARED_PART -> "两座炉不能共用部件";
            default -> ForgeStructure.cellName(index) + " 缺 "
                    + ForgeStructure.expectedName(ForgeStructure.expected(index));
        };
    }

    private void box(GuiGraphics g, int x, int y, int width, int height, int fill) {
        g.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + height, EDGE);
        g.fill(leftPos + x + 1, topPos + y + 1, leftPos + x + width - 1, topPos + y + height - 1, fill);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        box(g, -3, -3, imageWidth + 6, imageHeight + 6, EDGE);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PARCHMENT);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 23, SLATE);
        box(g, 8, 29, 124, 111, 0xff514842);
        box(g, 137, 29, 87, 79, 0xffeee3c8);
        g.fill(leftPos + 136, topPos + 23, leftPos + 137, topPos + 142, COPPER);
        for (int i = 0; i < 6; i++) {
            var slot = menu.slots.get(i);
            box(g, slot.x - 2, slot.y - 2, 20, 20, 0xffaa9478);
        }
        for (int i = 6; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            box(g, slot.x - 1, slot.y - 1, 18, 18, 0xffb8a98d);
        }
        box(g, 70, 49, 25, 5, EDGE);
        g.fill(leftPos + 71, topPos + 50,
                leftPos + 71 + 23 * Math.min(menu.workTicks(), Math.max(0, menu.progress())) / menu.workTicks(), topPos + 53, COPPER);
        box(g, 13, 98, 114, 5, EDGE);
        g.fill(leftPos + 14, topPos + 99,
                leftPos + 14 + 112 * Math.max(0, menu.charge()) / MagicForgeBlockEntity.MAX_CHARGE,
                topPos + 102, 0xff6ad5b6);
        for (int layer = 0; layer < 3; layer++) {
            int x = 19 + layer * 39;
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                int cell = ForgeStructure.index(layer, row, col);
                int colour = menu.cell(cell) == ForgeStructure.OK ? 0xff72c6a4
                        : menu.cell(cell) == ForgeStructure.UNLOADED ? 0xff7d8396 : 0xffd17160;
                g.fill(leftPos + x + col * 6, topPos + 116 + row * 6,
                        leftPos + x + col * 6 + 5, topPos + 116 + row * 6 + 5, colour);
            }
        }
        List<Integer> recipes = filteredRecipes();
        scroll = Math.min(scroll, Math.max(0, recipes.size() - 3));
        for (int row = 0; row < 3 && scroll + row < recipes.size(); row++) {
            int index = recipes.get(scroll + row);
            box(g, 141, 52 + row * 18, 79, 17,
                    index == menu.selectedIndex() ? 0xffdfc095 : 0xffe9dec9);
        }
        g.fill(leftPos + 8, topPos + 152, leftPos + 224, topPos + 153, 0xff867362);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "载魔锻造炉", 9, 8, 0xffffe7bc, false);
        g.drawString(font, font.plainSubstrByWidth(issue(), 111), 12, 32,
                menu.formed() ? 0xffb4efcc : 0xffffc6aa, false);
        g.drawString(font, "四格投料", 15, 39, 0xffffeed6, false);
        g.drawString(font, "成品", 102, 39, 0xffffeed6, false);
        g.drawString(font, "升级", 75, 57, 0xffffeed6, false);
        g.drawString(font, "魔力 " + menu.charge() + "/200", 13, 87, 0xffd8faed, false);
        String[] layers = {"底层", "中层", "顶层"};
        for (int layer = 0; layer < 3; layer++)
            g.drawString(font, layers[layer], 17 + layer * 39, 106, 0xffffe0ad, false);
        g.drawString(font, "工艺", 142, 31, 0xff46372c, false);
        g.drawString(font, "全  材  用  工", 143, 42, 0xff5a4938, false);
        List<Integer> recipes = filteredRecipes();
        for (int row = 0; row < 3 && scroll + row < recipes.size(); row++) {
            int index = recipes.get(scroll + row);
            ManaForgeRecipe recipe = menu.recipes().get(index);
            ItemStack output = recipe.getResultItem(minecraft.level.registryAccess());
            g.renderItem(output, 143, 53 + row * 18);
            String label = output.getHoverName().getString();
            if (font.width(label) > 57) label = font.plainSubstrByWidth(label, 53) + "…";
            g.drawString(font, label, 160, 57 + row * 18, 0xff342c28, false);
        }
        if (recipes.isEmpty()) g.drawString(font, "暂无工艺", 145, 61, 0xff786b5b, false);
        ManaForgeRecipe selected = menu.selectedIndex() >= 0 && menu.selectedIndex() < menu.recipes().size()
                ? menu.recipes().get(menu.selectedIndex()) : null;
        if (selected != null) {
            g.drawString(font, "需 " + selected.mana() + " 魔力", 143, 103, 0xff4c665d, false);
            for (int i = 0; i < selected.inputs().size(); i++) {
                if (menu.slots.get(i).hasItem()) continue;
                ItemStack[] sample = selected.inputs().get(i).ingredient().getItems();
                if (sample.length == 0) continue;
                int x = menu.slots.get(i).x;
                int y = menu.slots.get(i).y;
                g.renderItem(sample[0], x, y);
                g.fill(x, y, x + 16, y + 16, 0x770c1013);
                g.drawString(font, String.valueOf(selected.inputs().get(i).count()), x + 11, y + 9, 0xffffffff, true);
            }
        } else g.drawString(font, "先选工艺", 72, 39, 0xffffe0ad, false);
        g.drawString(font, playerInventoryTitle, 34, inventoryLabelY, 0xff473a31, false);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        int x = (int) mx - leftPos, y = (int) my - topPos;
        if (x >= 141 && x < 220 && y >= 41 && y < 52) {
            filter = Math.min(3, Math.max(0, (x - 142) / 20));
            scroll = 0;
            return true;
        }
        if (x >= 141 && x < 220 && y >= 52 && y < 106) {
            int row = (y - 52) / 18;
            List<Integer> visible = filteredRecipes();
            if (row >= 0 && scroll + row < visible.size()) {
                ForgeRecipeNetwork.select(menu.containerId, menu.recipes().get(visible.get(scroll + row)).getId());
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        if (mx >= leftPos + 137 && mx < leftPos + 224 && my >= topPos + 29 && my < topPos + 109) {
            scroll = Math.min(Math.max(0, filteredRecipes().size() - 3), Math.max(0, scroll - (int)Math.signum(amount)));
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        super.render(g, mx, my, partial);
        if (mx >= leftPos + 8 && mx < leftPos + 132 && my >= topPos + 29 && my < topPos + 43)
            g.renderTooltip(font, Component.literal(issue()), mx, my);
        else if (mx >= leftPos + 76 && mx < leftPos + 97 && my >= topPos + 65 && my < topPos + 86) {
            g.renderTooltip(font, Component.literal(menu.upgradeUses() > 0
                    ? "本次锻造耗时减少20% · 剩余" + menu.upgradeUses() + "次；拆下保留次数"
                    : "炉预热油10次 / 冷凝壳20次 · 只装一件，效果不叠加"), mx, my);
        } else if (mx >= leftPos + 19 && mx < leftPos + 116 && my >= topPos + 116 && my < topPos + 134) {
            int layer = (mx - leftPos - 19) / 39;
            int col = ((mx - leftPos - 19) % 39) / 6;
            int row = (my - topPos - 116) / 6;
            if (layer < 3 && col < 3 && row < 3) {
                int index = ForgeStructure.index(layer, row, col);
                String info = ForgeStructure.cellName(index) + " · " + ForgeStructure.expectedName(ForgeStructure.expected(index));
                if (menu.cell(index) != ForgeStructure.OK) info += "（尚未就绪）";
                g.renderTooltip(font, Component.literal(info), mx, my);
            }
        } else renderTooltip(g, mx, my);
    }
}
