package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.world.entity.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.spell_engine.api.event.CombatEvents;
import net.spell_engine.internals.casting.SpellCast;

public final class WindEarthVisualMechanics {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,TNMod.MODID);
    public static final RegistryObject<EntityType<EarthAttackEntity>> ATTACK=TYPES.register("earth_attack",()->EntityType.Builder.<EarthAttackEntity>of(EarthAttackEntity::new,MobCategory.MISC).sized(.4F,.4F).clientTrackingRange(12).updateInterval(1).build("tnc:earth_attack"));
    public static final RegistryObject<EntityType<WindEarthVisualEntity>> VISUAL=TYPES.register("wind_earth_visual",
            ()->EntityType.Builder.<WindEarthVisualEntity>of(WindEarthVisualEntity::new,MobCategory.MISC)
                    .sized(.1F,.1F).clientTrackingRange(10).updateInterval(10).build("tnc:wind_earth_visual"));
    private WindEarthVisualMechanics(){}
    public static void register(IEventBus bus) {
        TYPES.register(bus);EarthParticles.register(bus);
        try {CombatEvents.SPELL_CAST.register(WindEarthVisualMechanics::cast);}
        catch(LinkageError absent) {org.slf4j.LoggerFactory.getLogger("tnc/wind_earth").debug("Visual cast hook unavailable in headless fixture",absent);}
    }
    public static void ensureFlightAura(ServerPlayer player, boolean permanent) {
        if (!player.serverLevel().getEntitiesOfClass(WindEarthVisualEntity.class,
                player.getBoundingBox().inflate(12),e->e.ownerId()==player.getId()&&e.kind()==0).isEmpty())return;
        var visual=VISUAL.get().create(player.serverLevel());
        if(visual==null)return;
        visual.configure(WindEarthVisualRules.find(permanent?"super_wind_speed":"wind_field"),player);
        player.serverLevel().addFreshEntity(visual);
    }
    private static void cast(CombatEvents.SpellCast.Args args) {
        if(args==null||args.action()!=SpellCast.Action.RELEASE||!(args.caster() instanceof ServerPlayer player)
                ||args.spell()==null||!"tnc".equals(args.spell().id().getNamespace()))return;
        var spec=WindEarthVisualRules.find(args.spell().id().getPath());
        if(spec==null||spec.kind()==WindEarthVisualRules.WIND_GALE||spec.kind()==WindEarthVisualRules.WIND_FLIGHT)return;
        if(spec.kind()>=3&&spec.tier()>=4) {
            var circle=TNOrbEntities.MAGIC_CIRCLE.get().create(player.serverLevel());
            if(circle!=null){circle.configure(spec.tier()==5?5:3.5,50,TNMagicCircleEntity.STYLE_EARTH);circle.setPos(player.position().add(0,.06,0));player.serverLevel().addFreshEntity(circle);}
        }
        EarthAttackMechanics.cast(player,args.spell(),spec);
        if((spec.kind()==3&&spec.tier()>=3)||(spec.kind()==4&&spec.tier()>=2))return;
        // Buffs replace an existing aura; static damage fields keep their separate positions.
        if(spec.followsOwner())player.serverLevel().getEntitiesOfClass(WindEarthVisualEntity.class,
                player.getBoundingBox().inflate(24),e->e.ownerId()==player.getId()&&e.kind()==spec.kind()).forEach(Entity::discard);
        var visual=VISUAL.get().create(player.serverLevel());
        if(visual==null)return;
        visual.configure(spec,player);player.serverLevel().addFreshEntity(visual);
    }
}
