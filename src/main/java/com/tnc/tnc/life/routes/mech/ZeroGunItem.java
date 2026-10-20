package com.tnc.tnc.life.routes.mech;

import com.tnc.tnc.life.routes.RouteChargeLedger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.List;

/** Zero is a spell launcher, separate from the six-barrel crystal gun. */
public final class ZeroGunItem extends Item {
    public ZeroGunItem(){super(new Properties().stacksTo(1).rarity(Rarity.EPIC));}
    public static ZeroRules.Form form(ItemStack stack){return ZeroRules.Form.of(stack.hasTag()?stack.getTag().getInt("ZeroForm"):0);}
    @Override public int getUseDuration(ItemStack stack){return 72000;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var gun=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND||MechCombat.suit(player).isEmpty()||!player.isAlive()||player.isSpectator()
                ||com.tnc.tnc.combat.DownedCombat.isDowned(player)){
            if(!level.isClientSide)player.displayClientMessage(Component.literal("零式需要穿戴天穹式魔导铠，由机铠供能"),true);
            return InteractionResultHolder.fail(gun);
        }
        if(player.isShiftKeyDown()){
            if(!level.isClientSide){var next=ZeroRules.Form.of(form(gun).ordinal()+1);gun.getOrCreateTag().putInt("ZeroForm",next.ordinal());
                player.displayClientMessage(Component.literal("零式 · "+next.name+"形态 / "+next.spellName),true);
                level.playSound(null,player.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.45f,1+next.ordinal()*.15f);}
        }else player.startUsingItem(hand);
        return InteractionResultHolder.sidedSuccess(gun,level.isClientSide);
    }
    public static boolean fire(ServerPlayer p){
        var gun=p.getUseItem();var suit=MechCombat.suit(p);
        if(!(gun.getItem() instanceof ZeroGunItem)||p.getUsedItemHand()!=InteractionHand.MAIN_HAND||suit.isEmpty()
                ||!p.isAlive()||p.isSpectator()||com.tnc.tnc.combat.DownedCombat.isDowned(p))return false;
        var circuit=suit.getOrCreateTag();long now=MechCombat.now(p);
        if(circuit.contains("ZeroLastShot")&&now-circuit.getLong("ZeroLastShot")<ZeroRules.PERIOD)return true;
        var ledger=RouteChargeLedger.get(p.serverLevel());
        if(ledger.amount(suit)<ZeroRules.COST){p.displayClientMessage(Component.literal("零式 · 机铠储魔不足（每发72M）"),true);return false;}
        Vec3 eye=p.getEyePosition(),look=p.getLookAngle();
        Vec3 muzzle=eye.add(look.scale(.85)).add(0,-.12,0);
        var obstruction=p.level().clip(new net.minecraft.world.level.ClipContext(eye,muzzle,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        if(obstruction.getType()!=HitResult.Type.MISS)muzzle=eye;
        var mode=form(gun);var entity=ZeroSpellPayload.prepare(p,mode,muzzle);
        if(entity==null){p.displayClientMessage(Component.literal("零式术式未就绪："+mode.spellName),true);return false;}
        if(!ledger.spend(suit,ZeroRules.COST))return false;
        entity.getPersistentData().putUUID("ZeroCaster",p.getUUID());
        if(!p.serverLevel().addFreshEntity(entity)){ledger.deposit(suit,MechRules.CAPACITY,ZeroRules.COST);return false;}
        circuit.putLong("ZeroLastShot",now);gun.getOrCreateTag().putLong("ZeroFiring",now);
        circuit.putInt("ZeroShots",circuit.getInt("ZeroShots")+1);
        MechNetwork.fx(p,muzzle,muzzle.add(look.scale(2)),mode.colorIndex,5);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.BEACON_POWER_SELECT,SoundSource.PLAYERS,.22f,1.35f+p.getRandom().nextFloat()*.25f);
        return true;
    }
    @Override public void onUseTick(Level level,LivingEntity actor,ItemStack stack,int remaining){if(actor instanceof ServerPlayer p&&!fire(p))p.stopUsingItem();}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        var mode=form(stack);lines.add(Component.literal("五相术式机关枪 · "+mode.name+"形态 / "+mode.spellName));
        lines.add(Component.literal("穿戴天穹式后长按右键；潜行右键切换水/火/土/雷/风"));
        lines.add(Component.literal("每秒10发真实法术，每发72机铠魔力；满储能约16.6秒"));
        lines.add(Component.literal("不需弹药，不扣个人魔力；独立回路，无晶铳过热停火"));
    }
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean slotChanged){
        if(slotChanged||oldStack.getItem()!=newStack.getItem())return true;
        var a=oldStack.copy();var b=newStack.copy();a.removeTagKey("ZeroFiring");b.removeTagKey("ZeroFiring");
        if(a.hasTag()&&a.getTag().isEmpty())a.setTag(null);
        if(b.hasTag()&&b.getTag().isEmpty())b.setTag(null);
        return !ItemStack.isSameItemSameTags(a,b);
    }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions(){
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new ZeroGunRenderer();return renderer;}
        });
    }
}
