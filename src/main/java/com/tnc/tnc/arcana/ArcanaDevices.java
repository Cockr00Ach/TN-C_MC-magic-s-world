package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.*;
import java.util.*;
/** Advanced nodes reuse the existing owner-bound network and material inventory. */
public final class ArcanaDevices {
    public static boolean advanced(RouteNodeEntity n){return n.kind().ordinal()>=RouteKind.ABYSS_TOWER.ordinal();}
    public static boolean formed(ServerLevel l,BlockPos p){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<3;y++){
        if(x==0&&z==0)continue;var q=p.offset(x,y,z);if(!l.hasChunkAt(q)||!l.getBlockState(q).is(ArcanaContent.TOWER_BRICK))return false;
    }return l.getBlockState(p.above()).isAir()&&l.getBlockState(p.above(2)).isAir();}
    public static boolean jammed(ServerLevel l,UUID owner,BlockPos pos){var p=owner==null?null:l.getServer().getPlayerList().getPlayer(owner);return p!=null&&p.serverLevel()==l&&p.distanceToSqr(Vec3.atCenterOf(pos))<=25&&ArcanaPhysiology.toxicity(p)>80&&l.getGameTime()%100<40;}
    public static void tick(ServerLevel l,RouteNodeEntity n){if(!advanced(n))return;long now=l.getGameTime();var t=n.getPersistentData();ArcanaSky.tick(l);if(ArcanaTechnology.device(n)){ArcanaTechnology.tick(l,n);return;}
        switch(n.kind()){
            case ABYSS_TOWER->{ArcanaAmbientIntake.collect(l,n);tower(l,n);}
            case SOLAR,LUNAR->{boolean aurora=ArcanaSky.aurora(l);boolean valid=ArcanaRealms.powered(l,n.getBlockPos())&&l.canSeeSky(n.getBlockPos().above())&&(n.kind()==RouteKind.SOLAR?l.isDay():!l.isDay());int made=valid?(int)Math.round((n.kind()==RouteKind.SOLAR?24:16*RitualRules.moonFactor(l.getMoonPhase()))*(aurora?6:1)):0;
                if(n.kind()==RouteKind.SOLAR&&n.getBlockPos().getY()<l.getSeaLevel()+16)made=0;
                int accepted=n.receive(made);String aspect=aurora?"astral":n.kind()==RouteKind.SOLAR?"solar":"lunar";ArcanaEnergy.credit(n,aspect,accepted);n.status=valid?aspect+" · "+accepted+" M/s":"需要露天、正确昼夜；日镜须高于海平面16格";
                t.putInt("CelestialYield",accepted);t.putLong("CelestialFlowUntil",accepted>0?now+25:0);
            }
            case OBSERVATORY->n.status="空手改写天象（塔300000M） · "+ArcanaSky.forecast(l)+(ArcanaSky.aurora(l)?" · 极光正在流淌":"");
            case GEOTHERMAL->n.status=t.getLong("BathUntil")>now&&n.mana>0?"浴盐已溶 · 周围源水排毒中":"投入舒缓浴盐并接魔力，周围铺源水";
            case SPRAYER->spray(l,n);
            case NURTURE->nurture(l,n);
            case MEAL_PLATE->n.status=ArcanaMeals.state(n);
            case RESTRAINT,HOOD,PROVOKER,PRESSURE_PLATE->ArcanaCages.tick(l,n);
            case SKY_LAUNCHER,SKY_RELAY->ArcanaHighway.tick(l,n);
            case RELIEF->n.status="潜行空手开启泄压：抽取三格内聚能塔5000M，过载下降20点";
            case SINGULARITY->{ArcanaAmbientIntake.collect(l,n);t.remove("SingularityBalance");n.status="奇点实存 "+n.mana+"/2000000000M · 直收 "+t.getInt("AmbientRate")+"/秒";}
            default->{}
        }
        n.setChanged();l.sendBlockUpdated(n.getBlockPos(),n.getBlockState(),n.getBlockState(),3);
    }
    public static boolean interact(ServerPlayer p,RouteNodeEntity n,InteractionHand hand,BlockHitResult hit){if(!advanced(n))return false;if(ArcanaTechnology.device(n))return ArcanaTechnology.interact(p,n,hand);if(RouteMirrorLinks.mirror(n))return false;var held=p.getItemInHand(hand);var t=n.getPersistentData();long now=p.level().getGameTime();
        if(ArcanaHighway.ring(n)&&held.is(ArcanaContent.item("symbiosis_marker"))){ArcanaHighway.select(p,n,held);return true;}
        if(ArcanaHighway.ring(n)&&p.isShiftKeyDown()&&held.isEmpty()){t.remove("SkyTarget");t.remove("SkyPath");n.setChanged();return true;}
        if(n.kind()==RouteKind.OBSERVATORY&&held.isEmpty()&&!p.isShiftKeyDown()){var heart=ArcanaRealms.tower(p.serverLevel(),n.getBlockPos(),p.getUUID(),16);if(heart!=null&&p.serverLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)&&heart.spend(300000)){ArcanaSky.forceTonight(p.serverLevel());com.tnc.tnc.mana.sight.ManaSightServer.record(p.serverLevel(),net.minecraft.world.phys.Vec3.atCenterOf(heart.getBlockPos()),net.minecraft.world.phys.Vec3.atCenterOf(n.getBlockPos()).add(0,80,0),300000,0xb6ffe8,4);n.status="天象改写 · 已消耗300000M";}else n.status="16格内成型自有深渊塔储量需≥300000M";}
        else if(n.kind()==RouteKind.GEOTHERMAL&&held.is(ArcanaContent.item("soothing_bath_salt"))){t.putLong("BathUntil",now+24000);ArcanaItem.consume(p,held);}
        else if(n.kind()==RouteKind.RELIEF&&p.isShiftKeyDown()&&held.isEmpty())relieve(p,n);
        else if(n.kind()==RouteKind.MEAL_PLATE){ArcanaMeals.interact(p,n,hand,hit);return true;}
        else if(n.kind()==RouteKind.NURTURE){if(breakthrough(p,n,held)){}else if(n.inventory.getItem(0).isEmpty()&&held.getItem() instanceof com.tnc.tnc.adventure.ElementWands.Wand w&&w.design.tier()>=3)n.inventory.setItem(0,held.split(1));else if(p.isShiftKeyDown()&&held.isEmpty())recover(p,n);}
        else if(n.kind()==RouteKind.SPRAYER){if(held.getItem() instanceof ArcanaItem a&&a.id.endsWith("elixir")&&n.inventory.getItem(0).isEmpty())n.inventory.setItem(0,held.split(Math.min(8,held.getCount())));else if(p.isShiftKeyDown()&&held.isEmpty())recover(p,n);}
        else if(n.kind()==RouteKind.RESTRAINT){ArcanaCages.interact(p,n,held);}
        else if(p.isShiftKeyDown()&&held.isEmpty())recover(p,n);
        n.setChanged();p.displayClientMessage(net.minecraft.network.chat.Component.literal(n.kind().name+" · "+n.mana+" M · "+n.status),true);return true;
    }
    private static void recover(ServerPlayer p,RouteNodeEntity n){for(int i=0;i<n.inventory.getContainerSize();i++){var s=n.inventory.removeItemNoUpdate(i);if(!s.isEmpty()&&!p.getInventory().add(s))p.drop(s,false);}}
    public static void bathe(ServerPlayer p){var l=p.serverLevel();for(var pos:RouteNetwork.positions(l)){
        if(pos.distToCenterSqr(p.position())>25||!l.hasChunkAt(pos)||!(l.getBlockEntity(pos) instanceof RouteNodeEntity n)||n.kind()!=RouteKind.GEOTHERMAL||!p.getUUID().equals(n.owner)||n.getPersistentData().getLong("BathUntil")<=l.getGameTime())continue;
        if(l.getFluidState(p.blockPosition()).isSource()&&n.spend(1)){ArcanaPhysiology.data(p).putLong("BathingUntil",ArcanaPhysiology.now(p)+25);ArcanaPhysiology.detox(p,2);p.heal(.1f);com.tnc.tnc.mana.sight.ManaSightServer.record(l,p.position().add(0,.7,0),p.position().add(0,2.5,0),2,0xffd97d,4);}return;
    }}
    private static void tower(ServerLevel l,RouteNodeEntity n){var t=n.getPersistentData();long now=l.getGameTime();boolean formed=formed(l,n.getBlockPos());ArcanaTowerBrick.shell(l,n.getBlockPos(),formed);if(formed!=t.getBoolean("ShellFormed")){t.putBoolean("ShellFormed",formed);t.putLong("TowerFormedAt",now);ArcanaTowerBrick.shell(l,n.getBlockPos(),formed);if(formed)l.playSound(null,n.getBlockPos(),net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,net.minecraft.sounds.SoundSource.BLOCKS,1,.65f);for(var at:BlockPos.betweenClosed(n.getBlockPos().offset(-2,-1,-2),n.getBlockPos().offset(2,3,2)))if(l.hasChunkAt(at)&&ArcanaTowerPorts.socket(n.getBlockPos(),at)&&l.getBlockState(at).getBlock() instanceof RouteWireBlock wire)wire.refresh(l,at);}
        if(t.hasUUID("Devourer")&&ArcanaDisasters.get(l).dead(t.getUUID("Devourer"))){t.remove("Devourer");t.putDouble("overload_level",40);}
        double pressure=RitualRules.pressure(t.getDouble("overload_level"),n.inRate+t.getInt("AmbientRate"),formed);t.putDouble("overload_level",pressure);int stage=RitualRules.pressureStage(pressure);t.putInt("OverloadStage",stage);n.status=!formed?"未成型 · 3×3周边三层聚能砖，中心上方两格留空":"过载 "+(int)pressure+"% · 进流 "+(n.inRate+t.getInt("AmbientRate"))+"/320M/s · 直收 "+t.getInt("AmbientRate")+"M/s";
        if(stage==1)boostConnected(l,n,now+30);
        if(stage>=2&&formed){if(now%100==0){for(var p:l.players())if(p.distanceToSqr(Vec3.atCenterOf(n.getBlockPos()))<1024){p.playNotifySound(net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT,net.minecraft.sounds.SoundSource.BLOCKS,.6f,.7f);if(com.tnc.tnc.mana.sight.ManaSightServer.enabled(p)){var state=new CompoundTag();state.putString("Type","overload");state.putLong("Until",now+100);state.putInt("Stage",stage);ArcanaNetwork.sync(p,state);}}}
            for(int i=0;i<3;i++){var at=n.getBlockPos().offset(l.random.nextInt(65)-32,l.random.nextInt(9)-4,l.random.nextInt(65)-32);if(l.hasChunkAt(at)&&RouteAccess.allowed(l,at,n.owner)&&l.getBlockState(at).is(Blocks.GRASS_BLOCK))l.setBlock(at,ArcanaContent.RED_GRASS.defaultBlockState(),3);}
            for(var animal:l.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,new AABB(n.getBlockPos()).inflate(8)))animal.getPersistentData().putLong("ArcanaRageUntil",now+80);
        }
        if(stage==3&&formed&&t.getLong("DisasterCooldown")<=now&&!t.hasUUID("Devourer")){
            var beast=ArcanaContent.DEVOURER.create(l);if(beast!=null){beast.setPos(n.getBlockPos().getX()+.5,n.getBlockPos().getY()+5,n.getBlockPos().getZ()+.5);beast.owner=n.owner;if(l.addFreshEntity(beast)){t.putUUID("Devourer",beast.getUUID());t.putLong("DisasterCooldown",now+24000);for(var p:l.players())if(p.distanceToSqr(beast)<256){Vec3 away=p.position().subtract(Vec3.atCenterOf(n.getBlockPos())).normalize();p.push(away.x*1.5,.8,away.z*1.5);p.hurtMarked=true;p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS,200,1));p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION,100,0));}}}
        }
        if(t.hasUUID("Devourer")&&l.getEntity(t.getUUID("Devourer")) instanceof VoidDevourer d&&!d.isAlive())t.remove("Devourer");
    }
    public static void boostConnected(ServerLevel l,RouteNodeEntity source,long until){var seen=new HashSet<BlockPos>();var q=new ArrayDeque<BlockPos>();q.add(source.getBlockPos());
        while(!q.isEmpty()&&seen.size()<256){var p=q.remove();if(!seen.add(p)||!l.hasChunkAt(p))continue;var raw=l.getBlockEntity(p);
            if(raw instanceof RouteNodeEntity n&&source.owner.equals(n.owner)){n.getPersistentData().putLong("OverloadBoostUntil",until);if(RouteMirrorLinks.mirror(n)){var peer=RouteMirrorLinks.remote(l,n);if(peer!=null)q.add(peer);}}
            else if(raw instanceof com.tnc.tnc.production.MagicForgeBlockEntity f&&source.owner.equals(f.owner()))f.getPersistentData().putLong("OverloadBoostUntil",until);else continue;
            q.addAll(ArcanaTowerPorts.links(l,p,source.owner));
            for(var side:Direction.values())if(RouteConnections.connects(l,p,p.relative(side))&&RouteAccess.allowed(l,p.relative(side),source.owner))q.add(p.relative(side));
        }
    }
    private static void relieve(ServerPlayer p,RouteNodeEntity valve){for(var at:BlockPos.betweenClosed(valve.getBlockPos().offset(-3,-3,-3),valve.getBlockPos().offset(3,3,3)))if(p.serverLevel().hasChunkAt(at)&&p.serverLevel().getBlockEntity(at) instanceof RouteNodeEntity tower&&tower.kind()==RouteKind.ABYSS_TOWER&&tower.mayUse(p)&&tower.spend(5000)){
        var t=tower.getPersistentData();t.putDouble("overload_level",Math.max(0,t.getDouble("overload_level")-20));tower.setChanged();for(var player:p.serverLevel().players())if(player.distanceToSqr(Vec3.atCenterOf(tower.getBlockPos()))<144){ArcanaPhysiology.detox(player,8);p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,player.getX(),player.getY()+6,player.getZ(),100,2,1,2,.02);}return;}}
    private static void spray(ServerLevel l,RouteNodeEntity n){if(l.getGameTime()%100!=0||n.mana<10)return;var s=n.inventory.getItem(0);var output=n.inventory.getItem(5);if(!(s.getItem() instanceof ArcanaItem a)||!a.id.endsWith("elixir")||!output.isEmpty()&&(!output.is(Items.GLASS_BOTTLE)||output.getCount()>=64))return;
        var owner=l.getServer().getPlayerList().getPlayer(n.owner);if(owner==null||owner.serverLevel()!=l)return;
        for(var p:BlockPos.betweenClosed(n.getBlockPos().offset(-4,-1,-4),n.getBlockPos().offset(4,1,4)))if(ArcanaEcology.applyPlant(owner,p,a.id,s.copyWithCount(1),false)){n.spend(10);s.shrink(1);if(output.isEmpty())n.inventory.setItem(5,new ItemStack(Items.GLASS_BOTTLE));else output.grow(1);n.status="已喷洒一份真实药剂";return;}
    }
    public static String wandElement(RouteNodeEntity n){var w=n.inventory.getItem(0);return w.getItem() instanceof com.tnc.tnc.adventure.ElementWands.Wand item&&item.design.tier()>=3?item.design.element().id():"";}
    private static void nurture(ServerLevel l,RouteNodeEntity n){var wand=n.inventory.getItem(0);String type=wandElement(n);if(type.isEmpty()){n.status="插入王级法杖，只收同属性魔力；无需魔药";return;}
        var t=wand.getOrCreateTag();int growth=Math.min(10000,Math.max(0,t.getInt("SpiritMana")));int take=Math.min(256,Math.min(10000-growth,ArcanaEnergy.amount(n,type)));if(take>0&&ArcanaEnergy.spend(n,type,take)){growth+=take;t.putInt("SpiritMana",growth);t.putUUID("SpiritOwner",n.owner);t.putString("SpiritElement",type);}
        n.status=type+"同源温养 "+growth+"/10000M"+(growth>=10000?" · 手持突破核右键":" · 每秒最多256M");
    }
    public static boolean breakthrough(ServerPlayer p,RouteNodeEntity n,ItemStack core){var wand=n.inventory.getItem(0);String type=wandElement(n);if(type.isEmpty()||wand.getOrCreateTag().getInt("SpiritMana")<10000||wand.getOrCreateTag().contains("SpiritAffix")||!core.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,net.minecraft.resources.ResourceLocation.parse("tnc:wand_breakthrough_cores"))))return false;
        var t=wand.getOrCreateTag();t.putUUID("SpiritOwner",p.getUUID());t.putString("SpiritElement",type);t.putString("SpiritAffix","element_"+type);t.putInt("SpiritGrowth",100);boolean bottle=core.is(Items.DRAGON_BREATH);core.shrink(1);if(bottle&&!p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE)))p.drop(new ItemStack(Items.GLASS_BOTTLE),false);n.status="器灵觉醒 · "+type;n.setChanged();return true;
    }
    private ArcanaDevices(){}
}
