package com.tnc.tnc.alchemy;
import com.tnc.tnc.magic.MagicStone;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.*;
import net.minecraft.network.chat.Component;
import java.util.List;
public final class ElixirItem extends Item {
    public final Elixir kind;
    public ElixirItem(Elixir e){super(new Properties().stacksTo(8).rarity(Rarity.UNCOMMON));kind=e;}
    @Override public UseAnim getUseAnimation(ItemStack s){return UseAnim.DRINK;}
    @Override public int getUseDuration(ItemStack s){return 24;}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){return ItemUtils.startUsingInstantly(l,p,h);}
    @Override public ItemStack finishUsingItem(ItemStack s,Level l,LivingEntity e){if(e instanceof net.minecraft.server.level.ServerPlayer p&&drink(p,s)){if(s.isEmpty())return new ItemStack(Items.GLASS_BOTTLE);}return s;}
    public static boolean drink(net.minecraft.server.level.ServerPlayer p,ItemStack s){if(!(s.getItem() instanceof ElixirItem item)||p.getCooldowns().isOnCooldown(item)||!p.isAlive()||p.isSpectator()||com.tnc.tnc.combat.DownedCombat.isDowned(p)||com.tnc.tnc.dungeon.DungeonService.recovering(p))return false;
        var k=item.kind;var magic=MagicStone.getOrNull(p);switch(k){
            case BLOOM->p.heal(8);case SPRING->{if(magic!=null)magic.addMana(30);}case AWAKEN->{if(magic!=null)magic.addMana(60);}case REVIVE->{p.heal(12);effect(p,MobEffects.ABSORPTION,90);}
            case EDGE->effect(p,MobEffects.DAMAGE_BOOST,k.seconds);case STRIDE->effect(p,MobEffects.MOVEMENT_SPEED,k.seconds);case SHELL->effect(p,MobEffects.DAMAGE_RESISTANCE,k.seconds);
            case CINDER->effect(p,MobEffects.FIRE_RESISTANCE,k.seconds);case TIDE->effect(p,MobEffects.WATER_BREATHING,k.seconds);case FEATHER->effect(p,MobEffects.SLOW_FALLING,k.seconds);case NIGHT->effect(p,MobEffects.NIGHT_VISION,k.seconds);
            case CLEANSE->{for(var bad:List.of(MobEffects.POISON,MobEffects.WITHER,MobEffects.HUNGER))p.removeEffect(bad);}
            case HEARTH->{p.getFoodData().eat(8,.5f);effect(p,MobEffects.REGENERATION,30);}
            case FLOW,FOCUS->{var t=com.tnc.tnc.journey.JourneyData.get(p.server).player(p.getUUID());t.putLong("ElixirManaUntil",p.server.overworld().getGameTime()+k.seconds*20L);t.putInt("ElixirManaRate",k==Elixir.FOCUS?2:1);com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();}
            case ECHO->effect(p,AlchemyContent.ECHO,90);
        }
        for(var elixir:AlchemyContent.POTIONS.values())p.getCooldowns().addCooldown(elixir,160);if(magic!=null)com.tnc.tnc.network.MagicStoneNetwork.syncTo(p);
        p.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(org.joml.Vector3f.class.cast(new org.joml.Vector3f((k.color>>16&255)/255f,(k.color>>8&255)/255f,(k.color&255)/255f)),1.3f),p.getX(),p.getY()+1,p.getZ(),16,.3,.5,.3,.03);
        p.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,.8f,1.2f);
        if(!p.getAbilities().instabuild){s.shrink(1);if(!s.isEmpty()&&!p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE)))p.drop(new ItemStack(Items.GLASS_BOTTLE),false);}return true;
    }
    private static void effect(Player p,MobEffect e,int seconds){p.addEffect(new MobEffectInstance(e,seconds*20,0));}
    @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag flag){lines.add(Component.literal(kind.help).withStyle(net.minecraft.ChatFormatting.AQUA));lines.add(Component.literal("饮用24刻 · 所有魔药共用8秒冷却").withStyle(net.minecraft.ChatFormatting.GRAY));}
}
