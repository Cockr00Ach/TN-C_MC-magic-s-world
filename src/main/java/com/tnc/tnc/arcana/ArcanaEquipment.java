package com.tnc.tnc.arcana;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.Set;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class ArcanaEquipment {
    public static boolean tailwind(ServerPlayer p){var s=p.getItemBySlot(EquipmentSlot.CHEST);return !s.isEmpty()&&s.hasTag()&&s.getTag().getBoolean("TailwindSoles");}
    public static void tick(ServerPlayer p){var worn=p.getItemBySlot(EquipmentSlot.CHEST);var magic=com.tnc.tnc.magic.MagicStone.getOrNull(p);if(!(worn.getItem() instanceof ArcanaContent.LivingArmor armor)||magic==null)return;
        var t=worn.getOrCreateTag();if(!t.hasUUID("RootedOwner")){t.putUUID("RootedOwner",p.getUUID());p.hurt(p.damageSources().magic(),1);p.playSound(net.minecraft.sounds.SoundEvents.AZALEA_LEAVES_PLACE,1,.7f);}
        if(!p.getUUID().equals(t.getUUID("RootedOwner")))return;
        if(magic.spendMana(1))t.putInt("PlantMana",Math.min(200,t.getInt("PlantMana")+1));else if(p.getFoodData().getFoodLevel()>0)p.getFoodData().addExhaustion(1);else return;
        int stored=t.getInt("PlantMana");
        if(armor.id.equals("thorn_tyrant_vine")&&stored>=6&&p.tickCount%40==0){int struck=0;for(var enemy:p.serverLevel().getEntitiesOfClass(Monster.class,p.getBoundingBox().inflate(4),e->e.isAlive()&&!(e instanceof DimensionalSpirit)&&!e.getPersistentData().hasUUID("CageOwner")&&!p.isAlliedTo(e)&&e!=p.getVehicle())){if(struck++>=3)break;
            if(enemy.hurt(p.damageSources().indirectMagic(p,p),6)){var away=enemy.position().subtract(p.position()).normalize();enemy.push(away.x*.6,.2,away.z*.6);com.tnc.tnc.mana.sight.ManaSightServer.record(p.serverLevel(),p.position().add(0,1.2,0),enemy.getEyePosition(),2,0x68e482,4);}}if(struck>0)t.putInt("PlantMana",stored-6);
        }else if(armor.id.equals("emerald_lifemoss")&&stored>0&&p.getHealth()<p.getMaxHealth()&&p.serverLevel().getBlockState(p.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)){p.heal(.5f);t.putInt("PlantMana",stored-1);}
        else if(armor.id.equals("predator_bud")&&magic.getMana()<magic.getMaxMana()){
            int room=magic.getMaxMana()-magic.getMana();for(var orb:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,p.getBoundingBox().inflate(3))){if(orb.getValue()<=room){magic.addMana(orb.getValue());var saved=new net.minecraft.nbt.CompoundTag();orb.addAdditionalSaveData(saved);int count=Math.max(1,saved.getInt("Count"));if(count>1){saved.putInt("Count",count-1);orb.readAdditionalSaveData(saved);}else orb.discard();break;}}
            for(var drop:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(3))){var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(drop.getItem().getItem()).toString();if(Set.of("minecraft:rotten_flesh","minecraft:bone","minecraft:string","minecraft:gunpowder").contains(id)&&(drop.getOwner()==null||drop.getOwner()==p)&&!com.tnc.tnc.home.TownProtection.denied(p,drop.blockPosition())){drop.getItem().shrink(1);if(drop.getItem().isEmpty())drop.discard();magic.addMana(2);break;}}
        }
        com.tnc.tnc.network.MagicStoneNetwork.syncTo(p);
    }
    @SubscribeEvent public static void protect(net.minecraftforge.event.entity.living.LivingHurtEvent e){if(!(e.getEntity() instanceof ServerPlayer p))return;var s=p.getItemBySlot(EquipmentSlot.CHEST);if(s.getItem() instanceof ArcanaContent.LivingArmor a&&a.id.equals("emerald_lifemoss")&&s.hasTag()&&p.getUUID().equals(s.getTag().hasUUID("RootedOwner")?s.getTag().getUUID("RootedOwner"):null)&&s.getTag().getInt("PlantMana")>=10){s.getTag().putInt("PlantMana",s.getTag().getInt("PlantMana")-10);e.setAmount(Math.max(0,e.getAmount()-6));}}
    @SubscribeEvent public static void spiritProc(net.minecraftforge.event.entity.ProjectileImpactEvent e){var projectile=e.getProjectile();
        var cooling=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(projectile.getType());
        if(projectile.level() instanceof net.minecraft.server.level.ServerLevel l&&e.getRayTraceResult() instanceof net.minecraft.world.phys.BlockHitResult hit&&(cooling.getPath().contains("ice")||cooling.getPath().contains("frost")))com.tnc.tnc.alchemy.AlchemyEntity.coolNear(l,hit.getBlockPos(),projectile.getOwner());if(!net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(projectile.getType()).getNamespace().equals("tnc"))return;if(!(projectile.getOwner() instanceof ServerPlayer p)||projectile.getPersistentData().getBoolean("SpiritProc"))return;
        var wand=p.getMainHandItem();if(!(wand.getItem() instanceof com.tnc.tnc.adventure.ElementWands.Wand)||!wand.hasTag())return;var t=wand.getTag();if(!t.hasUUID("SpiritOwner")||!p.getUUID().equals(t.getUUID("SpiritOwner"))||!t.contains("SpiritAffix"))return;
        long now=ArcanaPhysiology.now(p);var state=ArcanaPhysiology.data(p);if(state.getLong("SpiritProcUntil")>now||p.getRandom().nextFloat()>=.2)return;var mana=com.tnc.tnc.magic.MagicStone.getOrNull(p);if(mana==null||!mana.spendMana(15))return;
        projectile.getPersistentData().putBoolean("SpiritProc",true);state.putLong("SpiritProcUntil",now+200);
        if(t.getString("SpiritAffix").equals("meteor_echo"))com.tnc.tnc.magic.fire.TNMeteorFallEntity.cast(p.serverLevel(),p,e.getRayTraceResult().getLocation());
        else for(var ally:p.serverLevel().getEntitiesOfClass(ServerPlayer.class,p.getBoundingBox().inflate(6)))if(ally==p||p.isAlliedTo(ally))ally.heal(4);
        com.tnc.tnc.network.MagicStoneNetwork.syncTo(p);com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();
    }
    @SubscribeEvent public static void fall(net.minecraftforge.event.entity.living.LivingFallEvent e){if(e.getEntity() instanceof ServerPlayer p&&(tailwind(p)||ArcanaPhysiology.data(p).getLong("SkyGraceUntil")>ArcanaPhysiology.now(p)))e.setDamageMultiplier(0);}
    private ArcanaEquipment(){}
}
