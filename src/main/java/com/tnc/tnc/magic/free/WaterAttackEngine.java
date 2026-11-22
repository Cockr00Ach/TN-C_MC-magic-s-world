package com.tnc.tnc.magic.free;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.tnc.tnc.magic.free.FreeMagicIntent.Behavior;
import static com.tnc.tnc.magic.free.FreeMagicIntent.DURABLE;
import static com.tnc.tnc.magic.free.FreeMagicIntent.FAST;
import static com.tnc.tnc.magic.free.FreeMagicIntent.Intent;
import static com.tnc.tnc.magic.free.FreeMagicIntent.Shape;
import static com.tnc.tnc.magic.free.FreeMagicIntent.SHOCK;
import static com.tnc.tnc.magic.free.FreeMagicNetwork.*;

final class WaterAttackEngine {
    private static final int AREA_LIMIT = 12;
    private static final int SWEEP_TICKS = 14;
    private static final int VORTEX_INTERVAL = 12;
    private static final int UPDATE_INTERVAL = 5;
    private static final double WALL_EPSILON = .04;
    private static final List<Attack> ATTACKS = new ArrayList<>();
    private static long nextAttack;
    private record Residue(Attack attack,Vec3 at,long started) {}
    private static final List<Residue> RESIDUES=new ArrayList<>();

    private WaterAttackEngine() {}

    private static final class Attack {
        final long id = ++nextAttack;
        final long started;
        final ServerPlayer owner;
        final ServerLevel level;
        final LoadedBlocks blocks;
        final Intent intent;
        final Shape shape;
        final Vec3 aim;
        final Set<UUID> viewers = new HashSet<>();
        final Set<Integer> struck = new HashSet<>();
        Vec3 position;
        Vec3 velocity;
        boolean returning;
        boolean leftResidue;
        LivingEntity target;

        Attack(ServerPlayer owner, Intent intent, Vec3 eye, Vec3 aim) {
            this.owner = owner;
            this.level = owner.serverLevel();
            this.started = level.getGameTime();
            this.blocks = new LoadedBlocks(level);
            this.intent = intent;
            this.shape = intent.shape();
            this.aim = aim;
            this.position = eye;
            this.velocity = aim.scale(intent.speed());
        }
    }

    private record Contact(LivingEntity target, Vec3 at, double distanceSquared) {}

    static long activeCount(ServerPlayer owner) {
        return ATTACKS.stream().filter(attack -> attack.owner == owner).count()+RESIDUES.stream().filter(r->r.attack.owner==owner).count();
    }

    static void launch(ServerPlayer owner, Intent intent, int index, int count) {
        Shape shape = intent.shape();
        if (shape.stationary()) {
            stationaryTarget(owner, intent, index, count).ifPresent(at -> launchStationary(owner, intent, at));
            return;
        }
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();
        Vec3 origin = WaterCastGeometry.origin(intent, eye, look, index, count);
        launchFrom(owner,intent,origin);
    }

    static void launchFrom(ServerPlayer owner,Intent intent,Vec3 origin){
        Vec3 eye=owner.getEyePosition(),look=owner.getLookAngle();
        Vec3 aim = WaterCastGeometry.direction(eye, look, origin);
        var attack = new Attack(owner, intent, origin, aim);
        playRelease(attack);
        if (!advanceSegment(attack, origin, true)) start(attack);
    }

    static void launchStationary(ServerPlayer owner, Intent intent, Vec3 at) {
        var attack = new Attack(owner, intent, at, Vec3.ZERO);
        playRelease(attack);
        start(attack);
    }

    private static void playRelease(Attack attack) {
        Shape shape = attack.shape;
        ServerPlayer owner = attack.owner;
        if(attack.intent.has(SHOCK))attack.level.playSound(null,owner.blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.65f,1.4f);
        if(attack.intent.has(FreeMagicIntent.EMPOWER))attack.level.playSound(null,owner.blockPosition(),SoundEvents.TRIDENT_THUNDER,SoundSource.PLAYERS,.45f,1.55f);
        attack.level.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_SPLASH,
                SoundSource.PLAYERS, .45f, shape.large() || shape.behavior == Behavior.EXPLODE ? .7f : 1.2f);
    }

    private static void start(Attack attack) {
        ATTACKS.add(attack);
        track(attack);
    }

    static void tick() {
        ATTACKS.removeIf(WaterAttackEngine::advance);
        RESIDUES.removeIf(WaterAttackEngine::advanceResidue);
    }

    static void removeOwner(UUID id) {
        ATTACKS.removeIf(attack -> attack.owner.getUUID().equals(id) && finish(attack, attack.position));
        RESIDUES.removeIf(r->r.attack.owner.getUUID().equals(id));
    }

    static void clear() {
        ATTACKS.clear();
        RESIDUES.clear();
        nextAttack = 0;
    }

    private static boolean advance(Attack attack) {
        long age = attack.level.getGameTime() - attack.started;
        if (!FreeMagicServer.canAct(attack.owner) || attack.owner.level() != attack.level
                || age >= attack.intent.lifetime()) return finish(attack, attack.position);
        if (age == 0) return false;

        boolean finished = switch (attack.shape.behavior) {
            case COLUMN -> column(attack, age);
            case VORTEX -> vortex(attack, age);
            case SWEEP -> sweep(attack, age);
            case RETURN -> returning(attack, age);
            case SEEK -> seeking(attack, age);
            default -> advanceSegment(attack, attack.position.add(attack.velocity), false);
        };
        if (!finished && age % UPDATE_INTERVAL == 0) track(attack);
        return finished;
    }

    /** Tests the whole swept volume, including the path from the caster to the array. */
    private static boolean advanceSegment(Attack attack, Vec3 end, boolean fromCaster) {
        Vec3 start = attack.position;
        BlockHitResult block = trace(attack, start, end);
        boolean hitWall = block.getType() != HitResult.Type.MISS;
        Vec3 stop = hitWall ? beforeWall(start, block.getLocation()) : end;
        List<Contact> contacts = contacts(attack, start, stop, attack.intent.radius());
        if(!contacts.isEmpty()||hitWall)leaveResidue(attack,contacts.isEmpty()?stop:contacts.get(0).at());
        switch (attack.shape.behavior) {
            case PIERCE, SWEEP, WAVE, RETURN -> {
                for (Contact hit : contacts) {
                    strike(attack, hit.target(), hit.at());
                    if (attack.shape.behavior == Behavior.PIERCE && attack.struck.size() >= attack.intent.pierceLimit()) {
                        return finish(attack, hit.at());
                    }
                }
            }
            case EXPLODE -> {
                if (!contacts.isEmpty() || hitWall) {
                    Vec3 impact = contacts.isEmpty() ? stop : contacts.get(0).at();
                    explode(attack, impact);
                    return finish(attack, impact);
                }
            }
            case NET -> {
                if (!contacts.isEmpty()) {
                    Vec3 impact = contacts.get(0).at();
                    net(attack, impact);
                    return finish(attack, impact);
                }
            }
            default -> {
                if (!contacts.isEmpty()) {
                    Contact hit = contacts.get(0);
                    strike(attack, hit.target(), hit.at());
                    return finish(attack, hit.at());
                }
            }
        }

        attack.position = stop;
        if (!hitWall) return false;
        if (attack.shape.behavior == Behavior.RETURN && !attack.returning && !fromCaster) {
            attack.returning = true;
            attack.velocity = attack.owner.getEyePosition().subtract(stop).normalize().scale(attack.intent.speed());
            track(attack);
            return false;
        }
        return finish(attack, stop);
    }

    private static List<Contact> contacts(Attack attack, Vec3 start, Vec3 stop, double radius) {
        return contacts(attack.owner, attack.blocks, attack.struck, start, stop, radius);
    }

    private static List<Contact> contacts(ServerPlayer owner, LoadedBlocks blocks, Set<Integer> struck,
                                          Vec3 start, Vec3 stop, double radius) {
        var hits = new ArrayList<Contact>();
        var bounds = new AABB(start, stop).inflate(radius);
        for (LivingEntity victim : blocks.level.getEntitiesOfClass(LivingEntity.class, bounds, target -> eligible(owner, target))) {
            if (struck.contains(victim.getId())) continue;
            var box = victim.getBoundingBox().inflate(radius);
            var hit = box.contains(start) ? Optional.of(start) : box.clip(start, stop);
            if (hit.isEmpty() || !visible(owner, blocks, hit.get(), victim.getBoundingBox().getCenter())) continue;
            hits.add(new Contact(victim, hit.get(), start.distanceToSqr(hit.get())));
        }
        hits.sort(Comparator.comparingDouble(Contact::distanceSquared));
        return hits;
    }

    private static boolean eligible(ServerPlayer owner, LivingEntity target) {
        return target != owner && target.isAlive() && !target.isSpectator()
                && !target.isAlliedTo(owner) && !owner.isAlliedTo(target)
                && (!(target instanceof Player player) || owner.canHarmPlayer(player));
    }

    private static boolean hurt(Attack attack, LivingEntity target) {
        return target.hurt(attack.level.damageSources().indirectMagic(attack.owner, attack.owner), (float) attack.intent.damage());
    }

    private static void strike(Attack attack, LivingEntity target, Vec3 at) {
        if (attack.struck.size() >= AREA_LIMIT || !attack.struck.add(target.getId()) || !hurt(attack, target)) return;
        if (attack.shape.behavior == Behavior.PRISON) {
            restrain(attack, target, 68, 3);
            if (attack.intent.has(SHOCK)) knockback(attack, target, attack.velocity, .28 * attack.intent.knockback());
        } else {
            double strength = attack.shape.behavior == Behavior.WAVE ? 1.25 : .28;
            knockback(attack, target, attack.velocity, strength * attack.intent.knockback());
            if (attack.shape.behavior == Behavior.WAVE || attack.shape.behavior == Behavior.PIERCE) {
                impact(attack, at, 12, -1);
            }
        }
    }

    private static void knockback(Attack attack, LivingEntity target, Vec3 direction, double strength) {
        target.knockback(strength, -direction.x, -direction.z);
        if(attack.intent.has(SHOCK)){
            double resistance=target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
            target.push(0,1.05*Math.max(0,1-resistance),0);
            target.hurtMarked=true;
            impact(attack,target.position(),20,-1);
        }
    }

    private static List<LivingEntity> nearby(Attack attack, Vec3 center, AABB bounds) {
        var targets = attack.level.getEntitiesOfClass(LivingEntity.class, bounds, target -> eligible(attack.owner, target));
        targets.sort(Comparator.comparingDouble(target -> target.distanceToSqr(center)));
        return targets;
    }

    private static void explode(Attack attack, Vec3 at) {
        double radius = attack.intent.radius() * 3.6;
        var bounds = new AABB(at, at).inflate(radius);
        int attempted = 0;
        for (LivingEntity target : nearby(attack, at, bounds)) {
            Vec3 center = target.getBoundingBox().getCenter();
            if (center.distanceToSqr(at) > radius * radius || !visible(attack, at, center)) continue;
            if (hurt(attack, target)) knockback(attack, target, center.subtract(at), .95 * attack.intent.knockback());
            if (++attempted >= AREA_LIMIT) break;
        }
        impact(attack, at, 18, -1);
    }

    private static void net(Attack attack, Vec3 at) {
        double radius = attack.intent.radius() * 2.25;
        int attempted = 0;
        for (LivingEntity target : nearby(attack, at, new AABB(at, at).inflate(radius))) {
            Vec3 center = target.getBoundingBox().getCenter();
            if (center.distanceToSqr(at) > radius * radius || !visible(attack, at, center)) continue;
            if (hurt(attack, target)) {
                restrain(attack, target, 44, 1);
                if (attack.intent.has(SHOCK)) knockback(attack, target, center.subtract(at), .28 * attack.intent.knockback());
            }
            if (++attempted >= 3) break;
        }
    }

    private static void restrain(Attack attack, LivingEntity target, int duration, int amplifier) {
        duration = attack.intent.restraintTicks(duration);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier, false, true), attack.owner);
        impact(attack, target.position(), duration, target.getId());
    }

    /** Resolve the same loaded, visible placement for preview and release without creating an attack. */
    static Optional<Vec3> formationOrigin(ServerPlayer owner,Intent intent,int index,int count){
        Vec3 eye=owner.getEyePosition(),desired=WaterCastGeometry.origin(intent,eye,owner.getLookAngle(),index,count);
        var blocks=new LoadedBlocks(owner.serverLevel());
        var hit=trace(owner,blocks,eye,desired);
        Vec3 at=hit.getType()==HitResult.Type.MISS?desired:beforeWall(eye,hit.getLocation());
        // A point-blank target shortens the formation itself; release never sweeps a relocation path.
        var close=contacts(owner,blocks,Set.of(),eye,at,intent.radius());
        if(!close.isEmpty())at=close.get(0).at();
        return blocks.open(at)?Optional.of(at):Optional.empty();
    }

    static Optional<Vec3> stationaryTarget(ServerPlayer owner, Intent intent, int index, int count) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();
        Vec3 origin = WaterCastGeometry.origin(intent, eye, look, index, count);
        var blocks = new LoadedBlocks(owner.serverLevel());
        BlockHitResult formationPath = trace(owner, blocks, eye, origin);
        Vec3 formationStop = formationPath.getType() == HitResult.Type.MISS
                ? origin : beforeWall(eye, formationPath.getLocation());
        List<Contact> close = contacts(owner, blocks, Set.of(), eye, formationStop, .25);
        Vec3 target;
        if (!close.isEmpty()) {
            target = close.get(0).target().position();
        } else {
            Vec3 endpoint = formationStop;
            if (formationPath.getType() == HitResult.Type.MISS) {
                double range = intent.fieldRange();
                Vec3 aim = WaterCastGeometry.direction(eye, look, origin);
                Vec3 end = origin.add(aim.scale(Math.max(0, range - eye.distanceTo(origin))));
                BlockHitResult targetPath = trace(owner, blocks, origin, end);
                endpoint = targetPath.getType() == HitResult.Type.MISS
                        ? end : beforeWall(origin, targetPath.getLocation());
                List<Contact> aimed = contacts(owner, blocks, Set.of(), origin, endpoint, .28);
                if (!aimed.isEmpty()) endpoint = aimed.get(0).target().position();
            }
            BlockHitResult floor = trace(owner, blocks, endpoint, endpoint.add(0, -6, 0));
            target = floor.getType() == HitResult.Type.MISS ? endpoint : floor.getLocation().add(0, .08, 0);
        }
        if (validPlacement(owner, blocks, eye, target)) return Optional.of(target);
        return validPlacement(owner, blocks, eye, formationStop) ? Optional.of(formationStop) : Optional.empty();
    }

    static boolean validPlacement(ServerPlayer owner, Intent intent, Vec3 target) {
        Vec3 eye = owner.getEyePosition();
        double range = intent.fieldRange() + 6;
        return eye.distanceToSqr(target) <= range * range
                && validPlacement(owner, new LoadedBlocks(owner.serverLevel()), eye, target);
    }

    private static boolean validPlacement(ServerPlayer owner, LoadedBlocks blocks, Vec3 eye, Vec3 target) {
        return blocks.open(target) && visible(owner, blocks, eye, target);
    }

    private static boolean column(Attack attack, long age) {
        boolean fast = attack.intent.has(FAST);
        if (age < (fast ? 3 : 4) || age > activeTicks(attack, fast ? 11 : 16)) return false;
        Vec3 origin = attack.position;
        double radius = attack.intent.radius();
        var bounds = new AABB(origin, origin.add(0, 2.75 * scaleMultiplier(attack), 0)).inflate(radius, .3, radius);
        for (LivingEntity target : nearby(attack, origin, bounds)) {
            Vec3 center = target.getBoundingBox().getCenter();
            if (center.subtract(origin).horizontalDistanceSqr() > radius * radius
                    || attack.struck.size() >= AREA_LIMIT || attack.struck.contains(target.getId())
                    || !visible(attack, origin, center)) continue;
            attack.struck.add(target.getId());
            if (hurt(attack, target)) target.push(0, .8 * attack.intent.knockback(), 0);
        }
        return false;
    }

    private static boolean vortex(Attack attack, long age) {
        // Less than ten ticks would make every other pulse bounce off ordinary hurt immunity.
        int interval = attack.intent.has(FAST) ? 10 : VORTEX_INTERVAL;
        if (age % interval != 1) return false;
        Vec3 core = attack.position.add(0, 1.3, 0);
        double radius = attack.intent.radius() * 2.4;
        int attempted = 0;
        var bounds = new AABB(core, core).inflate(radius, 2.2 * scaleMultiplier(attack), radius);
        for (LivingEntity target : nearby(attack, core, bounds)) {
            Vec3 center = target.getBoundingBox().getCenter();
            Vec3 pull = core.subtract(center);
            if (pull.horizontalDistanceSqr() > radius * radius || !visible(attack, core, center)) continue;
            if (hurt(attack, target)) {
                double multiplier = attack.intent.knockback();
                Vec3 inward = pull.multiply(1, 0, 1).normalize().scale(.6 * multiplier);
                target.push(inward.x, .12 * multiplier, inward.z);
            }
            if (++attempted >= AREA_LIMIT) break;
        }
        return false;
    }

    private static boolean sweep(Attack attack, long age) {
        int duration = activeTicks(attack, attack.intent.has(FAST) ? 9 : SWEEP_TICKS);
        if (age > duration) return false;
        if(attack.intent.has(DURABLE)&&age%20==0)attack.struck.clear();
        double yaw = Math.toRadians(Mth.lerp((double) age / duration, -48, 48));
        Vec3 direction = attack.aim.yRot((float) yaw);
        attack.velocity = direction.scale(attack.intent.speed());
        double reach = (attack.shape == Shape.TENTACLE ? 1.8 : 2.1) * scaleMultiplier(attack);
        Vec3 end = attack.position.add(direction.scale(reach));
        BlockHitResult wall = trace(attack, attack.position, end);
        if (wall.getType() != HitResult.Type.MISS) end = beforeWall(attack.position, wall.getLocation());
        for (Contact hit : contacts(attack, attack.position, end, attack.intent.radius() * .65)) {
            strike(attack, hit.target(), hit.at());
        }
        return false;
    }

    private static double scaleMultiplier(Attack attack) {
        return attack.intent.scale() / attack.shape.scale;
    }

    private static void leaveResidue(Attack attack,Vec3 at){
        if(!attack.intent.has(DURABLE)||attack.leftResidue||attack.shape.stationary()
                ||attack.shape.behavior==Behavior.PRISON||attack.shape.behavior==Behavior.NET)return;
        attack.leftResidue=true;
        if(RESIDUES.stream().filter(r->r.attack.owner==attack.owner).count()>=12)return;
        RESIDUES.add(new Residue(attack,at,attack.level.getGameTime()));
        impact(attack,at,80,-2);
    }

    private static boolean advanceResidue(Residue residue){
        var attack=residue.attack();long age=attack.level.getGameTime()-residue.started();
        if(age>=80||!FreeMagicServer.canAct(attack.owner)||attack.owner.level()!=attack.level)return true;
        if(age%20!=0)return false;
        double radius=Math.max(1.6,attack.intent.radius()*1.8);int hits=0;
        for(var target:nearby(attack,residue.at(),new AABB(residue.at(),residue.at()).inflate(radius))){
            var center=target.getBoundingBox().getCenter();
            if(center.distanceToSqr(residue.at())>radius*radius||!visible(attack,residue.at(),center))continue;
            target.hurt(attack.level.damageSources().indirectMagic(attack.owner,attack.owner),attack.intent.damage()*.3f);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,30,0,false,true),attack.owner);
            if(++hits>=AREA_LIMIT)break;
        }
        return false;
    }

    private static int activeTicks(Attack attack, int base) {
        return attack.intent.has(DURABLE) ? Mth.ceil(base * 3) : base;
    }

    private static boolean returning(Attack attack, long age) {
        if (attack.returning || age >= activeTicks(attack, 32)) {
            attack.returning = true;
            Vec3 home = attack.owner.getEyePosition().subtract(attack.position);
            if (home.lengthSqr() < 1.1) return finish(attack, attack.position);
            attack.velocity = turn(attack.velocity, home, 22).scale(attack.intent.speed());
        }
        return advanceSegment(attack, attack.position.add(attack.velocity), false);
    }

    private static boolean seeking(Attack attack, long age) {
        if (age % UPDATE_INTERVAL == 1) {
            double radius = 9;
            Vec3 forward = attack.velocity.normalize();
            attack.target = null;
            for (LivingEntity target : nearby(attack, attack.position, new AABB(attack.position, attack.position).inflate(radius))) {
                Vec3 center = target.getBoundingBox().getCenter();
                Vec3 offset = center.subtract(attack.position);
                if (offset.lengthSqr() <= radius * radius && offset.normalize().dot(forward) > .15
                        && visible(attack, attack.position, center)) {
                    attack.target = target;
                    break;
                }
            }
        }
        if (attack.target != null && attack.target.level() == attack.level && eligible(attack.owner, attack.target)) {
            Vec3 aim = attack.target.getBoundingBox().getCenter().subtract(attack.position);
            attack.velocity = turn(attack.velocity, aim, 7).scale(attack.intent.speed());
        }
        return advanceSegment(attack, attack.position.add(attack.velocity), false);
    }

    static Vec3 turn(Vec3 velocity, Vec3 aim, double maxDegrees) {
        Vec3 from = velocity.normalize();
        Vec3 to = aim.normalize();
        if (from.lengthSqr() == 0) return to;
        if (to.lengthSqr() == 0) return from;
        double dot = Mth.clamp(from.dot(to), -1, 1);
        double radians = Math.toRadians(maxDegrees);
        if (dot >= Math.cos(radians)) return to;
        Vec3 tangent = to.subtract(from.scale(dot)).normalize();
        if (tangent.lengthSqr() == 0) tangent = WaterCastGeometry.right(from);
        return from.scale(Math.cos(radians)).add(tangent.scale(Math.sin(radians)));
    }

    private static BlockHitResult trace(Attack attack, Vec3 from, Vec3 to) {
        return trace(attack.owner, attack.blocks, from, to);
    }

    private static BlockHitResult trace(ServerPlayer owner, LoadedBlocks blocks, Vec3 from, Vec3 to) {
        return blocks.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
    }

    private static boolean visible(Attack attack, Vec3 from, Vec3 to) {
        return visible(attack.owner, attack.blocks, from, to);
    }

    private static boolean visible(ServerPlayer owner, LoadedBlocks blocks, Vec3 from, Vec3 to) {
        return trace(owner, blocks, from, to).getType() == HitResult.Type.MISS;
    }

    private static Vec3 beforeWall(Vec3 start, Vec3 impact) {
        Vec3 offset = impact.subtract(start);
        return start.add(offset.normalize().scale(Math.max(0, offset.length() - WALL_EPSILON)));
    }

    private static Event event(Attack attack, int kind, Vec3 at, Vec3 velocity, int duration, int anchor) {
        return new Event(attack.level.dimension().location(), kind, attack.owner.getUUID(), attack.id, "",
                attack.shape.ordinal(), attack.intent.mode().ordinal(), 1, at, velocity, attack.started, duration, anchor,
                List.of(), attack.intent.modifiers(), attack.intent.chantPower());
    }

    private static void track(Attack attack) {
        showNearby(attack.level, attack.viewers, event(attack, SHOT, attack.position, attack.velocity, attack.intent.lifetime(), -1));
    }

    private static void impact(Attack attack, Vec3 at, int duration, int anchor) {
        showNearby(attack.level, attack.viewers, event(attack, IMPACT, at, Vec3.ZERO, duration, anchor));
    }

    private static boolean finish(Attack attack, Vec3 at) {
        finishForViewers(attack.level, attack.viewers, event(attack, HIT, at, Vec3.ZERO, 0, -1));
        attack.level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 12, .3, .3, .3, .08);
        return true;
    }

    /** Clip against loaded chunks only, treating the first unavailable block as a solid boundary. */
    private record LoadedBlocks(ServerLevel level) implements BlockGetter {
        private LevelChunk chunk(BlockPos pos) {
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
            return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        }

        boolean open(Vec3 point) {
            var pos = BlockPos.containing(point);
            LevelChunk chunk = chunk(pos);
            if (chunk == null) return false;
            Vec3 local = point.subtract(Vec3.atLowerCornerOf(pos));
            var shape = chunk.getBlockState(pos).getCollisionShape(this, pos);
            return shape.toAabbs().stream().noneMatch(box -> box.contains(local));
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            LevelChunk chunk = chunk(pos);
            return chunk == null ? Blocks.BARRIER.defaultBlockState() : chunk.getBlockState(pos);
        }

        @Override public FluidState getFluidState(BlockPos pos) {
            LevelChunk chunk = chunk(pos);
            return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos);
        }

        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            LevelChunk chunk = chunk(pos);
            return chunk == null ? null : chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
        }

        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }
}
