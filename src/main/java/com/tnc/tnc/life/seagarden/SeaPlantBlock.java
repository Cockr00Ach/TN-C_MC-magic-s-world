package com.tnc.tnc.life.seagarden;

import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SeaPlantBlock extends Block implements net.minecraft.world.level.block.LiquidBlockContainer {
    public static final IntegerProperty STAGE=IntegerProperty.create("stage",0,2);
    private static final Vec3 TENDRIL_DRAG=new Vec3(.62,.72,.62);
    final SeaGarden.Kind kind;
    private static final VoxelShape SHAPE=box(1,0,1,15,15,15);

    SeaPlantBlock(SeaGarden.Kind kind) {
        super(Properties.copy(Blocks.SEAGRASS).noCollission().noOcclusion().strength(.3F)
                .lightLevel(s -> switch (kind) {
                    case LANTERN -> switch (s.getValue(STAGE)) { case 0 -> 10; case 1 -> 13; default -> 3; };
                    case BLOSSOM -> 2+4*s.getValue(STAGE);
                    case ANEMONE -> s.getValue(STAGE)==2 ? 9 : 3;
                    default -> 1;
                }));
        this.kind=kind;
        registerDefaultState(defaultBlockState().setValue(STAGE,0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {builder.add(STAGE);}
    @Override public boolean isRandomlyTicking(BlockState state) { return false; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {return SHAPE;}
    @Override public FluidState getFluidState(BlockState state) {return Fluids.WATER.getSource(false);}
    @Override public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, net.minecraft.world.level.material.Fluid fluid) {return false;}
    @Override public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {return false;}
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if(kind==SeaGarden.Kind.LANTERN && state.getValue(STAGE)==0) {
            level.addParticle(ParticleTypes.BUBBLE,pos.getX()+.39+.22*random.nextDouble(),pos.getY()+.76,pos.getZ()+.39+.22*random.nextDouble(),0,.04,0);
        }
    }
    static boolean source(BlockState state) {return state.is(Blocks.WATER) && state.getFluidState().isSource();}
    static boolean seaBed(BlockState state) {
        return state.is(BlockTags.SAND) || state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.CORAL_BLOCKS) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.CALCITE) || state.is(Blocks.TUFF);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return seaBed(level.getBlockState(pos.below()));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var level=context.getLevel();
        var pos=context.getClickedPos();
        if (!source(level.getBlockState(pos))) return null;
        var state=defaultBlockState();
        if (kind==SeaGarden.Kind.BLOSSOM) state=state.setValue(STAGE,moonStage(level.getDayTime()));
        return state.canSurvive(level,pos) ? state : null;
    }
    @Override public BlockState updateShape(BlockState state, Direction face, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        if (face==Direction.DOWN && !state.canSurvive(level,pos)) return Blocks.WATER.defaultBlockState();
        return super.updateShape(state,face,neighbor,level,pos,neighborPos);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state,level,pos,old,moving);
        if (!old.is(this) && !level.isClientSide && kind==SeaGarden.Kind.BLOSSOM) level.scheduleTick(pos,this,20);
    }
    // Dawn and dusk are visible half-open transitions even after sleeping or /time changes.
    static int moonStage(long dayTime) {
        int t=(int)Math.floorMod(dayTime,24000L);
        if (t>=14000 && t<22200) return 2;
        return t>=12000 || t<1000 ? 1 : 0;
    }
    private void state(ServerLevel level, BlockPos pos, int stage, int delay) {
        level.setBlock(pos,defaultBlockState().setValue(STAGE,stage),Block.UPDATE_CLIENTS);
        if (delay>0) level.scheduleTick(pos,this,delay);
    }
    static void particles(ServerLevel level, BlockPos pos, ParticleOptions particle, int count) {
        level.sendParticles(particle,pos.getX()+.5,pos.getY()+.58,pos.getZ()+.5,count,.22,.25,.22,.025);
    }
    static void cue(ServerLevel level, BlockPos pos, float pitch) {
        level.playSound(null,pos,SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,SoundSource.BLOCKS,.5F,pitch);
    }
    private static boolean diver(ServerPlayer player) {return player.isAlive() && !player.isSpectator() && player.isUnderWater();}
    boolean takeBreath(ServerLevel level, BlockPos pos, ServerPlayer diver) {
        var current=level.getBlockState(pos);
        if (kind!=SeaGarden.Kind.LANTERN || !current.is(this) || current.getValue(STAGE)!=0
                || !diver(diver) || diver.getAirSupply()>=diver.getMaxAirSupply() || TownProtection.denied(diver,pos)) return false;
        diver.setAirSupply(Math.min(diver.getMaxAirSupply(),diver.getAirSupply()+100));
        state(level,pos,1,22);
        particles(level,pos,ParticleTypes.BUBBLE,18);
        cue(level,pos,1.5F);
        diver.displayClientMessage(Component.translatable("seagarden.tnc.lantern_breath"),true);
        return true;
    }
    void prime(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(STAGE)!=0 || level.getBlockTicks().hasScheduledTick(pos,this)) return;
        int delay=kind==SeaGarden.Kind.BINDWEED ? 14 : 8;
        state(level,pos,1,delay);
        particles(level,pos,ParticleTypes.BUBBLE,4);
        cue(level,pos,.85F);
    }
    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof Player player) || !player.isAlive() || player.isSpectator() || !player.isUnderWater()) return;
        if (kind==SeaGarden.Kind.BINDWEED && state.getValue(STAGE)==2) {
            // Use the same contact multiplier in local swimming prediction and in server collision checks.
            if (!player.isCreative()) player.makeStuckInBlock(state,TENDRIL_DRAG);
        } else if (level instanceof ServerLevel server && (kind==SeaGarden.Kind.BINDWEED || kind==SeaGarden.Kind.ANEMONE)) {
            prime(server,pos,state);
        }
    }
    @Override public void onBlockStateChange(LevelReader reader, BlockPos pos, BlockState oldState, BlockState newState) {
        if (!(reader instanceof Level level) || kind!=SeaGarden.Kind.BINDWEED || !oldState.is(this)
                || oldState.getValue(STAGE)!=2 || newState.getValue(STAGE)==2) return;
        // On the client this runs when the open stage arrives, including cuts made by another swimmer.
        for (var swimmer : level.getEntitiesOfClass(Player.class,new AABB(pos),p->p.isAlive() && !p.isSpectator() && !p.isCreative() && p.isUnderWater())) {
            // move() consumes the multiplier on the next movement. Cancel its pending application
            // after cutting, except where the swimmer is still touching a second clasp.
            boolean another=BlockPos.betweenClosedStream(swimmer.getBoundingBox().deflate(.001)).anyMatch(at->{
                var other=level.getBlockState(at);
                return !at.equals(pos) && other.is(this) && other.getValue(STAGE)==2;
            });
            if (!another) swimmer.makeStuckInBlock(newState,Vec3.ZERO);
        }
    }
    @Override public void tick(BlockState current, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage=current.getValue(STAGE);
        switch (kind) {
            case BLOSSOM -> {
                int target=moonStage(level.getDayTime());
                if (stage!=target) {
                    state(level,pos,target,0);
                    if(target>stage) particles(level,pos,ParticleTypes.END_ROD,4);
                }
                level.scheduleTick(pos,this,180+Math.floorMod(pos.hashCode(),70));
            }
            case LANTERN -> {
                if (stage==1) {state(level,pos,2,600); particles(level,pos,ParticleTypes.BUBBLE,8);}
                else if (stage==2) {state(level,pos,0,0);cue(level,pos,1.4F);}
            }
            case BINDWEED, ANEMONE, FAN -> {
                if (stage==2) {state(level,pos,0,60);return;}
                if (stage!=1) return;
                boolean fan=kind==SeaGarden.Kind.FAN;
                var box=fan ? new AABB(pos).inflate(2.1) : new AABB(pos);
                var players=level.getEntitiesOfClass(ServerPlayer.class,box,SeaPlantBlock::diver);
                if (players.isEmpty()) {state(level,pos,0,30);return;}
                state(level,pos,2,fan?36:28);
                if(kind==SeaGarden.Kind.BINDWEED) {
                    particles(level,pos,ParticleTypes.FALLING_NECTAR,6);
                } else if (fan) {
                    for (var player : players) if (player.isSwimming())
                        player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE,44,0,false,true,true));
                    particles(level,pos,ParticleTypes.NAUTILUS,12);
                } else {
                    particles(level,pos,ParticleTypes.GLOW,18);
                    particles(level,pos,ParticleTypes.GLOW_SQUID_INK,6);
                }
                cue(level,pos,fan?1.5F:.66F);
            }
            default -> {}
        }
    }
    @Override public InteractionResult use(BlockState current, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind!=SeaGarden.Kind.LANTERN && !(kind==SeaGarden.Kind.BINDWEED && player.getItemInHand(hand).is(Items.SHEARS)))
            return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || TownProtection.denied(sp,pos)) return InteractionResult.FAIL;
        var server=sp.serverLevel();
        if (kind==SeaGarden.Kind.BINDWEED) {
            if (current.getValue(STAGE)==0) return InteractionResult.PASS;
            state(server,pos,0,60);
            // A previously scheduled reaction might fire sooner; an idle bindweed ignores that tick.
            if (!sp.isCreative()) player.getItemInHand(hand).hurtAndBreak(1,player,p->p.broadcastBreakEvent(hand));
            particles(server,pos,ParticleTypes.BUBBLE,6);
            cue(server,pos,1.1F);
        } else {
            if (!takeBreath(server,pos,sp)) {
                sp.displayClientMessage(Component.translatable(current.getValue(STAGE)==0?"seagarden.tnc.lantern_full":"seagarden.tnc.lantern_rest"),true);
            }
        }
        return InteractionResult.CONSUME;
    }
}
