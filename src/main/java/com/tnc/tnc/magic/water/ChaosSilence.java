package com.tnc.tnc.magic.water;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.List;

/** Independent magic: suppression adapters, not an AI freeze. Unsupported mod skills remain explicit. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class ChaosSilence {
    // Verified against this project's 1.20.1 SRG mappings; Forge remaps the name in dev.
    private static final java.lang.reflect.Field CREEPER_FUSE=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(Creeper.class,"f_32270_");
    public static boolean silenced(LivingEntity entity){return entity.hasEffect(TNEffects.CHAOS_SILENCE.get());}
    public static boolean apply(LivingEntity target) {
        boolean applied=target.addEffect(new MobEffectInstance(TNEffects.CHAOS_SILENCE.get(),60,0,false,true));
        if(applied && target instanceof Creeper creeper)resetFuse(creeper);
        return applied;
    }
    public static void cast(ServerPlayer player,net.minecraft.resources.ResourceLocation id) {
        if(!id.toString().equals("tnc:chaos_magic"))return;
        LivingEntity target=TNWaterFieldEntity.aimEnemy(player,24);
        if(target==null || !apply(target)) {
            player.displayClientMessage(Component.literal("§7乱魔：没有可命中的敌人，或目标拒绝沉默。"),true);return;
        }
        var level=player.serverLevel();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,target.getX(),target.getY()+target.getBbHeight()*.6,target.getZ(),32,.6,.8,.6,.15);
        level.playSound(null,target.blockPosition(),net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,net.minecraft.sounds.SoundSource.PLAYERS,1,.55F);
        player.displayClientMessage(Component.literal("§d乱魔 · "+target.getName().getString()+" · 沉默3秒"),true);
    }
    private static void resetFuse(Creeper creeper) {
        try { CREEPER_FUSE.setInt(creeper,0);creeper.setSwellDir(-1); }
        catch(IllegalAccessException e){throw new IllegalStateException("Cannot suppress creeper fuse",e);}
    }
    @SubscribeEvent public static void explosion(net.minecraftforge.event.level.ExplosionEvent.Start event) {
        var source=event.getExplosion().getDirectSourceEntity();
        if(source instanceof LivingEntity living && silenced(living))event.setCanceled(true);
    }
    @SubscribeEvent public static void spawn(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide)return;
        // Existing projectiles are deliberately untouched. Only new skill products are denied.
        var e=event.getEntity();LivingEntity owner=null;
        if(e instanceof Projectile p && p.getOwner() instanceof LivingEntity living)owner=living;
        else if(e instanceof EvokerFangs f)owner=f.getOwner();
        else if(e instanceof Vex v)owner=v.getOwner();
        if(owner!=null && silenced(owner))event.setCanceled(true);
    }
    @SubscribeEvent public static void attack(LivingAttackEvent event) {
        var source=event.getSource();
        if(!(source.getEntity() instanceof LivingEntity caster) || !silenced(caster))return;
        // Do not cancel direct physical melee, and do not erase a projectile already in flight.
        if(source.getDirectEntity() instanceof Projectile)return;
        String id=source.getMsgId();
        if(source.is(DamageTypeTags.IS_EXPLOSION) || id.equals("magic") || id.equals("indirectMagic")
                || id.equals("sonic_boom") || id.equals("wither") || id.equals("dragonBreath")) event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        var target=event.getEntity();if(!(target.level() instanceof ServerLevel level)||!silenced(target))return;
        if(target instanceof Creeper creeper)resetFuse(creeper);
        if(target.tickCount%4==0) {
            var dust=new DustParticleOptions(new Vector3f(.55F,.18F,.88F),1);
            for(int i=0;i<10;i++) {
                double a=i*Math.PI/5+target.tickCount*.06;
                level.sendParticles(dust,target.getX()+Math.cos(a)*.65,target.getY()+target.getBbHeight()+.15,target.getZ()+Math.sin(a)*.65,1,0,0,0,0);
            }
        }
    }
    /** Legacy save-compatible carrier; learning/casting now goes through the magic stone and wand. */
    public static final class SealItem extends Item {
        public SealItem(){super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));}
        @Override public Component getName(ItemStack stack){return Component.literal("旧版乱魔符印（已停用）");}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.literal("§7按V打开魔法石 → 独立 → 学习乱魔"));
            lines.add(Component.literal("§8保留此旧物品仅为兼容旧存档，无法施放。"));
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            ItemStack stack=player.getItemInHand(hand);
            if(level.isClientSide)return InteractionResultHolder.success(stack);
            player.displayClientMessage(Component.literal("§d乱魔已移入魔法石：按V → 独立 → 学习后用法杖施放。"),true);
            return InteractionResultHolder.fail(stack);
        }
    }
}
