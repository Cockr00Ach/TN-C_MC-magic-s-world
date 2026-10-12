package com.tnc.tnc.life.routes;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
public final class RouteAccessory extends Item implements Equipable,ManaCharged {
    public enum Kind {RETURN("homeward_ribbon","归途缎带",300),FEATHER("descent_feather","缓降羽饰",200),ORE("ore_whisper_earring","探矿耳坠",200),WARD("dew_ward_charm","守露护符",300),RULER("mana_building_ruler","魔导营造尺",600),CROSSBOW("rune_crossbow","符文弩",800),MECH("mana_mech","魔道机甲",6000),GUN("mech_spellgun","法术机枪模块",0);
        public final String id,name;public final int capacity;Kind(String id,String name,int capacity){this.id=id;this.name=name;this.capacity=capacity;}}
    public final Kind kind;public RouteAccessory(Kind kind){super(new Properties().stacksTo(1));this.kind=kind;}
    public int manaCapacity(){return kind.capacity;}
    @Override public EquipmentSlot getEquipmentSlot(){return kind==Kind.MECH?EquipmentSlot.CHEST:EquipmentSlot.OFFHAND;}
    @Override public int getUseDuration(ItemStack stack){return kind==Kind.GUN?160:32;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return kind==Kind.GUN?UseAnim.BOW:UseAnim.NONE;}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player player,InteractionHand hand){var stack=player.getItemInHand(hand);if(kind==Kind.MECH)return swapWithEquipmentSlot(this,l,player,hand);
        if(l.isClientSide)return InteractionResultHolder.success(stack);if(!(player instanceof ServerPlayer p))return InteractionResultHolder.pass(stack);var ledger=RouteChargeLedger.get(p.serverLevel());
        if(kind==Kind.RETURN){var plot=com.tnc.tnc.home.HousingService.mine(p,com.tnc.tnc.home.PlotCatalog.Kind.HOME);if(plot==null||p.getLastHurtByMob()!=null&&p.tickCount-p.getLastHurtByMobTimestamp()<200)return InteractionResultHolder.fail(stack);var home=com.tnc.tnc.home.HousingService.home(p.server,plot.id());var base=BlockPos.of(home.getLong("Origin"));BlockPos target=base.offset(plot.entry());var world=p.server.overworld();if(!world.hasChunkAt(target))return InteractionResultHolder.fail(stack);var box=p.getBoundingBox().move(target.getX()+.5-p.getX(),target.getY()-p.getY(),target.getZ()+.5-p.getZ());if(!world.noCollision(p,box)||!ledger.spend(stack,120))return InteractionResultHolder.fail(stack);p.teleportTo(world,target.getX()+.5,target.getY(),target.getZ()+.5,p.getYRot(),0);p.getCooldowns().addCooldown(this,200);}
        else if(kind==Kind.ORE){if(!ledger.spend(stack,15))return InteractionResultHolder.fail(stack);BlockPos found=null;for(BlockPos q:BlockPos.betweenClosed(p.blockPosition().offset(-8,-8,-8),p.blockPosition().offset(8,8,8))){if(!l.hasChunkAt(q))continue;var id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(l.getBlockState(q).getBlock());if(id!=null&&id.getPath().endsWith("_ore")&&(found==null||q.distSqr(p.blockPosition())<found.distSqr(p.blockPosition())))found=q.immutable();}p.displayClientMessage(Component.literal(found==null?"八格内没有矿物回声":"矿物回声来自"+(found.getY()<p.getBlockY()-2?"下方":found.getY()>p.getBlockY()+2?"上方":Math.abs(found.getX()-p.getBlockX())>Math.abs(found.getZ()-p.getBlockZ())?(found.getX()>p.getBlockX()?"东侧":"西侧"):(found.getZ()>p.getBlockZ()?"南侧":"北侧"))),true);p.getCooldowns().addCooldown(this,200);}
        else if(kind==Kind.CROSSBOW){shoot(p,stack,25);p.getCooldowns().addCooldown(this,20);}
        else if(kind==Kind.GUN){if(p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof RouteMechItem)player.startUsingItem(hand);else return InteractionResultHolder.fail(stack);}
        else if(kind==Kind.FEATHER||kind==Kind.WARD){return swapWithEquipmentSlot(this,l,player,hand);}
        return InteractionResultHolder.success(stack);
    }
    private static boolean shoot(ServerPlayer p,ItemStack charged,int cost){int ammo=-1;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(Items.ARROW)){ammo=i;break;}if(ammo<0||!RouteChargeLedger.get(p.serverLevel()).spend(charged,cost))return false;p.getInventory().getItem(ammo).shrink(1);var arrow=new net.minecraft.world.entity.projectile.Arrow(p.level(),p);arrow.setBaseDamage(3);arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;arrow.shootFromRotation(p,p.getXRot(),p.getYRot(),0,2.5f,1);p.serverLevel().addFreshEntity(arrow);return true;}
    @Override public void onUseTick(Level level,LivingEntity actor,ItemStack stack,int remaining){if(actor instanceof ServerPlayer p&&kind==Kind.GUN&&(160-remaining)%7==0){var mech=p.getItemBySlot(EquipmentSlot.CHEST);if(!(mech.getItem() instanceof RouteMechItem)||!shoot(p,mech,20))p.stopUsingItem();}}
    @Override public void releaseUsing(ItemStack stack,Level l,LivingEntity actor,int remaining){if(kind==Kind.GUN&&actor instanceof Player p)p.getCooldowns().addCooldown(this,80);}
    @Override public ItemStack finishUsingItem(ItemStack stack,Level l,LivingEntity actor){if(kind==Kind.GUN&&actor instanceof Player p)p.getCooldowns().addCooldown(this,80);return stack;}
    @Override public InteractionResult useOn(UseOnContext c){
        if(kind!=Kind.RULER)return InteractionResult.PASS;
        if(!(c.getPlayer() instanceof ServerPlayer p))return InteractionResult.SUCCESS;
        var level=p.serverLevel();var start=c.getClickedPos().relative(c.getClickedFace());
        Item material=level.getBlockState(c.getClickedPos()).getBlock().asItem();
        if(!(material instanceof BlockItem item))return InteractionResult.FAIL;
        var line=p.getDirection();var cross=line.getClockWise();int placed=0;
        for(int i=0;i<(p.isShiftKeyDown()?8:5);i++){
            BlockPos target=p.isShiftKeyDown()?start.relative(line,new int[]{0,1,2,2,2,1,0,0}[i]).relative(cross,new int[]{0,0,0,1,2,2,2,1}[i]):start.relative(line,i);
            if(!level.hasChunkAt(target)||!level.getBlockState(target).canBeReplaced()||!RouteAccess.allowed(level,target,p.getUUID()))continue;
            int slot=-1;for(int j=0;j<p.getInventory().getContainerSize();j++)if(p.getInventory().getItem(j).is(material)){slot=j;break;}
            if(slot<0||RouteChargeLedger.get(level).amount(c.getItemInHand())<3)break;
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target),c.getClickedFace(),target,false);
            var context=new net.minecraft.world.item.context.BlockPlaceContext(level,p,c.getHand(),p.getInventory().getItem(slot),hit);
            if(item.place(context).consumesAction()){RouteChargeLedger.get(level).spend(c.getItemInHand(),3);placed++;}
        }
        p.displayClientMessage(Component.literal("已用真实材料放置 "+placed+" 块；每块3魔力，普通五格直线，潜行三格框"),true);
        return placed>0?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
    @Override public void inventoryTick(ItemStack s,Level l,Entity holder,int slot,boolean selected){if(!(holder instanceof ServerPlayer p))return;var ledger=RouteChargeLedger.get(p.serverLevel());ledger.sync(s);if(kind==Kind.FEATHER&&p.getOffhandItem()==s&&p.tickCount%20==0&&!p.onGround()&&p.getDeltaMovement().y<-.1&&ledger.spend(s,2))p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,25,0));}
    @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag flags){if(kind==Kind.GUN){lines.add(Component.literal("穿戴魔道机甲后长按右键：每发20机甲魔力与一支箭；连射最多8秒，冷却4秒"));return;}lines.add(Component.literal("储魔 "+(s.hasTag()?s.getTag().getInt("ChargedMana"):0)+"/"+kind.capacity+" · 在饰品充能台补充"));lines.add(Component.literal(kind==Kind.MECH?"右键穿戴；普通行走免费，机枪消耗机甲储魔和箭":kind==Kind.FEATHER||kind==Kind.WARD?"右键装备副手后生效":"右键使用；不从背包其他魔力物品偷偷扣费"));}
}
