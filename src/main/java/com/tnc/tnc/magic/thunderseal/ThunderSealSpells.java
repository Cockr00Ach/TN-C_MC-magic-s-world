package com.tnc.tnc.magic.thunderseal;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="tnc")
public final class ThunderSealSpells {
 @SubscribeEvent public static void release(net.minecraftforge.event.entity.living.MobEffectEvent.Added e){if(!e.getEntity().level().isClientSide&&e.getEffectInstance().getEffect() instanceof ThunderSealContent.Marker m)cast(e.getEntity(),m.tier);}
 public static ThunderSealEntity cast(LivingEntity owner,int tier){
  if(!(owner.level() instanceof net.minecraft.server.level.ServerLevel level)||!owner.isAlive())return null;
  if(level.getEntitiesOfClass(ThunderSealEntity.class,owner.getBoundingBox().inflate(160),s->s.owner()==owner).size()>=2)return null;
  var target=com.tnc.tnc.magic.wind.WindChainSpells.aim(owner,72);
  Vec3 eye=owner.getEyePosition(),end=eye.add(owner.getLookAngle().scale(48));
  var hit=level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,owner));
  Vec3 point=target!=null?target.position():hit.getLocation();
  if(target==null&&hit.getType()==HitResult.Type.MISS)point=owner.position().add(owner.getLookAngle().multiply(1,0,1).normalize().scale(16));
  var pos=net.minecraft.core.BlockPos.containing(point);if(!level.hasChunkAt(pos))return null;
  if(target==null){var floor=level.clip(new net.minecraft.world.level.ClipContext(point.add(0,3,0),point.add(0,-24,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,owner));if(floor.getType()==HitResult.Type.BLOCK)point=floor.getLocation();}
  var seal=ThunderSealContent.SEAL.create(level);if(seal==null)return null;seal.configure(owner,tier,point,target);return level.addFreshEntity(seal)?seal:null;
 }
 @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent e){e.getDispatcher().register(net.minecraft.commands.Commands.literal("tnc").then(net.minecraft.commands.Commands.literal("thunderseal").requires(s->s.hasPermission(2)).then(net.minecraft.commands.Commands.argument("tier",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,5)).executes(c->cast(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"tier"))==null?0:1))));}
}
