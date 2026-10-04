package com.tnc.tnc.life.botanical;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import java.util.UUID;
/** Every mutation flushes to the original held item. No second escrow inventory exists. */
public final class PortableFieldMenu extends AbstractContainerMenu {
    public final boolean archive;
    public final int capacity,carrierSlot;
    private final Player player;
    private final UUID identity;
    private boolean loading;
    private final SimpleContainer contents;
    public PortableFieldMenu(int id,Inventory inv,boolean archive,int carrierSlot){super(BotanicalContent.FIELD_MENU,id);this.player=inv.player;this.archive=archive;this.capacity=archive?12:4;this.carrierSlot=carrierSlot;
        var held=inv.getItem(carrierSlot);identity=held.hasTag()&&held.getTag().hasUUID("FieldContainer")?held.getTag().getUUID("FieldContainer"):null;
        loading=true;contents=new SimpleContainer(capacity){@Override public void setChanged(){super.setChanged();if(!loading)flush();}};
        if(!player.level().isClientSide&&held.hasTag())contents.fromTag(held.getTag().getList("FieldContents",10));
        for(int i=0;i<capacity;i++){var stack=contents.getItem(i);if(!stack.isEmpty()){if(!accepts(stack))contents.setItem(i,ItemStack.EMPTY);else stack.setCount(Math.min(stack.getCount(),archive?1:16));}}loading=false;
        for(int i=0;i<capacity;i++){final int target=i;int x=archive?52+(i%4)*18:70+(i%2)*18,y=archive?20+(i/4)*18:24+(i/2)*18;addSlot(new Slot(contents,i,x,y){@Override public boolean mayPlace(ItemStack stack){if(!accepts(stack))return false;if(!archive)for(int n=0;n<capacity;n++)if(n!=target&&contents.getItem(n).is(stack.getItem()))return false;return true;}@Override public int getMaxStackSize(){return archive?1:16;}});}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addInventorySlot(inv,col+row*9+9,8+col*18,84+row*18);
        for(int col=0;col<9;col++)addInventorySlot(inv,col,8+col*18,142);
    }
    private void addInventorySlot(Inventory inv,int index,int x,int y){addSlot(new Slot(inv,index,x,y){@Override public boolean mayPickup(Player p){return index!=carrierSlot;}@Override public boolean mayPlace(ItemStack stack){return index!=carrierSlot;}});}
    public boolean accepts(ItemStack stack){if(stack.isEmpty()||stack.getItem() instanceof PortableFieldItem)return false;var key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());if(archive)return (stack.is(Items.PAPER)||key!=null&&(key.getPath().endsWith("_page")||key.getPath().endsWith("_card")))&&stack.hasTag()&&stack.getTag().hasUUID("Sampler");return stack.getItem() instanceof ItemNameBlockItem||key!=null&&(key.getPath().endsWith("_seed")||key.getPath().endsWith("_seeds"));}
    private boolean bound(){if(identity==null)return false;var held=player.getInventory().getItem(carrierSlot);return held.getItem() instanceof PortableFieldItem field&&field.archive==archive&&held.hasTag()&&held.getTag().hasUUID("FieldContainer")&&identity.equals(held.getTag().getUUID("FieldContainer"));}
    private void flush(){if(player.level().isClientSide||!bound())return;player.getInventory().getItem(carrierSlot).getOrCreateTag().put("FieldContents",contents.createTag());player.getInventory().setChanged();}
    @Override public boolean stillValid(Player p){return p==player&&p.isAlive()&&(p.level().isClientSide||bound());}
    @Override public void clicked(int slot,int button,ClickType type,Player p){if(!stillValid(p))return;if(type==ClickType.SWAP&&(button==carrierSlot||button==40&&carrierSlot==40))return;if(slot>=capacity&&slot<slots.size()&&slots.get(slot).getSlotIndex()==carrierSlot)return;super.clicked(slot,button,type,p);flush();}
    @Override public ItemStack quickMoveStack(Player p,int index){if(!stillValid(p)||index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;var stack=slot.getItem();var old=stack.copy();boolean moved=index<capacity?moveItemStackTo(stack,capacity,slots.size(),true):accepts(stack)&&moveItemStackTo(stack,0,capacity,false);if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);flush();return old;}
    @Override public void removed(Player p){flush();super.removed(p);}
}
