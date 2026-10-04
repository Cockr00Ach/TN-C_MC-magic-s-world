package com.tnc.tnc.production;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.HousingService;
import com.tnc.tnc.home.TownProtection;
import com.tnc.tnc.life.ManaRoot;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.network.MagicStoneNetwork;
import com.tnc.tnc.production.energy.OwnedManaPlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One inventory and one mana ledger for the entire 26-part, hollow forge. */
public final class MagicForgeBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int MAX_CHARGE = 200;
    public static final int MANA_PER_COIL = 40;
    public static final int FORGE_TICKS = 80;
    public static final int OUTPUT_SLOT = 4;
    public static final int DATA_CELLS_START = 6;
    public static final int UPGRADE_SLOT = 5;
    public static final int DATA_UPGRADE_USES = DATA_CELLS_START + ForgeStructure.CELL_COUNT;
    public static final int DATA_COUNT = DATA_UPGRADE_USES + 1;
    private static final int[] NO_DIRECT_PORT = {};
    private static final DustParticleOptions PLANT_SPARK =
            new DustParticleOptions(new Vector3f(.17f, .86f, .28f), 1.0f);

    private final NonNullList<ItemStack> items = NonNullList.withSize(6, ItemStack.EMPTY);
    private int charge;
    private int progress;
    private String selectedRecipe = "";
    @Nullable private UUID owner;
    private long lastScanTick = Long.MIN_VALUE;
    private ForgeStructure.Scan cachedScan = new ForgeStructure.Scan(new int[ForgeStructure.CELL_COUNT], 0);
    private boolean wasFormed;

    public MagicForgeBlockEntity(BlockPos pos, BlockState state) {
        super(MagicForgeContent.FORGE_ENTITY, pos, state);
    }

    public Direction front() {
        return getBlockState().hasProperty(MagicForgeBlock.FACING)
                ? getBlockState().getValue(MagicForgeBlock.FACING) : Direction.SOUTH;
    }

    public void setOwner(UUID id) {
        if (owner == null) { owner = id; setChanged(); }
    }

    @Nullable public UUID owner() { return owner; }

    /** A legacy unowned core can be claimed only by someone allowed at its position. */
    public boolean canOperate(ServerPlayer player, boolean claimLegacy) {
        if (TownProtection.denied(player, worldPosition)) return false;
        if (owner == null && claimLegacy) { owner = player.getUUID(); setChanged(); }
        return owner != null && owner.equals(player.getUUID());
    }

    public int charge() { return charge; }
    public int progress() { return progress; }

    public ForgeStructure.Scan refreshStructure() {
        if (level == null) return cachedScan;
        ForgeStructure.Scan report = ForgeStructure.scan(level, worldPosition, front());
        cachedScan = report;
        lastScanTick = level.getGameTime();
        boolean formed = report.formed();
        if (!level.isClientSide && formed != wasFormed) {
            wasFormed = formed;
            frameVisuals(formed);
            if (formed && level instanceof ServerLevel server) {
                BlockPos chamber = worldPosition.relative(front().getOpposite());
                server.sendParticles(ParticleTypes.END_ROD, chamber.getX() + .5, chamber.getY() + .5,
                        chamber.getZ() + .5, 24, .7, .7, .7, .04);
                level.playSound(null, chamber, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, .65f, 1.35f);
            }
            syncLit();
        }
        return report;
    }

    public void invalidateStructure() { lastScanTick = Long.MIN_VALUE; }

    public ForgeStructure.Scan report() {
        if (level == null) return cachedScan;
        if (lastScanTick == Long.MIN_VALUE || level.getGameTime() - lastScanTick >= 20)
            return refreshStructure();
        return cachedScan;
    }

    public boolean formedCached() { return report().formed(); }
    public boolean formed() { return refreshStructure().formed(); }

    /** Recheck all physical cells before an external inventory transfer. */
    public boolean formedForTransfer() {
        if (level == null || isRemoved() || level.getBlockEntity(worldPosition) != this) return false;
        if (!ForgeStructure.basicFormed(level, worldPosition, front())) {
            invalidateStructure();
            return false;
        }
        return formedCached();
    }

    public boolean automaticAccessAllowed() {
        if (!(level instanceof ServerLevel server) || owner == null) return false;
        var plot = HousingService.ownedAt(server, worldPosition);
        if (plot != null) {
            var record = HousingService.home(server.getServer(), plot.id());
            return record.hasUUID("Owner") && owner.equals(record.getUUID("Owner"))
                    && !record.getBoolean("Preparing");
        }
        return HousingService.plotAt(server, worldPosition) == null
                && !TownProtection.town(server, worldPosition);
    }

    private void frameVisuals(boolean formed) {
        if (level == null) return;
        for (int i = 0; i < ForgeStructure.CELL_COUNT; i++) {
            if (ForgeStructure.expected(i) != ForgeStructure.Part.FRAME) continue;
            BlockPos pos = ForgeStructure.at(worldPosition, front(), i);
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(MagicForgeContent.COPPER_FRAME) && state.getValue(ForgeFrameBlock.GLOW) != formed)
                level.setBlock(pos, state.setValue(ForgeFrameBlock.GLOW, formed), 3);
        }
    }

    public String firstIssueMessage() {
        ForgeStructure.Scan report = refreshStructure();
        if (report.formed()) return "炉台完整，可以锻造。";
        int i = report.firstBad();
        int code = report.code(i);
        if (code == ForgeStructure.UNLOADED) return "第" + (i / 9 + 1) + "层有区块未加载，炉台暂停。";
        if (code == ForgeStructure.FILLED_CHAMBER) return "中层中央必须留空作炉膛。";
        if (code == ForgeStructure.WRONG_DIRECTION) return "中层后方注能口应朝背面外侧。";
        if (code == ForgeStructure.SHARED_PART) return "两个炉芯不能共用 " + ForgeStructure.cellName(i) + " 的部件。";
        return ForgeStructure.cellName(i) + " 应放 " + ForgeStructure.expectedName(ForgeStructure.expected(i)) + "。";
    }

    public List<ManaForgeRecipe> recipes() {
        if (level == null) return List.of();
        return level.getRecipeManager().getAllRecipesFor(MagicForgeContent.FORGE_RECIPE_TYPE).stream()
                .sorted(Comparator.comparing(recipe -> recipe.getId().toString())).toList();
    }

    public int selectedIndex() {
        List<ManaForgeRecipe> recipes = recipes();
        for (int i = 0; i < recipes.size(); i++) if (recipes.get(i).getId().toString().equals(selectedRecipe)) return i;
        return -1;
    }

    @Nullable public ManaForgeRecipe chosenRecipe() {
        for (ManaForgeRecipe recipe : recipes()) if (recipe.getId().toString().equals(selectedRecipe)) return recipe;
        return null;
    }

    public boolean chooseRecipe(int index) {
        List<ManaForgeRecipe> recipes = recipes();
        if (index < 0 || index >= recipes.size()) return false;
        String next = recipes.get(index).getId().toString();
        if (!next.equals(selectedRecipe)) { selectedRecipe = next; progress = 0; setChanged(); }
        return true;
    }

    public ContainerData dataAccess() {
        return new ContainerData() {
            @Override public int get(int index) {
                ForgeStructure.Scan report = report();
                return switch (index) {
                    case 0 -> charge;
                    case 1 -> progress;
                    case 2 -> workTicks();
                    case 3 -> selectedIndex();
                    case 4 -> report.formed() ? 1 : 0;
                    case 5 -> report.firstBad();
                    case DATA_UPGRADE_USES -> upgradeUses(items.get(UPGRADE_SLOT));
                    default -> index >= DATA_CELLS_START && index < DATA_CELLS_START + ForgeStructure.CELL_COUNT
                            ? report.code(index - DATA_CELLS_START) : 0;
                };
            }
            @Override public void set(int index, int value) { /* server-owned; menu syncs client copy */ }
            @Override public int getCount() { return DATA_COUNT; }
        };
    }

    @Override public Component getDisplayName() { return Component.translatable("block.tnc.magic_forge"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MagicForgeMenu(id, inventory, this, dataAccess());
    }

    public int comparatorSignal() {
        if (!items.get(OUTPUT_SLOT).isEmpty()) return 15;
        return progress > 0 ? Math.max(1, Math.min(14, progress * 14 / Math.max(1, workTicks()))) : 0;
    }

    private int workTicks() {
        ManaForgeRecipe recipe = chosenRecipe();
        int base = recipe == null ? FORGE_TICKS : recipe.workTicks();
        return upgradeUses(items.get(UPGRADE_SLOT)) > 0 ? Math.max(1, (base * 4 + 4) / 5) : base;
    }

    /** A single removable upgrade; counters belong to the actual item, not the slot. */
    public static int upgradeCapacity(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals("tnc")) return 0;
        return switch (id.getPath()) { case "hearth_oil" -> 10; case "condensing_shell" -> 20; default -> 0; };
    }
    public static int upgradeUses(ItemStack stack) {
        int max = upgradeCapacity(stack);
        if (max == 0) return 0;
        return stack.hasTag() && stack.getTag().contains("ForgeUses")
                ? Math.max(0, Math.min(max, stack.getTag().getInt("ForgeUses"))) : max;
    }
    private void spendUpgrade() {
        ItemStack upgrade = items.get(UPGRADE_SLOT);
        int uses = upgradeUses(upgrade);
        if (uses <= 0) return;
        if (uses == 1) items.set(UPGRADE_SLOT, ItemStack.EMPTY);
        else upgrade.getOrCreateTag().putInt("ForgeUses", uses - 1);
    }

    public String injectMana(ServerPlayer player, BlockPos source) {
        if (!canOperate(player, true)) return "这座炉台不属于你，或当前位置禁止使用。";
        if (!formed()) return firstIssueMessage() + " 未扣魔力。";
        var magic = MagicStone.getOrNull(player);
        if (magic == null) return "魔法石尚未醒来，无法注魔。";
        int amount = Math.min(25, Math.min(magic.getMana(), MAX_CHARGE - charge));
        if (amount <= 0) return charge == MAX_CHARGE ? "炉内魔力已满。" : "魔力不足。";
        if (!magic.spendMana(amount)) return "魔力不足。";
        lineFx(player.blockPosition(), source, false);
        addCharge(amount, source);
        MagicStoneNetwork.syncTo(player);
        return "注入 " + amount + " 魔力，炉内现有 " + charge + "/" + MAX_CHARGE + "。";
    }

    public String useRootCore(ServerPlayer player, ItemStack held, BlockPos injector) {
        if (!canOperate(player, true)) return "这座炉台不属于你，或当前位置禁止使用。";
        if (!formed() || !injector.equals(ForgeStructure.at(worldPosition, front(), 1, 0, 1)))
            return firstIssueMessage() + " 根芯未消耗。";
        if (!held.is(TNMod.MANA_ROOT_CORE.get())) return "请手持蓄魔根芯。";
        if (MAX_CHARGE - charge < ManaRoot.CORE_FORGE_MANA)
            return "炉内需空出 " + ManaRoot.CORE_FORGE_MANA + " 魔力，才收下根芯。";
        if (!player.getAbilities().instabuild) held.shrink(1);
        lineFx(player.blockPosition(), injector, false);
        addCharge(ManaRoot.CORE_FORGE_MANA, injector);
        return "蓄魔根芯释放 " + ManaRoot.CORE_FORGE_MANA + " 魔力。";
    }

    public void addCharge(int amount) { addCharge(amount, worldPosition); }

    private void addCharge(int amount, BlockPos source) {
        if (amount <= 0 || level == null || level.isClientSide) return;
        int accepted = Math.min(amount, MAX_CHARGE - charge);
        if (accepted <= 0) return;
        charge += accepted;
        changed();
        lineFx(source, worldPosition, false);
        level.playSound(null, source, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, .6f, 1.4f);
    }

    private void lineFx(BlockPos from, BlockPos to, boolean green) {
        if (!(level instanceof ServerLevel server)) return;
        for (int n = 0; n <= 8; n++) {
            double t = n / 8.0;
            double x = from.getX() + .5 + (to.getX() - from.getX()) * t;
            double y = from.getY() + .75 + (to.getY() - from.getY()) * t;
            double z = from.getZ() + .5 + (to.getZ() - from.getZ()) * t;
            server.sendParticles(green ? PLANT_SPARK : ParticleTypes.ENCHANT, x, y, z, 1, .02, .02, .02, .01);
        }
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, MagicForgeBlockEntity forge) {
        forge.tickForge();
    }

    private void tickForge() {
        if (!(level instanceof ServerLevel server)) return;
        ForgeStructure.Scan structure = report();
        if (!structure.formed() || !ForgeStructure.basicFormed(level, worldPosition, front())) {
            invalidateStructure();
            return;
        }
        if (server.getGameTime() % 20 == 0) drawAdjacentPlant(server);
        ManaForgeRecipe recipe = chosenRecipe();
        if (recipe == null || charge < recipe.mana()) return;
        int[] costs = recipe.costs(this);
        if (costs == null) return;
        ItemStack output = items.get(OUTPUT_SLOT);
        ItemStack produced = recipe.getResultItem(server.registryAccess());
        if (!output.isEmpty() && (!ItemStack.isSameItemSameTags(output, produced)
                || output.getCount() + produced.getCount() > output.getMaxStackSize())) return;
        progress++;
        if (server.getGameTime() % 8 == 0) {
            BlockPos chamber = worldPosition.relative(front().getOpposite());
            server.sendParticles(ParticleTypes.SMALL_FLAME, chamber.getX() + .5, chamber.getY() + .45,
                    chamber.getZ() + .5, 3, .27, .18, .27, .01);
            server.sendParticles(ParticleTypes.ENCHANT, chamber.getX() + .5, chamber.getY() + .9,
                    chamber.getZ() + .5, 3, .38, .2, .38, .02);
            BlockPos vent = chamber.above();
            server.sendParticles(ParticleTypes.SMOKE, vent.getX() + .5, vent.getY() + .8,
                    vent.getZ() + .5, 2, .16, .05, .16, .01);
        }
        if (progress >= workTicks()) {
            // Inventory and mana are rechecked immediately before the atomic server mutation.
            costs = recipe.costs(this);
            if (costs != null && charge >= recipe.mana() && formed()) {
                for (int slot = 0; slot < 4; slot++) if (costs[slot] > 0) items.get(slot).shrink(costs[slot]);
                if (output.isEmpty()) items.set(OUTPUT_SLOT, produced.copy());
                else output.grow(produced.getCount());
                charge -= recipe.mana();
                spendUpgrade();
                completeFx(server);
            }
            progress = 0;
        }
        changed();
    }

    private void drawAdjacentPlant(ServerLevel server) {
        if (owner == null || charge >= MAX_CHARGE) return;
        BlockPos injector = ForgeStructure.at(worldPosition, front(), 1, 0, 1);
        BlockPos plantPos = injector.relative(front().getOpposite());
        if (!server.hasChunkAt(plantPos) || TownProtection.town(server, plantPos)
                && HousingService.ownedAt(server, plantPos) == null) return;
        var corePlot = HousingService.ownedAt(server, worldPosition);
        var plantPlot = HousingService.ownedAt(server, plantPos);
        if (!Objects.equals(corePlot, plantPlot)) return;
        if (plantPlot != null) {
            var record = HousingService.home(server.getServer(), plantPlot.id());
            if (!record.hasUUID("Owner") || !owner.equals(record.getUUID("Owner"))) return;
        }
        BlockState state = server.getBlockState(plantPos);
        if (!(state.getBlock() instanceof ManaPlantSource source)
                || !(server.getBlockEntity(plantPos) instanceof OwnedManaPlant owned)
                || !owner.equals(owned.manaOwner())) return;
        int actual = Math.max(0, Math.min(1, source.drawMana(server, plantPos, state, 1)));
        if (actual <= 0) return;
        charge += actual;
        changed();
        lineFx(plantPos, injector, true);
        lineFx(injector, worldPosition, true);
    }

    private void completeFx(ServerLevel server) {
        BlockPos chamber = worldPosition.relative(front().getOpposite());
        server.sendParticles(ParticleTypes.END_ROD, chamber.getX() + .5, chamber.getY() + .8,
                chamber.getZ() + .5, 28, .65, .55, .65, .07);
        server.playSound(null, chamber, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.05f);
    }

    private void syncLit() {
        if (level == null) return;
        BlockState state = getBlockState();
        boolean lit = wasFormed && charge > 0;
        if (state.hasProperty(MagicForgeBlock.LIT) && state.getValue(MagicForgeBlock.LIT) != lit)
            level.setBlock(worldPosition, state.setValue(MagicForgeBlock.LIT, lit), 3);
    }

    private void changed() {
        setChanged();
        if (level == null) return;
        syncLit();
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Format", 3);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Charge", charge);
        tag.putInt("Progress", progress);
        tag.putString("SelectedRecipe", selectedRecipe);
        if (owner != null) tag.putUUID("ForgeOwner", owner);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        if (tag.getInt("Format") >= 2) ContainerHelper.loadAllItems(tag, items);
        else {
            NonNullList<ItemStack> legacy = NonNullList.withSize(4, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, legacy);
            items.set(0, legacy.get(0));
            items.set(1, legacy.get(1));
            items.set(OUTPUT_SLOT, legacy.get(2));
            // Old fuel slot is no longer an energy input. Preserve the item in a
            // generic slot for manual retrieval rather than silently deleting it.
            items.set(2, legacy.get(3));
        }
        charge = Math.max(0, Math.min(MAX_CHARGE, tag.getInt("Charge")));
        progress = Math.max(0, tag.getInt("Progress"));
        selectedRecipe = tag.getString("SelectedRecipe");
        owner = tag.hasUUID("ForgeOwner") ? tag.getUUID("ForgeOwner") : null;
        lastScanTick = Long.MIN_VALUE;
        wasFormed = false;
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
        if (!taken.isEmpty()) changed();
        return taken;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack taken = ContainerHelper.takeItem(items, slot);
        if (!taken.isEmpty()) changed();
        return taken;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) return;
        if (slot == UPGRADE_SLOT && !stack.isEmpty() && upgradeUses(stack) <= 0) return;
        items.set(slot, stack.copy());
        if (!items.get(slot).isEmpty() && items.get(slot).getCount() > Math.min(getMaxStackSize(), stack.getMaxStackSize()))
            items.get(slot).setCount(Math.min(getMaxStackSize(), stack.getMaxStackSize()));
        if (slot == UPGRADE_SLOT && !items.get(slot).isEmpty()) items.get(slot).setCount(1);
        changed();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64
                && (!(player instanceof ServerPlayer server) || canOperate(server, false));
    }
    @Override public void clearContent() { for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY); changed(); }
    // Direct hopper/pipe access to the core would bypass the 26-part structure.
    @Override public int[] getSlotsForFace(Direction side) { return NO_DIRECT_PORT; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < 4 || slot == UPGRADE_SLOT && upgradeUses(stack) > 0;
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
}
