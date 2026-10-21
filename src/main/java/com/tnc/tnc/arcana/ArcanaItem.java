package com.tnc.tnc.arcana;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import java.util.List;
public final class ArcanaItem extends Item {
    public final String id;
    public ArcanaItem(String id){super(properties(id));this.id=id;}
    private static Properties properties(String id){var p=new Properties().stacksTo(id.endsWith("elixir")||id.equals("strange_waste")?8:64).rarity(id.contains("singularity")?Rarity.EPIC:Rarity.UNCOMMON);if(id.equals("slime_pudding")||id.equals("moon_honey_cake"))p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationMod(.6f).build());return p;}
    @Override public InteractionResult useOn(UseOnContext c){if(!(c.getPlayer() instanceof net.minecraft.server.level.ServerPlayer p))return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
        if(id.equals("strange_waste")){var pos=c.getClickedPos().above();if(!com.tnc.tnc.home.TownProtection.denied(p,pos)&&p.serverLevel().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BushBlock){p.serverLevel().destroyBlock(pos,false);consume(p,c.getItemInHand());return InteractionResult.CONSUME;}}
        if(id.equals("soothing_bath_salt")){if(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof com.tnc.tnc.life.routes.RouteNodeEntity n&&n.kind()==com.tnc.tnc.life.routes.RouteKind.GEOTHERMAL&&n.mayUse(p)){n.getPersistentData().putLong("BathUntil",c.getLevel().getGameTime()+24000);n.setChanged();consume(p,c.getItemInHand());p.displayClientMessage(Component.literal("浴盐已融入热源，周围源水可排毒。"),true);return InteractionResult.CONSUME;}}
        if(ArcanaEcology.applyPlant(p,c.getClickedPos(),id,c.getItemInHand()))return InteractionResult.CONSUME;
        return InteractionResult.PASS;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player player,InteractionHand hand){var stack=player.getItemInHand(hand);if(player instanceof net.minecraft.server.level.ServerPlayer p&&id.equals("tailwind_boots")){var chest=p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);if(!chest.isEmpty()&&!chest.getOrCreateTag().getBoolean("TailwindSoles")){chest.getOrCreateTag().putBoolean("TailwindSoles",true);consume(p,stack);p.sendSystemMessage(Component.literal("顺风魔导靴已嵌入全身衣装：天路加速并防摔。"));}return InteractionResultHolder.success(stack);}if(player instanceof net.minecraft.server.level.ServerPlayer p&&id.equals("forbidden_scroll")){var t=ArcanaPhysiology.data(p);if(!t.getBoolean("ForbiddenTech")){t.putBoolean("ForbiddenTech",true);consume(p,stack);com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();p.sendSystemMessage(Component.literal("禁忌古卷：已解锁深渊聚能、奇点与星界工艺。"));}return InteractionResultHolder.success(stack);}if(id.equals("aurora_wings")){player.startUsingItem(hand);return InteractionResultHolder.consume(stack);}return isEdible()?super.use(l,player,hand):InteractionResultHolder.pass(stack);}
    @Override public int getUseDuration(ItemStack s){return id.equals("aurora_wings")?72000:super.getUseDuration(s);}
    @Override public void onUseTick(Level l,net.minecraft.world.entity.LivingEntity e,ItemStack s,int left){if(!id.equals("aurora_wings")||!(e instanceof net.minecraft.server.level.ServerPlayer p))return;var m=com.tnc.tnc.magic.MagicStone.getOrNull(p);if(m==null||p.tickCount%5==0&&!m.spendMana(2)){p.stopUsingItem();return;}var v=p.getLookAngle().scale(.8).add(0,.06,0);p.setDeltaMovement(v);p.hurtMarked=true;p.fallDistance=0;ArcanaPhysiology.data(p).putLong("SkyGraceUntil",ArcanaPhysiology.now(p)+100);p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));com.tnc.tnc.network.MagicStoneNetwork.syncTo(p);com.tnc.tnc.mana.sight.ManaSightServer.record(p.serverLevel(),p.position().add(0,.8,0),p.position().add(v.scale(-3)),2,0xb0eaff,4);}
    public static void consume(net.minecraft.server.level.ServerPlayer p,ItemStack s){if(!p.isCreative()){boolean bottle=s.getItem() instanceof ArcanaItem a&&(a.id.endsWith("elixir")||a.id.equals("strange_waste"));s.shrink(1);if(bottle&&!p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE)))p.drop(new ItemStack(Items.GLASS_BOTTLE),false);}}
    @Override public void appendHoverText(ItemStack s,Level l,List<Component> list,TooltipFlag f){if(id.endsWith("elixir"))list.add(Component.literal("农牧药剂 · 手持右键目标，或放入魔力农田喷灌塔").withStyle(net.minecraft.ChatFormatting.AQUA));}
}
