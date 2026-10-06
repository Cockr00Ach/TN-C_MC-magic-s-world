package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureSavedData;
import com.tnc.tnc.home.HousingService;
import com.tnc.tnc.home.PlotCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

/** A living house flower: active travel, ownership and return cause its bloom. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class HomewardFlower {
    public static Block FLOWER;
    public static Item SEED;
    public static Item PETAL;
    public static Item TEA;
    static final int TRIP_DISTANCE = 512;
    static final int TRIP_TICKS = 1200;
    static final int TRIP_MOVEMENT = 128;
    static final int DAILY_BLOOMS = 3;
    private static final Map<UUID, Journey> JOURNEYS = new HashMap<>();

    private HomewardFlower() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    /** Session-only travel duration prevents disconnects and idle time at home from earning blooms. */
    static final class Journey {
        private int awayTicks;
        private int movedBlocks;
        private boolean qualified;
        private boolean wasHome;
        private BlockPos lastPosition;
        private ResourceKey<Level> lastDimension;

        boolean sample(boolean atHome, boolean farAway, int elapsedTicks,
                       BlockPos position, ResourceKey<Level> dimension) {
            if (farAway && lastPosition != null && lastDimension.equals(dimension)) {
                // A teleport counts as one short step, not an entire expedition.
                movedBlocks = Math.min(TRIP_MOVEMENT, movedBlocks +
                        Math.min(16, (int) Math.sqrt(lastPosition.distSqr(position))));
            }
            lastPosition = position;
            lastDimension = dimension;
            if (atHome) {
                boolean arrival = qualified && !wasHome;
                awayTicks = 0;
                movedBlocks = 0;
                qualified = false;
                wasHome = true;
                return arrival;
            }
            wasHome = false;
            if (farAway) awayTicks = Math.min(TRIP_TICKS, awayTicks + elapsedTicks);
            else if (!qualified) { awayTicks = 0; movedBlocks = 0; }
            if (awayTicks >= TRIP_TICKS && movedBlocks >= TRIP_MOVEMENT) qualified = true;
            return false;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) return;
        ServerLevel overworld = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            if (sampleCamp(player)) continue;
            PlotCatalog.Plot plot = HousingService.mine(player, PlotCatalog.Kind.HOME);
            if (plot == null) { JOURNEYS.remove(player.getUUID()); continue; }
            CompoundTag home = HousingService.home(player.server, plot.id());
            if (home.getBoolean("Preparing") || !home.hasUUID("Owner")
                    || !home.getUUID("Owner").equals(player.getUUID())) continue;
            BlockPos origin = BlockPos.of(home.getLong("Origin"));
            BlockPos local = player.blockPosition().subtract(origin);
            boolean atHome = player.serverLevel() == overworld && plot.contains(local);
            BlockPos entry = origin.offset(plot.entry());
            long dx = (long) player.blockPosition().getX() - entry.getX();
            long dz = (long) player.blockPosition().getZ() - entry.getZ();
            boolean far = player.serverLevel() != overworld
                    || dx * dx + dz * dz >= (long) TRIP_DISTANCE * TRIP_DISTANCE;
            Journey journey = JOURNEYS.computeIfAbsent(player.getUUID(), ignored -> new Journey());
            if (journey.sample(atHome, far, 20, player.blockPosition(), player.level().dimension()))
                bloomOnReturn(overworld, plot, origin, home, player);
        }
    }

    /** A planted flower is a wilderness camp marker, not a house ownership gate. */
    static boolean bindCampFlower(ServerPlayer player, BlockPos pos, boolean moveCamp) {
        var saved=LifeSavedData.get(player.server);
        String dim=player.level().dimension().location().toString();
        for(var entry:saved.accounts.entrySet())if(!entry.getKey().equals(player.getUUID()))
            for(var flower:entry.getValue().homewardFlowers)
                if(flower.getString("Dimension").equals(dim)&&flower.getLong("Pos")==pos.asLong())return false;
        var account=saved.account(player.getUUID());
        boolean known=account.homewardFlowers.stream().anyMatch(f->f.getString("Dimension").equals(dim)&&f.getLong("Pos")==pos.asLong());
        if(!known&&account.homewardFlowers.size()>=256)return false;
        if(!known&&account.homewardFlowers.size()<256){var tag=new CompoundTag();tag.putString("Dimension",dim);tag.putLong("Pos",pos.asLong());account.homewardFlowers.add(tag);}
        if(moveCamp||account.campDimension.isEmpty()){
            account.campDimension=dim;account.campPosition=pos.asLong();JOURNEYS.remove(player.getUUID());
        }
        saved.setDirty();return true;
    }

    static boolean sampleCamp(ServerPlayer player) {
        var saved=LifeSavedData.get(player.server);var account=saved.account(player.getUUID());
        if(account.campDimension.isEmpty())return false;
        BlockPos camp=BlockPos.of(account.campPosition);
        boolean same=player.level().dimension().location().toString().equals(account.campDimension);
        boolean atHome=same&&player.blockPosition().distSqr(camp)<=16*16;
        boolean far=!same||player.blockPosition().distSqr(camp)>=(long)TRIP_DISTANCE*TRIP_DISTANCE;
        var journey=JOURNEYS.computeIfAbsent(player.getUUID(),ignored->new Journey());
        if(journey.sample(atHome,far,20,player.blockPosition(),player.level().dimension()))bloomCamp(player);
        return true;
    }

    static int bloomCamp(ServerPlayer player) {
        var saved=LifeSavedData.get(player.server);var a=saved.account(player.getUUID());
        if(!player.level().dimension().location().toString().equals(a.campDimension))return 0;
        long day=player.server.overworld().getGameTime()/24000;
        if(a.campBloomDay!=day){a.campBloomDay=day;a.campBloomCount=0;}
        int count=0;var level=player.serverLevel();var camp=BlockPos.of(a.campPosition);
        for(var flower:a.homewardFlowers){
            if(count+a.campBloomCount>=DAILY_BLOOMS)break;
            if(!flower.getString("Dimension").equals(a.campDimension))continue;
            var pos=BlockPos.of(flower.getLong("Pos"));
            if(pos.distSqr(camp)>16*16||level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)continue;
            var state=level.getBlockState(pos);
            if(state.is(FLOWER)&&!state.getValue(FlowerBlock.BLOOMING)&&!com.tnc.tnc.home.TownProtection.denied(player,pos)){
                level.setBlock(pos,state.setValue(FlowerBlock.BLOOMING,true),3);count++;
            }
        }
        a.campBloomCount+=count;saved.setDirty();return count;
    }

    static int bloomOnReturn(ServerLevel level, PlotCatalog.Plot plot, BlockPos origin,
                             CompoundTag home, ServerPlayer owner) {
        long day = level.getGameTime() / 24000L;
        if (home.getLong("HomewardBloomDay") != day) {
            home.putLong("HomewardBloomDay", day);
            home.putInt("HomewardBloomCount", 0);
        }
        int allowance = DAILY_BLOOMS - home.getInt("HomewardBloomCount");
        if (allowance <= 0) return 0;
        int bloomed = 0;
        BlockPos min = origin.offset(plot.min());
        BlockPos max = origin.offset(plot.max());
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (bloomed >= allowance) break;
            // A return never loads another chunk or reaches outside the purchased plot.
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(FLOWER) && !state.getValue(FlowerBlock.BLOOMING)) {
                level.setBlock(pos, state.setValue(FlowerBlock.BLOOMING, true), 3);
                bloomed++;
            }
        }
        if (bloomed > 0) {
            home.putInt("HomewardBloomCount", home.getInt("HomewardBloomCount") + bloomed);
            AdventureSavedData.get(owner.server).setDirty();
        }
        return bloomed;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        JOURNEYS.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void serverStopped(ServerStoppedEvent event) { JOURNEYS.clear(); }

    public static final class FlowerBlock extends BushBlock {
        public static final BooleanProperty BLOOMING = BooleanProperty.create("blooming");
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);

        public FlowerBlock() {
            super(BlockBehaviour.Properties.copy(Blocks.POPPY).noCollission()
                    .lightLevel(state -> state.getValue(BLOOMING) ? 4 : 0));
            registerDefaultState(stateDefinition.any().setValue(BLOOMING, false).setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,false));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BLOOMING,com.tnc.tnc.life.routes.RoutePlantBlock.WILD);
        }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
        @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            BlockState soil = level.getBlockState(pos.below());
            return soil.is(BlockTags.DIRT) || soil.is(Blocks.FARMLAND);
        }
        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                               InteractionHand hand, BlockHitResult hit) {
            if(player instanceof ServerPlayer serverPlayer&&com.tnc.tnc.home.TownProtection.denied(serverPlayer,pos))return InteractionResult.FAIL;
            if(player.isShiftKeyDown()&&player.getItemInHand(hand).isEmpty()){
                if(player instanceof ServerPlayer serverPlayer){
                    boolean bound=bindCampFlower(serverPlayer,pos,true);
                    serverPlayer.displayClientMessage(Component.literal(bound?"已把这朵花标为露营归处。远行后回来，它会迎接你。":"这朵花已认得另一位旅人；可自己种一朵。"),true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!state.getValue(BLOOMING)) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer owner) || com.tnc.tnc.home.TownProtection.denied(owner,pos))
                return InteractionResult.FAIL;
            Block.popResource(level, pos, player.getItemInHand(hand).is(net.minecraft.world.item.Items.SHEARS)?new ItemStack(SEED):new ItemStack(PETAL, 2));
            com.tnc.tnc.life.routes.RouteProgress.award(owner,"harvest/homeward_flower");
            level.setBlock(pos, state.setValue(BLOOMING, false).setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,false), 3);
            return InteractionResult.CONSUME;
        }
        @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
            if(!state.is(next.getBlock())&&level instanceof ServerLevel serverLevel){
                var saved=LifeSavedData.get(serverLevel.getServer());String dim=level.dimension().location().toString();
                for(var account:saved.accounts.values())account.homewardFlowers.removeIf(f->f.getString("Dimension").equals(dim)&&f.getLong("Pos")==pos.asLong());
                saved.setDirty();
            }
            super.onRemove(state,level,pos,next,moving);
        }
    }

    public static final class HomeSeedItem extends ItemNameBlockItem {
        public HomeSeedItem(Block block) { super(block, new Item.Properties()); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> text, TooltipFlag flag) {
            text.add(Component.literal("在获准野外或自家田地的魔法土上种植，八格内放流动魔力；潜行空手右键花株设置露营归处。"));
            text.add(Component.literal("远行512格，走动并停留一分钟；回到归处16格内开放，每游戏日最多三朵。"));
        }
        @Override public InteractionResult place(BlockPlaceContext context) {
            if (!context.getLevel().isClientSide) {
                if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
                if(com.tnc.tnc.home.TownProtection.denied(player,context.getClickedPos()))return InteractionResult.FAIL;
            }
            InteractionResult result=super.place(context);
            if(result.consumesAction()&&context.getPlayer() instanceof ServerPlayer player&&context.getLevel().getBlockState(context.getClickedPos()).is(FLOWER))bindCampFlower(player,context.getClickedPos(),false);
            return result;
        }
    }

    public static final class TeaItem extends Item {
        public TeaItem() {
            super(new Item.Properties().stacksTo(16).food(new net.minecraft.world.food.FoodProperties.Builder()
                    .nutrition(2).saturationMod(0.3F).alwaysEat()
                    .effect(() -> new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.REGENERATION, 100, 0), 1.0F).build()));
        }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> text, TooltipFlag flag) {
            text.add(Component.literal("归家时用花瓣与蜂蜜调制，饮用后短暂恢复生命。"));
        }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity) {
            ItemStack result = super.finishUsingItem(stack, level, entity);
            if (entity instanceof Player player && !player.getAbilities().instabuild) {
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (result.isEmpty()) return bottle;
                if (!level.isClientSide && !player.getInventory().add(bottle)) player.drop(bottle, false);
            }
            return result;
        }
    }

    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void register(RegisterEvent event) {
            event.register(Registries.BLOCK, helper -> {
                // Forge can deliver this callback more than once during the GameTest
                // registry lifecycle. A fresh instance at the same ID becomes an
                // override whose unregistered value prints as minecraft:air.
                if (FLOWER != null) return;
                FLOWER = new FlowerBlock();
                helper.register(id("homeward_flower"), FLOWER);
            });
            event.register(Registries.ITEM, helper -> {
                if (SEED != null) return;
                SEED = new HomeSeedItem(FLOWER);
                PETAL = new Item(new Item.Properties());
                TEA = new TeaItem();
                helper.register(id("homeward_flower_seed"), SEED);
                helper.register(id("homeward_flower_petal"), PETAL);
                helper.register(id("homecoming_tea"), TEA);
            });
        }
        @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event) {
            if (!event.getTabKey().equals(TNMod.TNC_TAB.getKey())) return;
            event.accept(SEED);
            event.accept(PETAL);
            event.accept(TEA);
        }
    }
}
