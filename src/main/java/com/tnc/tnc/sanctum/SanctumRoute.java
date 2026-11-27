package com.tnc.tnc.sanctum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One party's linear approach to the final boss. Persistent milestones are independent of combat actors. */
@Mod.EventBusSubscriber(modid="tnc")
public final class SanctumRoute {
    // Saved copies are rebuilt from the party's milestone. Only this live carrier may join a level.
    private static final Set<Mob> liveCarriers=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    @SubscribeEvent public static void rejectStaleCarrier(EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide() && event.getEntity() instanceof Mob m
                && m.getPersistentData().getBoolean("TncSanctumRouteCarrier") && !liveCarriers.contains(m)) event.setCanceled(true);
    }
    enum Step { FOYER, GALLERY, CHIMES, WEDGE, GATE, CRANK, PAGE, CANDLE, ARMED, BOSS }
    private final ServerLevel level;
    private final BlockPos origin;
    private final String actorTag;
    private final SanctumArchitecture masonry;
    private final SanctumGateCombat gate;
    private final List<SanctumGalleryFoe> foes=new ArrayList<>();
    private final Set<UUID> actors=new HashSet<>();
    private Mob idol;
    private Step step=Step.FOYER;
    private int tick, lampTurns, wave, chimes, wrongChimes, shadeTicks;
    private boolean chandelier, waveActive;
    private final java.util.Map<BlockPos,Long> presses=new java.util.HashMap<>();
    private List<ServerPlayer> party=List.of();

    public SanctumRoute(ServerLevel level, BlockPos origin) {
        this.level=level; this.origin=origin.immutable();
        actorTag="tnc_sanctum_route_"+origin.asLong();
        masonry=new SanctumArchitecture(level,origin,this::opened,this::removeOldActors);
        gate=new SanctumGateCombat(this);
    }
    public boolean built() {return masonry.built();}
    public BlockPos bossCenter() {return at(0,0,SanctumArchitecture.FINAL_Z);}
    public BlockPos checkpoint() {
        if(step.ordinal()>=Step.ARMED.ordinal()) return at(0,0,150);
        if(step.ordinal()>=Step.CRANK.ordinal()) return at(0,0,119);
        if(step==Step.GATE) return at(0,0,79);
        return origin;
    }
    public boolean readyForBoss() {return step==Step.BOSS;}
    /** Called after construction, once, when this party uses the administrator's boss shortcut. */
    void openBossRehearsal() {
        if(!built()||readyForBoss())return;
        retry();
        step=Step.BOSS;
        for(int i=0;i<SanctumArchitecture.DOORS.length;i++)masonry.door(i,true);
        bell(false);
    }
    public boolean owns(UUID id) {return actors.contains(id);}
    private boolean opened(int door) {
        return switch(door) {
            case 0 -> after(Step.FOYER);
            case 1 -> after(Step.GALLERY);
            case 2 -> after(Step.WEDGE);
            case 3 -> after(Step.CRANK);
            case 4 -> after(Step.CANDLE);
            default -> false;
        };
    }
    private boolean after(Step other) {return step.ordinal()>other.ordinal();}
    public CompoundTag save() {
        var tag=new CompoundTag();
        tag.putString("Stage",step.name());
        tag.putInt("Lamp",lampTurns); tag.putInt("Choir",wave);
        tag.putInt("Bells",chimes); tag.putBoolean("Chandelier",chandelier);
        return tag;
    }
    public void restore(CompoundTag tag) {
        if(!tag.contains("Stage")) return;
        try {step=Step.valueOf(tag.getString("Stage"));} catch(IllegalArgumentException e) {step=Step.FOYER;}
        lampTurns=Math.max(0,Math.min(3,tag.getInt("Lamp")));
        chimes=Math.max(0,Math.min(3,tag.getInt("Bells")));
        wave=Math.max(0,Math.min(3,tag.getInt("Choir")));
        chandelier=tag.getBoolean("Chandelier");
        retry();
    }
    public void retry() {
        discard(idol);idol=null;
        clearFoes();
        gate.reset();
        shadeTicks=0;
        if(step==Step.BOSS){step=Step.ARMED;if(built())bell(true);}
    }
    public void close() { discard(idol);idol=null;clearFoes(); gate.reset(); masonry.close(); }
    private void clearFoes() {
        for(SanctumGalleryFoe foe:foes)discard(foe.body);
        foes.clear();waveActive=false;
    }

    BlockPos at(int x,int y,int z) {return origin.offset(x,y,z);}
    Vec3 foot(int x,int z) {return Vec3.atBottomCenterOf(at(x,0,z));}
    ServerLevel level() {return level;}
    boolean partySource(net.minecraft.world.damagesource.DamageSource source) {
        var actor=source.getEntity();
        if(actor instanceof net.minecraft.world.entity.OwnableEntity ownable)actor=ownable.getOwner();
        return actor instanceof ServerPlayer member&&party.contains(member);
    }
    void tell(String line) {for(var p:party)p.sendSystemMessage(Component.literal(line));}
    void hint(String line) {for(var p:party)p.displayClientMessage(Component.literal(line),true);}
    void sound(BlockPos pos,net.minecraft.sounds.SoundEvent sound,float pitch) {
        level.playSound(null,pos,sound,SoundSource.BLOCKS,1.2f,pitch);
    }
    void ring(Vec3 center,double r,boolean red) {
        for(int n=0;n<24;n++) {
            double angle=n*Math.PI/12;
            level.sendParticles(red?ParticleTypes.CRIMSON_SPORE:ParticleTypes.END_ROD,
                    center.x+r*Math.cos(angle),center.y+.14,center.z+r*Math.sin(angle),1,0,0,0,0);
        }
    }
    Mob spawn(EntityType<? extends Mob> type,String model,String name,Vec3 pos,double health) {
        Mob mob=type.create(level);
        if(mob==null)throw new IllegalStateException("Cannot create sanctum carrier "+type);
        mob.moveTo(pos.x,pos.y,pos.z,180,0);
        mob.yBodyRot=180;mob.yHeadRot=180;
        mob.setNoAi(true);mob.setPersistenceRequired();mob.setSilent(true);mob.setNoGravity(true);
        CompoundTag equipment=mob.saveWithoutId(new CompoundTag());
        equipment.putString("DeathLootTable","minecraft:empty");
        mob.readAdditionalSaveData(equipment);
        mob.setCustomName(Component.literal(name));
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        mob.setHealth(mob.getMaxHealth());
        mob.addTag(actorTag);
        mob.getPersistentData().putBoolean("TncSanctumRouteCarrier",true);
        liveCarriers.add(mob);
        if(!model.isEmpty())SanctumAppearance.mark(mob,model);
        level.addFreshEntity(mob);
        actors.add(mob.getUUID());
        return mob;
    }
    void discard(Mob mob) {
        if(mob!=null){actors.remove(mob.getUUID());liveCarriers.remove(mob);mob.discard();}
    }
    private void removeOldActors(ChunkPos chunk) {
        AABB bounds=new AABB(chunk.getMinBlockX(),origin.getY()-4,chunk.getMinBlockZ(),
                chunk.getMaxBlockX()+1,origin.getY()+96,chunk.getMaxBlockZ()+1);
        for(Mob m:level.getEntitiesOfClass(Mob.class,bounds,m->m.getTags().contains(actorTag)&&!owns(m.getUUID()))) m.discard();
    }
    private void advance(Step next,int door,String text) {
        step=next;
        if(door>=0){masonry.door(door,true);sound(at(0,0,SanctumArchitecture.DOORS[door]),SoundEvents.IRON_DOOR_OPEN,.55f);}
        tell(text);
    }
    private void bell(boolean raised) {
        masonry.tollBell(raised);
    }
    private void mirror() {
        int[][] directions={{0,-1},{-1,0},{0,1},{1,0}};
        for(int i=0;i<4;i++){
            int[] d=directions[i];
            masonry.set(-7+d[0],-1,8+d[1],i==lampTurns?Blocks.OCHRE_FROGLIGHT:Blocks.CUT_COPPER);
            masonry.set(-7+d[0],2,8+d[1],i==lampTurns?Blocks.OCHRE_FROGLIGHT:Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB);
        }
        masonry.set(0,8,17,lampTurns==3?Blocks.OCHRE_FROGLIGHT:Blocks.CHISELED_POLISHED_BLACKSTONE);
    }
    private boolean near(ServerPlayer p,BlockPos pos) {return p.distanceToSqr(Vec3.atCenterOf(pos))<=25;}
    private boolean device(BlockPos pos,int x,int z) {
        return pos.getX()==origin.getX()+x && pos.getZ()==origin.getZ()+z
               && pos.getY()>=origin.getY() && pos.getY()<=origin.getY()+2;
    }
    private boolean fitting(BlockPos p,int x,int z,int half,int depth,int top){
        return Math.abs(p.getX()-origin.getX()-x)<=half && Math.abs(p.getZ()-origin.getZ()-z)<=depth
                && p.getY()>=origin.getY() && p.getY()<=origin.getY()+top && !level.getBlockState(p).isAir();
    }
    private void bellSignals(){
        for(int i=0;i<3;i++){
            int x=SanctumArchitecture.BELLS[i];
            for(int dot=0;dot<=i;dot++)masonry.set(x,-1,63-dot,i<chimes?Blocks.VERDANT_FROGLIGHT:Blocks.OCHRE_FROGLIGHT);
            masonry.set(x,5+i,65,i<chimes?Blocks.VERDANT_FROGLIGHT:Blocks.CHISELED_POLISHED_BLACKSTONE);
        }
    }
    private void toll(int x,int z){
        if(level.getBlockEntity(at(x,1,z)) instanceof net.minecraft.world.level.block.entity.BellBlockEntity bell)
            bell.onHit(net.minecraft.core.Direction.NORTH);
    }
    private boolean accept(BlockPos pos) {
        long now=level.getGameTime();
        Long previous=presses.put(new BlockPos(pos.getX(),origin.getY(),pos.getZ()),now);
        return previous==null||previous!=now;
    }

    public boolean interact(ServerPlayer player,BlockPos pos) {
        if(!built()||!near(player,pos)||player.isSpectator()||!player.isAlive())return false;
        if(fitting(pos,-7,8,1,2,4)) {
            if(step==Step.FOYER && accept(at(-7,0,8))){
                lampTurns=Math.min(3,lampTurns+1);
                mirror();
                sound(pos,SoundEvents.LEVER_CLICK,.7f+lampTurns*.16f);
                if(lampTurns==3)tell("灯纹合拢。走到灯对面的石柱后，站进断续的白纹。");
                else hint("铜镜转过一档 · "+lampTurns+" / 3");
            }
            return true;
        }
        if(fitting(pos,5,49,1,1,4)) {
            if(step==Step.GALLERY && accept(at(5,0,49))){
                shadeTicks=0; chandelier=true;
                masonry.set(5,2,49,Blocks.OCHRE_FROGLIGHT);
                tell("吊灯照到左壁的书龛。清除守卫后，翻开讲台残页。");
            }
            return true;
        }
        if(device(pos,-5,50)){
            if(step==Step.GALLERY && wave==3 && chandelier && shadeTicks==0)
                advance(Step.CHIMES,1,"第一页值夜录「小钟认一痕，中钟认两痕，大钟认三痕。按此顺序敲。」");
            else if(step==Step.GALLERY)hint(wave<3?"回廊里还有未醒的守卫。向前走，清除三拱敌人。":"书页漆黑。拨开对面吊灯的铜罩。");
            return true;
        }
        for(int i=0;i<3;i++) if(fitting(pos,SanctumArchitecture.BELLS[i],65,i==2?3:2,1,5+i)){
            if(step==Step.CHIMES && accept(at(SanctumArchitecture.BELLS[i],0,65))){
                toll(SanctumArchitecture.BELLS[i],65);
                sound(pos,SoundEvents.BELL_BLOCK,1.5f-i*.4f);
                if(i==chimes) {
                    if(++chimes==3)advance(Step.WEDGE,-1,"第三道回声从门后传来。右边的绞盘弹出铁楔，推入它便能进入门神庭。");
                    else hint("第 "+chimes+" 道锁扣已落。");
                } else {chimes=0;wrongChimes++;sound(pos,SoundEvents.CHAIN_BREAK,.53f);hint("回音断了。地砖上的一、二、三点光对应三口钟。");}
                bellSignals();
            }
            return true;
        }
        if(fitting(pos,4,71,2,1,4)){
            if(step==Step.WEDGE)advance(Step.GATE,2,"铁楔咬合。门神庭的外碑已亮。看地上的金锚，留心它的手掌。");
            return true;
        }
        if(fitting(pos,0,126,2,1,4)){
            if(step==Step.CRANK)advance(Step.PAGE,3,"绞盘转动。门后每一张长椅都背向神坛，只有尽头的书席还亮着。");
            return true;
        }
        if(device(pos,5,144)){
            if(step==Step.PAGE)advance(Step.CANDLE,-1,"末页「它以前从未睁眼。如果看见红光，立即背过身。」触碰唱经台的灯，把归路留在这里。");
            return true;
        }
        if(device(pos,0,148)){
            if(step==Step.CANDLE)advance(Step.ARMED,4,"唱经台的余灯记住了你。进入正殿，敲响中央走道尽头的值夜钟。");
            return true;
        }
        if(fitting(pos,0,SanctumArchitecture.ALTAR_Z,2,1,6)){
            if(step==Step.ARMED){discard(idol);idol=null;bell(false);advance(Step.BOSS,-1,"钟鸣了。闭目的天使双手慢慢抬起。");sound(pos,SoundEvents.BELL_RESONATE,.6f);}
            return true;
        }
        return false;
    }

    public void hurt(LivingHurtEvent e) {
        if(!owns(e.getEntity().getUUID()))return;
        if(gate.hurt(e))return;
        for(var foe:foes)if(foe.body==e.getEntity()){foe.hurt(e);break;}
    }

    public void tick(List<ServerPlayer> players) {
        party=players;
        if(!built()){
            masonry.tick();
            if(level.getGameTime()%80==0)hint("无昼圣堂 · 石门正在开启 "+masonry.percent()+"%");
            if(built()){
                mirror();
                if(chimes>0)bellSignals();
                if(chandelier)masonry.set(5,2,49,Blocks.OCHRE_FROGLIGHT);
                tell(step==Step.FOYER?"守灯前室。右键转动左侧铜镜，对准门纹，再借石柱挡住灯光。":instruction());
            }
            return;
        }
        if(players.isEmpty())return;
        tick++;
        guardBoundary(players);
        if(tick%100==0 && step!=Step.BOSS) hint(instruction());
        if(tick%60==0 && step!=Step.BOSS) markObjective();
        if(tick%200==0 && step!=Step.BOSS){
            var listener=players.get((tick/200)%players.size());
            sound(listener.blockPosition().offset(4,4,7),SoundEvents.AMBIENT_CAVE.value(),.65f);
        }
        if(step==Step.FOYER) foyer(players);
        if(step==Step.GALLERY) gallery(players);
        if(step==Step.GATE && gate.tick(players))advance(Step.CRANK,-1,"最后一尊门神也倒下了。转动庭后铜轮，进入静默礼拜堂。");
        if(step==Step.ARMED && (idol==null||idol.isRemoved()) && players.stream().anyMatch(p->p.distanceToSqr(foot(0,SanctumArchitecture.FINAL_Z))<74*74)){
            discard(idol);idol=spawn(EntityType.IRON_GOLEM,"angel","天使",foot(0,SanctumArchitecture.FINAL_Z),40);
            idol.setInvulnerable(true);
        }
    }
    private void guardBoundary(List<ServerPlayer> players) {
        int door=0;
        while(door<SanctumArchitecture.DOORS.length && opened(door))door++;
        if(door==SanctumArchitecture.DOORS.length)return;
        int plane=SanctumArchitecture.DOORS[door];
        for(var p:players){
            if(p.isCreative()||p.isSpectator()||p.getZ()<origin.getZ()+plane+1)continue;
            // Crossing a sealed doorway returns only to its threshold. Other room bounds are physical.
            BlockPos safe=at(0,0,plane-2);
            p.teleportTo(safe.getX()+.5,safe.getY(),safe.getZ()+.5);p.fallDistance=0;
        }
    }
    private String instruction() {
        return switch(step){
            case FOYER -> lampTurns<3?"守灯前室 · 转动左侧铜镜，照亮门纹":"灯纹已亮 · 站到中央石柱背光一侧的断纹上";
            case GALLERY -> wave<3?"折翼回廊 · 第 "+(wave+1)+" 拱的檐下传来抓挠声":!chandelier||shadeTicks>0?"回廊已静 · 拨开右侧铜罩，让灯照到书龛":"第一页被照亮了 · 翻开左侧壁龛的残页";
            case CHIMES -> "回声钟房 · 看脚下光点，按小、中、大敲钟（"+chimes+" / 3）";
            case WEDGE -> "回声钟房 · 将右前方绞盘的铁楔推入门闩";
            case GATE -> "双阙门神 · 踩亮锚引掌，掌印锁定后横移，再击中地面腕锁";
            case CRANK -> "门神庭 · 转动尽头的绞盘";
            case PAGE -> "静默礼拜堂 · 翻开右前方孤席上的末页";
            case CANDLE -> "静默礼拜堂 · 触碰中央的唱经台灯火";
            case ARMED -> "守曦大殿 · 沿铜线走到值夜钟，准备好后敲响";
            case BOSS -> "";
        };
    }
    private void markObjective() {
        BlockPos mark=switch(step){
            case FOYER->lampTurns<3?at(-7,2,8):at(5,0,8);
            case GALLERY->wave<3?at(0,2,25+wave*12):chandelier?at(-5,1,50):at(5,2,49);
            case CHIMES->wrongChimes>=2?at(SanctumArchitecture.BELLS[Math.min(2,chimes)],2,65):null;
            case WEDGE->at(4,1,71);case CRANK->at(0,1,126);case PAGE->at(5,1,144);
            case CANDLE->at(0,1,148);case ARMED->at(0,1,SanctumArchitecture.ALTAR_Z);
            default->null;
        };
        if(mark!=null)level.sendParticles(ParticleTypes.END_ROD,mark.getX()+.5,mark.getY()+1,mark.getZ()+.5,9,.25,.4,.25,.01);
    }
    private void foyer(List<ServerPlayer> players) {
        if(tick%6==0){
            double direction=(3-lampTurns)*Math.PI/2;
            for(int i=1;i<=13;i++){
                Vec3 point=foot(-7,8).add(Math.cos(direction)*i,.85,Math.sin(direction)*i);
                if(lampTurns==3 && i>=6)break;
                level.sendParticles(ParticleTypes.END_ROD,point.x,point.y,point.z,1,0,0,0,0);
            }
        }
        if(lampTurns<3)return;
        Vec3 lamp=foot(-7,8).add(0,1.4,0);
        AABB pillar=new AABB(at(-1,0,7),at(2,7,10));
        for(var p:players) {
            Vec3 torso=p.position().add(0,1,0);
            if(torso.distanceToSqr(lamp)<=16*16 && pillar.clip(lamp,torso).isPresent()
                    && p.getX()>origin.getX()+2 && Math.abs(p.getZ()-origin.getZ()-8.5)<3) {
                advance(Step.GALLERY,0,"一道白光从头顶越过。门上的眼睛闭合了，折翼回廊露出来。");break;
            }
        }
    }
    private void gallery(List<ServerPlayer> players) {
        if(shadeTicks>0 && --shadeTicks==0)masonry.set(5,2,49,chandelier?Blocks.OCHRE_FROGLIGHT:Blocks.PEARLESCENT_FROGLIGHT);
        if(wave>=3)return;
        int z=25+wave*12;
        if(!waveActive) {
            if(players.stream().noneMatch(p->p.distanceToSqr(foot(0,z))<=14*14))return;
            if(wave!=1)addFoe(true,-4,z,0);
            if(wave!=0)addFoe(false,4,z,65);
            if(wave==2 && players.size()>=3)addFoe(true,2,z+2,42);
            waveActive=true;
            tell(wave==0?"檐下的石像收翼。避开落尘线，砸地后击碎胸印。":wave==1?"锁链拖地。攻击正在捂灯的影鬼可以打断它。":"两种抓挠声同时响了，灯罩开始摇晃。");
        }
        boolean interrupted=foes.stream().anyMatch(f->f.body.isRemoved() && !f.body.isDeadOrDying());
        if(interrupted){clearFoes();return;}
        for(var it=foes.iterator();it.hasNext();){
            var f=it.next();
            if(!f.body.isAlive()){actors.remove(f.body.getUUID());liveCarriers.remove(f.body);it.remove();continue;}
            var target=players.stream().min(Comparator.comparingDouble(p->p.distanceToSqr(f.body))).orElseThrow();
            f.tick(players,target);
        }
        if(foes.isEmpty()){waveActive=false;wave++;if(wave==3)tell(instruction());}
    }
    private void addFoe(boolean stone,int x,int z,int offset) {
        Vec3 loc=foot(x,z).add(0,stone?2.8:0,0);
        Mob mob=spawn(EntityType.IRON_GOLEM,stone?"gargoyle":"shade",stone?"檐狱石像鬼":"失影侍鬼",loc,stone?28:22);
        foes.add(new SanctumGalleryFoe(this,mob,stone,offset));
    }
    void coverLamp() {
        shadeTicks=120;
        masonry.set(5,2,49,Blocks.TINTED_GLASS);
        tell("吊灯被罩住了。拨开铜罩能立即复明。");
    }
}
