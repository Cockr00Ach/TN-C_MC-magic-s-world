package com.tnc.tnc.boss;

import com.tnc.tnc.dark.TNDarkSummonEntity;
import com.tnc.tnc.magic.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_power.api.SpellPower;
import java.util.Comparator;

/** Spell Engine integration is isolated so the arsenal remains usable without the optional engine. */
final class TNDarkGiantCasting {
    /** 巨兽人领主的默认值：召唤物 ×10、落点半径 14 格（它肩宽 4 格，召唤物得摆在它身子外面 ✓）。 */
    private static final double GIANT_SUMMON_SCALE = 10.0D;
    private static final double GIANT_SUMMON_RADIUS = 14.0D;

    public static boolean cast(LivingEntity caster,TNDarkGiantSpells.Move move){
        return cast(caster,move,GIANT_SUMMON_SCALE,GIANT_SUMMON_RADIUS);
    }

    /**
     * 同一套暗系法术，给不同体型的 boss 用 ✓（2026-11-07：黑魔法师要和巨兽人领主放同一套法术）。
     *
     * <p>★ 为什么要有这个重载：召唤那一路以前把 {@code setScale(10)} 和
     * {@code radius = caster.getBbWidth() + 14} 写死 ✗ —— 那是照 4 格宽的巨人量的 ✓。
     * 黑魔法师只有 1 格宽，照抄的话三个召唤物会摆在 15 格开外 ✗（看不见、也打不到人 ✗），
     * 体型还会是巨人那个 10 倍 ✗。所以把这两个数变成参数 ✓：巨人 10 / 14，法师 2.6 / 4.5。
     *
     * <p>★ 半径的公式<b>没有改</b>（还是 {@code caster.getBbWidth() + summonRadius} ✓）——
     * 只把"加多少"变成参数 ✓ ⇒ 巨人拿到 14 时算出来和以前<b>一模一样</b> ✓（24+14=38 ✓，
     * 零行为变化 ✓），法师拿到 4.5 ⇒ 1.1+4.5≈5.6 格 ✓（就在他身边 ✓）。
     *
     * @param summonScale  召唤物倍率（1.0 = 原样）
     * @param summonRadius 召唤物离 caster 的横向距离（格，另加 caster 自身宽度）
     */
    public static boolean cast(LivingEntity caster,TNDarkGiantSpells.Move move,double summonScale,double summonRadius){
        if(!(caster.level() instanceof ServerLevel level))return false;
        var target=caster instanceof Mob mob?mob.getTarget():null;
        if(caster instanceof TNDarkGiantEntity giant&&"slay_light".equals(move.path())&&giant.skyfallLanding()!=null)
            return hands(level,caster,target,5);
        if(target==null||!target.isAlive()||target.distanceToSqr(caster)>TNDarkGiantSpells.CAST_RANGE*TNDarkGiantSpells.CAST_RANGE)return false;
        return switch(move.chain()){
            case 0 -> summon(level,caster,target,move.tier(),summonScale,summonRadius);
            case 2 -> fog(level,caster,move.path());
            case 3 -> siphon(level,caster);
            default -> target!=null&&target.isAlive()&&hands(level,caster,target,move.tier());
        };
    }
    /**
     * 黑雾能撑多久（tick）✓ —— 直接读法术 JSON 的
     * {@code release.target.cloud.time_to_live_seconds} ✓（数据驱动：以后改雾的时长，分身自动跟着改 ✓）。
     *
     * <p>读不到（法术还没注册 / 没装 Spell Engine）就按 15 秒 = 300 tick 算 ✓ ——
     * 黑魔法师靠这个数排"雾散了再让三个身影一起攻击"✓（2026-11-07 第三轮）。
     */
    static int mistTicks(String path) {
        try {
            var spell = SpellRegistry.getSpell(ResourceLocation.fromNamespaceAndPath("tnc", path));
            if (spell != null && spell.release != null && spell.release.target != null
                    && spell.release.target.cloud != null
                    && spell.release.target.cloud.time_to_live_seconds > 0.0F) {
                return Math.max(20, (int) (spell.release.target.cloud.time_to_live_seconds * 20.0F));
            }
        } catch (Throwable ignored) {
            // 引擎是可选依赖 ⇒ 类不在 / 字段改了就落到下面的保底 ✓，不能让 boss tick 崩 ✗
        }
        return 300;
    }

    private static boolean hands(ServerLevel level,LivingEntity caster,LivingEntity target,int tier){
        if(TNOrbEntities.NIGHT_GRIP==null)return false;
        int count=NightHandReleaseRules.count(tier);
        for(int i=0;i<count;i++){
            var hand=TNOrbEntities.NIGHT_GRIP.get().create(level);if(hand==null)return false;
            if(tier>=5){
                double angle=i*Math.PI*2/count;
                Vec3 center=caster instanceof TNDarkGiantEntity giant&&giant.skyfallLanding()!=null?giant.skyfallLanding():target.position();
                Vec3 landing=i==0?center:center.add(Math.cos(angle)*4.5,0,Math.sin(angle)*4.5);
                hand.launchSkyfall(caster,i==0&&target!=null?target.getId():-1,tier,i,count,landing);
            }else hand.launch(caster,target.getId(),tier,i,count);
            level.addFreshEntity(hand);
        }
        return true;
    }
    private static boolean siphon(ServerLevel level,LivingEntity caster){
        if(TNOrbEntities.DRAIN==null)return false;
        double range=TNDarkGiantSpells.CAST_RANGE;
        level.getEntitiesOfClass(TNDarkDrainEntity.class,caster.getBoundingBox().inflate(range),d->d.casterId()==caster.getId())
                .forEach(net.minecraft.world.entity.Entity::discard);
        var victims=level.getEntitiesOfClass(LivingEntity.class,caster.getBoundingBox().inflate(range),
                e->e!=caster&&e.isAlive()&&!e.isSpectator()&&!e.isAlliedTo(caster)
                        &&!(e instanceof TNDarkSummonEntity)&&!(e instanceof net.minecraft.world.entity.player.Player p&&p.isCreative())
                        &&e.distanceToSqr(caster)<=range*range&&caster.hasLineOfSight(e));
        var primary=caster instanceof Mob mob?mob.getTarget():null;
        victims.sort(Comparator.comparingDouble(e->e==primary?-1:e.distanceToSqr(caster)));
        int spawned=0;
        for(var victim:victims.subList(0,Math.min(victims.size(),DarkSiphonRules.targets(3)))){
            var drain=TNOrbEntities.DRAIN.get().create(level);if(drain==null)continue;
            drain.configureSiphon(caster,victim,3,range);level.addFreshEntity(drain);spawned++;
        }
        return spawned>0;
    }
    private static boolean fog(ServerLevel level,LivingEntity caster,String path){
        var id=ResourceLocation.fromNamespaceAndPath("tnc",path);
        var spell=SpellRegistry.getSpell(id);
        if(spell==null||spell.release==null||spell.release.target==null||spell.release.target.cloud==null||TNOrbEntities.FOG==null)return false;
        // Refresh rather than accumulating damaging domains on every cast.
        level.getEntitiesOfClass(DarkFogCloudEntity.class,caster.getBoundingBox().inflate(32),f->f.isOwnedBy(caster))
                .forEach(net.minecraft.world.entity.Entity::discard);
        var cloud=new TNDarkGiantFog(TNOrbEntities.FOG.get(),level);
        cloud.prepare(spell);
        var original=SpellPower.getSpellPower(spell.school,caster);
        var power=new SpellPower.Result(spell.school,Math.max(10,original.baseValue()),original.criticalChance(),original.criticalDamage());
        cloud.setOwner(caster);cloud.setPos(caster.position());
        cloud.onCreatedFromSpell(id,cloud.getSpell().release.target.cloud,new SpellHelper.ImpactContext().position(caster.position()).power(power));
        level.addFreshEntity(cloud);return true;
    }
    private static boolean summon(ServerLevel level,LivingEntity caster,LivingEntity target,int tier,
                                  double summonScale,double summonRadius){
        var mine=level.getEntitiesOfClass(TNDarkSummonEntity.class,caster.getBoundingBox().inflate(128),e->caster.getUUID().equals(e.owner()));
        mine.sort(Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));
        for(int i=0;i<Math.max(0,mine.size()+3-6);i++)mine.get(i).discard();
        double radius=caster.getBbWidth()+summonRadius;
        int spawned=0;
        for(int i=0;i<3;i++){
            var type=i==0?(tier==5?TNOrbEntities.EVIL_GOD.get():TNOrbEntities.DARK_KING.get())
                    :(tier==5?TNOrbEntities.DARK_KING.get():TNOrbEntities.DARK_LORD.get());
            var summon=type.create(level);if(summon==null)continue;
            double angle=Math.toRadians(caster.getYRot())+i*Math.PI*2/3;
            double x=caster.getX()+Math.cos(angle)*radius,z=caster.getZ()+Math.sin(angle)*radius;
            var pos=net.minecraft.core.BlockPos.containing(x,caster.getY()+3,z);
            for(int drop=0;drop<12&&level.isEmptyBlock(pos.below());drop++)pos=pos.below();
            summon.setOwner(caster.getUUID());summon.setScale(summonScale);summon.setLifetime((tier==5?70:50)*20);
            summon.moveTo(x,pos.getY(),z,caster.getYRot(),0);summon.setTarget(target);
            level.addFreshEntity(summon);spawned++;
        }
        return spawned>0;
    }
}
