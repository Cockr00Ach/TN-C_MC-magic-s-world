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
        if(!com.tnc.tnc.arcana.ArcanaPhysiology.permitDrink(p))return false;
        var quality=com.tnc.tnc.arcana.RitualRules.Quality.values()[Math.max(0,Math.min(2,s.getOrCreateTag().contains("RitualQuality")?s.getOrCreateTag().getInt("RitualQuality"):1))];
        double scale=com.tnc.tnc.arcana.ArcanaPhysiology.potency(p,quality);boolean master=quality==com.tnc.tnc.arcana.RitualRules.Quality.MASTER;
        var k=item.kind;var magic=MagicStone.getOrNull(p);switch(k){
            case BLOOM->p.heal((float)(8*scale));case SPRING->{if(magic!=null)magic.addMana((int)Math.round(30*scale));}case AWAKEN->{if(magic!=null)magic.addMana((int)Math.round(60*scale));}case REVIVE->{p.heal((float)(12*scale));effect(p,MobEffects.ABSORPTION,90,scale,master);}
            case EDGE->effect(p,MobEffects.DAMAGE_BOOST,k.seconds,scale,master);case STRIDE->effect(p,MobEffects.MOVEMENT_SPEED,k.seconds,scale,master);case SHELL->effect(p,MobEffects.DAMAGE_RESISTANCE,k.seconds,scale,master);
            case CINDER->effect(p,MobEffects.FIRE_RESISTANCE,k.seconds,scale,master);case TIDE->effect(p,MobEffects.WATER_BREATHING,k.seconds,scale,master);case FEATHER->effect(p,MobEffects.SLOW_FALLING,k.seconds,scale,master);case NIGHT->effect(p,MobEffects.NIGHT_VISION,k.seconds,scale,master);
            case CLEANSE->{for(var bad:List.of(MobEffects.POISON,MobEffects.WITHER,MobEffects.HUNGER)){var current=p.getEffect(bad);if(current!=null){p.removeEffect(bad);if(scale<1)p.addEffect(new MobEffectInstance(bad,(int)(current.getDuration()*(1-scale)),current.getAmplifier()));}}}
            case HEARTH->{p.getFoodData().eat((int)Math.round(8*scale),.5f);effect(p,MobEffects.REGENERATION,30,scale,master);}
            case FLOW,FOCUS->{var t=com.tnc.tnc.journey.JourneyData.get(p.server).player(p.getUUID());t.putLong("ElixirManaUntil",p.server.overworld().getGameTime()+Math.max(1,(int)(k.seconds*20*scale)));t.putInt("ElixirManaRate",Math.max(1,(int)Math.round((k==Elixir.FOCUS?2:1)*scale)));com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();}
            case ECHO->effect(p,AlchemyContent.ECHO,90,scale,master);
        }
        com.tnc.tnc.arcana.ArcanaPhysiology.drink(p,quality);
        for(var elixir:AlchemyContent.POTIONS.values())p.getCooldowns().addCooldown(elixir,p.hasEffect(com.tnc.tnc.arcana.ArcanaMealEffects.HARMONY)?80:160);if(magic!=null)com.tnc.tnc.network.MagicStoneNetwork.syncTo(p);
        p.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(org.joml.Vector3f.class.cast(new org.joml.Vector3f((k.color>>16&255)/255f,(k.color>>8&255)/255f,(k.color&255)/255f)),1.3f),p.getX(),p.getY()+1,p.getZ(),16,.3,.5,.3,.03);
        p.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,.8f,1.2f);
        if(!p.getAbilities().instabuild){s.shrink(1);if(!s.isEmpty()&&!p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE)))p.drop(new ItemStack(Items.GLASS_BOTTLE),false);}return true;
    }
    private static void effect(Player p,MobEffect e,int seconds,double scale,boolean master){p.addEffect(new MobEffectInstance(e,Math.max(1,(int)(seconds*20*scale)),master?1:0));}
    @Override public Component getName(ItemStack s){if(!s.hasTag()||!s.getTag().contains("RitualQuality"))return super.getName(s);int q=s.getTag().getInt("RitualQuality");return Component.literal((q==2?"臻品·大师 ":q==0?"浑浊次品 ":"标准 ")+kind.name);}
    @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag flag){if(s.hasTag()&&s.getTag().contains("RitualQuality")){int q=s.getTag().getInt("RitualQuality");lines.add(Component.literal(q==2?"药效200% · 时长双倍 · 零淤积":q==0?"药效30% · 淤积+25":"药效100% · 淤积+10").withStyle(net.minecraft.ChatFormatting.GOLD));}lines.add(Component.literal(kind.help).withStyle(net.minecraft.ChatFormatting.AQUA));lines.add(Component.literal("饮用24刻 · 所有魔药共用8秒冷却").withStyle(net.minecraft.ChatFormatting.GRAY));}
}
