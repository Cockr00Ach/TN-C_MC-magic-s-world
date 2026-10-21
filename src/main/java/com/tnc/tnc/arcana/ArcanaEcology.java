package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.life.botanical.*;
import com.tnc.tnc.life.pasture.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class ArcanaEcology {
    public static UUID owner(BlockEntity be){return be instanceof RoutePlantEntity r?r.owner:be instanceof BotanicalPlantEntity b?b.owner:null;}
    public static boolean mature(BlockEntity be){return be instanceof RoutePlantEntity r?r.growth>=18000:be instanceof BotanicalPlantEntity b&&b.growth>=b.species().firstTicks;}
    public static double factor(ServerLevel l,BlockEntity be){var t=be.getPersistentData();double boost=t.getLong("OverdraftUntil")>l.getGameTime()?5:1;return boost*(t.getLong("ResonanceUntil")>l.getGameTime()?1.2:1);}
    public static boolean applyPlant(ServerPlayer p,BlockPos pos,String potion,ItemStack held){return applyPlant(p,pos,potion,held,true);}
    public static boolean applyPlant(ServerPlayer p,BlockPos pos,String potion,ItemStack held,boolean consume){var l=p.serverLevel();var be=l.getBlockEntity(pos);if(be==null||owner(be)==null||!owner(be).equals(p.getUUID())||!RouteAccess.allowed(l,pos,p.getUUID()))return false;var t=be.getPersistentData();long now=l.getGameTime();
        switch(potion){
            case "overdraft_elixir"->{if(t.getLong("OverdraftUntil")>0||!mature(be))return false;t.putLong("OverdraftUntil",now+2400);}
            case "gigantism_elixir","miniature_elixir"->{t.putFloat("ArcanaScale",potion.equals("gigantism_elixir")?3:.5f);}
            case "flame_twist_elixir"->{
                boolean aquatic=be instanceof BotanicalPlantEntity b&&Set.of(BotanicalSpecies.MIRROR_LOTUS,BotanicalSpecies.FROST_CHIME).contains(b.species())||be instanceof RoutePlantEntity r&&r.kind()==NewPlantKind.DEW;
                if(!aquatic||t.getBoolean("MutationRolled"))return false;t.putBoolean("MutationRolled",true);be.setChanged();
                boolean success=l.random.nextBoolean();if(success){l.setBlock(pos,RouteContent.PLANTS.get("steam_lotus").get().defaultBlockState(),3);var next=(RoutePlantEntity)l.getBlockEntity(pos);next.owner=p.getUUID();next.growth=18000;next.getPersistentData().putBoolean("MutationRolled",true);l.setBlock(pos,next.getBlockState().setValue(RoutePlantBlock.AGE,3),3);next.setChanged();}
                else{l.destroyBlock(pos,false);net.minecraft.world.Containers.dropItemStack(l,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,new ItemStack(ArcanaContent.item("mana_ash")));}
            }
            default->{return false;}
        }
        if(consume)ArcanaItem.consume(p,held);be.setChanged();l.sendBlockUpdated(pos,l.getBlockState(pos),l.getBlockState(pos),3);return true;
    }
    public static void tickPlant(ServerLevel l,BlockPos pos,BlockEntity be){if(l.getGameTime()%20!=0||owner(be)==null||!RouteAccess.allowed(l,pos,owner(be)))return;var t=be.getPersistentData();long now=l.getGameTime();
        if(t.getLong("OverdraftUntil")>0&&now>=t.getLong("OverdraftUntil")){l.destroyBlock(pos,false);net.minecraft.world.Containers.dropItemStack(l,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,new ItemStack(ArcanaContent.item("mana_ash"),2));return;}
        if(!mature(be))return;
        var animals=l.getEntitiesOfClass(PastureAnimal.class,new AABB(pos).inflate(6),a->owner(be).equals(a.owner())&&!a.isBaby());
        for(var animal:animals){if(!compatible(be,animal))continue;
            t.putLong("ResonanceUntil",now+30);animal.getPersistentData().putLong("ResonanceUntil",now+30);
            com.tnc.tnc.mana.sight.ManaSightServer.record(l,animal.getEyePosition(),net.minecraft.world.phys.Vec3.atCenterOf(pos),1,0xb8f8a8,4);be.setChanged();
        }
    }
    public static boolean compatible(BlockEntity be,PastureAnimal a){
        if(be instanceof BotanicalPlantEntity b)return switch(b.species()){
            case HONEY_CLUSTER,DAWN_DISK->Set.of("mirrorwing_moth","apiary_toad").contains(a.speciesId());
            case HEARTH_PEPPER->Set.of("mistbelly_otter","forgegill_tapir").contains(a.speciesId());
            case ECHO_BEAN,WIND_SAIL->Set.of("wirecall_lizard","papersail_ray").contains(a.speciesId());
            case STAR_DEW,STAR_REST->a.speciesId().equals("dewbound_whale");default->false;};
        return be instanceof RoutePlantEntity r&&switch(r.kind()){
            case THERMAL,STEAM->Set.of("mistbelly_otter","forgegill_tapir").contains(a.speciesId());
            case SHADOW->a.speciesId().equals("dusk_lantern_deer");
            case PRISM->a.speciesId().equals("prismatic_antelope");
            case STORM->a.speciesId().equals("wirecall_lizard");default->false;};
    }
    public static boolean applyAnimal(ServerPlayer p,LivingEntity target,String id,ItemStack held){var l=p.serverLevel();if(com.tnc.tnc.home.TownProtection.denied(p,target.blockPosition()))return false;var t=target.getPersistentData();long now=l.getGameTime();
        if(id.equals("capture_shrink_elixir")){
            if(!p.isAlive()||p.isSpectator()||t.hasUUID("CageOwner")||!(target instanceof Monster)||target instanceof VoidDevourer||target instanceof com.tnc.tnc.dungeon.DungeonMob m&&m.guardian()||target.getMaxHealth()>250||target.getHealth()>target.getMaxHealth()*.25)return false;
            t.putLong("CaptureUntil",now+200);t.putUUID("CaptureOwner",p.getUUID());t.putFloat("ArcanaScale",.3f);target.refreshDimensions();ArcanaItem.consume(p,held);return true;
        }
        if(id.equals("strange_waste")&&ArcanaWasteFlask.corrode(p,target)){ArcanaItem.consume(p,held);return true;}
        if(id.equals("fission_elixir")&&target instanceof Slime s&&s.getSize()<=2&&t.getLong("NextFission")<=now&&p.getInventory().countItem(Items.SUGAR)>0&&l.getEntitiesOfClass(Slime.class,s.getBoundingBox().inflate(9)).size()<12){var child=(Slime)s.getType().create(l);if(child==null)return false;child.setSize(s.getSize(),true);child.setPos(s.getX()+1,s.getY(),s.getZ());child.getPersistentData().putLong("NextFission",now+24000);if(!l.addFreshEntity(child))return false;t.putLong("NextFission",now+24000);p.getInventory().clearOrCountMatchingItems(sugar->sugar.is(Items.SUGAR),1,p.inventoryMenu.getCraftSlots());ArcanaItem.consume(p,held);return true;}
        if(!(target instanceof PastureAnimal a)||!p.getUUID().equals(a.owner()))return false;
        if(id.equals("gigantism_elixir")||id.equals("miniature_elixir")){t.putFloat("ArcanaScale",id.equals("gigantism_elixir")?3:.5f);t.putLong("ScaleUntil",now+24000);target.refreshDimensions();}
        else if(id.equals("fission_elixir")){
            if(p.getInventory().countItem(Items.SUGAR)==0||!Set.of("mirrorwing_moth","watch_mantis").contains(a.speciesId())||a.isBaby()||t.getLong("NextFission")>now||l.getEntitiesOfClass(PastureAnimal.class,a.getBoundingBox().inflate(9),x->x.speciesId().equals(a.speciesId())).size()>=12)return false;
            var child=a.getBreedOffspring(l,a);if(child==null)return false;child.setPos(a.getX()+1,a.getY(),a.getZ());child.setAge(0);child.getPersistentData().putLong("NextFission",now+24000);if(!l.addFreshEntity(child))return false;t.putLong("NextFission",now+24000);p.getInventory().clearOrCountMatchingItems(sugar->sugar.is(Items.SUGAR),1,p.inventoryMenu.getCraftSlots());
        }else return false;
        ArcanaItem.consume(p,held);return true;
    }
    public static ItemStack giantFruit(BlockEntity be){var s=new ItemStack(ArcanaContent.item("giant_mana_fruit"));s.getOrCreateTag().putString("Root",net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock()).toString());return s;}
    public static net.minecraft.world.item.Item giantProduct(ItemStack s){if(!s.hasTag())return null;String root=s.getTag().getString("Root");for(var k:NewPlantKind.values())if(root.equals("tnc:"+k.id))return RouteContent.item(k.product);for(var k:BotanicalSpecies.values())if(root.equals("tnc:"+k.id))return BotanicalContent.PRODUCTS.get(k.product);return null;}
    public static boolean giantHarvest(ServerPlayer p,BlockEntity be){if(be.getPersistentData().getFloat("ArcanaScale")<2||!mature(be)||!p.getUUID().equals(owner(be)))return false;
        var fruit=new ItemStack(ArcanaContent.item("giant_mana_fruit"));fruit.getOrCreateTag().putString("Root",net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock()).toString());
        if(!p.getInventory().add(fruit))p.drop(fruit,false);be.getPersistentData().remove("ArcanaScale");if(be instanceof RoutePlantEntity r){r.growth=6000;r.mana=0;p.serverLevel().setBlock(be.getBlockPos(),be.getBlockState().setValue(RoutePlantBlock.AGE,1),3);}else if(be instanceof BotanicalPlantEntity b)b.harvest();be.setChanged();return true;
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGH) public static void blockUse(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e){if(!(e.getEntity() instanceof ServerPlayer p)||!(e.getItemStack().getItem() instanceof ArcanaItem item))return;if(applyPlant(p,e.getPos(),item.id,e.getItemStack())){e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);}}
    @SubscribeEvent public static void interact(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract e){if(!(e.getEntity() instanceof ServerPlayer p)||!(e.getTarget() instanceof LivingEntity target))return;
        var held=e.getItemStack();if(held.getItem() instanceof ArcanaItem item&&applyAnimal(p,target,item.id,held)){e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);}
        else if(held.is(RouteContent.item("ecology_restraint_cage"))&&ArcanaCages.capture(p,target,held)){e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);}
    }
    @SubscribeEvent public static void size(net.minecraftforge.event.entity.EntityEvent.Size e){var t=e.getEntity().getPersistentData();float scale=t.contains("ArcanaScale")?t.getFloat("ArcanaScale"):1;if(scale>0&&scale!=1)e.setNewSize(e.getNewSize().scale(Math.max(.25f,Math.min(3,scale))));}
    @SubscribeEvent public static void living(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e){if(!(e.getEntity().level() instanceof ServerLevel l)||e.getEntity().tickCount%20!=0)return;var a=e.getEntity();var t=a.getPersistentData();long now=l.getGameTime();
        if(t.getLong("ScaleUntil")>0&&now>=t.getLong("ScaleUntil")||t.getLong("CaptureUntil")>0&&now>=t.getLong("CaptureUntil")){t.remove("ArcanaScale");t.remove("ScaleUntil");t.remove("CaptureUntil");a.refreshDimensions();}
        if(a instanceof Animal animal&&t.getLong("ArcanaRageUntil")>now){var p=l.getNearestPlayer(animal,12);if(p!=null&&!p.isCreative()&&!p.isSpectator()){animal.getNavigation().moveTo(p,1.4);if(animal.distanceToSqr(p)<4&&animal.tickCount%40==0)p.hurt(animal.damageSources().mobAttack(animal),3);}}
        if(t.contains("ArcanaScale")&&a.tickCount%40==0){var packet=new net.minecraft.nbt.CompoundTag();packet.putString("Type","entity_scale");packet.putInt("Entity",a.getId());packet.putFloat("Scale",t.getFloat("ArcanaScale"));for(var p:l.players())if(p.distanceToSqr(a)<4096)ArcanaNetwork.sync(p,packet);}
    }
    private ArcanaEcology(){}
}
