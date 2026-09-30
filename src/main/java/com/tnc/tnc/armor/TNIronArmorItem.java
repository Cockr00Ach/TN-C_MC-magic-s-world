package com.tnc.tnc.armor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

/**
 * 铁甲 —— 外观沿用原版铁胸甲，穿上之后受到的伤害只剩 1%（= 99% 减伤 ✓）。
 *
 * <p>减伤不是在属性上做的 ✗：原版护甲公式上限只有 80% 左右，靠 {@code Attributes.ARMOR} 永远到不了 99% ✗。
 * 实际逻辑在 {@link TNIronArmorEvents}（Forge 的 {@code LivingHurtEvent} 里把伤害 ×0.01 ✓）。
 */
public class TNIronArmorItem extends ArmorItem {

    public TNIronArmorItem() {
        super(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE, new Properties());
    }

    /** 直接穿原版铁甲那张贴图 ✓（作者："用铁甲"） */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return "minecraft:textures/models/armor/iron_layer_1.png";
    }
}