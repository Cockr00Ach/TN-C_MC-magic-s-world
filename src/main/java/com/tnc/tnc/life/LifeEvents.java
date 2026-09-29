package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.UUID;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class LifeEvents {
    private static final UUID FEAST_HEALTH=UUID.fromString("0fcbd424-c927-419b-965e-5179719a38ed");
    public static void open(ServerPlayer p) {
        if(com.tnc.tnc.combat.DownedCombat.isDowned(p))return;
        var account=LifeSavedData.get(p.server).account(p.getUUID());
        NetworkHooks.openScreen(p,new SimpleMenuProvider((id,inv,player)->new TravelBagMenu(id,inv,account.bag),Component.literal("钱袋与旅途食物袋")));
    }
    public static class PouchItem extends Item {
        public PouchItem(){super(new Properties().stacksTo(1));}
        @Override public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player p,InteractionHand hand){if(p instanceof ServerPlayer s)open(s);return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),level.isClientSide());}
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(e.getEntity() instanceof ServerPlayer p){var a=LifeSavedData.get(p.server).account(p.getUUID());
            if(!AdventureService.profile(p).hasMilestone("pouches")) {
                a.bag.setItem(0,new ItemStack(TNMod.MONEY_POUCH.get()));a.bag.setItem(1,new ItemStack(TNMod.FOOD_POUCH.get()));AdventureService.milestone(p,"pouches");
            }health(p);
        }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) {health(p);if(!e.isEndConquered()){p.getFoodData().setFoodLevel(6);p.getFoodData().setSaturation(0);}}
    }
    @SubscribeEvent public static void finish(LivingEntityUseItemEvent.Finish e){if(e.getEntity() instanceof ServerPlayer p&&e.getItem().isEdible()){taste(p,e.getItem().getItem());AdventureService.milestone(p,"meal");}}
    public static void taste(ServerPlayer p,Item food) {
        var a=LifeSavedData.get(p.server).account(p.getUUID());String id=ForgeRegistries.ITEMS.getKey(food).toString();
        if(a.tasted.add(id))p.displayClientMessage(Component.literal("第一次品尝「"+food.getDescription().getString()+"」：旅途也值得好好吃饭。"),true);
        if(food instanceof DelicacyItem&&a.delicacies.size()<8&&a.delicacies.add(id)){health(p);p.displayClientMessage(Component.literal("珍馐记忆：永久生命上限 +2。相同料理不会重复增加。"),true);AdventureService.milestone(p,"delicacy");}
        LifeSavedData.get(p.server).setDirty();
    }
    private static void health(ServerPlayer p) {
        var attr=p.getAttribute(Attributes.MAX_HEALTH);if(attr==null)return;attr.removeModifier(FEAST_HEALTH);
        int count=LifeSavedData.get(p.server).account(p.getUUID()).delicacies.size();if(count>0)attr.addPermanentModifier(new AttributeModifier(FEAST_HEALTH,"TN-C memorable feasts",Math.min(8,count)*2,AttributeModifier.Operation.ADDITION));
    }
    public static class DelicacyItem extends Item {
        public DelicacyItem(){super(new Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(10).saturationMod(1).alwaysEat().build()));}
        @Override public ItemStack finishUsingItem(ItemStack stack,net.minecraft.world.level.Level level,net.minecraft.world.entity.LivingEntity entity){
            var result=super.finishUsingItem(stack,level,entity);
            if(entity instanceof ServerPlayer p)taste(p,this);
            if(result.isEmpty())return new ItemStack(Items.BOWL);
            if(entity instanceof ServerPlayer p){var tx=new InventoryTransaction(p.getInventory());if(tx.add(new ItemStack(Items.BOWL)))tx.commit();else p.drop(new ItemStack(Items.BOWL),false);}
            return result;
        }
    }
}
