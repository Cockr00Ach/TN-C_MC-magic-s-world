package com.tnc.tnc.dungeon;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 小怪的两条 AI：<b>追击</b>与<b>起手</b>。
 *
 * <p>为什么不直接用 {@code MeleeAttackGoal} ✗：原版近战是"贴上去每 20 tick 砍一刀"，
 * 而我们的怪要的是**看得见前摇的起手**（冲撞要低头蓄力、弩手要拉弦）。
 * 起手时长写在 {@link DungeonSpecies.Kind#windup} 里，动画按同一组 tick 出关键帧，
 * 所以玩家能靠"看到它抬手的瞬间"躲开 —— 这是这套怪的核心手感。
 */
public final class DungeonGoals {

    private DungeonGoals() {
    }

    /** 追击：还没进起手距离、且当前不能出手时朝目标走。 */
    public static final class Approach extends Goal {

        private final TNDungeonMob mob;

        public Approach(TNDungeonMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            // 出手中 / 过热 / 定身 → 原地不动（后摇是给玩家的反打窗口，不能边退边打）
            if (mob.isMidAttack() || mob.stateKey() != 0) {
                return false;
            }
            // 远程怪停在起手距离外（别贴脸），近战怪一路压到 2 格。
            // 冷却期也照走 —— 否则冷却一好，怪还站在 6 格外发呆。
            double stop = mob.kind().ranged ? mob.kind().reachSqr() * 0.5D : 4.0D;
            return mob.distanceToSqr(target) > stop;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            mob.getNavigation().moveTo(target, 1.0D);
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }

    /** 起手：进了距离、有视线、冷却好了就开打（前摇到点才由实体结算伤害）。 */
    public static final class Attack extends Goal {

        private final TNDungeonMob mob;

        public Attack(TNDungeonMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            if (!mob.canStartAttack()) {
                return false;
            }
            if (mob.distanceToSqr(target) > mob.kind().reachSqr()) {
                return false;
            }
            return mob.getSensing().hasLineOfSight(target);
        }

        @Override
        public void start() {
            mob.beginAttack();
        }

        @Override
        public boolean canContinueToUse() {
            return mob.isMidAttack();
        }

        @Override
        public void tick() {
            mob.tickAttack();
            LivingEntity target = mob.getTarget();
            if (target != null) {
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
        }
    }
}
