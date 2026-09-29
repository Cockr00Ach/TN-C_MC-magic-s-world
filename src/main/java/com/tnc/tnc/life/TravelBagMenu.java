package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public final class TravelBagMenu extends AbstractContainerMenu {
    private final SimpleContainer bag;
    private final Player owner;
    private final DataSlot balance=DataSlot.standalone();
    private final DataSlot balanceHigh=DataSlot.standalone();
    private long lastAction=-100;
    public TravelBagMenu(int id,Inventory inventory){this(id,inventory,new SimpleContainer(7));}
    public TravelBagMenu(int id,Inventory inventory,SimpleContainer bag) {
        super(TNMod.TRAVEL_BAG_MENU.get(),id);this.bag=bag;owner=inventory.player;
        addSlot(new Slot(bag,0,26,29){@Override public boolean mayPlace(ItemStack s){return s.is(TNMod.MONEY_POUCH.get());}@Override public int getMaxStackSize(){return 1;}});
        addSlot(new Slot(bag,1,26,61){@Override public boolean mayPlace(ItemStack s){return s.is(TNMod.FOOD_POUCH.get());}@Override public int getMaxStackSize(){return 1;}});
        for(int i=0;i<5;i++)addSlot(new Slot(bag,i+2,66+i*22,61){@Override public boolean mayPlace(ItemStack s){return validFood(s)&&!bag.getItem(1).isEmpty();}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,8+col*18,122+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,180));
        if(owner instanceof ServerPlayer p) {
            addDataSlot(new DataSlot(){@Override public int get(){return (int)AdventureService.profile(p).coins()&0xffff;}@Override public void set(int value){}});
            addDataSlot(new DataSlot(){@Override public int get(){return (int)(AdventureService.profile(p).coins()>>>16);}@Override public void set(int value){}});
        } else {addDataSlot(balance);addDataSlot(balanceHigh);}
    }
    public int balance(){return owner instanceof ServerPlayer p?(int)AdventureService.profile(p).coins():(balance.get()&0xffff)|((balanceHigh.get()&0xffff)<<16);}
    public static boolean validFood(ItemStack s){return !s.isEmpty()&&s.isEdible()&&s.getMaxStackSize()<=64;}
    @Override public boolean stillValid(Player p){return p==owner&&p.isAlive()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p);}
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var copy=stack.copy();boolean moved;
        if(index<7)moved=moveItemStackTo(stack,7,43,true);
        else if(stack.is(TNMod.MONEY_POUCH.get()))moved=moveItemStackTo(stack,0,1,false);
        else if(stack.is(TNMod.FOOD_POUCH.get()))moved=moveItemStackTo(stack,1,2,false);
        else if(validFood(stack))moved=moveItemStackTo(stack,2,7,false);
        else return ItemStack.EMPTY;
        if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return copy;
    }
    @Override public boolean clickMenuButton(Player player,int button) {
        if(!(player instanceof ServerPlayer p)||!stillValid(p)||!getCarried().isEmpty())return false;
        long now=p.server.overworld().getGameTime();if(now-lastAction<10)return false;lastAction=now;
        var account=AdventureService.profile(p);String message="";
        if(button>=10&&button<=12) {
            if(bag.getItem(0).isEmpty())return false;
            int value=button==10?1:button==11?100:10000;
            var item=button==10?TNMod.COPPER_COIN.get():button==11?TNMod.SILVER_COIN.get():TNMod.GOLD_COIN.get();
            var tx=new InventoryTransaction(p.getInventory());
            if(account.coins()<value||!tx.add(new ItemStack(item)))message="余额不足或背包已满，余额未扣除。";
            else{account.debit(value,"取出实体币");tx.commit();message="已取出实体币。背包里的币遵循普通死亡掉落。";}
        } else if(button==13) {
            if(bag.getItem(0).isEmpty())return false;
            var materials=List.of(new ContractCatalog.Material("tnc:copper_coin",false,InventoryTransaction.count(p.getInventory(),new ContractCatalog.Material("tnc:copper_coin",false,1))),
                    new ContractCatalog.Material("tnc:silver_coin",false,InventoryTransaction.count(p.getInventory(),new ContractCatalog.Material("tnc:silver_coin",false,1))),
                    new ContractCatalog.Material("tnc:gold_coin",false,InventoryTransaction.count(p.getInventory(),new ContractCatalog.Material("tnc:gold_coin",false,1))));
            long value=materials.get(0).count()+100L*materials.get(1).count()+10000L*materials.get(2).count();
            var tx=new InventoryTransaction(p.getInventory());if(value>0&&account.canCredit(value)&&tx.take(materials,false)){tx.commit();account.credit(value,"存入实体币");message="实体币已存入个人账户。";}else message="没有可存的实体币，或余额达到上限。";
        } else if(button>=0&&button<5) {
            // Explicit quick meal still spends the food; one action per use duration.
            var food=bag.getItem(button+2);if(bag.getItem(1).isEmpty()||!validFood(food))return false;
            var properties=food.getFoodProperties(p);if(properties==null||(!p.canEat(properties.canAlwaysEat())))return false;
            var life=LifeSavedData.get(p.server);var personal=life.account(p.getUUID());if(now<personal.nextMealTick)return false;
            var eaten=food.copyWithCount(1);var foodItem=food.getItem();var before=eaten.copy();
            var start=new net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start(p,eaten,food.getUseDuration());
            if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(start)||start.getDuration()<=0)return false;
            personal.nextMealTick=now+Math.max(10,start.getDuration());life.setDirty();
            food.shrink(1);bag.setChanged();
            var result=eaten.finishUsingItem(p.level(),p);
            var finish=new net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish(p,before,start.getDuration(),result);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(finish);result=finish.getResultStack();
            if(!result.isEmpty()){var tx=new InventoryTransaction(p.getInventory());if(tx.add(result))tx.commit();else p.drop(result,false);}
            LifeEvents.taste(p,foodItem);AdventureService.milestone(p,"meal");
            message="吃了一份旅途补给，容器返还到背包；背包满时放到脚边。";
        }
        AdventureSavedData.get(p.server).setDirty();if(!message.isEmpty())p.displayClientMessage(net.minecraft.network.chat.Component.literal(message),true);broadcastChanges();return true;
    }
}
