package com.tnc.tnc.magic;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * 「宝箱传说残卷」的一卷 —— <b>右键阅读</b> ✓。
 *
 * <p>正文不写在这里 ✗：正文是**文书专题**的文字资产，放在语言文件里
 * （键 {@code scroll.tnc.<id>}，由 {@code tools/gen_scroll_lang.ps1}
 * 从 {@code 剧情/宝箱传说残卷.md} 的【卷文】段生成 ✓）。
 * 好处：文案改字不用碰 Java，重跑脚本 + 重新编译即可 ✓。
 *
 * <p>只放【卷文】✗<b>不放【暗扣】</b> —— 那是给制作组看的，进游戏就剧透了 ✗。
 *
 * <p>阅读界面直接复用原版书本界面（{@code BookViewScreen}），
 * 见 {@code com.tnc.tnc.client.TNScrollScreen}：
 * 不画新 UI、不加贴图、翻页和排版都是原版行为 ✓。
 *
 * <p>开界面只在**客户端**做 ✓（服务端不需要知道玩家在看什么），
 * 所以不需要网络包；客户端类经 {@link DistExecutor} 调用，
 * 专用服务端不会去加载 GUI 类 ✗。
 */
public class TNScrollItem extends Item {

    /** 正文的语言键（整卷正文一个键，段落用 \n 分隔） */
    private final String textKey;
    /** 卷名的语言键（就是物品名） */
    private final String titleKey;

    public TNScrollItem(String id, Rarity rarity) {
        super(new Item.Properties().stacksTo(1).rarity(rarity));
        this.textKey = TNScrolls.TEXT_PREFIX + id;
        this.titleKey = "item.tnc." + id;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            final String title = this.titleKey;
            final String text = this.textKey;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.tnc.tnc.client.TNScrollScreen.open(title, text));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        // 提示玩家"这东西能右键"，否则没人会去试 ✓
        tooltip.add(Component.translatable("tooltip.tnc.scroll.read").withStyle(ChatFormatting.GRAY));
    }
}
