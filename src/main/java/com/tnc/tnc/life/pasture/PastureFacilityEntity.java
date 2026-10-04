package com.tnc.tnc.life.pasture;

import com.tnc.tnc.production.energy.EnergyBlockEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/** Four real slots of sixteen, GUI, ownership, loaded-time eggs and charged perches. */
public final class PastureFacilityEntity extends BlockEntity implements Container,MenuProvider {
    private final NonNullList<ItemStack> items=NonNullList.withSize(4,ItemStack.EMPTY);
    private final PastureFacilityBlock.Kind kind;
    @Nullable private UUID owner,lightAnimal;
    private long lightExpires;
    private int water,eggClock;
    private boolean lastSignal;
    public PastureFacilityEntity(BlockPos pos,BlockState state){super(PastureRegistry.FACILITY_ENTITY,pos,state);kind=state.getBlock() instanceof PastureFacilityBlock block?block.kind():PastureFacilityBlock.Kind.TRAY;}
    public PastureFacilityBlock.Kind kind(){return kind;}
    @Nullable public UUID owner(){return owner;}
    @Nullable public UUID lightAnimal(){return lightAnimal;}
    public void claim(UUID id){if(owner==null){owner=id;setChanged();}}
    public void setLight(UUID animal,long expiration){lightAnimal=animal;lightExpires=expiration;setChanged();}
    public InteractionResult use(ServerPlayer player,ItemStack held){
        if(owner==null&&PastureAnimal.mayOperate(player.serverLevel(),worldPosition,player.getUUID()))claim(player.getUUID());
        if(!stillValid(player))return InteractionResult.FAIL;
        if(kind==PastureFacilityBlock.Kind.MARKER){for(PastureAnimal animal:level.getEntitiesOfClass(PastureAnimal.class,new AABB(worldPosition).inflate(8),a->owner.equals(a.owner())))animal.setHome(player,worldPosition.above());PastureAnimal.message(player,"八格范围内你的动物已记住窝点。牧养册会显示不合适的水池、暖窝、遮棚或栖架。");return InteractionResult.SUCCESS;}
        if(kind==PastureFacilityBlock.Kind.TROUGH&&held.is(Items.WATER_BUCKET)){if(water>0){PastureAnimal.message(player,"水槽仍有 "+water+"/16份，先用完再添水。");return InteractionResult.CONSUME;}water=16;if(!player.isCreative()){held.shrink(1);give(player,new ItemStack(Items.BUCKET));}setChanged();PastureAnimal.message(player,"真实一桶水存为16份，每次水獭浓缩需4份。");return InteractionResult.SUCCESS;}
        if(!held.isEmpty()&&canPlaceItem(0,held)){ItemStack remaining=insert(held);int inserted=held.getCount()-remaining.getCount();if(!player.isCreative())held.shrink(inserted);PastureAnimal.message(player,"投入 "+inserted+" 件；空手右键打开牧场设施库存。");return InteractionResult.SUCCESS;}
        net.minecraftforge.network.NetworkHooks.openScreen(player,this,worldPosition);return InteractionResult.SUCCESS;
    }
    private static void give(ServerPlayer player,ItemStack stack){player.getInventory().add(stack);if(!stack.isEmpty())player.drop(stack,false);}
    public boolean takeFood(Item item){if(owner==null||!(level instanceof ServerLevel server)||!PastureAnimal.mayOperate(server,worldPosition,owner))return false;ItemStack got=extractMatching(s->s.is(item),1);return !got.isEmpty();}
    public int takeWater(int count){if(count<=0||water<count)return 0;water-=count;setChanged();return count;}
    public boolean chargeAnimal(PastureAnimal animal){
        if(kind!=PastureFacilityBlock.Kind.CHARGING||owner==null||!owner.equals(animal.owner())||animal.storedResource()>20||!(level instanceof ServerLevel server)||!PastureAnimal.mayOperate(server,worldPosition,owner))return false;
        for(Direction side:Direction.values()){BlockPos neighbor=worldPosition.relative(side);if(PastureAnimal.mayOperate(server,neighbor,owner)&&server.getBlockEntity(neighbor) instanceof EnergyBlockEntity energy&&owner.equals(energy.owner())&&energy.energy()>=100){int drawn=energy.removeEnergy(100);if(drawn==100){animal.receiveStaticCharge(80);return true;}energy.addEnergy(drawn);}}return false;
    }
    public ItemStack insert(ItemStack original){ItemStack remaining=original.copy();for(int slot=0;slot<4&&!remaining.isEmpty();slot++){ItemStack current=items.get(slot);if(!current.isEmpty()&&!ItemStack.isSameItemSameTags(current,remaining))continue;if(!canPlaceItem(slot,remaining))continue;int count=Math.min(Math.min(16,remaining.getMaxStackSize())-current.getCount(),remaining.getCount());if(count<=0)continue;if(current.isEmpty())items.set(slot,remaining.copyWithCount(count));else current.grow(count);remaining.shrink(count);setChanged();}return remaining;}
    public ItemStack extract(int maximum){return extractMatching(s->true,maximum);}
    public ItemStack extractMatching(Predicate<ItemStack> predicate,int maximum){for(int slot=0;slot<4;slot++)if(!items.get(slot).isEmpty()&&predicate.test(items.get(slot)))return removeItem(slot,maximum);return ItemStack.EMPTY;}
    private boolean hatch(ServerLevel server,ItemStack egg){
        CompoundTag data=egg.getOrCreateTag();String species=data.getString("Species");var type=PastureRegistry.TYPES.get(species);if(type==null)return false;
        int x=(worldPosition.getX()>>4)<<4,z=(worldPosition.getZ()>>4)<<4;AABB area=new AABB(x,server.getMinBuildHeight(),z,x+16,server.getMaxBuildHeight(),z+16);
        if(server.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,area).size()>=24||server.getEntitiesOfClass(PastureAnimal.class,area,a->a.getType()==type).size()>=8)return false;
        PastureAnimal animal=type.create(server);if(animal==null)return false;
        for(int radius=0;radius<=2;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            BlockPos target=worldPosition.offset(dx,1,dz);if(!PastureAnimal.mayOperate(server,target,owner)||!server.getBlockState(target).isAir()||!server.getBlockState(target.above()).isAir())continue;
            int bornX=(target.getX()>>4)<<4,bornZ=(target.getZ()>>4)<<4;AABB bornArea=new AABB(bornX,server.getMinBuildHeight(),bornZ,bornX+16,server.getMaxBuildHeight(),bornZ+16);
            if(server.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,bornArea).size()>=24||server.getEntitiesOfClass(PastureAnimal.class,bornArea,a->a.getType()==type).size()>=8)continue;
            CompoundTag saved=new CompoundTag();saved.putUUID("PastureOwner",owner);saved.putInt("Age",-PastureSpecies.byId(species).adultDays()*24000);saved.putLong("Home",target.asLong());animal.readAdditionalSaveData(saved);animal.setPos(target.getX()+.5,target.getY(),target.getZ()+.5);
            if(!server.noCollision(animal,animal.getBoundingBox())||!server.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,animal.getBoundingBox()).isEmpty())continue;
            if(server.addFreshEntity(animal))return true;
        }animal.discard();return false;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,PastureFacilityEntity facility){
        if(!(level instanceof ServerLevel server))return;
        if(facility.kind==PastureFacilityBlock.Kind.LIGHT){if(facility.lightExpires<=server.getGameTime())server.removeBlock(pos,false);return;}
        if(facility.owner==null||!PastureAnimal.mayOperate(server,pos,facility.owner))return;
        if(facility.kind==PastureFacilityBlock.Kind.TROUGH&&server.getGameTime()%100==0){for(var sheep:server.getEntitiesOfClass(com.tnc.tnc.life.fauna.BellwoolSheepEntity.class,new AABB(pos).inflate(5),s->facility.owner.equals(s.caretaker())&&s.canFallInLove())){if(sheep.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>4){sheep.getNavigation().moveTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,1);continue;}if(facility.takeFood(com.tnc.tnc.TNMod.BELLWOOL_FODDER.get()))sheep.setInLove(null);break;}} if(facility.kind==PastureFacilityBlock.Kind.EGGS&&++facility.eggClock>=20){facility.eggClock=0;for(int slot=0;slot<4;slot++){ItemStack egg=facility.items.get(slot);if(!egg.is(PastureRegistry.item("fertile_pasture_egg"))||!egg.hasTag()||!egg.getTag().hasUUID("EggOwner")||!facility.owner.equals(egg.getTag().getUUID("EggOwner")))continue;CompoundTag data=egg.getOrCreateTag();data.putInt("Incubation",Math.min(48000,Math.min(48000,Math.max(0,data.getInt("Incubation")))+20));facility.setChanged();if(data.getInt("Incubation")<48000)continue;if(facility.hatch(server,egg)){egg.shrink(1);facility.setChanged();}}}
        if(facility.kind==PastureFacilityBlock.Kind.DEW&&server.getGameTime()%20==0){for(net.minecraft.world.entity.item.ItemEntity item:server.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(2))){CompoundTag saved=new CompoundTag();item.saveWithoutId(saved);if(saved.hasUUID("Thrower")&&!facility.owner.equals(saved.getUUID("Thrower")))continue;String id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem().getItem()).toString();if(!Set.of("tnc:lantern_antler","tnc:lamp_wax","tnc:loam_pebble","tnc:flight_feather").contains(id))continue;ItemStack left=facility.insert(item.getItem());if(left.isEmpty())item.discard();else item.setItem(left);}}
        if(facility.kind==PastureFacilityBlock.Kind.BOTTLE){boolean signal=server.hasNeighborSignal(pos);if(signal&&!facility.lastSignal&&facility.items.get(0).getItem() instanceof ManaBottleItem){ManaBottleItem.transferFromBase(server,pos,facility.owner,facility.items.get(0),5);facility.setChanged();}if(facility.lastSignal!=signal){facility.lastSignal=signal;facility.setChanged();}}
    }
    @Override public int getContainerSize(){return 4;}
    @Override public int getMaxStackSize(){return 16;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<4?items.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot<0||slot>=4)return ItemStack.EMPTY;ItemStack result=ContainerHelper.removeItem(items,slot,count);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return slot>=0&&slot<4?ContainerHelper.takeItem(items,slot):ItemStack.EMPTY;}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=4)return;items.set(slot,stack);stack.setCount(Math.min(Math.min(16,stack.getMaxStackSize()),stack.getCount()));setChanged();}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){if(slot<0||slot>=4||kind==PastureFacilityBlock.Kind.LIGHT||kind==PastureFacilityBlock.Kind.MARKER||kind==PastureFacilityBlock.Kind.CHARGING)return false;return switch(kind){case TROUGH->PastureSpecies.ALL.stream().anyMatch(s->stack.is(s.food()))||stack.is(com.tnc.tnc.TNMod.BELLWOOL_FODDER.get());case EGGS->stack.is(PastureRegistry.item("fertile_pasture_egg"))&&stack.hasTag()&&stack.getTag().hasUUID("EggOwner")&&Objects.equals(owner,stack.getTag().getUUID("EggOwner"));case BOTTLE->slot==0&&stack.getItem() instanceof ManaBottleItem;default->true;};}
    @Override public boolean stillValid(Player p){return !isRemoved()&&level!=null&&level.getBlockEntity(worldPosition)==this&&owner!=null&&owner.equals(p.getUUID())&&p.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64&&(!(p instanceof ServerPlayer server)||PastureAnimal.mayOperate(server.serverLevel(),worldPosition,owner));}
    @Override public void clearContent(){items.clear();setChanged();}
    @Override public Component getDisplayName(){return Component.translatable(getBlockState().getBlock().getDescriptionId());}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new PastureMenu(id,inventory,this,new ContainerData(){@Override public int get(int index){return switch(index){case 0->kind.ordinal();case 1->water;case 2->items.stream().filter(s->s.is(PastureRegistry.item("fertile_pasture_egg"))).mapToInt(s->s.hasTag()?Math.min(1000,s.getTag().getInt("Incubation")*1000/48000):0).min().orElse(0);case 3->items.stream().mapToInt(ItemStack::getCount).sum();default->0;};}@Override public void set(int index,int value){}@Override public int getCount(){return 4;}},worldPosition);}
    @Override public void setChanged(){super.setChanged();if(level!=null&&!level.isClientSide&&level.getBlockEntity(worldPosition)==this)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);ContainerHelper.saveAllItems(tag,items);if(owner!=null)tag.putUUID("Owner",owner);if(lightAnimal!=null)tag.putUUID("LightAnimal",lightAnimal);tag.putLong("LightExpires",lightExpires);tag.putInt("Water",water);tag.putBoolean("LastSignal",lastSignal);}
    @Override public void load(CompoundTag tag){super.load(tag);ContainerHelper.loadAllItems(tag,items);owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;lightAnimal=tag.hasUUID("LightAnimal")?tag.getUUID("LightAnimal"):null;lightExpires=tag.getLong("LightExpires");water=Math.max(0,Math.min(16,tag.getInt("Water")));lastSignal=tag.getBoolean("LastSignal");for(ItemStack stack:items)stack.setCount(Math.min(16,stack.getCount()));}
}




