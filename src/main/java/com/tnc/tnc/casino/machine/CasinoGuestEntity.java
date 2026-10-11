package com.tnc.tnc.casino.machine;

import com.tnc.tnc.dialogue.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.*;

/** Casino staff and guests use real navigation, with no villager trades or village schedules. */
public final class CasinoGuestEntity extends Villager {
    private boolean reception;
    private BlockPos home=BlockPos.ZERO;
    private List<BlockPos> route=List.of();
    private int layoutVersion;
    private long nextPathAttempt;
    private record Budget(long tick,int used) {}
    private record Obstacles(long tick,List<net.minecraft.world.phys.AABB> boxes) {}
    private static final Map<Level,Budget> BUDGET=new WeakHashMap<>();
    private static final Map<Level,Obstacles> OBSTACLES=new WeakHashMap<>();
    public int layoutVersion(){return layoutVersion;}
    static boolean reservePath(Level l) {
        var old=BUDGET.get(l);long tick=l.getGameTime();int used=old!=null&&old.tick()==tick?old.used():0;
        if(used>=2)return false;BUDGET.put(l,new Budget(tick,used+1));return true;
    }
    public CasinoGuestEntity(EntityType<? extends Villager> type,Level level) {
        super(type,level);setPersistenceRequired();setInvulnerable(true);setAge(0);
    }
    public void configure(boolean front,int persona,BlockPos pos,List<BlockPos> walk) {
        reception=front;home=pos;layoutVersion=3;setNoGravity(false);
        route=walk.stream().filter(p->!p.equals(home)&&p.getY()==home.getY()&&Math.abs(p.getX()-home.getX())<=2&&p.distSqr(home)<=625).toList();setNoAi(front);
        nextPathAttempt=level().getGameTime()+20+persona%40;
        setCustomName(Component.literal(front?"赌场前台 · 小金":"赌场客人 "+persona));setCustomNameVisible(front);
        VillagerType[] types={VillagerType.PLAINS,VillagerType.DESERT,VillagerType.TAIGA,VillagerType.SAVANNA,VillagerType.SWAMP};
        VillagerProfession[] jobs={VillagerProfession.CARTOGRAPHER,VillagerProfession.LIBRARIAN,VillagerProfession.MASON,VillagerProfession.LEATHERWORKER};
        setVillagerData(new VillagerData(types[persona%types.length],jobs[persona%jobs.length],1));
        moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new Patrol());
        goalSelector.addGoal(3,new LookAtPlayerGoal(this,Player.class,4));goalSelector.addGoal(4,new RandomLookAroundGoal(this));
    }
    @Override protected void customServerAiStep() { /* Goal navigation replaces the vanilla work/home brain. */ }
    @Override public boolean canChangeDimensions(){return false;}
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){return null;}
    private final class Patrol extends Goal {
        private net.minecraft.world.level.pathfinder.Path path;
        private int pause;
        Patrol(){setFlags(EnumSet.of(Flag.MOVE));}
        public boolean canUse() {
            if(reception||route.isEmpty()||pause-->0||level().getGameTime()<nextPathAttempt)return false;
            nextPathAttempt=level().getGameTime()+40+random.nextInt(80);
            for(int attempt=0;attempt<1;attempt++) {
                var p=route.get(random.nextInt(route.size()));
                if(p.getY()!=home.getY()||Math.abs(p.getX()-home.getX())>2||p.distSqr(home)>625||p.distSqr(blockPosition())<4)continue;
                if(!reservePath(level()))return false;
                path=getNavigation().createPath(p,0);
                if(path!=null&&path.canReach()&&clearOfMachines(path))return true;
            }
            pause=30;return false;
        }
        public void start(){getNavigation().moveTo(path,.48);}
        public boolean canContinueToUse(){return !reception&&!getNavigation().isDone();}
        public void stop(){getNavigation().stop();pause=40+random.nextInt(100);}
    }
    private boolean clearOfMachines(net.minecraft.world.level.pathfinder.Path path) {
        var cached=OBSTACLES.get(level());long tick=level().getGameTime();
        if(cached==null||tick-cached.tick()>=100) {
            var boxes=new ArrayList<net.minecraft.world.phys.AABB>();
            for(int cx=(home.getX()-64)>>4;cx<=(home.getX()+64)>>4;cx++)for(int cz=(home.getZ()-64)>>4;cz<=(home.getZ()+64)>>4;cz++) {
                if(!level().hasChunk(cx,cz))continue;
                for(var e:level().getChunk(cx,cz).getBlockEntities().values())if(e instanceof TNMachineEntity m)
                    boxes.add(m.getBlockState().getShape(level(),m.getBlockPos()).bounds().move(m.getBlockPos()));
            }
            cached=new Obstacles(tick,List.copyOf(boxes));OBSTACLES.put(level(),cached);
        }
        for(int i=0;i<path.getNodeCount();i++) {
            var p=path.getNode(i).asBlockPos();var body=new net.minecraft.world.phys.AABB(p.getX()+.2,p.getY(),p.getZ()+.2,p.getX()+.8,p.getY()+1.95,p.getZ()+.8);
            for(var box:cached.boxes())if(box.intersects(body))return false;
        }
        return true;
    }
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(player instanceof ServerPlayer server&&distanceToSqr(player)<=36) {
            List<String> lines=reception?List.of(
                "欢迎来到天空赌场！地下大厅有轮盘、庄家牌桌、摇骰桌、老虎机和幸运转轮。每局投入一枚实体铜币。",
                "右键设备任意部位即可操作；潜行右键切换投注。轮盘桌的红、绿、黑按键可直接选色，绿零中奖返回36倍。",
                "结果出来后右键领奖，只有投注者能领取。庄家牌桌 A 最小、K 最大，同点返本；净赢钱会放烟花。"):
                List.of("我来逛逛赌场，先看看别人玩。","设备旁边留着通道，走累了也可以上楼休息。");
            var id=ResourceLocation.fromNamespaceAndPath("tnc",reception?"casino/reception":"casino/guest");
            var script=new DialogueScript(id,null,null,List.of(),null,List.of(),List.of(),lines.stream().map(line->new DialogueScript.Line(getCustomName().getString(),line)).toList());
            DialogueNetwork.openFor(server,script,getUUID());
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);t.putInt("CasinoLayoutVersion",layoutVersion);t.putBoolean("CasinoReception",reception);t.putLong("CasinoHome",home.asLong());
        t.putLongArray("CasinoRoute",route.stream().mapToLong(BlockPos::asLong).toArray());
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);layoutVersion=t.getInt("CasinoLayoutVersion");reception=t.getBoolean("CasinoReception");home=BlockPos.of(t.getLong("CasinoHome"));
        route=Arrays.stream(t.getLongArray("CasinoRoute")).mapToObj(BlockPos::of).toList();
        setNoAi(reception);setPersistenceRequired();setInvulnerable(true);setAge(0);
        nextPathAttempt=level().getGameTime()+20+getId()%80;
    }
}
