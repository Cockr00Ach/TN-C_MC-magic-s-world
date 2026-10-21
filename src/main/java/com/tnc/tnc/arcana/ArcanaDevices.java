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
    public static void tick(ServerLevel l,RouteNodeEntity n){if(!advanced(n))return;long now=l.getGameTime();var t=n.getPersistentData();ArcanaSky.tick(l);
        switch(n.kind()){
            case ABYSS_TOWER->{ArcanaAmbientIntake.collect(l,n);tower(l,n);}
            case SOLAR,LUNAR->{boolean aurora=ArcanaSky.aurora(l);boolean valid=l.canSeeSky(n.getBlockPos().above())&&(n.kind()==RouteKind.SOLAR?l.isDay():!l.isDay());int made=valid?(int)Math.round((n.kind()==RouteKind.SOLAR?24:16*RitualRules.moonFactor(l.getMoonPhase()))*(aurora?6:1)):0;
                if(n.kind()==RouteKind.SOLAR&&n.getBlockPos().getY()<l.getSeaLevel()+16)made=0;
                int accepted=n.receive(made);String aspect=aurora?"astral":n.kind()==RouteKind.SOLAR?"solar":"lunar";ArcanaEnergy.credit(n,aspect,accepted);n.status=valid?aspect+" · "+accepted+" M/s":"需要露天、正确昼夜；日镜须高于海平面16格";
                if(accepted>0)com.tnc.tnc.mana.sight.ManaSightServer.record(l,Vec3.atCenterOf(n.getBlockPos()).add(0,48,0),Vec3.atCenterOf(n.getBlockPos()),accepted,aurora?0xc6a6ff:n.kind()==RouteKind.SOLAR?0xffd46c:0xb6e8ff,4);
            }
            case OBSERVATORY->n.status=ArcanaSky.forecast(l)+(ArcanaSky.aurora(l)?" · 极光正在流淌":"");
            case GEOTHERMAL->n.status=t.getLong("BathUntil")>now&&n.mana>0?"浴盐已溶 · 周围源水排毒中":"投入舒缓浴盐并接魔力，周围铺源水";
            case SPRAYER->spray(l,n);
            case NURTURE->nurture(l,n);
            case MEAL_PLATE->n.status=ArcanaMeals.state(n);
            case RESTRAINT,HOOD,PROVOKER,PRESSURE_PLATE->ArcanaCages.tick(l,n);
            case SKY_LAUNCHER,SKY_RELAY->ArcanaHighway.tick(l,n);
            case RELIEF->n.status="潜行空手开启泄压：抽取三格内聚能塔5000M，过载下降20点";
            case SINGULARITY->{ArcanaAmbientIntake.collect(l,n);long balance=Math.max(0,t.getLong("SingularityBalance"));if(n.mana>1000000){int stored=n.extract(n.mana-1000000);balance=balance>Long.MAX_VALUE-stored?Long.MAX_VALUE:balance+stored;}if(n.mana<500000&&balance>0){int take=(int)Math.min(balance,1000000-n.mana);n.receive(take);balance-=take;}t.putLong("SingularityBalance",balance);n.status="奇点实存 "+balance+" + 缓冲 "+n.mana+" M · 直收 "+t.getInt("AmbientRate")+"/秒";}
            default->{}
        }
        n.setChanged();l.sendBlockUpdated(n.getBlockPos(),n.getBlockState(),n.getBlockState(),3);
    }
    public static boolean interact(ServerPlayer p,RouteNodeEntity n,InteractionHand hand,BlockHitResult hit){if(!advanced(n))return false;if(RouteMirrorLinks.mirror(n))return false;var held=p.getItemInHand(hand);var t=n.getPersistentData();long now=p.level().getGameTime();
        if(ArcanaHighway.ring(n)&&held.is(ArcanaContent.item("symbiosis_marker"))){ArcanaHighway.select(p,n,held);return true;}
        if(ArcanaHighway.ring(n)&&p.isShiftKeyDown()&&held.isEmpty()){t.remove("SkyTarget");t.remove("SkyPath");n.setChanged();return true;}
        if(n.kind()==RouteKind.GEOTHERMAL&&held.is(ArcanaContent.item("soothing_bath_salt"))){t.putLong("BathUntil",now+24000);ArcanaItem.consume(p,held);}
        else if(n.kind()==RouteKind.RELIEF&&p.isShiftKeyDown()&&held.isEmpty())relieve(p,n);
        else if(n.kind()==RouteKind.MEAL_PLATE){ArcanaMeals.interact(p,n,hand,hit);return true;}
        else if(n.kind()==RouteKind.NURTURE){if(n.inventory.getItem(0).isEmpty()&&held.getItem() instanceof com.tnc.tnc.adventure.ElementWands.Wand w&&w.design.tier()>=3)n.inventory.setItem(0,held.split(1));else if(!held.isEmpty()&&n.inventory.getItem(1).isEmpty())n.inventory.setItem(1,held.split(1));else if(p.isShiftKeyDown()&&held.isEmpty())recover(p,n);}
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
    private static void tower(ServerLevel l,RouteNodeEntity n){var t=n.getPersistentData();long now=l.getGameTime();boolean formed=formed(l,n.getBlockPos());if(formed!=t.getBoolean("ShellFormed")){t.putBoolean("ShellFormed",formed);for(var at:BlockPos.betweenClosed(n.getBlockPos().offset(-2,-1,-2),n.getBlockPos().offset(2,3,2)))if(l.hasChunkAt(at)&&ArcanaTowerPorts.socket(n.getBlockPos(),at)&&l.getBlockState(at).getBlock() instanceof RouteWireBlock wire)wire.refresh(l,at);}
        if(t.hasUUID("Devourer")&&ArcanaDisasters.get(l).dead(t.getUUID("Devourer"))){t.remove("Devourer");t.putDouble("overload_level",40);}
        double pressure=RitualRules.pressure(t.getDouble("overload_level"),n.inRate+t.getInt("AmbientRate"),formed);t.putDouble("overload_level",pressure);int stage=RitualRules.pressureStage(pressure);t.putInt("OverloadStage",stage);n.status=!formed?"未成型 · 3×3周边三层聚能砖，中心上方两格留空":"过载 "+(int)pressure+"% · 进流 "+(n.inRate+t.getInt("AmbientRate"))+"/320M/s · 直收 "+t.getInt("AmbientRate")+"M/s";
        if(stage==1||stage==2)boostConnected(l,n,now+30);
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
    private static void nurture(ServerLevel l,RouteNodeEntity n){var wand=n.inventory.getItem(0);var food=n.inventory.getItem(1);if(!(wand.getItem() instanceof com.tnc.tnc.adventure.ElementWands.Wand w)||w.design.tier()<3){n.status="插入王级及以上法杖，投入高阶魔药或Boss核心";return;}
        String type=w.design.element().id();int mana=ArcanaEnergy.amount(n,type)+ArcanaEnergy.amount(n,"lunar")+ArcanaEnergy.amount(n,"astral");if(mana<100){n.status="需要100同属性/太阴/星界魔力";return;}
        boolean highPotion=food.getItem() instanceof com.tnc.tnc.alchemy.ElixirItem&&food.hasTag()&&food.getTag().getInt("RitualQuality")==2;
        if(!highPotion&&!food.is(Items.NETHER_STAR)&&!food.is(Items.DRAGON_BREATH)&&!food.is(ArcanaContent.item("collapsed_mana_singularity")))return;
        String use=ArcanaEnergy.amount(n,type)>=100?type:ArcanaEnergy.amount(n,"lunar")>=100?"lunar":"astral";if(!ArcanaEnergy.spend(n,use,100))return;
        var t=wand.getOrCreateTag();int xp=t.getInt("SpiritGrowth")+(highPotion?20:100);t.putInt("SpiritGrowth",xp);t.putUUID("SpiritOwner",n.owner);t.putString("SpiritElement",type);if(xp>=100&&!t.contains("SpiritAffix"))t.putString("SpiritAffix",highPotion&&((com.tnc.tnc.alchemy.ElixirItem)food.getItem()).kind.effectFamily()==0?"healing_wave":"meteor_echo");var rem=food.is(Items.DRAGON_BREATH)||highPotion?new ItemStack(Items.GLASS_BOTTLE):food.getCraftingRemainingItem();food.shrink(1);if(!rem.isEmpty())net.minecraft.world.Containers.dropItemStack(l,n.getBlockPos().getX()+.5,n.getBlockPos().getY()+1,n.getBlockPos().getZ()+.5,rem);n.status="器灵温养 "+xp+" · "+t.getString("SpiritAffix");
    }
    private ArcanaDevices(){}
}
