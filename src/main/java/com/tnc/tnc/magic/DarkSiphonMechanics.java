package com.tnc.tnc.magic;

import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.event.CombatEvents;
import net.spell_engine.internals.casting.SpellCast;

public final class DarkSiphonMechanics {
    private DarkSiphonMechanics(){}
    static {
        try {CombatEvents.SPELL_CAST.register(DarkSiphonMechanics::cast);}
        catch(LinkageError absent){org.slf4j.LoggerFactory.getLogger("TN-C/siphon").debug("Spell Engine absent",absent);}
    }
    public static void ensureLoaded(){}
    public static List<LivingEntity> targets(ServerPlayer player,int tier) {
        double radius=DarkSiphonRules.range(tier);
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(radius),
                e->e!=player&&e.isAlive()&&!e.isSpectator()&&!e.isAlliedTo(player)
                        &&e.distanceToSqr(player)<=radius*radius&&player.hasLineOfSight(e))
                .stream().sorted(Comparator.comparingDouble(e->e.distanceToSqr(player)))
                .limit(DarkSiphonRules.targets(tier)).toList();
    }
    private static void cast(CombatEvents.SpellCast.Args args) {
        if(args==null||args.action()!=SpellCast.Action.RELEASE||!(args.caster() instanceof ServerPlayer player)
                ||args.spell()==null||args.spell().spell()==null)return;
        var spell=args.spell().spell();
        if(!"dark_sacrifice".equals(spell.group)||spell.learn==null||TNOrbEntities.DRAIN==null)return;
        int tier=Math.max(1,Math.min(5,spell.learn.tier));
        // Refresh this player's channel rather than stacking duplicate drains on recasts.
        player.serverLevel().getEntitiesOfClass(TNDarkDrainEntity.class,player.getBoundingBox().inflate(40),
                e->e.casterId()==player.getId()).forEach(net.minecraft.world.entity.Entity::discard);
        for(var victim:targets(player,tier)) {
            var drain=TNOrbEntities.DRAIN.get().create(player.serverLevel());
            if(drain==null)continue;
            drain.configureSiphon(player,victim,tier);player.serverLevel().addFreshEntity(drain);
        }
    }
}
