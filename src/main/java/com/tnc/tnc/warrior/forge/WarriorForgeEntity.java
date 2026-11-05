package com.tnc.tnc.warrior.forge;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.registries.ForgeRegistries;

/** One physical workpiece, one owner, no menus, no passive automation. */
public final class WarriorForgeEntity extends BlockEntity {
    public ItemStack work = ItemStack.EMPTY;
    public ForgeSession session;
    public UUID owner;
    public int selected, water, pose;
    public long lastImpact = -1000, divineAt = -1000;
    public int impactZone = 1;
    public double impactScore;
    public int holdTicks;
    public boolean holding;
    private boolean slow;

    public WarriorForgeEntity(BlockPos p, BlockState s) { super(WarriorForgeContent.ENTITY, p, s); }
    public WarriorForgeBlock.Kind kind() { return ((WarriorForgeBlock)getBlockState().getBlock()).kind; }
    public boolean mayUse(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && (owner == null || owner.equals(p.getUUID()))
                && p.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 36
                && !com.tnc.tnc.combat.DownedCombat.isDowned(p)
                && !com.tnc.tnc.home.TownProtection.denied(p, worldPosition);
    }
    public static int zone(double localX) { return Math.max(0, Math.min(2, (int)(localX * 3))); }
    private int aimedZone(ServerPlayer p) {
        var hit = p.pick(5, 1, false);
        if (!(hit instanceof BlockHitResult b) || !b.getBlockPos().equals(worldPosition)) return -1;
        return zone(b.getLocation().x - worldPosition.getX());
    }
    private boolean watching(ServerPlayer p) { return aimedZone(p) >= 0; }

    public void interact(ServerPlayer p, BlockHitResult hit) {
        if (!mayUse(p)) { tell(p, "工件正在由原制作者托管，或此处不允许操作"); return; }
        var hand = p.getMainHandItem();
        if (hand.is(WarriorForgeContent.HAMMER)) {
            if (p.isShiftKeyDown()) {
                if (work.isEmpty()) { slow = !slow; tell(p, slow ? "慢速练习：窗口放宽，最高品质不受限" : "标准操作节奏"); changed(); }
                else if (kind() == WarriorForgeBlock.Kind.BATH) { pose = (pose + 1) % 4; tell(p, "入水姿态：" + ForgePattern.values()[pose].poseName()); changed(); }
                else if (session != null && session.stage == ForgeSession.Stage.SHAPING && session.strokes == 12) {
                    boolean repaired = session.correct(zone(hit.getLocation().x - worldPosition.getX()));
                    tell(p, repaired ? "偏斜已修正；还可修正 " + (2-session.corrections) + " 次" : "这里没有可修偏斜，或已用完两次修正"); changed();
                }
            }
            return;
        }
        if (kind() == WarriorForgeBlock.Kind.BATH && hand.is(Items.WATER_BUCKET)) {
            if (!work.isEmpty()) { tell(p, "先取出工件，再换槽液"); return; }
            if (!p.isCreative()) p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            water = 10; changed(); sound(SoundEvents.BUCKET_EMPTY, 1, 1); tell(p, "清水已添满 · 可淬火10次"); return;
        }
        if (work.isEmpty() && hand.is(WarriorForgeContent.BILLET)) {
            if (!accept(p, hand)) tell(p, "这件工件不属于你，或当前工序不适合此工位");
            return;
        }
        if (!work.isEmpty()) {
            if (!hand.isEmpty()) { tell(p, "使用锻造锤操作；空手右键取出工件"); return; }
            if (kind() == WarriorForgeBlock.Kind.HEARTH && session != null && session.stage == ForgeSession.Stage.HEATING) {
                if (session.heat < 650) {
                    if (session.pump()) { sound(SoundEvents.FIRECHARGE_USE, .6f, .7f); changed(); }
                    tell(p, "风箱提温 · 热度 " + session.heat + " / 可锻650～850"); return;
                }
                session.takeFromFire(); changed();
            }
            if (kind() == WarriorForgeBlock.Kind.ANVIL && session != null && session.stage == ForgeSession.Stage.SHAPING) {
                if (session.strokes == 12) session.toQuench();
            }
            take(p); return;
        }
        if (kind() == WarriorForgeBlock.Kind.HEARTH) {
            if (hand.isEmpty() && p.isShiftKeyDown()) {
                selected = (selected + 1) % ForgePattern.values().length; changed(); tell(p, "准备锻造：" + ForgePattern.values()[selected].title); return;
            }
            var pattern = ForgePattern.values()[selected];
            if (hand.is(Items.IRON_INGOT) && hand.getCount() >= pattern.iron) {
                int coal = find(p, Items.COAL);
                if (coal < 0 && !p.isCreative()) { tell(p, "需要煤炭1份；本次尚未扣材料"); return; }
                var billet = new ItemStack(WarriorForgeContent.BILLET);
                var tag = billet.getOrCreateTag(); tag.putUUID("Maker", p.getUUID()); tag.putString("MakerName", p.getGameProfile().getName());
                tag.putUUID("WorkId", UUID.randomUUID());
                if (!p.isCreative()) { hand.shrink(pattern.iron); p.getInventory().getItem(coal).shrink(1); }
                owner = p.getUUID(); session = new ForgeSession(pattern); session.slow = slow; work = billet;
                changed(); sound(SoundEvents.FLINTANDSTEEL_USE, .7f, 1); tell(p, "炭床已点燃：观察金属颜色，空手右键可压风箱或夹出");
                return;
            }
            tell(p, "潜行空手换形制；放入铁锭" + pattern.iron + "＋背包煤炭1 · 当前 " + pattern.title);
        } else tell(p, kind() == WarriorForgeBlock.Kind.BATH ? "水桶添液，放入已定形工件；潜行持锤换姿态" : "放入热坯；锤子短按轻敲、按住再松开重锤");
    }

    public boolean accept(ServerPlayer p, ItemStack billet) {
        if (!work.isEmpty() || !billet.hasTag() || !billet.getTag().hasUUID("Maker") || !billet.getTag().getUUID("Maker").equals(p.getUUID())) return false;
        var s = ForgeJobData.from(billet); if (s == null) return false;
        boolean appropriate = switch (kind()) {
            case HEARTH -> s.stage == ForgeSession.Stage.HEATING || s.stage == ForgeSession.Stage.SHAPING && s.heat < 650;
            case ANVIL -> s.stage == ForgeSession.Stage.SHAPING || s.stage == ForgeSession.Stage.ASSEMBLY;
            case BATH -> s.stage == ForgeSession.Stage.QUENCHING || s.stage == ForgeSession.Stage.ASSEMBLY;
        };
        if (!appropriate) return false;
        if (kind() == WarriorForgeBlock.Kind.HEARTH && s.stage == ForgeSession.Stage.SHAPING) s.reheat();
        work = billet.copyWithCount(1); billet.shrink(1); owner = p.getUUID(); session = s;
        changed(); sound(SoundEvents.ANVIL_PLACE, .6f, .8f); return true;
    }

    public boolean beginHold(ServerPlayer p) {
        if (!mayUse(p) || p.isShiftKeyDown() || session == null || work.isEmpty() || aimedZone(p) < 0) return false;
        if (kind() == WarriorForgeBlock.Kind.HEARTH) {
            if (session.pump()) { changed(); sound(SoundEvents.FIRECHARGE_USE, .5f, .7f); }
            return false;
        }
        if (kind() == WarriorForgeBlock.Kind.BATH) {
            if (water <= 0) { tell(p, "槽内没有水，工件与材料未消耗"); return false; }
            if (session.stage != ForgeSession.Stage.QUENCHING && session.stage != ForgeSession.Stage.ASSEMBLY) return false;
            sound(SoundEvents.FIRE_EXTINGUISH, .55f, 1.2f);
        }
        if (kind() == WarriorForgeBlock.Kind.ANVIL && session.stage == ForgeSession.Stage.ASSEMBLY && find(p, grip()) < 0) {
            tell(p, "缺少" + grip().getDescription().getString() + "，不会先吞工件"); return false;
        }
        return session.stage != ForgeSession.Stage.READY;
    }

    public void release(ServerPlayer p, int heldTicks) {
        holding=false; holdTicks=0;
        if (!mayUse(p) || session == null) return;
        int zone = aimedZone(p); if (zone < 0) return;
        if (kind() == WarriorForgeBlock.Kind.ANVIL && session.stage == ForgeSession.Stage.SHAPING) {
            double score = session.strike(zone, heldTicks);
            if (score < 0) { tell(p, session.heat < 650 ? "金属太冷：取回坯料到赤膛炉复热，不需重买材料" : "让锤头回位；完成12锤后可修正或取去淬火"); return; }
            lastImpact = level.getGameTime(); impactZone = zone; impactScore = score;
            p.swing(InteractionHand.MAIN_HAND, true);
            sound(heldTicks >= 9 ? SoundEvents.ANVIL_LAND : SoundEvents.ANVIL_USE, heldTicks >= 9 ? .5f : .35f, score >= 90 ? 1.3f : .8f);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), worldPosition.getX()+.17+zone/3., worldPosition.getY()+.9, worldPosition.getZ()+.5, score >= 90 ? 12 : 4, .1,.025,.1,.04);
                if (score >= 90) sl.sendParticles(ParticleTypes.CRIT, worldPosition.getX()+.17+zone/3.,worldPosition.getY()+.95,worldPosition.getZ()+.5, 6,.08,.03,.08,.02);
            }
            tell(p, (score >= 90 ? "压实！" : "落锤 " + Math.round(score) + "分") + " · " + session.strokes + "/12 · " + (session.strokes == 12 ? "定形完成：可潜行持锤修偏，空手取去淬火" : hint())); changed();
        } else if (kind() == WarriorForgeBlock.Kind.BATH && finishBath(heldTicks)) {
            water--; changed(); sound(SoundEvents.BUCKET_FILL, .65f, session.quenchScore >= 90 ? 1.4f : .7f);
            tell(p, "淬火 " + Math.round(session.quenchScore) + "分 · " + (session.quenchScore >= 90 ? "冷亮锋线贯通" : pose != session.pattern.pose() ? "姿态不合兵器形制" : "提起时机偏离收束水纹") + " · 可持锤重淬，或空手取回装柄");
        } else if (kind() == WarriorForgeBlock.Kind.ANVIL && session.stage == ForgeSession.Stage.ASSEMBLY) {
            var item = ForgeRegistries.ITEMS.getValue(WarriorForgeContent.id(session.pattern.item));
            int slot = find(p, grip());
            if (item == null || item == Items.AIR || slot < 0) return;
            if (!session.assemble(heldTicks, zone == 1)) { tell(p, "对准中心，按住约1秒再松开压合"); return; }
            if (!p.isCreative()) p.getInventory().getItem(slot).shrink(1);
            var result = new ItemStack(item); ForgeWeaponData.finish(result, work, session);
            work = result;
            if (session.quality() == ForgeQuality.PRIMORDIAL) divineAt = level.getGameTime();
            changed(); sound(SoundEvents.PLAYER_LEVELUP, .6f, 1.25f);
            tell(p, "你锻成了【" + session.quality().title + "·" + session.pattern.title + "】！空手右键领取 · " + summary());
        }
    }
    private boolean finishBath(int ticks) {
        if(water<=0)return false;
        var before=session.stage;
        if(before==ForgeSession.Stage.ASSEMBLY)session.requench();
        if(session.finishQuench(ticks,pose))return true;
        session.stage=before;return false;
    }
    private Item grip() { return session.pattern == ForgePattern.JIAN ? WarriorForgeContent.SWORD_GRIP : session.pattern == ForgePattern.MACE ? WarriorForgeContent.MACE_GRIP : WarriorForgeContent.LONG_GRIP; }
    public static int find(ServerPlayer p, Item item) { for (int i=0;i<36;i++) if (p.getInventory().getItem(i).is(item)) return i; return -1; }
    public String hint() {
        if (session == null) return "";
        return (session.pattern==ForgePattern.MACE?"第"+(session.strokes/3+1)+"棱面 · ":"") + session.pattern.zoneName(session.pattern.zone(session.strokes)) + " · " + (session.pattern.heavy(session.strokes) ? "按住约0.8秒，松开重锤" : "短按轻敲") + " · 等余音回弹再落锤";
    }
    public String summary() { return String.format(java.util.Locale.ROOT,"火候%.0f / 锻打%.0f / 定形%.0f / 淬火%.0f / 装配%.0f",session.heatScore,session.hammerScore,session.shapeScore,session.quenchScore,session.assemblyScore); }
    public void take(ServerPlayer p) {
        if (!mayUse(p) || !p.getMainHandItem().isEmpty() || work.isEmpty()) return;
        syncWork(); var result = work;
        work = ItemStack.EMPTY; session = null; owner = null; changed();
        p.setItemInHand(InteractionHand.MAIN_HAND, result); sound(SoundEvents.ITEM_PICKUP,.5f,1);
        if (ForgeWeaponData.needsName(result)) ForgeNamingNetwork.open(p, result);
    }
    private void syncWork() { if (!work.isEmpty() && session != null && work.is(WarriorForgeContent.BILLET)) work.getOrCreateTag().put(ForgeJobData.KEY, ForgeJobData.save(session)); }
    public void dropWork() { syncWork(); if (!work.isEmpty()) { var dropped=work; work=ItemStack.EMPTY; Block.popResource(level,worldPosition,dropped); } }
    public void changed() { syncWork(); setChanged(); if (level != null) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3); }
    private void sound(SoundEvent sound,float volume,float pitch) { level.playSound(null,worldPosition,sound,SoundSource.BLOCKS,volume,pitch); }
    private static void tell(ServerPlayer p,String message) { p.displayClientMessage(net.minecraft.network.chat.Component.literal(message),true); }
    public static void tick(Level l,BlockPos pos,BlockState state,WarriorForgeEntity be) {
        if (be.owner == null || be.session == null || be.session.stage == ForgeSession.Stage.READY) return;
        var p=((ServerLevel)l).getServer().getPlayerList().getPlayer(be.owner);
        if (p == null || p.level() != l || !be.mayUse(p) || !be.watching(p)) {
            if(be.holding){be.holding=false;be.holdTicks=0;be.changed();}return;
        }
        be.holding=p.isUsingItem()&&p.getUseItem().is(WarriorForgeContent.HAMMER);
        be.holdTicks=be.holding?p.getTicksUsingItem():0;
        be.session.tick(); be.setChanged();
        if (l.getGameTime()%4==0) be.changed();
        if (l.getGameTime()%20==0) {
            if (be.kind()==WarriorForgeBlock.Kind.HEARTH) tell(p,"炭床热度 " + be.session.heat + " · 橙红可锻700～800 · 空手右键夹出");
            else if(be.session.stage==ForgeSession.Stage.SHAPING && be.session.strokes<12) tell(p,be.hint());
        }
        if(be.kind()==WarriorForgeBlock.Kind.BATH && p.isUsingItem() && p.getUseItem().is(WarriorForgeContent.HAMMER)) {
            int t=p.getTicksUsingItem();
            if(l.getGameTime()%4==0) ((ServerLevel)l).sendParticles(t<65?ParticleTypes.CLOUD:ParticleTypes.BUBBLE_POP,pos.getX()+.5,pos.getY()+.85,pos.getZ()+.5,t<65?2:4,.22,.03,.22,.005);
            if(t>=68 && t<=92 && l.getGameTime()%8==0) { be.sound(SoundEvents.AMETHYST_BLOCK_CHIME,.35f,1.3f); tell(p,"水纹正在收束 · 现在提起！"); }
        }
    }
    @Override protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t); syncWork(); t.put("Work",work.save(new CompoundTag()));
        if (owner!=null)t.putUUID("Owner",owner); if(session!=null)t.put("Session",ForgeJobData.save(session));
        t.putInt("Selected",selected);t.putInt("Water",water);t.putInt("Pose",pose);t.putBoolean("Slow",slow);
        t.putLong("LastImpact",lastImpact);t.putInt("ImpactZone",impactZone);t.putDouble("ImpactScore",impactScore);t.putLong("DivineAt",divineAt);
        t.putInt("HoldTicks",holdTicks);t.putBoolean("Holding",holding);
    }
    @Override public void load(CompoundTag t) {
        super.load(t);work=ItemStack.of(t.getCompound("Work"));owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;
        session=t.contains("Session",10)?ForgeJobData.load(t.getCompound("Session")):null;
        selected=Math.max(0,Math.min(3,t.getInt("Selected")));water=Math.max(0,Math.min(10,t.getInt("Water")));pose=Math.max(0,Math.min(3,t.getInt("Pose")));slow=t.getBoolean("Slow");
        lastImpact=t.getLong("LastImpact");impactZone=Math.max(0,Math.min(2,t.getInt("ImpactZone")));impactScore=ForgeQuality.clamp(t.getDouble("ImpactScore"));divineAt=t.contains("DivineAt")?t.getLong("DivineAt"):-1000;
        holdTicks=Math.max(0,Math.min(72000,t.getInt("HoldTicks")));holding=t.getBoolean("Holding");
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(divineAt>=0?48:2); }
}
