package com.tnc.tnc.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Datapack-editable, four-slot, shapeless and server-authoritative mana forging. */
public final class ManaForgeRecipe implements Recipe<Container> {
    public record Input(Ingredient ingredient, int count) {}

    private final ResourceLocation id;
    private final List<Input> inputs;
    private final ItemStack result;
    private final int mana;
    private final int workTicks;
    private final String category;

    public ManaForgeRecipe(ResourceLocation id, List<Input> inputs, ItemStack result,
                           int mana, int workTicks, String category) {
        if (inputs.isEmpty() || inputs.size() > 4 || mana < 1 || mana > MagicForgeBlockEntity.MAX_CHARGE
                || workTicks < 1 || workTicks > 32_767 || result.isEmpty()
                || result.getCount() > result.getMaxStackSize()
                || inputs.stream().anyMatch(input -> input.count() < 1 || input.count() > 64
                        || input.ingredient().isEmpty()))
            throw new IllegalArgumentException("Invalid mana forging recipe " + id);
        this.id = id;
        this.inputs = List.copyOf(inputs);
        this.result = result.copy();
        this.mana = mana;
        this.workTicks = workTicks;
        this.category = switch (category) {
            case "utility", "workshop", "materials" -> category;
            default -> "materials";
        };
    }

    public List<Input> inputs() { return inputs; }
    public int mana() { return mana; }
    public int workTicks() { return workTicks; }
    public String category() { return category; }

    /** Returns per-slot costs, or null if ANY extra or missing stack exists. */
    @Nullable public int[] costs(Container container) {
        int occupied = 0;
        for (int slot = 0; slot < 4; slot++) if (!container.getItem(slot).isEmpty()) occupied++;
        if (occupied != inputs.size()) return null;
        int[] costs = new int[4];
        boolean[] used = new boolean[inputs.size()];
        return matchSlot(container, 0, used, costs) ? costs : null;
    }

    private boolean matchSlot(Container container, int slot, boolean[] used, int[] costs) {
        if (slot == 4) return true;
        ItemStack stack = container.getItem(slot);
        if (stack.isEmpty()) return matchSlot(container, slot + 1, used, costs);
        for (int i = 0; i < inputs.size(); i++) {
            Input input = inputs.get(i);
            if (used[i] || !input.ingredient().test(stack) || stack.getCount() < input.count()) continue;
            used[i] = true;
            costs[slot] = input.count();
            if (matchSlot(container, slot + 1, used, costs)) return true;
            used[i] = false;
            costs[slot] = 0;
        }
        return false;
    }

    @Override public boolean matches(Container container, Level level) { return costs(container) != null; }
    @Override public ItemStack assemble(Container container, RegistryAccess registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= inputs.size(); }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return result.copy(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return MagicForgeContent.FORGE_SERIALIZER; }
    @Override public RecipeType<?> getType() { return MagicForgeContent.FORGE_RECIPE_TYPE; }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<ManaForgeRecipe> {
        @Override public ManaForgeRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonArray array = GsonHelper.getAsJsonArray(json, "ingredients");
            if (array.isEmpty() || array.size() > 4) throw new JsonParseException("Mana forge needs 1–4 input types: " + id);
            List<Input> inputs = new ArrayList<>();
            for (var element : array) {
                JsonObject object = GsonHelper.convertToJsonObject(element, "ingredient");
                inputs.add(new Input(Ingredient.fromJson(object), GsonHelper.getAsInt(object, "count", 1)));
            }
            JsonObject output = GsonHelper.getAsJsonObject(json, "result");
            ResourceLocation itemId = ResourceLocation.parse(GsonHelper.getAsString(output, "item"));
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null || item == Items.AIR) throw new JsonParseException("Unknown mana forge output: " + itemId);
            ItemStack result = new ItemStack(item, GsonHelper.getAsInt(output, "count", 1));
            return new ManaForgeRecipe(id, inputs, result,
                    GsonHelper.getAsInt(json, "mana"), GsonHelper.getAsInt(json, "work_ticks", 80),
                    GsonHelper.getAsString(json, "category", "materials"));
        }

        @Override public @Nullable ManaForgeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int n = buffer.readVarInt();
            List<Input> inputs = new ArrayList<>();
            for (int i = 0; i < n; i++) inputs.add(new Input(Ingredient.fromNetwork(buffer), buffer.readVarInt()));
            return new ManaForgeRecipe(id, inputs, buffer.readItem(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf());
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, ManaForgeRecipe recipe) {
            buffer.writeVarInt(recipe.inputs.size());
            for (Input input : recipe.inputs) { input.ingredient().toNetwork(buffer); buffer.writeVarInt(input.count()); }
            buffer.writeItem(recipe.result);
            buffer.writeVarInt(recipe.mana);
            buffer.writeVarInt(recipe.workTicks);
            buffer.writeUtf(recipe.category);
        }
    }
}
