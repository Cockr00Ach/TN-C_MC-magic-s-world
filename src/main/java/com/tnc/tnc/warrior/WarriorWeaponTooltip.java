package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * 战士武器的提示框：一行"这条武器线 + 单手/双手"，高级提示（F3+H 打开）再多一行键位。
 *
 * <p>为什么要加：新武器有 11 把、分 4 条线，玩家拿到手只能看名字猜；而"这把是双手的"
 * 直接影响打法（副手不能拿盾/副手物品），"属于哪条线"决定它能打哪套连招 ——
 * 这两件事在游戏里原本完全不可见，只能翻文档。
 *
 * <p>只对本 mod 自己的物品生效（按命名空间过滤），不会污染原版剑和别的模组的武器。
 * 家族判定走 {@link WarriorWeaponFamily#of}，和连招用的是同一段代码，所以提示不会和实际
 * 行为对不上（标签没同步时它会退化成"长剑"，这时候提示也显示长剑，仍然是一致的）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class WarriorWeaponTooltip {

    private WarriorWeaponTooltip() {
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !TNMod.MODID.equals(id.getNamespace())) {
            return;
        }
        WarriorWeaponFamily family = WarriorWeaponFamily.of(stack);
        if (family == null) {
            return;
        }
        var line = Component.translatable(key(family)).withStyle(ChatFormatting.GRAY);
        line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(Component.translatable(handKey(family)).withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(line);

        if (event.getFlags().isAdvanced()) {
            event.getToolTip().add(Component.translatable("tooltip.tnc.weapon.keys")
                    .withStyle(ChatFormatting.DARK_GRAY));
            if(WarriorMartialArts.supports(id.toString())) {
                for(var skill:WarriorCombos.forFamily(family)) {
                    var move=WarriorMartialArts.adapt(skill,id.toString());
                    event.getToolTip().add(Component.literal(move.holdLabel()+" · "+move.name()).withStyle(ChatFormatting.GOLD));
                }
            }
        }
    }

    private static String key(WarriorWeaponFamily family) {
        return "tooltip.tnc.weapon." + family.name().toLowerCase(Locale.ROOT);
    }

    private static String handKey(WarriorWeaponFamily family) {
        if (family == WarriorWeaponFamily.BOW) {
            return "tooltip.tnc.weapon.ranged";
        }
        return family.twoHanded() ? "tooltip.tnc.weapon.two_handed" : "tooltip.tnc.weapon.one_handed";
    }
}
