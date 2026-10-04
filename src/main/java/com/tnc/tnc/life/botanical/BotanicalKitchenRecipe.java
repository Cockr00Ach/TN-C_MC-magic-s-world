package com.tnc.tnc.life.botanical;
import com.google.gson.JsonObject;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
/** Ordinary shapeless recipes, with actual water-bottle vessels returned. */
public final class BotanicalKitchenRecipe extends ShapelessRecipe {
    private BotanicalKitchenRecipe(ShapelessRecipe source){super(source.getId(),source.getGroup(),source.category(),source.getResultItem(RegistryAccess.EMPTY).copy(),source.getIngredients());}
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer inventory){var list=super.getRemainingItems(inventory);for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).is(Items.POTION))list.set(i,new ItemStack(Items.GLASS_BOTTLE));return list;}
    @Override public RecipeSerializer<?> getSerializer(){return BotanicalContent.KITCHEN_SERIALIZER;}
    public static final class Serializer implements RecipeSerializer<BotanicalKitchenRecipe> {
        private final ShapelessRecipe.Serializer delegate=new ShapelessRecipe.Serializer();
        @Override public BotanicalKitchenRecipe fromJson(ResourceLocation id,JsonObject json){return new BotanicalKitchenRecipe(delegate.fromJson(id,json));}
        @Override public BotanicalKitchenRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buffer){return new BotanicalKitchenRecipe(delegate.fromNetwork(id,buffer));}
        @Override public void toNetwork(FriendlyByteBuf buffer,BotanicalKitchenRecipe recipe){delegate.toNetwork(buffer,recipe);}
    }
}
