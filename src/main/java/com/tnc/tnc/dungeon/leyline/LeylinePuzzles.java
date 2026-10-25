package com.tnc.tnc.dungeon.leyline;

import com.tnc.tnc.alchemy.*;
import com.tnc.tnc.arcana.*;
import com.tnc.tnc.dungeon.*;
import com.tnc.tnc.dungeon.DungeonData.Run;
import com.tnc.tnc.dungeon.DungeonGraph.Room;
import com.tnc.tnc.journey.JourneyData;
import com.tnc.tnc.life.pasture.*;
import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.mana.sight.ManaSightServer;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Server-authoritative, persistent mechanics. No inventory is trusted for stored mana.
 * interact consumes its own accepted ingredients; potion/spell are already-paid impact
 * callbacks and NEVER consume held items. A true return means the action was intercepted.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "tnc")
public final class LeylinePuzzles {
    private static final UUID BLESSING = UUID.fromString("2efc489e-bd12-4117-9f60-3d7f4831dc7b");
    private static CompoundTag state(Run run, Room room) { return LeylineService.state(run, room); }
    private static List<CompoundTag> nodes(CompoundTag s, String role) { return LeylineService.nodes(s, role); }
    private static BlockPos pos(CompoundTag n) { return BlockPos.of(n.getLong("Pos")); }
    private static BlockPos first(CompoundTag s, String role, BlockPos fallback) { var n = nodes(s, role); return n.isEmpty() ? fallback : pos(n.get(0)); }
    private static CompoundTag hit(CompoundTag s, String role, BlockPos at) {
        return nodes(s,role).stream().filter(n->{
            var p=pos(n);if(p.equals(at))return true;
            if(role.equals("crystal"))return at.getX()==p.getX()&&at.getZ()==p.getZ()&&at.getY()>=BlockPos.of(s.getLong("Origin")).getY()+1&&at.getY()<=p.getY()+1;
            if(role.equals("amber")&&n.contains("Center")){var c=BlockPos.of(n.getLong("Center"));return Math.abs(at.getX()-c.getX())<=1&&Math.abs(at.getZ()-c.getZ())<=1&&at.getY()>=c.getY()&&at.getY()<=c.getY()+3;}
            if(role.equals("echo_wall"))return at.getZ()==p.getZ()&&Math.abs(at.getX()-p.getX())<=5&&Math.abs(at.getY()-p.getY())<=4;
            return false;
        }).findFirst().orElse(null);
    }
    private static boolean near(ServerPlayer p, BlockPos at, double distance) { return at.distToCenterSqr(p.position()) <= distance * distance; }
    private static void say(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), true); }
    private static void dirty(ServerLevel l) { LeylineService.markDirty(l.getServer()); }
    private static CompoundTag personal(CompoundTag s, ServerPlayer p) { String k = "Player_" + p.getUUID(); if (!s.contains(k, Tag.TAG_COMPOUND)) s.put(k, new CompoundTag()); return s.getCompound(k); }
    private static boolean valid(ServerPlayer p, Run r, Room room, BlockPos at, boolean ranged) {
        if (r.closed || r.building || !r.members.contains(p.getUUID()) || !p.isAlive() || p.isSpectator()
            || com.tnc.tnc.combat.DownedCombat.isDowned(p) || !DungeonRealm.inside(p.level())) return false;
        var o = DungeonRealm.center(r, room);
        return Math.abs(p.getX() - o.getX()) <= 14 && Math.abs(p.getZ() - o.getZ()) <= 14
            && p.getY() >= o.getY() - 12 && p.getY() <= o.getY() + 32 && near(p, at, ranged ? 40 : 6);
    }
    private static boolean blocked(CompoundTag s) { return s.getBoolean("Solved") || s.getBoolean("Failed"); }
    private static void solve(ServerLevel l, Run r, Room room, ServerPlayer p) { if (!blocked(state(r,room))) LeylineService.solve(l,r,room,p); }
    private static void clearRole(ServerLevel l, CompoundTag s, String role) { DungeonRealm.write(() -> nodes(s,role).forEach(n -> { var at=pos(n); if(role.equals("echo_wall")) { for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=2;dy++)l.setBlock(at.offset(dx,dy,0),Blocks.AIR.defaultBlockState(),3); } else l.setBlock(at, Blocks.AIR.defaultBlockState(),3); })); }
    private static void give(ServerPlayer p, ItemStack stack) { if (!stack.isEmpty() && !p.getInventory().add(stack)) p.drop(stack,false); }
    private static String id(ItemStack stack) { var id = ForgeRegistries.ITEMS.getKey(stack.getItem()); return id == null ? "" : id.getPath(); }
    private static boolean cooldown(CompoundTag s, String key, long now, int ticks) { if (s.contains(key) && now - s.getLong(key) < ticks) return false; s.putLong(key,now); return true; }
    private static void emptyMana(ServerPlayer p) { var m = MagicStone.getOrNull(p); if(m != null) { m.setMana(0); MagicStoneNetwork.syncTo(p); } }
    private static void motion(ServerPlayer p, Vec3 speed) { p.setDeltaMovement(speed); p.hurtMarked=true; p.connection.send(new ClientboundSetEntityMotionPacket(p)); }
    private static void initialize(CompoundTag s, Run r, Room room) {
        if(s.getBoolean("PuzzleInitialized")) return;
        long seed=r.seed+r.floor*883L+room.id()*79L; var random=new Random(seed);
        s.putBoolean("PuzzleInitialized",true); s.putInt("TrueIndex",random.nextInt(9)); s.putInt("CoreIndex",random.nextInt(150));
        s.putInt("TreasureHour",random.nextInt(4)); s.putInt("MirrorCell",4); s.putInt("TreasureMask",LeylinePuzzleRules.treasureMask(seed));
        s.putIntArray("Notes",LeylinePuzzleRules.notes(seed)); s.putInt("Craving",random.nextInt(2));
    }

    public static void tick(ServerLevel l, Run r, Room room, List<ServerPlayer> party) {
        var s=state(r,room); initialize(s,r,room); long now=l.getGameTime();
        if(s.contains("PuzzleTick") && s.getLong("PuzzleTick")==now) return;
        s.putLong("PuzzleTick",now);
        var players=party.stream().filter(p->valid(p,r,room,p.blockPosition(),true)).toList();
        var kind=LeylineService.kind(r,room);
        if(players.isEmpty()) { s.remove("ScaleOwner"); s.putInt("ScaleSeconds",0); return; }
        if(kind==LeylineKind.PAWN) { for(var p:players) bathe(l,r,room,s,p,now); }
        if(kind==LeylineKind.ALCHEMY) { alchemyTick(l,r,room,s,players.get(0),now); }
        if(kind==LeylineKind.GRAVITY) gravity(s,r,room,players,now);
        if(s.getBoolean("Failed")) { DungeonRealm.doors(l,r,room,false); return; }
        if(s.getBoolean("Solved")) return;
        LeylineActors.tick(l,r,room,s,kind,players,now);
        switch(kind) {
            case BRIDGE,DUAL_BRIDGE -> { for(var p:players) bridge(l,r,room,s,p,kind==LeylineKind.DUAL_BRIDGE,now); }
            case TIDE -> { s.putInt("TidePhase",(int)Math.floorMod(now,100)); for(var p:players) tide(l,s,p,now); }
            case ECHO -> { for(var p:players) echo(l,s,p,now); }
            case LENS -> lens(l,s,players);
            case REFRACTION -> refraction(l,r,room,s,players.get(0));
            case FALSE_VAULT -> { if(reflected(l,s,"prism")) { s.putBoolean("IllusionBroken",true); clearRole(l,s,"vault"); solve(l,r,room,players.get(0)); } }
            case MUSIC -> { if(now%20==0) { int note=s.getIntArray("Notes")[(int)(now/20%3)]; for(var n:nodes(s,"crystal")) if(n.getInt("Index")==note) ManaSightServer.record(l,Vec3.atCenterOf(pos(n)),Vec3.atCenterOf(DungeonRealm.center(r,room)).add(0,2,0),16,LeylinePuzzleRules.NOTE_COLORS[note],4); } }
            case POLLUTION -> { var pool=first(s,"pool",DungeonRealm.center(r,room)); for(var p:players) if(near(p,pool,2)&&p.isInWater()&&ArcanaPhysiology.toxicity(p)>80) { ArcanaPhysiology.detox(p,80); poisonVines(l,r,room,s,p); break; } }
            case GRAVITY -> { }
            case RAPIDS -> { for(var p:players) rapids(l,r,room,s,p,now); }
            case SCALE -> scale(l,r,room,s,players,now);
            case CAULDRON -> mouthDrops(l,r,room,s);
            default -> { }
        }
        if(now%20==0) dirty(l);
    }

    /** Handles only physical room controls; successful ingredient operations debit here. */
    public static boolean interact(ServerPlayer p, Run r, Room room, BlockPos at, InteractionHand hand) {
        if(!valid(p,r,room,at,false)) return false;
        var l=p.serverLevel(); var s=state(r,room); initialize(s,r,room); var kind=LeylineService.kind(r,room);
        var held=p.getItemInHand(hand); String heldId=id(held); long now=l.getGameTime();
        if(kind==LeylineKind.PAWN && (hit(s,"pawn",at)!=null||hit(s,"spring_core",at)!=null)) return pawn(p,r,room,s,at,hand);
        if(kind==LeylineKind.ALCHEMY && alchemyPart(l,s,at)) return alchemyInteract(p,r,room,s,hand);
        if(blocked(s)) return false;
        // The direct-use path consumes only after an actual recipe/pollution transition.
        String potionId=held.getItem() instanceof ElixirItem e?e.kind.id:heldId;
        boolean bottled=held.getItem() instanceof ElixirItem || heldId.equals("strange_waste");
        if(acceptPotion(p,r,room,s,at,potionId)) { LeylineService.consumeHeld(p,hand); if(bottled&&!p.isCreative()) give(p,new ItemStack(Items.GLASS_BOTTLE)); dirty(l); return true; }
        boolean handled=true;
        switch(kind) {
            case BRIDGE,DUAL_BRIDGE -> { if(hit(s,"return",at)==null)return false;var o=DungeonRealm.center(r,room);p.teleportTo(l,o.getX()+.5,o.getY()+1,o.getZ()-9,0,0);say(p,"回到了桥前。切换F8辨认浮空落脚点。"); }
            case FALSE_VAULT -> { var n=hit(s,"vault",at); if(n==null) return false; int index=n.getInt("Index"); if(index==s.getInt("TrueIndex")) solve(l,r,room,p); else if((s.getInt("OpenedVaults")&(1<<index))==0) { s.putInt("OpenedVaults",s.getInt("OpenedVaults")|(1<<index)); LeylineService.spawnEcology(l,r,room,"crab",at.above()); p.hurt(p.damageSources().magic(),4); say(p,"假箱的死气回路断裂，拟态蟹从外壳中爬出！"); } }
            case TIDE -> { if(hit(s,"plant",at)==null&&hit(s,"goal",at)==null) return false; clearRole(l,s,"plant"); solve(l,r,room,p); }
            case ECHO -> { if(hit(s,"echo_wall",at)==null) return false; say(p,"淡蓝残影在墙前举起了水属性法杖。"); }
            case SUNDIAL -> { var n=hit(s,"dial_door",at); if(n==null) return false; int hour=n.getInt("Index"); if(hour!=s.getInt("Hour")) say(p,"影针还没有指向这扇门。"); else if(hour==s.getInt("TreasureHour")) { var o=DungeonRealm.center(r,room); DungeonRealm.write(()->{ for(int side=-1;side<=1;side++)for(int y=-1;y<=2;y++)l.setBlock(at.offset(at.getX()==o.getX()?side:0,y,at.getX()==o.getX()?0:side),Blocks.AIR.defaultBlockState(),3); }); solve(l,r,room,p); } else if((s.getInt("TriggeredHours")&(1<<hour))==0) { s.putInt("TriggeredHours",s.getInt("TriggeredHours")|(1<<hour)); LeylineService.spawnEcology(l,r,room,"flash",at); say(p,"红光门后的强光闪灵苏醒了。"); } }
            case STOMACH -> { if(hit(s,"treasure",at)==null) return false; if(s.getBoolean("Sedated")) solve(l,r,room,p); else { LeylineActors.wake(l,r,room,s); say(p,"巨虫翻了个身。上风口传来低沉的呼吸声。"); } }
            case VINES -> { if(hit(s,"basin",at)==null) return false; if(held.getItem() instanceof ManaBottleItem) { var ledger=PastureBottleLedger.get(l); if(ledger.amount(held)>=100) { ledger.withdraw(held,100); clearRole(l,s,"vine"); s.putBoolean("Fed",true); solve(l,r,room,p); } else say(p,"干涸的魔力渠需要真实储存的100M魔力。"); } else if(heldId.equals("flowing_mana_bucket")||held.getItem() instanceof ManaBucketItem) { if(!p.isCreative()) p.setItemInHand(hand,new ItemStack(Items.BUCKET)); clearRole(l,s,"vine"); s.putBoolean("Fed",true); solve(l,r,room,p); } else say(p,"石盆空空，魔藤仍在饥饿地收紧。"); }
            case MEAL -> { var n=hit(s,"plate",at); if(n==null) return false; int index=n.getInt("Index"); if(index==0||index==2) { say(p,"火与水已固定在石盘；甘味可桥接相克。"); return true; } int bit=1<<index; if((s.getInt("Ingredients")&bit)!=0) return true; var taste=ArcanaMeals.taste(held); var expected=index==1?ArcanaMeals.Taste.NEUTRAL:index==3?ArcanaMeals.Taste.WIND:ArcanaMeals.Taste.EARTH; if(taste==expected) { s.putInt("Ingredients",s.getInt("Ingredients")|bit); LeylineService.consumeHeld(p,hand); if((s.getInt("Ingredients")&26)==26) solve(l,r,room,p); } else { say(p,"石盘拒绝了这味食材，未消耗。"); } }
            case GRAVITY -> { if(hit(s,"gravity_node",at)!=null) { if(heldId.equals(RouteKind.VALVE.id)) { if(s.getLong("GravityUntil")<=now) { s.putLong("GravityUntil",now+400); LeylineService.consumeHeld(p,hand); } } else say(p,"分流阀或逆向风法可以翻转魔力流。"); } else if(hit(s,"ceiling_goal",at)!=null&&personal(s,p).getBoolean("ReachedCeiling")) solve(l,r,room,p); else return false; }
            case SCALE -> { if(hit(s,"scale",at)==null) return false; if(!s.hasUUID("ScaleOwner")||s.getUUID("ScaleOwner").equals(p.getUUID())) { s.putUUID("ScaleOwner",p.getUUID()); s.putLong("ScaleHeldUntil",now+30); say(p,"保持潜行贴住手印：50M/秒，淤积+5/秒。松手即中止。"); } else say(p,"天平正在汲取另一位冒险者的魔力。"); }
            case CAULDRON -> { if(hit(s,"mouth",at)==null) return false; if(feed(l,r,room,s,p,held)) LeylineService.consumeHeld(p,hand); else say(p,s.getInt("Craving")==0?"味泡：赤红火光与臭黑烟。":"味泡：翠绿生机与甘甜回响。"); }
            case ALCHEMY -> { if(hit(s,"igniter",at)==null) return false; var pot=pot(l,s); if(pot!=null&&cooldown(s,"LastCooling",now,10)) { pot.temperature=Math.max(0,pot.temperature-160); pot.changed(); } }
            default -> handled=false;
        }
        if(handled) dirty(l); return handled;
    }

    public static boolean breakBlock(ServerPlayer p, Run r, Room room, BlockPos at) {
        if(!valid(p,r,room,at,false)) return false; var s=state(r,room); initialize(s,r,room);
        if(blocked(s)) return false; var l=p.serverLevel(); boolean handled=true;
        switch(LeylineService.kind(r,room)) {
            case FACTIONS -> { if(hit(s,"barrier_core",at)==null) return false; s.putBoolean("BarrierBroken",true); clearRole(l,s,"barrier"); DungeonRealm.write(()->l.setBlock(at,Blocks.AIR.defaultBlockState(),3)); }
            case LENS -> { var n=hit(s,"lens_wall",at); if(n==null) return false; int index=n.getInt("Index"); String key="Brick_"+index; if(s.getBoolean(key)) return true; s.putBoolean(key,true); if(index==s.getInt("CoreIndex")) { DungeonRealm.write(()->l.setBlock(at,Blocks.AIR.defaultBlockState(),3)); solve(l,r,room,p); } else { int misses=s.getInt("Misses")+1; s.putInt("Misses",misses); DungeonRealm.write(()->l.setBlock(at,Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),3)); say(p,"错误凿击："+misses+" / 3；再错会触发核心自毁。"); if(misses>3) fail(l,s,p,"古代核心已自毁。",8); } }
            case PUPPETS -> { var n=hit(s,"string",at); if(n==null) return false; if(p.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS)||p.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES)||p.getMainHandItem().getOrCreateTag().getBoolean("ManaStripped")) cut(l,r,room,s,n,p); else say(p,"锋利的剑斧或风刃可以切断暗影提线。"); }
            case VINES -> { if(hit(s,"vine",at)==null) return false; p.hurt(p.damageSources().cactus(),2); say(p,"荆棘因饥饿收得更紧了。"); }
            case STOMACH -> { if(hit(s,"treasure",at)==null) return false; if(s.getBoolean("Sedated")) solve(l,r,room,p); else LeylineActors.wake(l,r,room,s); }
            case MUSIC -> { if(hit(s,"crystal",at)==null) return false; note(l,r,room,s,hit(s,"crystal",at),p); }
            default -> handled=false;
        }
        if(handled) dirty(l); return handled;
    }

    /** Called on an actual paid spell or arrow impact. */
    public static boolean spell(ServerPlayer p, Run r, Room room, BlockPos at, String element) {
        if(!valid(p,r,room,at,true)) return false; var s=state(r,room); initialize(s,r,room); if(blocked(s)) return false;
        var l=p.serverLevel(); var kind=LeylineService.kind(r,room); String e=element.toLowerCase(Locale.ROOT); boolean handled=true;
        switch(kind) {
            case SUNDIAL -> { if(hit(s,"sun",at)==null||!(e.equals("wind")||e.equals("water"))) return false; if(cooldown(s,"SunHit",l.getGameTime(),8)) { s.putInt("Hour",LeylinePuzzleRules.nextHour(s.getInt("Hour"),e.equals("wind"))); say(p,"影针转向："+new String[]{"晨曦","正午","黄昏","子夜"}[s.getInt("Hour")]); } }
            case ECHO -> { if(hit(s,"echo_wall",at)==null) return false; if((e.equals("water")||e.equals("ice"))&&personal(s,p).getInt("EchoStep")>=nodes(s,"footprint").size()&&p.hasEffect(AlchemyContent.ECHO)&&ManaSightServer.enabled(p)) { clearRole(l,s,"echo_wall"); solve(l,r,room,p); } else say(p,"墙壁只响应完整复刻的时间记忆。"); }
            case FACTIONS -> { if(hit(s,"barrier_core",at)==null) return false; s.putBoolean("BarrierBroken",true); clearRole(l,s,"barrier"); }
            case STOMACH -> { if(!e.equals("arrow")) { LeylineActors.wake(l,r,room,s); say(p,"逸散魔力惊醒了胃袋里的巨虫！"); } }
            case VINES -> { if(hit(s,"vine",at)==null) return false; if(e.equals("fire")) { p.hurt(p.damageSources().magic(),3); say(p,"魔藤吞掉火魔力，荆棘更加茂密。"); } }
            case PUPPETS -> { var n=hit(s,"string",at); if(n==null||!e.equals("wind")) return false; cut(l,r,room,s,n,p); }
            case MUSIC -> { var n=hit(s,"crystal",at); if(n==null) return false; note(l,r,room,s,n,p); }
            case ALCHEMY -> { if(!alchemyPart(l,s,at)) return false; if(e.equals("water")||e.equals("ice")) { var pot=pot(l,s); if(pot!=null&&l.getGameTime()-pot.heatedAt>=10) { pot.temperature=Math.max(0,pot.temperature-220); pot.heatedAt=l.getGameTime();pot.changed(); } } }
            case GRAVITY -> { if(hit(s,"gravity_node",at)==null||!e.equals("wind")) return false; if(s.getLong("GravityUntil")<=l.getGameTime()) s.putLong("GravityUntil",l.getGameTime()+400); }
            case EXHIBITION -> { var n=hit(s,"amber",at); if(n==null||!e.equals("fire")) return false; thaw(l,r,room,s,n,p); }
            default -> handled=false;
        }
        if(handled) dirty(l); return handled;
    }

    /** Impact hook: the thrown potion was already debited by its thrower. */
    public static boolean potion(ServerPlayer p, Run r, Room room, BlockPos at, String id) {
        if(!valid(p,r,room,at,true)) return false; var s=state(r,room); initialize(s,r,room); if(blocked(s)) return false;
        boolean accepted=acceptPotion(p,r,room,s,at,id); if(accepted) dirty(p.serverLevel()); return accepted;
    }
    private static boolean acceptPotion(ServerPlayer p, Run r, Room room, CompoundTag s, BlockPos at, String id) {
        var l=p.serverLevel(); var kind=LeylineService.kind(r,room); boolean waste=id.equals("strange_waste")||id.equals("waste");
        if(kind==LeylineKind.STOMACH&&hit(s,"vent",at)!=null&&!s.getBoolean("Sedated")&&(id.equals("cleanse")||id.equals("elixir_cleanse")||id.equals("hushcap_elixir"))) { s.putBoolean("Sedated",true); s.putBoolean("Awake",false); LeylineActors.sedate(l,r,room,s); return true; }
        if(kind==LeylineKind.POLLUTION&&hit(s,"pool",at)!=null&&waste) { poisonVines(l,r,room,s,p); return true; }
        if(kind==LeylineKind.CAULDRON&&hit(s,"mouth",at)!=null&&waste&&s.getInt("Craving")==0&&(s.getInt("TasteMask")&1)==0) { s.putInt("TasteMask",s.getInt("TasteMask")|1); if((s.getInt("TasteMask")&3)==3) solve(l,r,room,p); return true; }
        String elixirId=id.startsWith("elixir_")?id.substring(7):id;
        if(kind==LeylineKind.ECHO&&hit(s,"echo_wall",at)!=null&&personal(s,p).getInt("EchoStep")>=nodes(s,"footprint").size()&&p.hasEffect(AlchemyContent.ECHO)&&ManaSightServer.enabled(p)&&(Elixir.find(elixirId)!=null||waste)) { clearRole(l,s,"echo_wall"); solve(l,r,room,p); return true; }
        return false;
    }

    private static void bridge(ServerLevel l,Run r,Room room,CompoundTag s,ServerPlayer p,boolean dual,long now) {
        var t=personal(s,p); int bits=t.getInt("BridgeSteps"), required=0;
        for(String role:dual?List.of("phase_a","phase_b"):List.of("bridge")) for(var n:nodes(s,role)) { int bit=1<<n.getInt("Index"); required|=bit; var at=pos(n); boolean solid=!dual||role.equals("phase_b")==ManaSightServer.enabled(p); if(solid&&Math.abs(p.getY()-(at.getY()+1))<.8&&Math.abs(p.getX()-at.getX()-1.5)<1.7&&Math.abs(p.getZ()-at.getZ()-1)<1.25) bits|=bit; }
        t.putInt("BridgeSteps",bits);
        for(var n:nodes(s,"drain")) if(near(p,pos(n).above(),1.1)&&cooldown(t,"Drained",now,20)) { emptyMana(p); p.hurt(p.damageSources().magic(),2); }
        var o=DungeonRealm.center(r,room);
        if(p.getY()<o.getY()-6&&cooldown(t,"Fell",now,20)) { p.hurt(p.damageSources().fall(),6); p.teleportTo(l,o.getX()+.5,o.getY()+1,o.getZ()-9,p.getYRot(),0); p.fallDistance=0; t.putInt("BridgeSteps",0); }
        if(required!=0&&(bits&required)==required&&near(p,first(s,"goal",o.offset(0,1,8)),2)) solve(l,r,room,p);
    }
    private static void tide(ServerLevel l,CompoundTag s,ServerPlayer p,long now) {
        if(now%5!=0) return; var plant=first(s,"plant",p.blockPosition()); int phase=(int)Math.floorMod(now,100);
        double waveZ=plant.getZ()-phase*.22; boolean cover=nodes(s,"cover").stream().anyMatch(n->near(p,pos(n),2.2));
        if(!cover&&Math.abs(p.getZ()-waveZ)<1.3&&cooldown(personal(s,p),"TideHit",now,40)) { emptyMana(p); motion(p,new Vec3(0,.45,-1.2)); p.hurt(p.damageSources().magic(),3); }
        ManaSightServer.record(l,new Vec3(plant.getX()-8,plant.getY()+1,waveZ),new Vec3(plant.getX()+8,plant.getY()+1,waveZ),32,0xf044a4,4);
    }
    private static void echo(ServerLevel l,CompoundTag s,ServerPlayer p,long now) {
        if(!ManaSightServer.enabled(p)||!p.hasEffect(AlchemyContent.ECHO)) return; var t=personal(s,p); int step=t.getInt("EchoStep");
        for(var n:nodes(s,"footprint")) { var at=pos(n); if(now%10==0) l.sendParticles(p,ParticleTypes.SOUL_FIRE_FLAME,false,at.getX()+.5,at.getY()+1.1,at.getZ()+.5,2,.1,0,.1,0);
            if(near(p,at.above(),.85)&&t.getInt("LastFootprint")!=n.getInt("Index")+1) { int index=n.getInt("Index"); t.putInt("LastFootprint",index+1); if(index==step) { t.putInt("EchoStep",++step); say(p,"时间脚印："+step+" / "+nodes(s,"footprint").size()); } else if(index>step) { t.putInt("EchoStep",0); p.hurt(p.damageSources().magic(),3); } }
        }
    }
    private static void lens(ServerLevel l,CompoundTag s,List<ServerPlayer> players) {
        var visible=new LinkedHashSet<Integer>();
        for(var base:nodes(s,"mirror_base")) { var at=pos(base).above(); if(!(l.getBlockEntity(at) instanceof RouteNodeEntity mirror)||!RouteMirrorLinks.mirror(mirror)) continue;
            for(var p:players) { if(!ManaSightServer.enabled(p)||!near(p,at,3)) continue;
                var wall=nodes(s,"lens_wall"); if(wall.isEmpty()||Math.abs(p.getLookAngle().z)<.1) continue;
                double distance=(pos(wall.get(0)).getZ()+.5-p.getEyePosition().z)/p.getLookAngle().z; if(distance<=0) continue;
                var aimed=p.getEyePosition().add(p.getLookAngle().scale(distance));
                var center=wall.stream().min(Comparator.comparingDouble(n->pos(n).distToCenterSqr(aimed))).orElseThrow();
                int index=center.getInt("Index"); for(var n:wall) if(LeylinePuzzleRules.lensCovers(index%15,index/15,n.getInt("Index"))) visible.add(n.getInt("Index"));
            }
        }
        s.putIntArray("LensVisible",visible.stream().mapToInt(Integer::intValue).toArray());
    }
    private static boolean reflected(ServerLevel l,CompoundTag s,String targetRole) {
        var bases=nodes(s,"mirror_base"); if(bases.isEmpty()||nodes(s,targetRole).isEmpty()) return false;
        var target=first(s,targetRole,pos(bases.get(0)));
        for(var base:bases) { var at=pos(base).above(); if(l.getBlockEntity(at) instanceof RouteNodeEntity mirror&&RouteMirrorLinks.mirror(mirror)&&RouteMirrorLinks.clear(l,at,target)) return true; }
        return false;
    }
    private static void refraction(ServerLevel l,Run r,Room room,CompoundTag s,ServerPlayer p) {
        var source=first(s,"beam_source",DungeonRealm.center(r,room)); var lock=first(s,"lock",source);
        for(var base:nodes(s,"mirror_base")) { var at=pos(base).above(); if(!(l.getBlockEntity(at) instanceof RouteNodeEntity mirror)||!RouteMirrorLinks.mirror(mirror)) continue;
            var remote=RouteMirrorLinks.remote(l,mirror); if(remote==null||!nodes(s,"mirror_base").stream().anyMatch(n->pos(n).above().equals(remote))) continue;
            if(RouteMirrorLinks.clear(l,source,at)&&RouteMirrorLinks.clear(l,remote,lock)) { ManaSightServer.record(l,Vec3.atCenterOf(source),Vec3.atCenterOf(at),32,0xffde85,4); ManaSightServer.record(l,Vec3.atCenterOf(at),Vec3.atCenterOf(remote),32,0xffde85,4); ManaSightServer.record(l,Vec3.atCenterOf(remote),Vec3.atCenterOf(lock),32,0xffde85,4); DungeonRealm.write(()->{for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=2;dy++)l.setBlock(lock.offset(dx,dy,0),Blocks.AIR.defaultBlockState(),3);}); solve(l,r,room,p); return; }
        }
    }
    private static void cut(ServerLevel l,Run r,Room room,CompoundTag s,CompoundTag node,ServerPlayer p) { s.putInt("CutStrings",s.getInt("CutStrings")|(1<<node.getInt("Index"))); if((s.getInt("CutStrings")&7)==7) { LeylineActors.collapse(l,r,room,s); solve(l,r,room,p); } }
    private static void note(ServerLevel l,Run r,Room room,CompoundTag s,CompoundTag node,ServerPlayer p) {
        if(!cooldown(s,"NoteAt",l.getGameTime(),5)) return;
        int note=node.getInt("Index"); int step=LeylinePuzzleRules.nextNote(s.getIntArray("Notes"),s.getInt("NoteStep"),note); s.putInt("NoteStep",step);
        l.playSound(null,pos(node),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.BLOCKS,1,(float)Math.pow(2,(note-4)/12.0));
        if(step==3) { for(var q:BlockPos.betweenClosed(DungeonRealm.center(r,room).offset(-9,0,-9),DungeonRealm.center(r,room).offset(9,3,9))) { var bs=l.getBlockState(q); if(bs.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop) DungeonRealm.write(()->l.setBlock(q,crop.getStateForAge(crop.getMaxAge()),3)); else if(bs.hasProperty(com.tnc.tnc.life.botanical.BotanicalBlock.AGE)) DungeonRealm.write(()->l.setBlock(q,bs.setValue(com.tnc.tnc.life.botanical.BotanicalBlock.AGE,3),3)); else if(bs.hasProperty(RoutePlantBlock.AGE)) DungeonRealm.write(()->l.setBlock(q,bs.setValue(RoutePlantBlock.AGE,3),3)); } solve(l,r,room,p); } else if(step==0) { motion(p,p.position().subtract(Vec3.atCenterOf(DungeonRealm.center(r,room))).normalize().scale(.8).add(0,.25,0)); say(p,"音序失谐。重新倾听中央护盾的三色回声。"); }
    }
    private static void poisonVines(ServerLevel l,Run r,Room room,CompoundTag s,ServerPlayer p) { s.putBoolean("Polluted",true); clearRole(l,s,"vine"); l.sendParticles(ParticleTypes.SMOKE,p.getX(),p.getY()+1,p.getZ(),40,3,1,3,.02); solve(l,r,room,p); }
    private static void gravity(CompoundTag s,Run r,Room room,List<ServerPlayer> players,long now) {
        boolean active=s.getLong("GravityUntil")>now; s.putBoolean("GravityActive",active);
        for(var p:players) if(active) { var ceiling=first(s,"ceiling_goal",DungeonRealm.center(r,room).above(25));
            if(p.getY()<ceiling.getY()-1) motion(p,new Vec3(p.getDeltaMovement().x,.48,p.getDeltaMovement().z));
            else motion(p,new Vec3(p.getDeltaMovement().x,.08,p.getDeltaMovement().z));
            p.fallDistance=0; if(near(p,ceiling,3)) personal(s,p).putBoolean("ReachedCeiling",true);
            if(s.getLong("GravityUntil")-now<=100&&now%20==0) say(p,"重力将在"+((s.getLong("GravityUntil")-now)/20)+"秒后恢复；轻羽浮沫可减缓坠落。");
        }
    }

    private static AlchemyEntity pot(ServerLevel l,CompoundTag s) { var n=nodes(s,"cauldron"); return n.isEmpty()?null:AlchemyBlock.master(l,pos(n.get(0))); }
    private static boolean alchemyPart(ServerLevel l,CompoundTag s,BlockPos at) { var actual=AlchemyBlock.master(l,at); return actual!=null&&actual==pot(l,s); }
    private static void alchemyTick(ServerLevel l,Run r,Room room,CompoundTag s,ServerPlayer p,long now) {
        if(s.getBoolean("Failed")) return; var pot=pot(l,s); if(pot==null) return;
        if(!s.contains("AlchemyDeadline")) { s.putLong("AlchemyDeadline",now+300); pot.temperature=780; pot.water=3; pot.hasIgnited=true; pot.fire=2; pot.burn=1600; pot.job=Elixir.FOCUS.id; pot.owner=null; for(int i=0;i<3;i++) pot.inventory.setItem(i,new ItemStack(ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse(Elixir.FOCUS.ingredients.get(i))))); pot.progress=Elixir.FOCUS.ticks-80; pot.changed(); dirty(l); }
        s.putInt("Temperature",pot.temperature); s.putInt("Vortex",pot.vortex_sync); s.putInt("PerfectTicks",pot.perfectRemaining);
        if(!s.getBoolean("SavedKettle")&&!pot.sealed) pot.overheated=0; // This ancient vessel's documented fuse is fifteen seconds.
        if(s.getBoolean("SavedKettle")) { // Keep the actual sealed pot in its green cooking range until its normal runtime finishes.
            if(pot.sealed&&pot.ready.isEmpty()) { pot.temperature=Math.min(600,Math.max(400,pot.temperature)); pot.fire=2; pot.burn=Math.max(pot.burn,200); }
            return;
        }
        if(pot.sealed) { if(pot.readyQuality==2&&LeylinePuzzleRules.temperatureSafe(pot.temperature)) { s.putBoolean("SavedKettle",true); s.putInt("Bottles",3); solve(l,r,room,p); } else fail(l,s,p,"封盖时错过了漩涡合一，古代药性散失。",4); }
        else if(now>=s.getLong("AlchemyDeadline")) { pot.ruined=true; pot.fire=0; pot.burn=0; pot.inventory.clearContent(); pot.changed(); fail(l,s,p,"十五秒已过，暴走药鼎炸裂，宝物毁损。",10); }
    }
    private static boolean alchemyInteract(ServerPlayer p,Run r,Room room,CompoundTag s,InteractionHand hand) {
        var l=p.serverLevel(); var pot=pot(l,s); if(pot==null) return true; var held=p.getItemInHand(hand);
        if(s.getBoolean("SavedKettle")&&held.is(Items.GLASS_BOTTLE)) { int remaining=s.getInt("Bottles"); if(remaining<=0) { say(p,"古代药鼎已经舀空。"); return true; } if(pot.ready.isEmpty()&&remaining==3) { say(p,"封盖后尚需后煮六秒。"); return true; } s.putInt("Bottles",remaining-1); LeylineService.consumeHeld(p,hand); var bottle=new ItemStack(AlchemyContent.POTIONS.get(Elixir.FOCUS)); bottle.getOrCreateTag().putInt("RitualQuality",2); bottle.getOrCreateTag().putBoolean("AncientVintage",true); give(p,bottle); if(remaining==1) { pot.ready=""; pot.water=0; give(p,pot.inventory.removeItemNoUpdate(6)); pot.inventory.clearContent(); pot.changed(); } dirty(l); return true; }
        if(blocked(s)) return true;
        if(id(held).equals("sealing_lid")) { if(!LeylinePuzzleRules.temperatureSafe(pot.temperature)||pot.vortex_sync<100||pot.perfectRemaining<=0||pot.ruined) { say(p,"先降至360—620度，等三环合一的十字星光再扣盖。"); return true; } DungeonRealm.write(()->pot.interact(p,hand)); alchemyTick(l,r,room,s,p,l.getGameTime()); dirty(l); }
        else say(p,"古代药鼎："+pot.temperature+"℃ · 融合 "+pot.vortex_sync+"% · 剩余 "+Math.max(0,(s.getLong("AlchemyDeadline")-l.getGameTime())/20)+"秒");
        return true;
    }

    private static void rapids(ServerLevel l,Run r,Room room,CompoundTag s,ServerPlayer p,long now) {
        var t=personal(s,p); var currents=nodes(s,"current"); if(currents.size()<2) return;
        var origin=DungeonRealm.center(r,room);
        if(p.getY()<origin.getY()-5){p.hurt(p.damageSources().fall(),4);p.teleportTo(l,origin.getX()+.5,origin.getY()+1,origin.getZ()-9,0,0);p.fallDistance=0;t.remove("Riding");return;}
        if(!p.hasEffect(MobEffects.SLOW_FALLING)&&!p.hasEffect(MobEffects.MOVEMENT_SPEED)) { t.remove("Riding"); return; }
        var start=pos(currents.get(0)); var end=first(s,"rapids_goal",pos(currents.get(currents.size()-1)));
        if(!t.getBoolean("Riding")) { if(!near(p,start,2.5)) return; t.putBoolean("Riding",true); t.putInt("Lane",0); t.putLong("RideStart",now); }
        boolean shift=p.isShiftKeyDown(); if(shift&&!t.getBoolean("WasShift")) { t.putInt("Lane",(t.getInt("Lane")+1)%3); t.putInt("Switches",t.getInt("Switches")+1); } t.putBoolean("WasShift",shift);
        int lane=t.getInt("Lane"); double progress=ArcanaHighway.projection(p.position(),Vec3.atCenterOf(start),Vec3.atCenterOf(end));
        var a=Vec3.atCenterOf(start); var b=Vec3.atCenterOf(end); var point=a.lerp(b,Math.min(1,progress+.06)).add((lane-1)*2,Math.sin(progress*Math.PI)*1.6,0);
        if(progress>.3&&progress<.75&&lane==(int)(now/40%3)&&cooldown(t,"Turbulence",now,30)) { p.hurt(p.damageSources().magic(),2); motion(p,new Vec3((lane-1)*.3,-.25,0)); say(p,"红色乱流！按潜行切换气流。"); }
        else { var desired=point.subtract(p.position()).normalize().scale(.35); var smooth=p.getDeltaMovement().lerp(desired,.16); motion(p,smooth.add(0,.08,0)); p.fallDistance=0; }
        if(now%10==0) ManaSightServer.record(l,a,b,32,0x75d8ff,4);
        if(near(p,end,2.3)&&t.getInt("Switches")>=2) { t.remove("Riding"); solve(l,r,room,p); }
        if(now-t.getLong("RideStart")>800||p.getY()<DungeonRealm.center(r,room).getY()-5) t.remove("Riding");
    }
    private static void scale(ServerLevel l,Run r,Room room,CompoundTag s,List<ServerPlayer> players,long now) {
        if(!s.hasUUID("ScaleOwner")||now%20!=0) return; var p=players.stream().filter(q->q.getUUID().equals(s.getUUID("ScaleOwner"))).findFirst().orElse(null);
        var at=first(s,"scale",DungeonRealm.center(r,room));
        if(p==null||!near(p,at,2.5)||!p.isShiftKeyDown()) { s.remove("ScaleOwner"); s.putInt("ScaleSeconds",0); if(p!=null) { p.hurt(p.damageSources().magic(),4); say(p,"献祭中断，天平反噬。"); } return; }
        var magic=MagicStone.getOrNull(p); boolean sun=ArcanaPhysiology.data(p).getLong("MealSunUntil")>ArcanaPhysiology.now(p);
        if(magic==null||(!sun&&!magic.spendMana(50))||ArcanaPhysiology.toxicity(p)>=95) { s.remove("ScaleOwner"); s.putInt("ScaleSeconds",0); p.hurt(p.damageSources().magic(),8); say(p,"法力或经脉已到极限，献祭失败。"); return; }
        ArcanaPhysiology.toxic(p,5); MagicStoneNetwork.syncTo(p); int seconds=s.getInt("ScaleSeconds")+1; s.putInt("ScaleSeconds",seconds); say(p,"天平吞食："+seconds+" / 18秒 · 淤积 "+ArcanaPhysiology.toxicity(p));
        if(seconds>=18) { s.remove("ScaleOwner"); solve(l,r,room,p); }
    }
    private static boolean feed(ServerLevel l,Run r,Room room,CompoundTag s,ServerPlayer p,ItemStack food) {
        String id=id(food); int mask=s.getInt("TasteMask"); boolean valid=false;
        if(s.getInt("Craving")==0) { if(id.equals("strange_waste")&&(mask&1)==0) { mask|=1; valid=true; } else if((id.equals("hearth_pepper_fruit")||id.equals("sunpepper_roast"))&&(mask&2)==0) { mask|=2; valid=true; } else if(food.getItem() instanceof ArcanaMealItem&&food.hasTag()&&food.getTag().getInt("Conflicts")>0) { mask=3; valid=true; } }
        else if(ArcanaMeals.taste(food)==ArcanaMeals.Taste.NEUTRAL||id.equals("cloudleaf_salad")||id.equals("concord_banquet")) { mask=3; valid=true; }
        if(valid) { s.putInt("TasteMask",mask); if(mask==3) { s.putInt("AncientDoses",5); solve(l,r,room,p); } } return valid;
    }
    private static void mouthDrops(ServerLevel l,Run r,Room room,CompoundTag s) {
        if(nodes(s,"mouth").isEmpty()) return; var at=first(s,"mouth",DungeonRealm.center(r,room));
        for(var item:l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(at).inflate(.8))) {
            if(!(item.getOwner() instanceof ServerPlayer p)||!valid(p,r,room,at,true)) continue;
            if(feed(l,r,room,s,p,item.getItem())) { item.getItem().shrink(1); if(item.getItem().isEmpty()) item.discard(); dirty(l); if(blocked(s)) break; }
        }
    }
    private static void thaw(ServerLevel l,Run r,Room room,CompoundTag s,CompoundTag n,ServerPlayer p) {
        int index=n.getInt("Index"),melted=s.getInt("Melted"); if((melted&(1<<index))!=0) return;
        if(!LeylinePuzzleRules.mayThaw(melted,index)) { s.putBoolean("ExhibitionLocked",true); say(p,"仅够融化两座的魔力已经耗尽，余下展品永久锁定。"); return; }
        melted|=1<<index; s.putInt("Melted",melted); s.putInt("Thaws",Integer.bitCount(melted)); var center=n.contains("Center")?BlockPos.of(n.getLong("Center")):pos(n).south().below();
        DungeonRealm.write(()->{for(var q:BlockPos.betweenClosed(center.offset(-1,0,-1),center.offset(1,3,1)))l.setBlock(q,Blocks.AIR.defaultBlockState(),3);});
        int good=s.getInt("TreasureMask"); int companion=Integer.numberOfTrailingZeros(good);
        if(index==companion) LeylineActors.exhibit(l,r,room,s,center,true,p);
        else if((good&(1<<index))==0&&index%2==0) { l.sendParticles(ParticleTypes.EXPLOSION_EMITTER,pos(n).getX(),pos(n).getY(),pos(n).getZ(),1,0,0,0,0); for(var q:l.players()) if(near(q,pos(n),5)) q.hurt(q.damageSources().magic(),10); }
        else if((good&(1<<index))==0) LeylineActors.exhibit(l,r,room,s,center,false,p);
        if((melted&s.getInt("TreasureMask"))==s.getInt("TreasureMask")) solve(l,r,room,p);
        else if(Integer.bitCount(melted)>=2) { s.putBoolean("ExhibitionLocked",true); if((melted&s.getInt("TreasureMask"))!=0) { s.putBoolean("PartialTreasure",true); solve(l,r,room,p); } else fail(l,s,p,"两次解冻均已用尽，真宝物永远封入琥珀。",0); }
    }
    private static boolean pawn(ServerPlayer p,Run r,Room room,CompoundTag s,BlockPos at,InteractionHand hand) {
        if(hit(s,"spring_core",at)!=null) { if(p.getItemInHand(hand).is(Items.BUCKET)) { var t=personal(s,p); if(t.getBoolean("SpringBucket")) { say(p,"泉心已为你凝成过一桶千年原浆。"); return true; } t.putBoolean("SpringBucket",true); LeylineService.consumeHeld(p,hand); var item=ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse("tnc:leyline_spring_bucket")); if(item==null||item==Items.AIR) { var bucket=new ItemStack(Items.WATER_BUCKET); bucket.getOrCreateTag().putBoolean("AncientLeylineSpring",true); give(p,bucket); } else give(p,new ItemStack(item)); dirty(p.serverLevel()); } return true; }
        if(!ManaSightServer.enabled(p)) { say(p,"盲眼地精仍然沉默，单片镜闪着微光。"); return true; }
        var t=personal(s,p); if(t.getBoolean("PawnSpent")) { say(p,"轮盘已经结清了你的这一笔毒性筹码。"); return true; }
        int toxicity=ArcanaPhysiology.toxicity(p); if(toxicity<5) { say(p,"轮盘至少需要5点魔力淤积作筹码。"); return true; }
        t.putBoolean("PawnSpent",true); int roll=Math.floorMod(Objects.hash(r.seed,r.floor,room.id(),p.getUUID()),100); t.putBoolean("PawnRare",LeylinePuzzleRules.rarePawn(toxicity,roll)); s.putBoolean("PawnRare",t.getBoolean("PawnRare")); s.putInt("PawnStake",toxicity); ArcanaPhysiology.detox(p,toxicity); LeylineRewards.award(p.server,r,room,p.getUUID(),t.getBoolean("PawnRare")); if(!s.getBoolean("Solved")){s.putBoolean("Solved",true);DungeonService.clearRoom(p.serverLevel(),r,room);} dirty(p.serverLevel()); return true;
    }
    private static void bathe(ServerLevel l,Run r,Room room,CompoundTag s,ServerPlayer p,long now) {
        var t=personal(s,p); boolean in=nodes(s,"spring").stream().anyMatch(n->near(p,pos(n),2.5))&&p.isInWater();
        if(!in) { t.remove("BathStart"); return; } if(!t.contains("BathStart")) t.putLong("BathStart",now);
        if(now-t.getLong("BathStart")<200||t.getLong("BathRewardUntil")>now) return;
        t.putLong("BathRewardUntil",now+18000); ArcanaPhysiology.detox(p,100); var physiology=ArcanaPhysiology.data(p); physiology.putLong("ClearUntil",Math.max(physiology.getLong("ClearUntil"),ArcanaPhysiology.now(p)+18000)); physiology.putLong("LeylineBlessingUntil",ArcanaPhysiology.now(p)+18000); p.heal(p.getMaxHealth()); p.getFoodData().eat(20,1); var mana=MagicStone.getOrNull(p); if(mana!=null) { mana.setMana(mana.getMaxMana()); MagicStoneNetwork.syncTo(p); } var armor=p.getAttribute(Attributes.ARMOR); if(armor!=null&&!armor.hasModifier(new AttributeModifier(BLESSING,"地脉母脉眷顾",6,AttributeModifier.Operation.ADDITION))) armor.addTransientModifier(new AttributeModifier(BLESSING,"地脉母脉眷顾",6,AttributeModifier.Operation.ADDITION)); JourneyData.get(p.server).setDirty(); dirty(l); say(p,"母脉眷顾：状态恢复，15分钟护甲+6与魔力视精神免耗。");
    }
    private static void fail(ServerLevel l,CompoundTag s,ServerPlayer p,String text,float damage) { s.putBoolean("Failed",true); if(damage>0) p.hurt(p.damageSources().magic(),damage); say(p,text); dirty(l); }
    @SubscribeEvent public static void blessingTick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) { if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%20!=0) return; var armor=p.getAttribute(Attributes.ARMOR); if(armor==null) return; var modifier=armor.getModifier(BLESSING); boolean active=ArcanaPhysiology.data(p).getLong("LeylineBlessingUntil")>ArcanaPhysiology.now(p); if(active&&modifier==null) armor.addTransientModifier(new AttributeModifier(BLESSING,"地脉母脉眷顾",6,AttributeModifier.Operation.ADDITION)); else if(!active&&modifier!=null) armor.removeModifier(BLESSING); }
    private LeylinePuzzles() {}
}
