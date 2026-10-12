package com.tnc.tnc.life.botanical;

import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.*;

/** Every processed field product has a real player consumer or recipe consumer. */
public final class BotanicalProductItem extends Item {
    public enum Mode { RAW,MANA30,MANA10,FOOD7,WARM_SOUP,FROST_MEAL,MIRROR_DRINK,SLEEP_TEA,FOG,SLOW_FALL,ROUTE_PAGE,ROCK_PAGE,PATTERN_CARD,FLOWER_GIFT,COMPONENT,BOTTLED_RAW }
    public static final Map<String,Mode> PROCESSED=new LinkedHashMap<>();
    static {
        PROCESSED.put("hearth_oil",Mode.COMPONENT);PROCESSED.put("morning_honey_breakfast",Mode.FOOD7);PROCESSED.put("hearth_pepper_soup",Mode.WARM_SOUP);PROCESSED.put("frost_pepper_meal",Mode.FROST_MEAL);PROCESSED.put("mist_cloth",Mode.COMPONENT);PROCESSED.put("folded_mist_tent",Mode.FOG);PROCESSED.put("folded_descent_cloth",Mode.SLOW_FALL);PROCESSED.put("mirror_lotus_drink",Mode.MIRROR_DRINK);PROCESSED.put("sleep_clock_tea",Mode.SLEEP_TEA);PROCESSED.put("star_dew_drink",Mode.MANA30);PROCESSED.put("star_rest_drink",Mode.MANA10);PROCESSED.put("honey_cluster_cake",Mode.FOOD7);PROCESSED.put("survey_route_page",Mode.ROUTE_PAGE);PROCESSED.put("stone_rubbing_page",Mode.ROCK_PAGE);PROCESSED.put("shadow_pattern_card",Mode.PATTERN_CARD);PROCESSED.put("flower_gift",Mode.FLOWER_GIFT);PROCESSED.put("field_envelope",Mode.COMPONENT);PROCESSED.put("condensing_shell",Mode.COMPONENT);PROCESSED.put("energy_saving_crystal",Mode.COMPONENT);PROCESSED.put("companion_bell_chip",Mode.COMPONENT);PROCESSED.put("dawn_direction_page",Mode.ROUTE_PAGE);
    }
    private final String id;private final Mode mode;
    public BotanicalProductItem(String id,Mode mode){super(properties(id,mode));this.id=id;this.mode=mode;}
    private static Properties properties(String id,Mode mode){Properties p=new Properties();if(mode==Mode.MANA30||mode==Mode.MANA10)p.food(new FoodProperties.Builder().nutrition(1).saturationMod(.1F).alwaysEat().build());else if(mode==Mode.FOOD7||mode==Mode.WARM_SOUP||mode==Mode.FROST_MEAL)p.food(new FoodProperties.Builder().nutrition(7).saturationMod(.7F).build());else if(mode==Mode.MIRROR_DRINK||mode==Mode.SLEEP_TEA)p.food(new FoodProperties.Builder().nutrition(1).saturationMod(.1F).alwaysEat().build());if(id.endsWith("drink")||id.endsWith("tea")||id.equals("dawn_honey")||id.equals("mirror_dew")||id.equals("frost_dew"))p.craftRemainder(Items.GLASS_BOTTLE);if(mode==Mode.WARM_SOUP)p.craftRemainder(Items.BOWL);return p;}
    @Override public int getUseDuration(ItemStack stack){return mode==Mode.MANA30&&!id.endsWith("drink")?16:32;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return id.endsWith("drink")||id.endsWith("tea")?UseAnim.DRINK:super.getUseAnimation(stack);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){var held=player.getItemInHand(hand);
        if(mode==Mode.MANA30||mode==Mode.MANA10){if(player instanceof ServerPlayer sp){var magic=MagicStone.getOrNull(sp);if((magic==null||magic.getMana()>=magic.getMaxMana())&&!player.canEat(false))return InteractionResultHolder.fail(held);}player.startUsingItem(hand);return InteractionResultHolder.consume(held);}
        if(mode==Mode.FOG||mode==Mode.SLOW_FALL){if(!level.isClientSide){if(mode==Mode.FOG){var data=player.getPersistentData();data.putLong("TncFogUntil",level.getGameTime()+600);data.putLong("TncFogCenter",player.blockPosition().asLong());}else player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,160,0));if(!player.isCreative())held.shrink(1);player.getCooldowns().addCooldown(this,mode==Mode.FOG?600:160);}return InteractionResultHolder.sidedSuccess(held,level.isClientSide);}
        if(mode==Mode.ROUTE_PAGE||mode==Mode.ROCK_PAGE||mode==Mode.PATTERN_CARD){if(!level.isClientSide){if(!held.hasTag()||!held.getTag().hasUUID("Sampler")){var t=held.getOrCreateTag();t.putUUID("Sampler",player.getUUID());t.putString("Dimension",level.dimension().location().toString());t.putString("Biome",level.getBiome(player.blockPosition()).unwrapKey().map(k->k.location().toString()).orElse("unknown"));t.putLong("SamplePosition",player.blockPosition().asLong());if(id.equals("dawn_direction_page"))t.putString("DawnDirection","east");}var t=held.getTag();player.displayClientMessage(Component.literal("调查页 · "+t.getString("Biome")+" · "+BlockPos.of(t.getLong("SamplePosition")).toShortString()+" · "+t.getString("Dimension")+(t.contains("Pattern")?" · 纹样 "+t.getInt("Pattern"):"")),false);}return InteractionResultHolder.sidedSuccess(held,level.isClientSide);}
        return super.use(level,player,hand);
    }
    @Override public InteractionResult useOn(UseOnContext c){if(c.getPlayer()==null)return InteractionResult.PASS;
        var plant=com.tnc.tnc.life.routes.RoutePlanting.plantFruit(id,c);if(plant!=InteractionResult.PASS)return plant;
        if(id.equals("stone_pattern_leaf")||id.equals("shadow_silk")){var p=c.getPlayer();var l=c.getLevel();if(l.isClientSide)return InteractionResult.SUCCESS;var needed=id.equals("stone_pattern_leaf")?"stone_rubbing_page":"shadow_pattern_card";int paper=find(p,Items.PAPER);if(paper<0)return InteractionResult.FAIL;ItemStack page=new ItemStack(BotanicalContent.PRODUCTS.get(needed));if(c.getItemInHand().hasTag())page.setTag(c.getItemInHand().getTag().copy());p.getInventory().getItem(paper).shrink(1);c.getItemInHand().shrink(1);if(!p.getInventory().add(page))p.drop(page,false);return InteractionResult.CONSUME;}
        return super.useOn(c);
    }
    private static int find(Player p,Item item){for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(item))return i;return -1;}
    @Override public ItemStack finishUsingItem(ItemStack stack,Level l,LivingEntity user){boolean vessel=id.endsWith("drink")||id.endsWith("tea");boolean bowl=mode==Mode.WARM_SOUP;var result=super.finishUsingItem(stack,l,user);if(!l.isClientSide&&user instanceof ServerPlayer p){
        if(mode==Mode.MANA30||mode==Mode.MANA10){var magic=MagicStone.getOrNull(p);if(magic!=null){magic.addMana(mode==Mode.MANA30?30:10);MagicStoneNetwork.syncTo(p);}p.getCooldowns().addCooldown(this,80);if(mode==Mode.MANA10)p.addEffect(new MobEffectInstance(MobEffects.GLOWING,400,0));}
        if(mode==Mode.FROST_MEAL)p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,200,0));
        if(mode==Mode.WARM_SOUP){p.getPersistentData().putLong("TncWarmUntil",l.getGameTime()+600);p.setTicksFrozen(0);}
        if(mode==Mode.MIRROR_DRINK){p.getPersistentData().putLong("TncMirrorUntil",l.getGameTime()+1200);if(p.isInWater())p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,220,0));}
        if(mode==Mode.SLEEP_TEA){p.removeEffect(MobEffects.DIG_SLOWDOWN);if(p.getLastHurtByMob()==null||p.tickCount-p.getLastHurtByMobTimestamp()>200)p.heal(4);p.getCooldowns().addCooldown(this,1200);}
        if(vessel||bowl){ItemStack bottle=new ItemStack(bowl?Items.BOWL:Items.GLASS_BOTTLE);if(result.isEmpty())result=bottle;else if(!p.getInventory().add(bottle))p.drop(bottle,false);}
    }return result;}
    @Override public void appendHoverText(ItemStack stack,Level l,List<Component> text,TooltipFlag flags){text.add(Component.literal(switch(mode){case MANA30->"完成食用回复30真实魔力，4秒冷却；满饥饿也可吃。";case MANA10->"回复10魔力；队友可见微光20秒。";case WARM_SOUP->"保暖30秒，停止积累细雪冻结。";case FROST_MEAL->"耐火10秒。";case MIRROR_DRINK->"水下清晰视野60秒，饮后返瓶。";case SLEEP_TEA->"解除挖掘疲劳；非战斗回复4生命；60秒冷却。";case FOG->"原地5×5雾帐30秒；8格外怪不能新锁定，攻击会结束。";case SLOW_FALL->"消耗一块布，缓降8秒，落地结束。";case ROUTE_PAGE,ROCK_PAGE,PATTERN_CARD->"右键记录/查看署名调查页；纸与岩叶/影绢右键加工保留纹样。";case FLOWER_GIFT->"交给小镇居民作礼物；花礼不生成铜币或免费家具。";case COMPONENT->"工坊材料；具有对应配方或设备用途。";default->"原创生态原料；用于食物、工坊与商行供货。";}));if(stack.hasTag()&&stack.getTag().contains("Pattern"))text.add(Component.literal("保存纹样："+stack.getTag().getInt("Pattern")));}
}
