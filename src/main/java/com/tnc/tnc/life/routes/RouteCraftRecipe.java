package com.tnc.tnc.life.routes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import com.tnc.tnc.life.pasture.*;
/** Full containers only; ResultSlot debits the authoritative ledger at the actual take. */
public final class RouteCraftRecipe extends CustomRecipe {
    public RouteCraftRecipe(ResourceLocation id,CraftingBookCategory category){super(id,category);}
    private boolean bucket(){return getId().getPath().endsWith("mana_bucket_merge");}
    @Override public boolean matches(CraftingContainer c,Level l){var identities=new java.util.HashSet<java.util.UUID>();int dirt=0,bottles=0,buckets=0;for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(s.isEmpty())continue;if(s.is(Items.DIRT)){dirt++;continue;}if(s.is(Items.BUCKET)){buckets++;continue;}if(!(s.getItem() instanceof ManaBottleItem)||s.getCount()!=1)return false;int stored=l instanceof ServerLevel server?PastureBottleLedger.get(server).amount(s):s.hasTag()?s.getTag().getInt("StoredMana"):0;if(stored<100||!s.hasTag()||!s.getTag().hasUUID("BottleId")||!identities.add(s.getTag().getUUID("BottleId")))return false;bottles++;}return bucket()?bottles==4&&buckets==1&&dirt==0:bottles==1&&dirt==8&&buckets==0;}
    @Override public ItemStack assemble(CraftingContainer c,net.minecraft.core.RegistryAccess access){var out=new ItemStack(bucket()?RouteContent.MANA_BUCKET.get():RouteContent.SOIL_SOURCE.get());var list=new net.minecraft.nbt.ListTag();for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(s.getItem() instanceof ManaBottleItem&&s.hasTag()&&s.getTag().hasUUID("BottleId")){var row=new net.minecraft.nbt.CompoundTag();row.putUUID("BottleId",s.getTag().getUUID("BottleId"));list.add(row);}}out.getOrCreateTag().put("ManaCraftAccounts",list);return out;}
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer c){var remaining=NonNullList.withSize(c.getContainerSize(),ItemStack.EMPTY);for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).getItem() instanceof ManaBottleItem){remaining.set(i,c.getItem(i).copyWithCount(1));remaining.get(i).getOrCreateTag().putInt("StoredMana",Math.max(0,remaining.get(i).getOrCreateTag().getInt("StoredMana")-100));}return remaining;}
    @Override public boolean canCraftInDimensions(int w,int h){return w*h>=(bucket()?5:9);}
    @Override public RecipeSerializer<?> getSerializer(){return RouteContent.CRAFT_SERIALIZER.get();}
    public static boolean mayTake(ServerPlayer p,ItemStack output){if(!output.hasTag()||!output.getTag().contains("ManaCraftAccounts"))return true;var ids=output.getTag().getList("ManaCraftAccounts",10);int expected=output.is(RouteContent.MANA_BUCKET.get())?4:1;if(ids.size()!=expected)return false;var seen=new java.util.HashSet<java.util.UUID>();for(var raw:ids){var row=(net.minecraft.nbt.CompoundTag)raw;if(!row.hasUUID("BottleId")||!seen.add(row.getUUID("BottleId")))return false;var dummy=new ItemStack(PastureRegistry.item("mana_bottle"));dummy.setTag(row.copy());if(PastureBottleLedger.get(p.serverLevel()).amount(dummy)<100)return false;}return true;}
    public static java.util.List<ItemStack> inputPayment(CraftingContainer c){int dirt=0,bucket=0;var bottles=new java.util.ArrayList<ItemStack>();for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(s.isEmpty())continue;if(s.is(Items.DIRT))dirt++;else if(s.is(Items.BUCKET))bucket++;else if(s.getItem() instanceof ManaBottleItem)bottles.add(s.copyWithCount(1));else return java.util.List.of();}return dirt==8&&bucket==0&&bottles.size()==1||dirt==0&&bucket==1&&bottles.size()==4?java.util.List.copyOf(bottles):java.util.List.of();}
}
