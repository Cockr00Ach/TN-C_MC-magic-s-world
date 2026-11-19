package com.tnc.tnc.warrior.client;

import com.tnc.tnc.client.ui.ArcaneGui;
import com.tnc.tnc.warrior.TemperingActionPacket;
import com.tnc.tnc.warrior.WarriorWeaponRanks;
import com.tnc.tnc.warrior.WeaponChampion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class WeaponChampionScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 216;
    private WeaponChampion selected;
    private int left;
    private int top;

    WeaponChampionScreen() {
        super(Component.literal("兵器首领约战"));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        var player = minecraft.player;
        var held = player == null ? null : WarriorWeaponRanks.branch(player.getMainHandItem());
        if (selected == null) selected = held == null ? WeaponChampion.LONGSWORD : WeaponChampion.valueOf(held.name());
        String[] tierNames = {"武师", "精英", "宗师", "传奇"};
        for (int rank = 2; rank < 2 + tierNames.length; rank++) {
            int tier = rank;
            var button = addRenderableWidget(Button.builder(Component.literal(tierNames[rank - 2] + " · " + rank + " 级"), b -> {
                selected = WeaponChampion.forBranch(selected.branch(), tier);
                rebuildWidgets();
            }).bounds(left + 12 + (rank - 2) * 70, top + 30, 66, 19).build());
            button.active = selected.rank() != rank;
        }
        int index = 0;
        for (var branch : WarriorWeaponRanks.Branch.values()) {
            var champion = WeaponChampion.forBranch(branch, selected.rank());
            var label = Component.translatable(champion.branch().translationKey());
            var button = addRenderableWidget(Button.builder(label, b -> {
                selected = champion;
                rebuildWidgets();
            }).bounds(left + 12 + index % 4 * 70, top + 55 + index / 4 * 24, 66, 20).build());
            button.active = selected != champion;
            index++;
        }
        addRenderableWidget(Button.builder(Component.literal("发出战帖"), b -> {
            TemperingActionPacket.send(TemperingActionPacket.Action.CHAMPION, selected.selectionKey());
            minecraft.setScreen(null);
        }).bounds(left + 12, top + 186, 132, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回图鉴"), b -> onClose())
            .bounds(left + 156, top + 186, 132, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        renderBackground(graphics);
        ArcaneGui.frame(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.drawString(font, title, left + 12, top + 13, 0xffe8c68a, false);
        graphics.drawString(font, selected.displayName(), left + 12, top + 112, 0xff000000 | selected.color(), false);
        graphics.drawString(font, selected.weaponName().copy().append(" · " + selected.rank() + " 级")
            .append("　身高 " + selected.height() + " 格")
            .append(selected.rank() >= 4 ? "　绝式 · " + selected.ultimateTitle() : ""),
            left + 12, top + 127, 0xffb9b1a3, false);
        graphics.drawWordWrap(font, Component.literal(selected.hint), left + 12, top + 142, 276, 0xffe6dccb);
        graphics.drawString(font, "在野外发帖，点击回帖赴约。挨打会扣生命。", left + 12, top + 171, 0xffb9b1a3, false);
        super.render(graphics, x, y, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new WarriorCodexScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
