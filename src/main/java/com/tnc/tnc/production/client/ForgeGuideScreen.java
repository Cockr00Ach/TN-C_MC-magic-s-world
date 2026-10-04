package com.tnc.tnc.production.client;

import com.tnc.tnc.production.ForgeStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Four-page illustrated construction and operation manual. */
public final class ForgeGuideScreen extends Screen {
    private static final String[] NAMES = {"底层 · 基座", "中层 · 炉膛", "顶层 · 排烟"};
    private final Screen previous;
    private int page;

    private ForgeGuideScreen(Screen previous) {
        super(Component.literal("炉台工匠札记"));
        this.previous = previous;
    }

    public static void open(Screen previous) {
        Minecraft.getInstance().setScreen(new ForgeGuideScreen(previous));
    }

    @Override protected void init() {
        int left = (width - 256) / 2;
        int top = (height - 202) / 2;
        addRenderableWidget(Button.builder(Component.literal("上一页"), b -> page = Math.max(0, page - 1))
                .bounds(left + 12, top + 174, 70, 19).build());
        addRenderableWidget(Button.builder(Component.literal("下一页"), b -> page = Math.min(3, page + 1))
                .bounds(left + 93, top + 174, 70, 19).build());
        addRenderableWidget(Button.builder(Component.literal("合上"), b -> onClose())
                .bounds(left + 174, top + 174, 70, 19).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        int left = (width - 256) / 2;
        int top = (height - 202) / 2;
        g.fill(left - 3, top - 3, left + 259, top + 205, 0xff342622);
        g.fill(left, top, left + 256, top + 202, 0xffdac8a6);
        g.fill(left + 6, top + 28, left + 250, top + 169, 0xfff1e6cf);
        g.drawCenteredString(font, "工匠札记  ·  " + (page + 1) + "/4", width / 2, top + 11, 0xff523a2e);
        if (page == 0) intro(g, left, top);
        if (page == 1) layers(g, left, top);
        if (page == 2) operation(g, left, top);
        if (page == 3) power(g, left, top);
        super.render(g, mx, my, partial);
    }

    private void lines(GuiGraphics g, int x, int y, String... lines) {
        for (int i = 0; i < lines.length; i++)
            g.drawString(font, lines[i], x, y + i * 17, 0xff493b32, false);
    }

    private void intro(GuiGraphics g, int left, int top) {
        lines(g, left + 16, top + 39,
                "一座真正的炉台，共 26 个实体部件。",
                "它占三层，每层 3×3；中层中心留空。",
                "准备：耐火炉砖 ×12、刻铜炉框 ×8、",
                "侧壁投料砖 ×2、底层出料砖 ×1、",
                "炉芯 ×1、注能口 ×1、排烟顶 ×1。",
                "先选炉芯朝外的一面，这面就是「前」。",
                "下一页按底→中→顶的顺序逐层搭建。");
    }

    private void layers(GuiGraphics g, int left, int top) {
        for (int layer = 0; layer < 3; layer++) {
            int x = left + 18 + layer * 78;
            g.drawString(font, NAMES[layer], x - 1, top + 37, 0xff694b35, false);
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                ForgeStructure.Part part = ForgeStructure.expected(ForgeStructure.index(layer, row, col));
                int color = part == ForgeStructure.Part.AIR ? 0xfff1e6cf
                        : part == ForgeStructure.Part.FRAME ? 0xffa57742
                        : part == ForgeStructure.Part.BRICK ? 0xff5d5960 : 0xff5eab9c;
                int cx = x + col * 16, cy = top + 53 + row * 16;
                g.fill(cx, cy, cx + 15, cy + 15, 0xff372e2a);
                g.fill(cx + 1, cy + 1, cx + 14, cy + 14, color);
                g.drawCenteredString(font, code(part), cx + 8, cy + 3,
                        part == ForgeStructure.Part.AIR ? 0xff57483c : 0xffffffff);
            }
        }
        lines(g, left + 17, top + 113,
                "砖=耐火砖  框=刻铜框  入=投料  出=出料",
                "芯=炉芯  注=注能口  烟=排烟  空=空气",
                "图的下方是前；注能口必须向后朝外。",
                "炉芯右键可看红格诊断；灰格是区块未加载。");
    }

    private String code(ForgeStructure.Part part) {
        return switch (part) {
            case BRICK -> "砖"; case FRAME -> "框"; case INPUT -> "入";
            case OUTPUT -> "出"; case CORE -> "芯"; case INJECTOR -> "注";
            case EXHAUST -> "烟"; case AIR -> "空";
        };
    }

    private void operation(GuiGraphics g, int left, int top) {
        lines(g, left + 16, top + 39,
                "右键前方炉芯，先选工艺，再向四格投料。",
                "空格会显示所选工艺的材料和数量。",
                "点击「注魔」消耗玩家真实魔力 25 点以内。",
                "也可手持蓄魔根芯右键后方注能口。",
                "仅结构完整时收取魔力和制作材料；",
                "拆走部件会暂停，不会吃掉库存。",
                "侧壁投料砖接漏斗，底部出料砖取成品。",
                "正在施工或未加载时端口自动闭锁。");
    }

    private void power(GuiGraphics g, int left, int top) {
        lines(g, left + 16, top + 39,
                "成熟且已认养的绿脉枝紧贴注能口背面，",
                "可缓慢输送它真正储存的魔力。",
                "只有炉主及同一地块的植物能向炉供能。",
                "绿色粒子显示植物→注口→炉芯的路径。",
                "发电座可将魔力转成有限的 Forge Energy；",
                "导能线、电池和工作灯使用这一路能源。",
                "Create 的机械转速与 FE 分开运行。",
                "配方可由数据包扩展，炉内四槽保持通用。");
    }

    @Override public void onClose() { minecraft.setScreen(previous); }
    @Override public boolean isPauseScreen() { return false; }
}
