package com.tnc.tnc.npc;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 给剧情 NPC 穿装备 —— 这是"让 NPC 帅气起来"的**低成本路线**。
 *
 * <h2>为什么值得做</h2>
 * 用户想要"帅气的骑士"。真做 GeckoLib 模型要 Blockbench（本环境没有），
 * 但**给 NPC 穿上装备**只要几行代码 —— 而盔甲层已经在
 * {@code TnNpcRenderer} 里挂好了，穿上就看得见（头盔/胸甲/护腿/靴子立体地凸出来）。
 *
 * <p>所以 {@link #equip} 就是"外观配置表"：想改谁的样子，改这里的一行即可。
 *
 * <h2>注意</h2>
 * <ul>
 *   <li>装备是**服务端**设置、由客户端渲染；在 {@code finalizeSpawn} 里做不会同步到客户端，
 *       所以统一在实体加入世界时（{@code onAddedToWorld}）设置。</li>
 *   <li>给的是普通物品（铁胸甲等），不影响战斗 —— NPC 本来就打不死。</li>
 *   <li>换存档/重放时都会重新穿上（每次都调用）。</li>
 * </ul>
 */
public final class TnNpcEquipment {

    private TnNpcEquipment() {
    }

    /**
     * 按 NPC 的皮肤名穿装备。
     *
     * <p>规则：<b>只给需要"看起来有甲"的 NPC 穿</b>；酒馆老板/铁匠这类平民不穿，
     * 保持他们各自的皮肤辨识度（Self 的围裙、cava 的皮围裙）。
     */
    public static void equip(TnDialogueNpc npc) {
        switch (npc.skinName()) {
            case "huai" -> equipKnight(npc);
            default -> {
                // 平民 NPC：不穿装备（皮肤本身已经说明身份）
            }
        }
    }

    /**
     * 槐 = 骑士/战士：铁质四件套。
     *
     * <p>选铁而不是钻石/下界合金：他是"铁匠的儿子"，铁甲在叙事上最贴
     * （也呼应他背上那面他爹打的盾）。头盔不戴 —— 他的脸和头发在剧本里是有戏的。
     */
    private static void equipKnight(TnDialogueNpc npc) {
        set(npc, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
        set(npc, EquipmentSlot.LEGS, Items.IRON_LEGGINGS);
        set(npc, EquipmentSlot.FEET, Items.IRON_BOOTS);
        // 手上给他一把铁剑 —— 战士的辨识度主要靠这个
        set(npc, EquipmentSlot.MAINHAND, Items.IRON_SWORD);
    }

    private static void set(TnDialogueNpc npc, EquipmentSlot slot, Item item) {
        npc.setItemSlot(slot, new ItemStack(item));
        // 百分百掉落：就算他以后变得能被打死，装备也不会凭空消失
        npc.setDropChance(slot, 1.0F);
    }
}
