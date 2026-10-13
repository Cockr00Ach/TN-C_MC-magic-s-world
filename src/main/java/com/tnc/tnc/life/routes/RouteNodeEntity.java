package com.tnc.tnc.life.routes;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.tnc.tnc.life.pasture.*;

/** Single authoritative mana balance. No FE capability is exposed. */
public final class RouteNodeEntity extends BlockEntity implements MenuProvider {
    public UUID owner,animal,manualPlayer;public long manualUntil;
    public int mana,inRate,outRate,priority,progress,targetColor,reserve;public boolean animalCharging;private double fractionalCost;
    public String status="等待接入魔力",job="";public final SimpleContainer inventory=new SimpleContainer(6){@Override public void setChanged(){super.setChanged();RouteNodeEntity.this.setChanged();}};
    public RouteNodeEntity(BlockPos p,BlockState s){super(RouteContent.NODE_ENTITY.get(),p,s);}
    public RouteKind kind(){return ((RouteNodeBlock)getBlockState().getBlock()).kind;}
    public boolean mayUse(ServerPlayer player){return owner!=null&&owner.equals(player.getUUID())&&RouteAccess.allowed(player.serverLevel(),worldPosition,owner);}
    public int receive(int offered){int amount=Math.max(0,Math.min(offered,kind().capacity-mana));mana+=amount;if(amount>0)setChanged();return amount;}
    public int extract(int requested){int amount=Math.max(0,Math.min(requested,mana));mana-=amount;if(amount>0)setChanged();return amount;}
    public boolean spend(int cost){if(cost<0||mana<cost)return false;mana-=cost;setChanged();return true;}
    public boolean upkeep(double perSecond){if(mana<=0)return false;fractionalCost+=perSecond;int due=(int)(fractionalCost+1e-9);if(mana<due){fractionalCost-=perSecond;return false;}mana-=due;fractionalCost=Math.max(0,fractionalCost-due);setChanged();return true;}
    private final Map<Direction,net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandler>> itemPorts=new EnumMap<>(Direction.class);
    @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability,Direction side){if(capability==net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER&&side!=null&&!isRemoved())return itemPorts.computeIfAbsent(side,d->net.minecraftforge.common.util.LazyOptional.of(()->new RouteItemPort(this,d))).cast();return super.getCapability(capability,side);}
    @Override public void invalidateCaps(){super.invalidateCaps();itemPorts.values().forEach(net.minecraftforge.common.util.LazyOptional::invalidate);itemPorts.clear();}
    public static void tick(ServerLevel l,BlockPos p,RouteNodeEntity be){if(l.getGameTime()%20!=0)return;operate(l,be);}
    static void operate(ServerLevel l,RouteNodeEntity be){String previous=be.status;var p=be.getBlockPos();if(!RouteAccess.allowed(l,p,be.owner))return;
        if(be.kind()==RouteKind.COLLECTOR)be.collectPlants(l);
        if(be.kind()==RouteKind.ANIMAL_COLLECTOR||be.kind()==RouteKind.COLOR_TARGET)be.collectAnimal(l);
        if(be.kind()==RouteKind.INFUSER){be.manual(l);be.fillContainer(l);}
        if(be.kind()==RouteKind.CHARGER)be.chargeAccessory(l);
        if(be.kind()==RouteKind.LAMP||be.kind()==RouteKind.BRIGHT_LAMP){boolean lit=be.upkeep(be.kind()==RouteKind.LAMP?.05:.1);be.status=lit?"灯火稳定":"待补魔力";if(l.getBlockState(p).getValue(RouteNodeBlock.LIT)!=lit)l.setBlock(p,l.getBlockState(p).setValue(RouteNodeBlock.LIT,lit),3);}
        if(be.kind()==RouteKind.GREENHOUSE){boolean enclosed=be.enclosed(l);boolean running=enclosed&&be.upkeep(.4);be.status=running?"护候中 · 9×9 农圃":"需要封闭温室及魔力";if(running)be.getPersistentData().putLong("ClimateUntil",l.getGameTime()+25);}
        RouteWork.tick(l,be);if(!previous.equals(be.status)||be.kind()==RouteKind.INFUSER||be.kind()==RouteKind.CHARGER)l.sendBlockUpdated(p,be.getBlockState(),be.getBlockState(),2);if(be.kind()==RouteKind.ALARM){boolean lit=be.getPersistentData().getLong("AlarmUntil")>l.getGameTime();if(l.getBlockState(p).getValue(RouteNodeBlock.LIT)!=lit){l.setBlock(p,l.getBlockState(p).setValue(RouteNodeBlock.LIT,lit),3);l.updateNeighborsAt(p,be.getBlockState().getBlock());}}be.setChanged();
    }
    private boolean enclosed(ServerLevel l){for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){var q=worldPosition.offset(dx,0,dz);if(!l.hasChunkAt(q))return false;boolean roof=false;for(int y=2;y<=6;y++){var at=q.above(y);var s=l.getBlockState(at);if(!s.getCollisionShape(l,at).isEmpty()&&s.getFluidState().isEmpty()){roof=true;break;}}if(!roof)return false;}for(int i=-4;i<=4;i++)for(int y=1;y<=2;y++)for(var side:net.minecraft.core.Direction.Plane.HORIZONTAL){var q=worldPosition.offset(side.getStepX()*4,y,side.getStepZ()*4).offset(side.getStepX()==0?i:0,0,side.getStepZ()==0?i:0);if(!l.hasChunkAt(q)||l.getBlockState(q).getCollisionShape(l,q).isEmpty())return false;}return true;}
    private void manual(ServerLevel l){if(manualPlayer==null||l.getGameTime()>manualUntil)return;var p=l.getServer().getPlayerList().getPlayer(manualPlayer);if(p==null||p.serverLevel()!=l||p.distanceToSqr(Vec3Position())>16||!mayUse(p)){manualPlayer=null;return;}var stone=com.tnc.tnc.magic.MagicStone.getOrNull(p);int amount=Math.min(5,kind().capacity-mana);if(stone!=null&&amount>0&&stone.spendMana(amount)){receive(amount);RouteNetwork.trace(l,p.blockPosition(),worldPosition,amount);com.tnc.tnc.network.MagicStoneNetwork.syncTo(p);status="正在注入自身魔力 · 5/秒";}else status="自身魔力不足或缓冲已满";}
    private net.minecraft.world.phys.Vec3 Vec3Position(){return net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);}
    private void collectPlants(ServerLevel l){int allowance=Math.min(kind().rate,kind().capacity-mana);for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){if(allowance<=0)return;BlockPos p=worldPosition.offset(dx,dy,dz);if(!RouteAccess.allowed(l,p,owner))continue;int amount=RouteSources.draw(l,p,owner,allowance);if(amount>0){receive(amount);RouteProgress.awardOwner(l,owner,"network/collected");allowance-=amount;RouteNetwork.trace(l,p,worldPosition,amount);status="正在收集植物魔力";}}}
    private void collectAnimal(ServerLevel l){if(animal==null){status="用牧务杖选兽后右键绑定";return;}var entity=l.getEntity(animal);if(!(entity instanceof PastureAnimal beast)||!owner.equals(beast.owner())||beast.distanceToSqr(Vec3Position())>144){status="绑定异兽不在12格内";return;}
        if(kind()==RouteKind.ANIMAL_COLLECTOR&&animalCharging){int accepted=beast.receiveNetworkMana(owner,Math.min(32,mana));extract(accepted);if(accepted>0)RouteNetwork.trace(l,worldPosition,beast.blockPosition(),accepted);status="正在向 "+beast.species().name()+" 充能 · "+accepted+"/秒";return;}
        if(kind()==RouteKind.COLOR_TARGET&&!beast.speciesId().equals("prismatic_antelope")){status="色靶只接收曳彩角羚";return;}
        if(kind()==RouteKind.COLOR_TARGET){int expected=Math.floorMod(beast.getUUID().hashCode(),16);if(targetColor!=expected){status="角羚喜欢 "+net.minecraft.world.item.DyeColor.byId(expected).getName()+" 色靶：拿染料右键改色";return;}var start=beast.getEyePosition();var sight=l.clip(new net.minecraft.world.level.ClipContext(start,Vec3Position(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,beast));if(sight.getType()!=net.minecraft.world.phys.HitResult.Type.MISS&&!sight.getBlockPos().equals(worldPosition)){status="色靶射线被挡住";return;}if(l.getGameTime()%80!=0)return;}
        int got=beast.drawNetworkMana(owner,Math.min(kind().rate,kind().capacity-mana),kind()==RouteKind.COLOR_TARGET);receive(got);if(got>0){RouteProgress.awardOwner(l,owner,"harvest/"+beast.speciesId());RouteProgress.awardOwner(l,owner,"network/collected");RouteNetwork.trace(l,beast.blockPosition(),worldPosition,got);}status="绑定："+beast.species().name();}
    private void fillContainer(ServerLevel l){ItemStack stack=inventory.getItem(0);if(stack.isEmpty())return;
        if(stack.is(Items.GLASS_BOTTLE)&&inventory.getItem(4).isEmpty()&&mana>=100){ItemStack bottle=new ItemStack(PastureRegistry.item("mana_bottle"));if(PastureBottleLedger.get(l).deposit(bottle,100,100)==100){spend(100);stack.shrink(1);inventory.setItem(4,bottle);status="魔力瓶已满 · 100";}}
        else if(stack.is(Items.BUCKET)&&inventory.getItem(4).isEmpty()&&spend(400)){stack.shrink(1);inventory.setItem(4,new ItemStack(RouteContent.MANA_BUCKET.get()));status="流动魔力桶已满 · 400";}
        else if(stack.getItem() instanceof ManaBottleItem){var ledger=PastureBottleLedger.get(l);int nominal=stack.is(PastureRegistry.item("refined_mana_bottle"))?400:100;int accepted=ledger.deposit(stack,nominal,Math.min(8,mana));extract(accepted);status="容器注入 "+ledger.amount(stack)+"/"+nominal;if(ledger.amount(stack)>=nominal&&inventory.getItem(4).isEmpty()){inventory.setItem(4,stack.copyWithCount(1));stack.shrink(1);}}
    }
    private void chargeAccessory(ServerLevel l){var stack=inventory.getItem(0);if(!(stack.getItem() instanceof ManaCharged accessory)||accessory.manaCapacity()<=0){status="放入充能饰品或魔道机甲";return;}int received=RouteChargeLedger.get(l).deposit(stack,accessory.manaCapacity(),Math.min(16,mana));extract(received);if(received>0)RouteProgress.awardOwner(l,owner,"network/charged");status="充能中 · "+RouteChargeLedger.get(l).amount(stack)+"/"+accessory.manaCapacity();}
    public final ContainerData data=new ContainerData(){public int get(int index){return switch(index){case 0->mana;case 1->kind().capacity;case 2->inRate;case 3->outRate;case 4->progress;case 5->kind().ordinal();case 6->priority;default->0;};}public void set(int i,int v){}public int getCount(){return 7;}};
    @Override public Component getDisplayName(){return Component.literal(kind().name);}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new RouteMenu(id,inv,this);}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)RouteNetwork.add(l,worldPosition);}
    @Override public void setRemoved(){if(level instanceof ServerLevel l)RouteNetwork.remove(l,worldPosition);super.setRemoved();}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);if(owner!=null)t.putUUID("Owner",owner);if(animal!=null)t.putUUID("Animal",animal);t.putInt("Reserve",reserve);t.putString("Status",status);t.putInt("Mana",mana);t.putInt("TargetColor",targetColor);t.putBoolean("AnimalCharging",animalCharging);t.putDouble("Fraction",fractionalCost);t.putInt("Priority",priority);t.putInt("Progress",progress);t.putString("Job",job);var items=NonNullList.withSize(6,ItemStack.EMPTY);for(int i=0;i<6;i++)items.set(i,inventory.getItem(i));ContainerHelper.saveAllItems(t,items);}
    @Override public void load(CompoundTag t){super.load(t);owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;animal=t.hasUUID("Animal")?t.getUUID("Animal"):null;mana=Math.max(0,Math.min(kind().capacity,t.getInt("Mana")));reserve=Math.max(0,Math.min(kind().capacity,t.getInt("Reserve")));status=t.getString("Status");targetColor=Math.floorMod(t.getInt("TargetColor"),16);animalCharging=t.getBoolean("AnimalCharging");fractionalCost=Math.max(0,Math.min(.999,t.getDouble("Fraction")));priority=Math.floorMod(t.getInt("Priority"),3);progress=Math.max(0,t.getInt("Progress"));job=t.getString("Job");var items=NonNullList.withSize(6,ItemStack.EMPTY);ContainerHelper.loadAllItems(t,items);for(int i=0;i<6;i++)inventory.setItem(i,items.get(i));}
}
