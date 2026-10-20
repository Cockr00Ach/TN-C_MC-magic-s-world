package com.tnc.tnc.alchemy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
public final class AlchemyMenu extends AbstractContainerMenu {
    public final BlockPos pos;private final ContainerData data;private AlchemyEntity bound;
    public AlchemyMenu(int id,Inventory inv,FriendlyByteBuf b){this(id,inv,new SimpleContainer(7),new SimpleContainerData(4),b.readBlockPos());}
    public AlchemyMenu(int id,Inventory inv,AlchemyEntity be){this(id,inv,be.inventory,be.data,be.getBlockPos());bound=be;}
    private AlchemyMenu(int id,Inventory inv,Container items,ContainerData data,BlockPos pos){super(AlchemyContent.MENU,id);this.pos=pos;this.data=data;
        int[][] spots={{28,74},{58,74},{28,104},{58,104},{115,144},{201,74},{201,112}};
        for(int i=0;i<7;i++){final int slot=i;addSlot(new Slot(items,i,spots[i][0],spots[i][1]){public boolean mayPlace(ItemStack s){return slot<4||slot==4&&s.is(AlchemyContent.FUEL)||slot==5&&s.is(Items.GLASS_BOTTLE);}});}
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlot(new Slot(inv,9+r*9+c,28+c*18,196+r*18));for(int c=0;c<9;c++)addSlot(new Slot(inv,c,28+c*18,254));addDataSlots(data);
    }
    public int value(int i){return data.get(i);}public Elixir recipe(){int n=value(3)-1;return n<0||n>=Elixir.values().length?null:Elixir.values()[n];}
    @Override public boolean stillValid(Player p){return p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<64&&(!(p instanceof net.minecraft.server.level.ServerPlayer sp)||bound!=null&&!bound.isRemoved()&&sp.serverLevel().getBlockEntity(pos)==bound&&bound.mayUse(sp));}
    @Override public boolean clickMenuButton(Player p,int id){if(id!=0||!(p instanceof net.minecraft.server.level.ServerPlayer sp)||!stillValid(p)||!(sp.serverLevel().getBlockEntity(pos) instanceof AlchemyEntity be))return false;var magic=com.tnc.tnc.magic.MagicStone.getOrNull(sp);boolean fire=magic!=null&&magic.getLearned().stream().anyMatch(s->com.tnc.tnc.magic.SpellCatalog.byId(s)!=null&&com.tnc.tnc.magic.SpellCatalog.byId(s).element()==com.tnc.tnc.magic.Element.FIRE);if(fire&&magic.spendMana(5)){be.ignite(true);com.tnc.tnc.network.MagicStoneNetwork.syncTo(sp);return true;}com.tnc.tnc.journey.CommissionService.message(sp,"学会任意火系法术后可花5法力点燃；也可用燧石右键釜体。");return false;}
    @Override public ItemStack quickMoveStack(Player p,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var old=stack.copy();boolean moved=index<7?moveItemStackTo(stack,7,slots.size(),true):stack.is(Items.GLASS_BOTTLE)?moveItemStackTo(stack,5,6,false):stack.is(AlchemyContent.FUEL)?moveItemStackTo(stack,4,5,false):moveItemStackTo(stack,0,4,false);if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return old;}
}
