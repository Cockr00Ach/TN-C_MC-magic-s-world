package com.tnc.tnc.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class WindEarthVisualGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_earth_visuals")
    public static void allThirtyFivePresentationsAreNonDestructiveAndFinite(GameTestHelper h) {
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,2,2));owner.setNoAi(true);
        var state=h.getLevel().getBlockState(h.absolutePos(new BlockPos(2,1,2)));
        float health=owner.getHealth();
        for(var spec:WindEarthVisualRules.all().values()) {
            var visual=WindEarthVisualMechanics.VISUAL.get().create(h.getLevel());
            h.assertTrue(visual!=null,"Visual entity registered without Spell Engine");
            visual.configure(spec,owner);
            h.assertTrue(visual.noPhysics&&!visual.isAttackable()&&!visual.isPickable(),"Presentation has no combat/collision semantics");
            h.assertTrue(visual.kind()==spec.kind()&&visual.radius()==(double)(float)spec.radius(),"Kind and area reach clients");
            visual.tickCount=1;visual.tick();
            h.assertTrue(owner.getHealth()==health,"Visual cannot damage the owner");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(2,1,2)))==state,"Visual cannot mutate terrain");
            visual.tickCount=spec.life()+1;visual.tick();
            h.assertTrue(visual.isRemoved(),"Every unsupported/expired presentation cleans itself up");
        }
        owner.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_earth_visuals")
    public static void buffAuraExtendsWithRealBuffAndEndsWhenRemoved(GameTestHelper h) {
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,2,2));owner.setNoAi(true);
        owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,1200,0));
        var visual=WindEarthVisualMechanics.VISUAL.get().create(h.getLevel());
        visual.configure(WindEarthVisualRules.find("earth_ring"),owner);
        visual.tickCount=20;visual.tick();
        h.assertTrue(!visual.isRemoved()&&visual.life()>=1200,"Aura follows actual buff life rather than stopping prematurely");
        owner.removeAllEffects();visual.tickCount=21;visual.tick();
        h.assertTrue(visual.isRemoved(),"No ghost armour after buff removal");
        owner.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_earth_visuals")
    public static void windSpiritSlotsReachClientsAndHigherTierMechanicsStayIntact(GameTestHelper h) {
        var spirit=TNOrbEntities.WIND_SPIRIT.get().create(h.getLevel());
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,2,2));
        // Binding needs a ServerPlayer in production, so inspect the synchronized default and table here.
        h.assertTrue(spirit!=null&&spirit.slot()==0,"Leader slot is synchronized from initialization");
        h.assertTrue(TNWindMechanics.orbsForTier(4)==3&&TNWindMechanics.orbsForTier(5)==5,"Existing spirit counts preserved");
        h.assertTrue(TNTornadoEntity.BLADES_PER_BURST[2]==3&&TNTornadoEntity.SPIN_PER_TICK==27,"Existing blade count and requested tornado speed preserved");
        spirit.discard();owner.discard();h.succeed();
    }
}
