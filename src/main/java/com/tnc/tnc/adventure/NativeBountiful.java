package com.tnc.tnc.adventure;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.tnc.tnc.TNMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static net.minecraft.commands.Commands.*;

/** Native Bountiful consumes objectives and paper; this adapter only pays the account. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class NativeBountiful {
    public static boolean isTownPaper(ItemStack s){return s.hasTag()&&s.getTag().toString().contains("tnc_bounty_reward");}
    public static boolean reward(ServerPlayer p,String kind,int units){
        if(units<1||units>100||!java.util.Set.of("supply","food","hunt").contains(kind)||!TownServices.atBoard(p)||!AdventureService.profile(p).registered())return false;
        var a=AdventureService.profile(p);long copper=units*(kind.equals("hunt")?40:30),xp=units*(kind.equals("hunt")?150:120);
        // Saturate the account at its documented cap; native objectives must never be
        // consumed by an overflow exception or a client-side duplicate transaction.
        long credited=Math.min(copper,AdventureRules.MAX_COINS-a.coins());
        a.credit(credited,"酒馆 · "+kind);a.addXp(xp);a.reputation=Math.min(1_000_000,a.reputation+units*10);
        AdventureService.milestone(p,"accepted");AdventureService.milestone(p,"first_contract");
        if(kind.equals("food"))com.tnc.tnc.life.FarmMagic.learn(p);
        AdventureSavedData.get(p.server).setDirty();AdventureService.refreshGrowth(p);
        p.sendSystemMessage(net.minecraft.network.chat.Component.literal("酒馆结算：+"+credited+"铜、+"+xp+"经验、+"+(units*10)+"声望。"+(credited<copper?"余额已达上限，超出部分未入账。":"")));return true;
    }
    public static void refreshCount(ServerPlayer p){
        var pos=TownServices.board(p.server.overworld());if(pos==null||!p.server.overworld().hasChunkAt(pos))return;
        var be=p.server.overworld().getBlockEntity(pos);if(be==null||!be.getClass().getName().equals("io.ejekta.bountiful.content.board.BoardBlockEntity"))return;
        try{
            var completed=com.google.gson.JsonParser.parseString(be.saveWithoutMetadata().getString("completed")).getAsJsonObject();
            var value=completed.get(p.getUUID().toString());if(value==null)return;int count=Math.max(0,Math.min(1_000_000,value.getAsInt()));var a=AdventureService.profile(p);
            if(count>a.nativeBounties){a.nativeBounties=count;AdventureSavedData.get(p.server).setDirty();}
            for(int n:new int[]{1,5,10,25,50,100})if(a.nativeBounties>=n)AdventureService.milestone(p,"bounties_"+n);
        }catch(IllegalStateException|com.google.gson.JsonParseException|NumberFormatException ex){/* Third-party data is never rewritten by this adapter. */}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(literal("tnc_bounty_reward").requires(s->s.hasPermission(4)&&s.getEntity()==null)
            .then(argument("recipient",StringArgumentType.string()).then(argument("kind",StringArgumentType.string()).then(argument("units",StringArgumentType.string()).executes(c->{
                var p=c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c,"recipient"));if(p==null)return 0;
                try{return reward(p,StringArgumentType.getString(c,"kind"),Integer.parseInt(StringArgumentType.getString(c,"units")))?1:0;}catch(NumberFormatException ex){return 0;}
            })))));
    }
}
