package com.tnc.tnc.magic.water;

import com.tnc.tnc.TNMod;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Operator-only creative test entry, separate from normal learning/mana/cooldowns. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class WaterTestCommand {
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("tnc").then(Commands.literal("water")
                .requires(s->s.hasPermission(2))
                .then(Commands.literal("cast").then(Commands.argument("spell",ResourceLocationArgument.id()).executes(c->{
                    var player=c.getSource().getPlayerOrException();
                    ResourceLocation id=ResourceLocationArgument.getId(c,"spell");
                    if(!player.isCreative()){c.getSource().sendFailure(Component.literal("测试入口仅限创造模式；正常施法请使用魔法石与法杖。"));return 0;}
                    if(id.equals(ResourceLocation.fromNamespaceAndPath("tnc","dragon_ruin"))){c.getSource().sendFailure(Component.literal("龙滅会永久破坏地形。独立测试世界中使用 /tnc water destructive 确认发射。"));return 0;}
                    boolean known=id.getNamespace().equals("tnc")&&WaterSpellRules.tier(id.getPath())>0;
                    if(known)TNWaterSpellEntity.cast(player,id);else known=TNWaterFieldEntity.cast(player,id);
                    if(!known){c.getSource().sendFailure(Component.literal("不是水法技能ID。"));return 0;}
                    c.getSource().sendSuccess(()->Component.literal("水法测试："+id+"（此入口不消耗魔力）"),false);return 1;
                })))
                .then(Commands.literal("destructive").executes(c->{var player=c.getSource().getPlayerOrException();
                    if(!player.isCreative())return 0;
                    TNWaterSpellEntity.cast(player,ResourceLocation.fromNamespaceAndPath("tnc","dragon_ruin"));
                    c.getSource().sendSuccess(()->Component.literal("龙滅测试：已发射，前方地形将永久改变。"),false);return 1;
                }))));
    }
}
