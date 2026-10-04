package com.tnc.tnc.production;

import com.tnc.tnc.home.HousingService;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** A port has no inventory. Every cached handle is bound to this BE, one face and one core instance. */
public final class ForgePortBlockEntity extends BlockEntity {
    private final Map<Direction, LazyOptional<IItemHandler>> handlers = new EnumMap<>(Direction.class);
    private final Map<Direction, MagicForgeBlockEntity> bindings = new EnumMap<>(Direction.class);

    public ForgePortBlockEntity(BlockPos pos, BlockState state) {
        super(MagicForgeContent.PORT_ENTITY, pos, state);
    }

    private boolean isOutput() { return getBlockState().is(MagicForgeContent.OUTPUT_PORT); }

    @Nullable private MagicForgeBlockEntity findCore() {
        if (level == null || level.isClientSide || isRemoved()
                || level.getBlockEntity(worldPosition) != this) return null;
        if (isOutput()) {
            BlockPos candidate = worldPosition.above();
            if (!level.hasChunkAt(candidate)) return null;
            if (level.getBlockEntity(candidate) instanceof MagicForgeBlockEntity forge
                    && ForgeStructure.at(candidate, forge.front(), 0, 2, 1).equals(worldPosition)) return forge;
            return null;
        }
        if (!getBlockState().is(MagicForgeContent.INPUT_PORT)) return null;
        for (Direction front : Direction.Plane.HORIZONTAL) {
            Direction right = front.getCounterClockWise();
            for (int side : new int[]{-1, 1}) {
                BlockPos chamber = worldPosition.relative(right, -side);
                BlockPos candidate = chamber.relative(front);
                if (!level.hasChunkAt(candidate)) continue;
                if (level.getBlockEntity(candidate) instanceof MagicForgeBlockEntity forge
                        && forge.front() == front
                        && ForgeStructure.at(candidate, front, 1, 1, side + 1).equals(worldPosition)) return forge;
            }
        }
        return null;
    }

    private boolean sameProperty(Direction face) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockPos neighbor = worldPosition.relative(face);
        if (!server.hasChunkAt(neighbor)) return false;
        var here = HousingService.ownedAt(server, worldPosition);
        var there = HousingService.ownedAt(server, neighbor);
        if (here == null && (HousingService.plotAt(server, worldPosition) != null
                || TownProtection.town(server, worldPosition))) return false;
        if (there == null && (HousingService.plotAt(server, neighbor) != null
                || TownProtection.town(server, neighbor))) return false;
        return Objects.equals(here, there);
    }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction face) {
        if (capability != ForgeCapabilities.ITEM_HANDLER || face == null
                || (isOutput() ? face != Direction.DOWN : face == Direction.DOWN)
                || !sameProperty(face)) return super.getCapability(capability, face);
        MagicForgeBlockEntity boundCore = findCore();
        if (boundCore == null) return super.getCapability(capability, face);
        if (bindings.get(face) != boundCore) {
            LazyOptional<IItemHandler> old = handlers.remove(face);
            if (old != null) old.invalidate();
            bindings.put(face, boundCore);
        }
        return handlers.computeIfAbsent(face, key ->
                LazyOptional.of(() -> new PortHandler(key, boundCore, isOutput()))).cast();
    }

    @Override public void invalidateCaps() {
        super.invalidateCaps();
        handlers.values().forEach(LazyOptional::invalidate);
        handlers.clear();
        bindings.clear();
    }

    @Override public void reviveCaps() { super.reviveCaps(); handlers.clear(); bindings.clear(); }

    private final class PortHandler implements IItemHandler {
        private final Direction face;
        private final MagicForgeBlockEntity boundCore;
        private final boolean output;

        private PortHandler(Direction face, MagicForgeBlockEntity boundCore, boolean output) {
            this.face = face;
            this.boundCore = boundCore;
            this.output = output;
        }

        @Nullable private MagicForgeBlockEntity activeCore() {
            if (level == null || isRemoved() || level.getBlockEntity(worldPosition) != ForgePortBlockEntity.this
                    || output != isOutput() || !sameProperty(face)
                    || boundCore.isRemoved() || level.getBlockEntity(boundCore.getBlockPos()) != boundCore
                    || findCore() != boundCore || !boundCore.automaticAccessAllowed()
                    || !boundCore.formedForTransfer()) return null;
            return boundCore;
        }

        @Override public int getSlots() { return output ? 1 : 4; }
        @Override public ItemStack getStackInSlot(int slot) {
            MagicForgeBlockEntity core = activeCore();
            if (core == null || slot < 0 || slot >= getSlots()) return ItemStack.EMPTY;
            return core.getItem(output ? 4 : slot).copy();
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (output || stack.isEmpty() || slot < 0 || slot >= 4) return stack;
            MagicForgeBlockEntity core = activeCore();
            if (core == null) return stack;
            ItemStack current = core.getItem(slot);
            if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, stack)) return stack;
            int room = Math.min(stack.getMaxStackSize(), core.getMaxStackSize()) - current.getCount();
            int accepted = Math.min(room, stack.getCount());
            if (accepted <= 0) return stack;
            if (!simulate) {
                ItemStack replacement = current.isEmpty() ? stack.copyWithCount(accepted) : current.copy();
                if (!current.isEmpty()) replacement.grow(accepted);
                core.setItem(slot, replacement);
            }
            return stack.copyWithCount(stack.getCount() - accepted);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!output || slot != 0 || amount <= 0) return ItemStack.EMPTY;
            MagicForgeBlockEntity core = activeCore();
            if (core == null) return ItemStack.EMPTY;
            ItemStack current = core.getItem(4);
            if (current.isEmpty()) return ItemStack.EMPTY;
            int accepted = Math.min(amount, current.getCount());
            return simulate ? current.copyWithCount(accepted) : core.removeItem(4, accepted);
        }
        @Override public int getSlotLimit(int slot) { return slot >= 0 && slot < getSlots() ? 64 : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return !output && slot >= 0 && slot < 4 && !stack.isEmpty() && activeCore() != null;
        }
    }
}
