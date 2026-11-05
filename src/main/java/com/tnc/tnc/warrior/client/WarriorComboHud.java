package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.ComboTracker;
import com.tnc.tnc.warrior.WarriorChain;
import com.tnc.tnc.warrior.WarriorCombos;
import com.tnc.tnc.warrior.WarriorSkill;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 武器技能槽 HUD。
 *
 * <p><b>位置照抄法术热键栏</b>：SpellEngine 的热键栏是 9 格 × 20×22，锚点
 * {@code BOTTOM + (-200, -11)}（见整合包 {@code config/spell_engine/hud_config.json}）。
 * 我们画在同一处、同样的格宽，所以拿武器时技能栏正好顶替法杖热键栏的位置 ——
 * 玩家的肌肉记忆不用换。拿法杖时这里不画（手上不是武器），两套栏不会同时出现。
 *
 * <p>槽位含义：0 = 原本"右键"那一格，1..8 = 数字键 2..9。连招是自动衔接的，
 * 所以格子只负责**展示**（顺序、冷却、是否解锁、属于哪条链），不需要玩家按键释放。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WarriorComboHud {

    private static final int SLOT_W = 20;
    private static final int SLOT_H = 22;
    private static final int SLOTS = WarriorSkill.SLOTS_PER_PAGE;
    /** SpellEngine 热键栏的锚点偏移（BOTTOM：屏幕宽/2 + dx，屏幕高 + dy）。 */
    private static final int SPELL_BAR_DX = -200;
    private static final float HUD_Z = 75.0F;

    private static final int COLOR_BG = 0xFF18120C;
    private static final int COLOR_BORDER_DIM = 0x668A6A42;
    /** 三条链的强调色：突击=冷蓝、攻击=赤红、防御=青绿。 */
    private static final int COLOR_ASSAULT = 0xFF6FA8D8;
    private static final int COLOR_ATTACK = 0xFFD86F5A;
    private static final int COLOR_DEFENSE = 0xFF6FD8B0;
    private static final int COLOR_TEXT = 0xFFF2DFC4;
    private static final int COLOR_DIM = 0xFF8B8378;
    private static final int COLOR_MET = 0xFF9FE0B8;
    private static final int COLOR_NEXT = 0xFFFFD479;
    private static final int COLOR_FLASH = 0xFFFFE9A8;

    private WarriorComboHud() {
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !TemperingClient.enrolled()) {
            return;
        }
        // 和"拿法杖才能施法"同一条：手上不是武器就不显示技能栏
        var family = WarriorComboClient.heldFamily();
        if (family == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int centerX = width / 2 + SPELL_BAR_DX;
        int rowWidth = SLOT_W * SLOTS;
        int x0 = centerX - rowWidth / 2;
        int y = height - SLOT_H;

        List<WarriorSkill> page = WarriorCombos.page(family, 0);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, HUD_Z);
        for (int i = 0; i < SLOTS; i++) {
            drawSlot(graphics, minecraft, page.get(i), x0 + i * SLOT_W, y);
        }
        drawHint(graphics, minecraft, centerX, y - 11);
        graphics.pose().popPose();
    }

    private static void drawSlot(GuiGraphics graphics, Minecraft minecraft, WarriorSkill skill, int x, int y) {
        boolean unlocked = skill != null && WarriorCombos.unlocked(skill, TemperingClient.progress());
        // 整格不透明：法术栏在"副手带使用动作"时仍可能露一格在正中，这里要盖住它
        graphics.fill(x, y, x + SLOT_W, y + SLOT_H, COLOR_BG);
        outline(graphics, x, y, unlocked && skill != null ? chainColor(skill.chain()) : COLOR_BORDER_DIM);
        if (skill == null) {
            return;
        }
        if (WarriorComboClient.flashing(skill)) {
            graphics.fill(x + 1, y + 1, x + SLOT_W - 2, y + SLOT_H - 2, 0x55FFE9A8);
            outline(graphics, x, y, COLOR_FLASH);
        }
        int cooldown = WarriorComboClient.cooldown(skill);
        // 冷却时格子下半截被黑色遮罩盖住，序号与秒数不能叠在同一行
        if (cooldown <= 0) {
            String sequence = skill.compactLabel();
            int color = unlocked ? COLOR_TEXT : COLOR_DIM;
            graphics.pose().pushPose();
            graphics.pose().translate(x + SLOT_W / 2.0F, y + 7.0F, 0.0F);
            graphics.pose().scale(.5F, .5F, 1.0F);
            graphics.drawString(minecraft.font, sequence, -minecraft.font.width(sequence) / 2, 0, color, false);
            graphics.pose().popPose();
        }

        if (!unlocked) {
            int need = WarriorCombos.temperRequirement(skill);
            String text = need > 0 ? String.valueOf(need) : "锁";
            graphics.pose().pushPose();
            graphics.pose().translate(x + SLOT_W / 2.0F, y + 13.0F, 0.0F);
            graphics.pose().scale(.5F, .5F, 1.0F);
            graphics.drawString(minecraft.font, text, -minecraft.font.width(text) / 2, 0, COLOR_DIM, false);
            graphics.pose().popPose();
            return;
        }

        if (cooldown > 0) {
            int filled = (int) Math.ceil((SLOT_H - 2) * (double) Math.min(cooldown, skill.cooldownTicks())
                    / skill.cooldownTicks());
            graphics.fill(x + 1, y + SLOT_H - 1 - filled, x + SLOT_W - 2, y + SLOT_H - 1, 0xAA000000);
            String seconds = String.format(java.util.Locale.ROOT, "%.0f", cooldown / 20.0);
            graphics.pose().pushPose();
            graphics.pose().translate(x + SLOT_W / 2.0F, y + 7.0F, 0.0F);
            graphics.pose().scale(.5F, .5F, 1.0F);
            graphics.drawString(minecraft.font, seconds, -minecraft.font.width(seconds) / 2, 0, COLOR_TEXT, true);
            graphics.pose().popPose();
        }
    }

    /** 连招进度：正在接哪招、按对了哪几下、下一步按什么。 */
    private static void drawHint(GuiGraphics graphics, Minecraft minecraft, int centerX, int y) {
        ComboTracker.Progress progress = WarriorComboClient.progress();
        if (progress == null) {
            WarriorSkill first = firstSkill();
            if (first == null || WarriorCombos.unlocked(first, TemperingClient.progress())) {
                return;
            }
            int need = WarriorCombos.temperRequirement(first);
            String hint = "连招 · 磨砺 " + need + " 解锁 " + first.name();
            graphics.drawString(minecraft.font, hint, centerX - minecraft.font.width(hint) / 2, y, COLOR_DIM, true);
            return;
        }
        String name = progress.skill().chain().cn() + " · " + progress.skill().name();
        int length = progress.skill().length();
        int seqWidth = 0;
        for (int i = 0; i < length; i++) {
            seqWidth += minecraft.font.width(progress.skill().sequence().get(i).label()) + 2;
        }
        int total = minecraft.font.width(name) + 10 + seqWidth;
        int tx = centerX - total / 2;
        graphics.drawString(minecraft.font, name, tx, y, COLOR_TEXT, true);
        int sx = tx + minecraft.font.width(name) + 10;
        for (int i = 0; i < length; i++) {
            String letter = progress.skill().sequence().get(i).label()
                    + (progress.skill().holdAt(i) ? "+" : "");
            int color = i < progress.matchedCount() ? COLOR_MET
                    : i == progress.matchedCount() ? COLOR_NEXT : COLOR_DIM;
            graphics.drawString(minecraft.font, letter, sx, y, color, true);
            sx += minecraft.font.width(letter) + 2;
        }
        if (progress.nextHold() || progress.waitingForHold()) {
            String hold = "按住";
            graphics.drawString(minecraft.font, hold, sx + 2, y, COLOR_NEXT, true);
        }
    }

    /** 当前武器第一条可提示的连招（HUD 还没解锁时的引导文案用）。 */
    private static WarriorSkill firstSkill() {
        var family = WarriorComboClient.heldFamily();
        if (family == null) {
            return null;
        }
        var list = WarriorCombos.forFamily(family);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 三条链的边框色：一眼能看出这格是突击/攻击/防御。 */
    private static int chainColor(WarriorChain chain) {
        return switch (chain) {
            case ASSAULT -> COLOR_ASSAULT;
            case ATTACK -> COLOR_ATTACK;
            case DEFENSE -> COLOR_DEFENSE;
            case EVADE -> COLOR_ASSAULT;
        };
    }

    private static void outline(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + SLOT_W - 1, y + 1, color);
        graphics.fill(x, y + SLOT_H - 2, x + SLOT_W - 1, y + SLOT_H - 1, color);
        graphics.fill(x, y, x + 1, y + SLOT_H - 1, color);
        graphics.fill(x + SLOT_W - 2, y, x + SLOT_W - 1, y + SLOT_H - 1, color);
    }
}
