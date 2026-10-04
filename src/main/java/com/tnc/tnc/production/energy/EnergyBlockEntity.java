package com.tnc.tnc.production.energy;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.HousingService;
import com.tnc.tnc.life.ManaRoot;
import com.tnc.tnc.life.OriginalCrops;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.network.MagicStoneNetwork;
import com.tnc.tnc.production.ManaPlantSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.EnumMap;

/** Persistent conservation ledger for generation, storage, and a real powered work lamp. */
public final class EnergyBlockEntity extends BlockEntity {
    public enum Kind {
        GENERATOR, CABLE, BATTERY, LAMP, PRESS;

        Item item() {
            return switch (this) {
                case GENERATOR -> EnergyContent.GENERATOR_ITEM;
                case CABLE -> EnergyContent.CABLE_ITEM;
                case BATTERY -> EnergyContent.BATTERY_ITEM;
                case LAMP -> EnergyContent.WORK_LAMP_ITEM;
                case PRESS -> EnergyContent.PAPER_PRESS_ITEM;
            };
        }
    }
    public enum LampMode {
        OFF("关闭"), ALWAYS("常亮"), NIGHT("仅夜间"), REDSTONE("红石控制");
        final String label;
        LampMode(String label) { this.label = label; }
        LampMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static final int MAX_MANA = 200;
    public static final int GENERATOR_FE = 2000;
    public static final int BATTERY_FE = 5000;
    public static final int LAMP_FE = 200;
    public static final int PRESS_FE = 1000;
    private static final DustParticleOptions GREEN_FLOW =
            new DustParticleOptions(new Vector3f(.24f, .9f, .42f), .85f);
    private Kind kind;
    private int mana;
    private int energy;
    private LampMode mode = LampMode.NIGHT;
    private UUID owner;
    private boolean networkLimited;
    private long lastOutputTick = Long.MIN_VALUE;
    private int outputThisTick;
    private int lampBurnTicks;
    private ItemStack lampCrystal = ItemStack.EMPTY;
    private ItemStack lampLens = ItemStack.EMPTY;
    public static final int SAVING_TICKS = 20 * 60 * 10;
    private int reeds;
    private int paper;
    private int waterUnits;
    private int pressProgress;
    private EnumMap<Direction, LazyOptional<IEnergyStorage>> sideCapabilities;
    private EnumMap<Direction, LazyOptional<IItemHandler>> itemCapabilities;

    public EnergyBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyContent.ENERGY_ENTITY, pos, state);
        kind = state.getBlock() instanceof EnergyBlock block ? block.kind() : Kind.CABLE;
        createCapability();
    }

    private void createCapability() {
        sideCapabilities = new EnumMap<>(Direction.class);
        itemCapabilities = new EnumMap<>(Direction.class);
        for (Direction side : Direction.values()) sideCapabilities.put(side, LazyOptional.of(() -> new IEnergyStorage() {
            private boolean allowed() {
                return !isRemoved() && level instanceof ServerLevel server
                        && server.getBlockEntity(worldPosition) == EnergyBlockEntity.this && owner != null
                        && EnergyPermissions.mayExposeTo(server, worldPosition, side, owner);
            }
            @Override public int receiveEnergy(int amount, boolean simulate) {
                if (!allowed() || kind == Kind.CABLE || kind == Kind.GENERATOR || amount <= 0) return 0;
                int accepted = Math.min(amount, capacity() - energy);
                if (!simulate) addEnergy(accepted);
                return accepted;
            }
            @Override public int extractEnergy(int amount, boolean simulate) {
                if (!allowed() || kind == Kind.CABLE || kind == Kind.LAMP || kind == Kind.PRESS || amount <= 0) return 0;
                return extractOutput(amount, simulate);
            }
            @Override public int getEnergyStored() { return allowed() ? energy : 0; }
            @Override public int getMaxEnergyStored() { return allowed() ? capacity() : 0; }
            @Override public boolean canExtract() { return allowed() && (kind == Kind.GENERATOR || kind == Kind.BATTERY); }
            @Override public boolean canReceive() { return allowed() && (kind == Kind.BATTERY || kind == Kind.LAMP || kind == Kind.PRESS); }
        }));
        for (Direction side : Direction.values()) itemCapabilities.put(side,
                LazyOptional.of(() -> new PressItemHandler(side)));
    }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,
            @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER && kind == Kind.PRESS
                && side != null && !isRemoved() && level instanceof ServerLevel server
                && server.getBlockEntity(worldPosition) == this && owner != null
                && EnergyPermissions.mayExposeTo(server, worldPosition, side, owner))
            return itemCapabilities.get(side).cast();
        return capability == ForgeCapabilities.ENERGY && kind != Kind.CABLE
                && side != null && !isRemoved() && level instanceof ServerLevel server
                && server.getBlockEntity(worldPosition) == this
                && owner != null && EnergyPermissions.mayExposeTo(server, worldPosition, side, owner)
                ? sideCapabilities.get(side).cast() : super.getCapability(capability, side);
    }

    @Override public void invalidateCaps() {
        super.invalidateCaps();
        sideCapabilities.values().forEach(LazyOptional::invalidate);
        itemCapabilities.values().forEach(LazyOptional::invalidate);
    }

    @Override public void reviveCaps() {
        super.reviveCaps();
        createCapability();
    }

    public Kind kind() { return kind; }
    @Nullable public UUID owner() { return owner; }
    public int mana() { return mana; }
    public int energy() { return energy; }
    public int capacity() {
        return switch (kind) {
            case GENERATOR -> GENERATOR_FE;
            case BATTERY -> BATTERY_FE;
            case LAMP -> LAMP_FE;
            case PRESS -> PRESS_FE;
            case CABLE -> 0;
        };
    }
    public LampMode mode() { return mode; }

    public void claim(UUID id) {
        if (id != null && !id.equals(owner)) { owner = id; setChanged(); }
    }

    public boolean mayUse(ServerPlayer player) {
        return owner == null || owner.equals(player.getUUID())
                || HousingService.mayDecorate(player, worldPosition);
    }

    public int addMana(int amount) {
        if (kind != Kind.GENERATOR || amount <= 0) return 0;
        int accepted = Math.min(amount, MAX_MANA - mana);
        mana += accepted;
        if (accepted > 0) setChanged();
        return accepted;
    }

    public int addEnergy(int amount) {
        if (amount <= 0 || kind == Kind.CABLE) return 0;
        int accepted = Math.min(amount, capacity() - energy);
        energy += accepted;
        if (accepted > 0) setChanged();
        return accepted;
    }

    public int removeEnergy(int amount) {
        if (amount <= 0) return 0;
        int sent = Math.min(amount, energy);
        energy -= sent;
        if (sent > 0) setChanged();
        return sent;
    }

    /** The network and all sided FE consumers share one real 20 FE output budget per tick. */
    int extractOutput(int amount, boolean simulate) {
        if (amount <= 0 || (kind != Kind.GENERATOR && kind != Kind.BATTERY)
                || isRemoved() || !(level instanceof ServerLevel server)
                || server.getBlockEntity(worldPosition) != this) return 0;
        long now = server.getGameTime();
        int used = now == lastOutputTick ? outputThisTick : 0;
        int sent = Math.min(amount, Math.min(energy, Math.max(0, 20 - used)));
        if (!simulate && sent > 0) {
            if (now != lastOutputTick) { lastOutputTick = now; outputThisTick = 0; }
            outputThisTick += sent;
            removeEnergy(sent);
        }
        return sent;
    }

    public String interact(ServerPlayer player, ItemStack held, boolean sneaking) {
        if (kind == Kind.PRESS) return interactPress(player, held, sneaking);
        if (kind == Kind.LAMP) return interactLamp(player, held, sneaking);
        if (kind != Kind.GENERATOR || sneaking) return status();
        if (held.is(TNMod.MANA_ROOT_CORE.get())) {
            if (MAX_MANA - mana < ManaRoot.CORE_FORGE_MANA)
                return "魔力仓须空出30格才可投入根芯；" + status();
            if (!player.getAbilities().instabuild) held.shrink(1);
            addMana(ManaRoot.CORE_FORGE_MANA);
            chargeFx(player.serverLevel(), player);
            return "根芯释放30魔力；" + status();
        }
        if (!held.isEmpty()) return status();
        var stone = MagicStone.getOrNull(player);
        if (stone == null) return "魔法石尚未醒来，无法注入；" + status();
        int accepted = Math.min(25, Math.min(stone.getMana(), MAX_MANA - mana));
        if (accepted <= 0) return "魔力不足或发电座已满；" + status();
        if (!stone.spendMana(accepted)) return "魔力不足，未扣除。";
        addMana(accepted);
        MagicStoneNetwork.syncTo(player);
        chargeFx(player.serverLevel(), player);
        return "注入" + accepted + "魔力；" + status();
    }

    private static boolean itemId(ItemStack stack, String path) {
        if (stack.isEmpty()) return false;
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getNamespace().equals("tnc") && id.getPath().equals(path);
    }
    public static int savingTicks(ItemStack stack) {
        if (!itemId(stack, "energy_saving_crystal")) return 0;
        return stack.hasTag() && stack.getTag().contains("LoadedTicks")
                ? Math.max(0, Math.min(SAVING_TICKS, stack.getTag().getInt("LoadedTicks"))) : SAVING_TICKS;
    }
    public int crystalTicks() { return savingTicks(lampCrystal); }
    public boolean hasFocusLens() { return itemId(lampLens, "focus_lens"); }

    private String interactLamp(ServerPlayer player, ItemStack held, boolean sneaking) {
        if (itemId(held, "energy_saving_crystal")) {
            if (!lampCrystal.isEmpty()) return "已有节能晶芯，潜行空手拆下后再换；" + status();
            int ticks = savingTicks(held);
            if (ticks == 0) return "晶芯已耗尽，不能靠拆装重置时间。";
            lampCrystal = held.copyWithCount(1);
            lampCrystal.getOrCreateTag().putInt("LoadedTicks", ticks);
            if (!player.getAbilities().instabuild) held.shrink(1);
            setChanged();
            return "装入节能晶芯：每8刻耗1 FE；" + status();
        }
        if (itemId(held, "focus_lens")) {
            if (!lampLens.isEmpty()) return "已有聚光镜片；" + status();
            lampLens = held.copyWithCount(1);
            if (!player.getAbilities().instabuild) held.shrink(1);
            setChanged(); updateLampState(player.serverLevel());
            return "装入聚光片，真实光照12→15；" + status();
        }
        if (held.isEmpty() && sneaking) {
            ItemStack removed;
            if (!lampCrystal.isEmpty()) { removed = lampCrystal; lampCrystal = ItemStack.EMPTY; }
            else if (!lampLens.isEmpty()) { removed = lampLens; lampLens = ItemStack.EMPTY; }
            else {
                mode = mode.next(); setChanged(); updateLampState(player.serverLevel());
                return "作业灯设为「" + mode.label + "」；" + status();
            }
            player.getInventory().add(removed);
            if (!removed.isEmpty()) net.minecraft.world.level.block.Block.popResource(player.serverLevel(), worldPosition, removed);
            setChanged(); updateLampState(player.serverLevel());
            return "已拆下升级，剩余时间随物品保存；" + status();
        }
        return status() + "；潜行空手先拆晶芯再拆镜片，无升级时切模式。";
    }

    private String interactPress(ServerPlayer player, ItemStack held, boolean sneaking) {
        if (held.is(OriginalCrops.TIDE_REED_STEM)) {
            int count = Math.min(held.getCount(), 64 - reeds);
            if (count == 0) return "苇仓已满；" + status();
            reeds += count;
            if (!player.getAbilities().instabuild) held.shrink(count);
            setChanged();
            return "投入听潮苇" + count + "根；" + status();
        }
        if (held.is(Items.WATER_BUCKET)) {
            if (waterUnits != 0) return "先用完现有水，水桶每次注满8份；" + status();
            waterUnits = 8;
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
                ItemStack empty = new ItemStack(Items.BUCKET);
                player.getInventory().add(empty);
                if (!empty.isEmpty()) net.minecraft.world.level.block.Block.popResource(
                        player.serverLevel(), worldPosition, empty);
            }
            setChanged();
            return "注入1桶真水（8份），空桶已退；" + status();
        }
        if (held.isEmpty() && sneaking) {
            if (paper == 0) return "还没有成纸；" + status();
            ItemStack collected = new ItemStack(Items.PAPER, paper);
            int before = collected.getCount();
            player.getInventory().add(collected);
            int taken = before - collected.getCount();
            paper -= taken;
            if (taken > 0) setChanged();
            return (taken > 0 ? "取出普通纸" + taken + "张；" : "背包已满，成纸留在机器内；") + status();
        }
        return status() + "；手持听潮苇/水桶右键投料，潜行空手取纸；上/侧面可自动投苇，底面可导出成纸。";
    }

    public String status() {
        String suffix = networkLimited ? "；线路超过64段，远端未接通" : "";
        return switch (kind) {
            case GENERATOR -> "发电座 魔力" + mana + "/200 · 电" + energy + "/2000 FE" + suffix;
            case BATTERY -> "蓄能匣 " + energy + "/5000 FE" + suffix;
            case LAMP -> "作业灯 " + mode.label + " · " + energy + "/200 FE · 光" + (hasFocusLens() ? 15 : 12)
                    + (crystalTicks() > 0 ? " · 节能" + (crystalTicks() + 19) / 20 + "秒" : "")
                    + (energy == 0 && lampBurnTicks == 0 ? "；储能空" : "");
            case CABLE -> "导能线：同一主人、已加载区块内连通；最多64段。";
            case PRESS -> "苇纸压纹机 电" + energy + "/1000 FE · 苇" + reeds
                    + "/64 · 水" + waterUnits + "/8份 · 成纸" + paper + "/64"
                    + (paper > 61 ? "；成品满，已停机" : waterUnits == 0 ? "；请投入水桶"
                    : reeds < 2 ? "；请投入两根听潮苇" : energy < 80 ? "；待电80 FE"
                    : "；压纹中" + pressProgress + "/80") + suffix;
        };
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, EnergyBlockEntity node) {
        if (!(level instanceof ServerLevel server) || node.owner == null
                || !EnergyPermissions.mayOperate(server, pos, node.owner)) return;
        switch (node.kind) {
            case GENERATOR -> node.tickGenerator(server);
            case BATTERY -> node.tickBattery(server);
            case LAMP -> node.tickLamp(server);
            case PRESS -> node.tickPress(server);
            case CABLE -> { }
        }
    }

    private void tickGenerator(ServerLevel level) {
        if (level.getGameTime() % 20 == 0 && mana < MAX_MANA) {
            for (Direction direction : Direction.values()) {
                BlockPos sourcePos = worldPosition.relative(direction);
                if (!level.hasChunkAt(sourcePos)
                        || !EnergyPermissions.mayDrawPlant(level, sourcePos, owner)) continue;
                BlockState sourceState = level.getBlockState(sourcePos);
                if (!(sourceState.getBlock() instanceof ManaPlantSource plant)) continue;
                int drawn = plant.drawMana(level, sourcePos, sourceState, Math.min(1, MAX_MANA - mana));
                if (drawn > 0) {
                    addMana(Math.min(1, drawn));
                    greenLine(level, sourcePos, worldPosition);
                }
            }
        }
        int convert = Math.min(2, Math.min(mana, (GENERATOR_FE - energy) / 5));
        if (convert > 0) {
            mana -= convert;
            addEnergy(convert * 5);
            setChanged();
        }
        if (energy > 0) {
            var result = EnergyNetwork.distribute(level, this, 20);
            networkLimited = result.truncated();
        }
    }

    private void tickBattery(ServerLevel level) {
        if (energy > 0) {
            var result = EnergyNetwork.distribute(level, this, 20);
            networkLimited = result.truncated();
        }
    }

    private void tickLamp(ServerLevel level) {
        if (!wantsLight(level)) {
            if (lampBurnTicks != 0) { lampBurnTicks = 0; setChanged(); }
        } else if (lampBurnTicks > 1) {
            lampBurnTicks--;
            setChanged();
        } else if (energy > 0) {
            removeEnergy(1);
            lampBurnTicks = crystalTicks() > 0 ? 8 : 4;
            setChanged();
        } else {
            lampBurnTicks = 0;
        }
        if (lampBurnTicks > 0 && crystalTicks() > 0) {
            int ticks = crystalTicks() - 1;
            if (ticks == 0) lampCrystal = ItemStack.EMPTY;
            else lampCrystal.getOrCreateTag().putInt("LoadedTicks", ticks);
            setChanged();
        }
        updateLampState(level);
    }

    private void tickPress(ServerLevel level) {
        boolean ready = energy >= 80 && reeds >= 2 && waterUnits > 0 && paper <= 61;
        if (!ready) {
            if (pressProgress != 0) { pressProgress = 0; setChanged(); }
        } else {
            pressProgress++;
            if (pressProgress >= 80) {
                removeEnergy(80);
                reeds -= 2;
                waterUnits--;
                paper += 3;
                pressProgress = 0;
                level.sendParticles(GREEN_FLOW, worldPosition.getX() + .5,
                        worldPosition.getY() + .95, worldPosition.getZ() + .5,
                        8, .28, .08, .28, .01);
                level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE,
                        SoundSource.BLOCKS, .45f, .8f);
            }
            setChanged();
        }
        BlockState state = getBlockState();
        boolean active = energy >= 80 && reeds >= 2 && waterUnits > 0 && paper <= 61;
        if (state.getValue(EnergyBlock.LIT) != active)
            level.setBlock(worldPosition, state.setValue(EnergyBlock.LIT, active), 3);
    }

    private final class PressItemHandler implements IItemHandler {
        private final Direction side;
        private PressItemHandler(Direction side) { this.side = side; }
        private boolean allowed() {
            return kind == Kind.PRESS && !isRemoved() && level instanceof ServerLevel server
                    && server.getBlockEntity(worldPosition) == EnergyBlockEntity.this && owner != null
                    && EnergyPermissions.mayExposeTo(server, worldPosition, side, owner);
        }
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) {
            if (slot != 0 || !allowed()) return ItemStack.EMPTY;
            return side == Direction.DOWN ? new ItemStack(Items.PAPER, paper)
                    : new ItemStack(OriginalCrops.TIDE_REED_STEM, reeds);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || !allowed() || side == Direction.DOWN
                    || !stack.is(OriginalCrops.TIDE_REED_STEM)) return stack;
            int accepted = Math.min(stack.getCount(), 64 - reeds);
            if (accepted <= 0) return stack;
            if (!simulate) { reeds += accepted; setChanged(); }
            ItemStack remaining = stack.copy();
            remaining.shrink(accepted);
            return remaining;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || !allowed() || side != Direction.DOWN || amount <= 0 || paper == 0)
                return ItemStack.EMPTY;
            int removed = Math.min(amount, paper);
            if (!simulate) { paper -= removed; setChanged(); }
            return new ItemStack(Items.PAPER, removed);
        }
        @Override public int getSlotLimit(int slot) { return slot == 0 ? 64 : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && side != Direction.DOWN && stack.is(OriginalCrops.TIDE_REED_STEM);
        }
    }

    private boolean wantsLight(ServerLevel level) {
        return switch (mode) {
            case OFF -> false;
            case ALWAYS -> true;
            case NIGHT -> !level.isDay();
            case REDSTONE -> level.hasNeighborSignal(worldPosition);
        };
    }

    private void updateLampState(ServerLevel level) {
        BlockState state = getBlockState();
        if (kind != Kind.LAMP || !state.hasProperty(EnergyBlock.LIT)) return;
        boolean lit = wantsLight(level) && (lampBurnTicks > 0 || energy > 0);
        boolean focused = hasFocusLens();
        if (state.getValue(EnergyBlock.LIT) != lit || state.getValue(EnergyBlock.FOCUSED) != focused)
            level.setBlock(worldPosition, state.setValue(EnergyBlock.LIT, lit).setValue(EnergyBlock.FOCUSED, focused), 3);
    }

    private static void greenLine(ServerLevel level, BlockPos from, BlockPos to) {
        for (int i = 1; i <= 4; i++) {
            double t = i / 5.0;
            level.sendParticles(GREEN_FLOW,
                    from.getX() + .5 + (to.getX() - from.getX()) * t,
                    from.getY() + .65 + (to.getY() - from.getY()) * t,
                    from.getZ() + .5 + (to.getZ() - from.getZ()) * t,
                    1, 0, 0, 0, 0);
        }
    }

    private void chargeFx(ServerLevel level, ServerPlayer player) {
        for (int i = 1; i <= 7; i++) {
            double t = i / 8.0;
            level.sendParticles(GREEN_FLOW,
                    player.getX() + (worldPosition.getX() + .5 - player.getX()) * t,
                    player.getEyeY() + (worldPosition.getY() + .7 - player.getEyeY()) * t,
                    player.getZ() + (worldPosition.getZ() + .5 - player.getZ()) * t,
                    1, 0, 0, 0, 0);
        }
        level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.BLOCKS, .5f, 1.35f);
    }

    public void writePortableState(ItemStack item) {
        CompoundTag portable = new CompoundTag();
        if (kind == Kind.GENERATOR) portable.putInt("Mana", mana);
        portable.putInt("Energy", energy);
        if (kind == Kind.LAMP) portable.putString("Mode", mode.name());
        if (kind == Kind.LAMP) portable.putInt("BurnTicks", lampBurnTicks);
        if (kind == Kind.LAMP && !lampCrystal.isEmpty()) portable.put("LampCrystal", lampCrystal.save(new CompoundTag()));
        if (kind == Kind.LAMP && !lampLens.isEmpty()) portable.put("LampLens", lampLens.save(new CompoundTag()));
        if (kind == Kind.PRESS) {
            portable.putInt("Reeds", reeds);
            portable.putInt("Paper", paper);
            portable.putInt("WaterUnits", waterUnits);
            portable.putInt("PressProgress", pressProgress);
        }
        portable.putLong("LastOutputTick", lastOutputTick);
        portable.putInt("OutputThisTick", outputThisTick);
        item.addTagElement("BlockEntityTag", portable);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Mana", mana);
        tag.putInt("Energy", energy);
        tag.putString("Mode", mode.name());
        tag.putInt("BurnTicks", lampBurnTicks);
        if (!lampCrystal.isEmpty()) tag.put("LampCrystal", lampCrystal.save(new CompoundTag()));
        if (!lampLens.isEmpty()) tag.put("LampLens", lampLens.save(new CompoundTag()));
        tag.putInt("Reeds", reeds);
        tag.putInt("Paper", paper);
        tag.putInt("WaterUnits", waterUnits);
        tag.putInt("PressProgress", pressProgress);
        tag.putLong("LastOutputTick", lastOutputTick);
        tag.putInt("OutputThisTick", outputThisTick);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        mana = Math.max(0, Math.min(MAX_MANA, tag.getInt("Mana")));
        energy = Math.max(0, Math.min(capacity(), tag.getInt("Energy")));
        lampBurnTicks = Math.max(0, Math.min(8, tag.getInt("BurnTicks")));
        lampCrystal = tag.contains("LampCrystal") ? ItemStack.of(tag.getCompound("LampCrystal")) : ItemStack.EMPTY;
        if (savingTicks(lampCrystal) <= 0) lampCrystal = ItemStack.EMPTY;
        else lampCrystal.setCount(1);
        lampLens = tag.contains("LampLens") ? ItemStack.of(tag.getCompound("LampLens")) : ItemStack.EMPTY;
        if (!hasFocusLens()) lampLens = ItemStack.EMPTY;
        else lampLens.setCount(1);
        reeds = Math.max(0, Math.min(64, tag.getInt("Reeds")));
        paper = Math.max(0, Math.min(64, tag.getInt("Paper")));
        waterUnits = Math.max(0, Math.min(8, tag.getInt("WaterUnits")));
        pressProgress = Math.max(0, Math.min(79, tag.getInt("PressProgress")));
        lastOutputTick = tag.contains("LastOutputTick") ? tag.getLong("LastOutputTick") : Long.MIN_VALUE;
        outputThisTick = Math.max(0, Math.min(20, tag.getInt("OutputThisTick")));
        try { mode = LampMode.valueOf(tag.getString("Mode")); }
        catch (IllegalArgumentException ignored) { mode = LampMode.NIGHT; }
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}
