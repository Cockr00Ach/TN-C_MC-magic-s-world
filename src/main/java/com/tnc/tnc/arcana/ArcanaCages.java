package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import net.minecraft.server.level.*;
import net.minecraft.nbt.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.UUID;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class ArcanaCages {
    public static final class Ledger extends SavedData {
        CompoundTag captures=new CompoundTag();
        static Ledger get(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(t->{var d=new Ledger();d.captures=t.getCompound("Captures");return d;},Ledger::new,"tnc_arcana_captures");}
        @Override public CompoundTag save(CompoundTag t){t.put("Captures",captures);return t;}
    }
    public static boolean capture(ServerPlayer p,LivingEntity entity,ItemStack cage){var t=entity.getPersistentData();if(!entity.isAlive()||entity.isRemoved()||entity.level()!=p.level()||cage.isEmpty()||!cage.is(RouteContent.item("ecology_restraint_cage"))||com.tnc.tnc.home.TownProtection.denied(p,entity.blockPosition())||!p.isAlive()||p.isSpectator()||com.tnc.tnc.combat.DownedCombat.isDowned(p)||com.tnc.tnc.dungeon.DungeonService.recovering(p)||t.hasUUID("CageOwner")||!(entity instanceof Monster)||!t.hasUUID("CaptureOwner")||!p.getUUID().equals(t.getUUID("CaptureOwner"))||t.getLong("CaptureUntil")<=p.level().getGameTime()||cage.hasTag()&&cage.getTag().contains("Captured"))return false;
        if(entity instanceof com.tnc.tnc.dungeon.ecology.EcologyMob ecology&&(!ecology.canReceivePotion(p)||entity.getHealth()>entity.getMaxHealth()*.25f))return false;
        var tag=new CompoundTag();if(!entity.save(tag))return false;
        if(entity instanceof com.tnc.tnc.dungeon.DungeonMob mob)com.tnc.tnc.dungeon.DungeonService.killed(mob);
        UUID token=UUID.randomUUID();var record=new CompoundTag();record.putUUID("Owner",p.getUUID());record.put("Entity",tag);record.putBoolean("Deployed",false);var ledger=Ledger.get(p.serverLevel());ledger.captures.put(token.toString(),record);ledger.setDirty();
        var packed=new ItemStack(RouteContent.item("ecology_restraint_cage"));var data=new CompoundTag();data.putUUID("Token",token);packed.getOrCreateTag().put("Captured",data);cage.shrink(1);if(!p.getInventory().add(packed))p.drop(packed,false);entity.discard();return true;
    }
    public static void placed(ServerPlayer p,RouteNodeEntity cage,ItemStack item){if(cage.kind()!=RouteKind.RESTRAINT||!item.hasTag()||!item.getTag().contains("Captured"))return;var tag=item.getTag().getCompound("Captured");if(!tag.hasUUID("Token"))return;var record=Ledger.get(p.serverLevel()).captures.getCompound(tag.getUUID("Token").toString());if(record.isEmpty()||record.getBoolean("Deployed")||!p.getUUID().equals(record.getUUID("Owner")))return;cage.getPersistentData().putUUID("SpecimenToken",tag.getUUID("Token"));cage.setChanged();}
    public static void interact(ServerPlayer p,RouteNodeEntity cage,ItemStack held){if(held.is(net.minecraft.world.item.Items.SUGAR)){cage.getPersistentData().putInt("SugarJumps",Math.min(100,cage.getPersistentData().getInt("SugarJumps")+10));held.shrink(1);}}
    public static boolean bound(RouteNodeEntity cage,Mob mob){var t=mob.getPersistentData();return t.hasUUID("CageOwner")&&cage.owner!=null&&cage.owner.equals(t.getUUID("CageOwner"))&&t.getLong("CagePos")==cage.getBlockPos().asLong();}
    public static void tick(ServerLevel l,RouteNodeEntity n){if((n.kind()==RouteKind.HOOD||n.kind()==RouteKind.PRESSURE_PLATE)&&!ArcanaRealms.powered(l,n.getBlockPos())){n.status="异界无魔荒漠 · 采集需要绿洲";return;}var t=n.getPersistentData();long now=l.getGameTime();
        if(n.kind()==RouteKind.RESTRAINT){
            if(t.hasUUID("SpecimenToken")&&!t.hasUUID("Captive")){var ledger=Ledger.get(l);var record=ledger.captures.getCompound(t.getUUID("SpecimenToken").toString());if(!record.getBoolean("Deployed")&&n.owner.equals(record.getUUID("Owner"))){var copy=record.getCompound("Entity").copy();var raw=EntityType.loadEntityRecursive(copy,l,e->e);if(raw instanceof Mob mob&&l.getEntity(mob.getUUID())==null){mob.setPos(n.getBlockPos().getX()+.5,n.getBlockPos().getY()+.8,n.getBlockPos().getZ()+.5);mob.getPersistentData().remove("CaptureUntil");mob.getPersistentData().remove("CaptureOwner");mob.getPersistentData().remove("ArcanaScale");for(String k:new String[]{"DungeonRun","DungeonFloor","DungeonRoom","DungeonRoomOrigin"})mob.getPersistentData().remove(k);if(mob instanceof com.tnc.tnc.dungeon.ecology.EcologyMob ecology)ecology.prepareForCage();mob.refreshDimensions();mob.setPersistenceRequired();if(l.addFreshEntity(mob)){record.putBoolean("Deployed",true);ledger.setDirty();t.putUUID("Captive",mob.getUUID());mob.getPersistentData().putLong("CagePos",n.getBlockPos().asLong());mob.getPersistentData().putUUID("CageOwner",n.owner);mob.setInvulnerable(true);}}}}
            if(!t.hasUUID("Captive")){n.status="先用缩水捕捉魔药标记低血量魔物，再手持空笼右键捕获";return;}
            var captive=l.getEntity(t.getUUID("Captive"));if(!(captive instanceof Mob mob)){n.status="等待实际魔物加载";return;}
            if(!bound(n,mob)){t.remove("Captive");t.remove("SpecimenToken");n.status="原魔物已离开此笼";return;}
            if(n.spend(3)){t.putInt("Unpowered",0);n.status="拘束维持中 · 3M/s";}else{int missing=t.getInt("Unpowered")+20;t.putInt("Unpowered",missing);n.status="断供警告 · "+Math.max(0,200-missing)/20+"秒后越狱";if(missing>=200){release(mob);t.remove("Captive");t.remove("SpecimenToken");t.putBoolean("Broken",true);n.status="拘束已破 · 魔物越狱";}}
        }else if(n.kind()==RouteKind.PROVOKER){if(now%100!=0||!n.spend(5))return;boolean water=false;for(var d:Direction.values())water|=l.getFluidState(n.getBlockPos().relative(d)).is(net.minecraft.tags.FluidTags.WATER);if(!water)return;
            for(var blaze:l.getEntitiesOfClass(Blaze.class,new AABB(n.getBlockPos()).inflate(6)))if(blaze.getPersistentData().hasUUID("CageOwner")&&n.owner.equals(blaze.getPersistentData().getUUID("CageOwner"))){var p=l.getServer().getPlayerList().getPlayer(n.owner);if(p!=null)blaze.setTarget(p);blaze.getPersistentData().putLong("ProvokedUntil",now+120);l.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,blaze.getX(),blaze.getY()+1,blaze.getZ(),15,.5,.5,.5,.03);}
        }else if(n.kind()==RouteKind.HOOD){for(var pos:RouteNetwork.positions(l)){if(!l.hasChunkAt(pos)||pos.distToCenterSqr(Vec3.atCenterOf(n.getBlockPos()))>36||!(l.getBlockEntity(pos) instanceof RouteNodeEntity cage)||cage.kind()!=RouteKind.RESTRAINT||!n.owner.equals(cage.owner))continue;var ct=cage.getPersistentData();if(!ct.hasUUID("Captive")||!(l.getEntity(ct.getUUID("Captive")) instanceof Mob mob)||ct.getInt("Unpowered")>0)continue;
            int produced=0;String aspect="fire";
            if(mob instanceof com.tnc.tnc.dungeon.ecology.SourceCrystalGrub grub&&mob.isAlive()){produced=grub.batteryRate();aspect=grub.batteryElement();}
            else if(mob instanceof Blaze&&mob.getTarget()!=null&&mob.getPersistentData().getLong("ProvokedUntil")>now)produced=24;
            else if(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()).getPath().matches(".*(shadow|umbral|demon|ghost).*")){
                boolean light=false;for(var d:Direction.values())if(l.getBlockEntity(n.getBlockPos().relative(d)) instanceof RouteNodeEntity mirror&&RouteMirrorLinks.mirror(mirror)&&l.isDay()&&l.canSeeSky(mirror.getBlockPos().above()))light=true;
                if(light){produced=12;aspect="dark";if(now%400==0&&cage.inventory.getItem(4).isEmpty())cage.inventory.setItem(4,new ItemStack(ArcanaContent.item("shadow_mana_crystal")));}
            }
            if(!bound(cage,mob))continue;if(produced>0&&(!mob.getPersistentData().contains("LastHeatDraw")||mob.getPersistentData().getLong("LastHeatDraw")!=now)){int accepted=n.receive(produced);if(accepted>0){mob.getPersistentData().putLong("LastHeatDraw",now);ArcanaEnergy.credit(n,aspect,accepted);com.tnc.tnc.mana.sight.ManaSightServer.record(l,mob.getEyePosition(),Vec3.atCenterOf(n.getBlockPos()),accepted,aspect.equals("fire")?0xff974e:aspect.equals("astral")?0x76f4dd:0x9782df,4);}}
        }}
    }
    public static void release(Mob mob){mob.getPersistentData().remove("CagePos");mob.getPersistentData().remove("CageOwner");mob.setInvulnerable(false);mob.setNoAi(false);}
    @SubscribeEvent public static void living(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event){var mob=event.getEntity();if(!(mob instanceof Mob)||!(mob.level() instanceof ServerLevel l)||!mob.getPersistentData().hasUUID("CageOwner"))return;var d=mob.getPersistentData();var pos=BlockPos.of(d.getLong("CagePos"));if(!l.hasChunkAt(pos))return;if(!(l.getBlockEntity(pos) instanceof RouteNodeEntity cage)||cage.kind()!=RouteKind.RESTRAINT){release((Mob)mob);return;}
        if(mob.getX()<pos.getX()||mob.getX()>pos.getX()+1||mob.getZ()<pos.getZ()||mob.getZ()>pos.getZ()+1)mob.setPos(pos.getX()+.5,Math.max(pos.getY()+.2,Math.min(pos.getY()+2,mob.getY())),pos.getZ()+.5);
        if(mob.getY()<pos.getY()||mob.getY()>pos.getY()+2.5)mob.setPos(mob.getX(),pos.getY()+.8,mob.getZ());
        if(mob instanceof Slime slime){boolean landed=mob.onGround()&&!d.getBoolean("WasGround");d.putBoolean("WasGround",mob.onGround());if(landed){var below=pos.below();if(l.getBlockEntity(below) instanceof RouteNodeEntity plate&&plate.kind()==RouteKind.PRESSURE_PLATE&&cage.owner.equals(plate.owner)){int accepted=plate.receive(20);ArcanaEnergy.credit(plate,"earth",accepted);com.tnc.tnc.mana.sight.ManaSightServer.emission(l,below,accepted);}}if(mob.onGround()&&mob.tickCount%20==0){double speed=cage.getPersistentData().getInt("SugarJumps")>0?.55:.38;mob.setDeltaMovement(0,speed,0);if(speed>.4)cage.getPersistentData().putInt("SugarJumps",cage.getPersistentData().getInt("SugarJumps")-1);}}
    }
    @SubscribeEvent public static void fireball(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(!e.getLevel().isClientSide&&e.getEntity() instanceof net.minecraft.world.entity.projectile.SmallFireball f&&f.getOwner() instanceof Mob m&&m.getPersistentData().hasUUID("CageOwner"))e.setCanceled(true);}
    @SubscribeEvent public static void captiveDamage(net.minecraftforge.event.entity.living.LivingAttackEvent e){var source=e.getSource().getEntity();if(source instanceof Mob m&&m.getPersistentData().hasUUID("CageOwner"))e.setCanceled(true);}
}
