package com.tnc.tnc.life.pasture;

import java.util.*;
import com.tnc.tnc.home.TownProtection;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.network.MagicStoneNetwork;
import com.tnc.tnc.production.*;
import com.tnc.tnc.production.energy.EnergyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Four-second living harvest, sixteen-tick drink, persistent transferable mana. */
public final class ManaBottleItem extends Item {
    private record Collect(UUID entity,net.minecraft.resources.ResourceLocation dimension,long until){}
    private static final Map<UUID,Collect> COLLECTING=new HashMap<>();
    private final int capacity;
    public ManaBottleItem(int capacity){super(new Properties().stacksTo(16));this.capacity=capacity;}
    public InteractionResult interactAnimal(ServerPlayer player,ItemStack stack,PastureAnimal animal,InteractionHand hand){return interactLivingEntity(stack,player,animal,hand);}
    @Override public int getMaxStackSize(ItemStack stack){return stack.hasTag()&&stack.getTag().hasUUID("BottleId")?1:16;}
    @Override public int getUseDuration(ItemStack stack){return stack.hasTag()&&stack.getTag().getBoolean("Collecting")?80:16;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.DRINK;}
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){
        if(!(target instanceof PastureAnimal animal))return InteractionResult.PASS;
        if(player instanceof ServerPlayer server){
            if(!animal.mayCareFor(server)||TownProtection.denied(server,animal.blockPosition()))return InteractionResult.FAIL;
            var ledger=PastureBottleLedger.get(server.serverLevel());
            if(animal.speciesId().equals("pillowlight_marten")){
                int offered=Math.min(25,ledger.amount(stack));int received=animal.receiveMana(server,offered);ledger.withdraw(stack,received);message(server,"给枕光貂注入 "+received+" 魔力。");return InteractionResult.CONSUME;
            }
            if(!animal.speciesId().equals("dewbound_whale"))return InteractionResult.PASS;
            if(ledger.amount(stack)>=ledger.capacity(stack,capacity)){message(server,"瓶子已经装满。");return InteractionResult.CONSUME;}
            COLLECTING.put(player.getUUID(),new Collect(animal.getUUID(),server.level().dimension().location(),server.level().getGameTime()+100));
            stack.getOrCreateTag().putBoolean("Collecting",true);player.startUsingItem(hand);server.inventoryMenu.broadcastChanges();
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer server){
            COLLECTING.remove(player.getUUID());stack.getOrCreateTag().remove("Collecting");
            var magic=MagicStone.getOrNull(server);var ledger=PastureBottleLedger.get(server.serverLevel());ledger.sync(stack);
            if(magic==null||magic.getMana()>=magic.getMaxMana()||ledger.amount(stack)==0)return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);return InteractionResultHolder.consume(stack);
    }
    @Override public void onUseTick(Level level,LivingEntity who,ItemStack stack,int remaining){
        if(!(who instanceof ServerPlayer player)||!COLLECTING.containsKey(player.getUUID()))return;
        var c=COLLECTING.get(player.getUUID());Entity target=player.serverLevel().getEntity(c.entity);
        if(level.getGameTime()>c.until||!player.level().dimension().location().equals(c.dimension)||!(target instanceof PastureAnimal animal)||!animal.isAlive()||player.distanceToSqr(animal)>36||!animal.mayCareFor(player)){
            player.stopUsingItem();COLLECTING.remove(player.getUUID());stack.getOrCreateTag().remove("Collecting");return;
        }
        if(remaining%8==0){var from=target.position().add(0,target.getBbHeight()*.65,0);var to=player.getEyePosition();for(int i=0;i<=6;i++){var v=from.lerp(to,i/6.0);player.serverLevel().sendParticles(ParticleTypes.ENCHANT,v.x,v.y,v.z,1,0,0,0,.01);}}
    }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity who){
        if(!(who instanceof ServerPlayer player))return stack;
        var ledger=PastureBottleLedger.get(player.serverLevel());Collect c=COLLECTING.remove(player.getUUID());stack.getOrCreateTag().remove("Collecting");
        if(c!=null){
            Entity target=player.serverLevel().getEntity(c.entity);
            if(!(target instanceof PastureAnimal animal)||!animal.isAlive()||level.getGameTime()>c.until||player.distanceToSqr(animal)>36||!animal.mayCareFor(player))return stack;
            ItemStack filled=stack.getCount()==1?stack:new ItemStack(this);
            int space=Math.max(0,ledger.capacity(filled,capacity)-ledger.amount(filled));
            int taken=animal.takeMana(player,Math.min(24,space));
            if(taken==0){message(player,"露囊尚未蓄好魔力，或采集间隔未过。");return stack;}
            int deposited=ledger.deposit(filled,capacity,taken);
            if(deposited!=taken)throw new IllegalStateException("Animal-to-bottle transfer lost mana");
            if(filled!=stack){stack.shrink(1);if(!player.getInventory().add(filled))player.drop(filled,false);}
            message(player,"采得 "+taken+" 魔力；瓶可饮用，或右键炉后注口/发电座。");
        }else{
            var magic=MagicStone.getOrNull(player);if(magic==null)return stack;
            int used=ledger.withdraw(stack,Math.min(24,magic.getMaxMana()-magic.getMana()));magic.addMana(used);MagicStoneNetwork.syncTo(player);message(player,"回复 "+used+" 魔力，瓶中还剩 "+ledger.amount(stack)+"。");
        }
        player.getCooldowns().addCooldown(this,80);player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();return stack;
    }
    @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity who,int remaining){COLLECTING.remove(who.getUUID());if(stack.hasTag())stack.getTag().remove("Collecting");}
    @Override public InteractionResult useOn(UseOnContext context){
        if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        BlockPos p=context.getClickedPos();if(TownProtection.denied(player,p))return InteractionResult.FAIL;
        ItemStack stack=context.getItemInHand();var ledger=PastureBottleLedger.get(player.serverLevel());int offered=Math.min(25,ledger.amount(stack));int accepted=0;
        if(context.getLevel().getBlockEntity(p) instanceof EnergyBlockEntity node&&node.mayUse(player)){accepted=node.addMana(offered);}
        else if(context.getLevel().getBlockState(p).getBlock() instanceof ForgeInjectorBlock){
            var dir=context.getLevel().getBlockState(p).getValue(ForgeInjectorBlock.FACING);
            if(context.getLevel().getBlockEntity(p.relative(dir.getOpposite(),2)) instanceof MagicForgeBlockEntity forge&&forge.canOperate(player,true)&&forge.formedForTransfer()){
                accepted=Math.min(offered,MagicForgeBlockEntity.MAX_CHARGE-forge.charge());forge.addCharge(accepted);
            }
        }else return InteractionResult.PASS;
        ledger.withdraw(stack,accepted);message(player,"转入 "+accepted+" 魔力，瓶中剩 "+ledger.amount(stack)+"。");return InteractionResult.CONSUME;
    }
    private static void message(ServerPlayer p,String text){p.displayClientMessage(Component.literal(text),true);}
    @Override public void inventoryTick(ItemStack stack,Level level,Entity holder,int slot,boolean selected){if(holder instanceof ServerPlayer player)PastureBottleLedger.get(player.serverLevel()).sync(stack);}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flags){tooltip.add(Component.literal("魔力 "+(stack.hasTag()?stack.getTag().getInt("StoredMana"):0)+"/"+capacity));tooltip.add(Component.literal("右键浮鲸采集4秒；空中右键饮用，右键注口/发电座注魔。"));}
    public static void clearSessions(){COLLECTING.clear();}
    /** One redstone pulse distributes at most five stored mana; never FE back into mana. */
    public static int transferFromBase(net.minecraft.server.level.ServerLevel level,BlockPos base,UUID owner,ItemStack stack,int maximum){
        if(owner==null||!(stack.getItem() instanceof ManaBottleItem))return 0;
        var ledger=PastureBottleLedger.get(level);int remaining=Math.min(5,Math.min(maximum,ledger.amount(stack))),total=0;
        var actor=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(owner,"bottle_base"));
        for(var d:net.minecraft.core.Direction.values()){
            BlockPos p=base.relative(d);if(remaining<=0)break;
            if(!level.hasChunkAt(p)||TownProtection.denied(actor,p))continue;
            int accepted=0;
            if(level.getBlockEntity(p) instanceof EnergyBlockEntity node&&owner.equals(node.owner()))accepted=node.addMana(remaining);
            else if(level.getBlockState(p).getBlock() instanceof ForgeInjectorBlock){var facing=level.getBlockState(p).getValue(ForgeInjectorBlock.FACING);if(level.getBlockEntity(p.relative(facing.getOpposite(),2)) instanceof MagicForgeBlockEntity forge&&owner.equals(forge.owner())&&forge.formedForTransfer()){accepted=Math.min(remaining,MagicForgeBlockEntity.MAX_CHARGE-forge.charge());forge.addCharge(accepted);}}
            if(accepted>0){ledger.withdraw(stack,accepted);remaining-=accepted;total+=accepted;level.sendParticles(ParticleTypes.ENCHANT,p.getX()+.5,p.getY()+.7,p.getZ()+.5,4,.2,.2,.2,.02);}
        }
        return total;
    }
}
