package com.tnc.tnc.production;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/** Four inputs, unchanged output, one removable upgrade and twenty-seven synced cells. */
public final class MagicForgeMenu extends AbstractContainerMenu {
    private final Container forgeInventory;
    private final ContainerData data;
    private final BlockPos pos;
    private final Inventory playerInventory;

    public MagicForgeMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    private MagicForgeMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory,
                inventory.player.level().getBlockEntity(pos) instanceof MagicForgeBlockEntity forge
                        ? forge : new SimpleContainer(6),
                new SimpleContainerData(MagicForgeBlockEntity.DATA_COUNT), pos);
    }

    public MagicForgeMenu(int id, Inventory inventory, MagicForgeBlockEntity forge, ContainerData data) {
        this(id, inventory, forge, data, forge.getBlockPos());
    }

    private MagicForgeMenu(int id, Inventory inventory, Container forgeInventory, ContainerData data, BlockPos pos) {
        super(MagicForgeContent.FORGE_MENU, id);
        this.forgeInventory = forgeInventory;
        this.data = data;
        this.pos = pos;
        this.playerInventory = inventory;
        checkContainerSize(forgeInventory, 6);
        checkContainerDataCount(data, MagicForgeBlockEntity.DATA_COUNT);
        for (int i = 0; i < 4; i++) addSlot(new Slot(forgeInventory, i, 18 + (i % 2) * 22, 60 + (i / 2) * 22));
        addSlot(new Slot(forgeInventory, 4, 109, 71) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new Slot(forgeInventory, MagicForgeBlockEntity.UPGRADE_SLOT, 109, 105) {
            @Override public boolean mayPlace(ItemStack stack) { return MagicForgeBlockEntity.upgradeUses(stack) > 0; }
            @Override public int getMaxStackSize() { return 1; }
            @Override public int getMaxStackSize(ItemStack stack) { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 79 + col * 18, 161 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 79 + col * 18, 219));
        addDataSlots(data);
    }

    public int charge() { return data.get(0); }
    public int progress() { return data.get(1); }
    public int workTicks() { return Math.max(1, data.get(2)); }
    public int selectedIndex() { return data.get(3); }
    public boolean formed() { return data.get(4) != 0; }
    public int firstBad() { return data.get(5); }
    public int cell(int index) { return data.get(MagicForgeBlockEntity.DATA_CELLS_START + index); }
    public int upgradeUses() { return data.get(MagicForgeBlockEntity.DATA_UPGRADE_USES); }
    public BlockPos pos() { return pos; }

    public List<ManaForgeRecipe> recipes() {
        return playerInventory.player.level().getRecipeManager()
                .getAllRecipesFor(MagicForgeContent.FORGE_RECIPE_TYPE).stream()
                .sorted(Comparator.comparing(recipe -> recipe.getId().toString())).toList();
    }

    public boolean selectRecipe(ServerPlayer player, ResourceLocation recipeId) {
        if (!(forgeInventory instanceof MagicForgeBlockEntity forge) || !stillValid(player)) return false;
        for (int i = 0; i < recipes().size(); i++) {
            if (recipes().get(i).getId().equals(recipeId)) {
                boolean changed = forge.chooseRecipe(i);
                broadcastChanges();
                return changed;
            }
        }
        return false;
    }

    @Override public boolean stillValid(Player player) { return forgeInventory.stillValid(player); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack moving = slot.getItem();
        ItemStack original = moving.copy();
        boolean moved;
        if (index < 6) moved = moveItemStackTo(moving, 6, slots.size(), true);
        else if (MagicForgeBlockEntity.upgradeUses(moving) > 0) {
            moved = moveItemStackTo(moving, 5, 6, false);
            if (!moved) moved = moveItemStackTo(moving, 0, 4, false);
        } else moved = moveItemStackTo(moving, 0, 4, false);
        if (!moved) return ItemStack.EMPTY;
        if (moving.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, moving);
        return original;
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server)
                || !(forgeInventory instanceof MagicForgeBlockEntity forge) || !stillValid(player)) return false;
        if (button == 0) {
            server.displayClientMessage(Component.literal(forge.injectMana(server, pos)), true);
        } else return false;
        broadcastChanges();
        return true;
    }
}
