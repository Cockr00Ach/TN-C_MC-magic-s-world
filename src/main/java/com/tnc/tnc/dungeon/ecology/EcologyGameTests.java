package com.tnc.tnc.dungeon.ecology;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.arcana.*;
import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.mana.sight.ManaSightServer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Actual registered entities, actual damage, serialized capture tokens and real typed mana balances. */
@GameTestHolder("tncdungeon") @PrefixGameTestTemplate(false)
public final class EcologyGameTests {
    private static EcologyMob spawn(GameTestHelper h,String kind){var mob=EcologyContent.create(kind,h.getLevel());h.assertTrue(mob!=null,"registered ecology type "+kind);var pos=h.absolutePos(new BlockPos(2,1,2));mob.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);mob.setNoAi(true);h.assertTrue(h.getLevel().addFreshEntity(mob),"actual ecology mob joins level");return mob;}
    private static ServerPlayer player(GameTestHelper h,Vec3 at){var player=AdventureGameTests.player(h);player.setPos(at.x,at.y,at.z);return player;}
    private static void face(ServerPlayer player,Vec3 at){Vec3 d=at.subtract(player.getEyePosition());player.setYRot((float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90);player.setXRot((float)(-Mth.atan2(d.y,d.horizontalDistance())*180/Math.PI));}
    private static RouteNodeEntity node(GameTestHelper h,BlockPos relative,RouteKind kind,UUID owner){var at=h.absolutePos(relative);h.setBlock(relative,RouteContent.NODES.get(kind.id).get());var node=(RouteNodeEntity)h.getLevel().getBlockEntity(at);node.owner=owner;RouteNetwork.add(h.getLevel(),at);return node;}

    @GameTest(template="building_test_empty",timeoutTicks=40,batch="ecology")
    public static void fourEcologiesUseDifferentEntityClassesAndStats(GameTestHelper h){
        Set<Class<?>> classes=new HashSet<>();Set<String> ids=new HashSet<>();
        for(String kind:EcologyContent.KINDS){var mob=spawn(h,kind);classes.add(mob.getClass());ids.add(mob.species().id());h.assertTrue(mob.ecologyKind().equals(kind)&&Math.abs(mob.getMaxHealth()-mob.species().health())<.01,"species factory owns attributes "+kind);mob.discard();}
        h.assertTrue(classes.size()==4&&ids.size()==4,"four independent implementations, no renamed vanilla monster");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="ecology")
    public static void severedArteryHalvesDamageAndSpeedExactlyOnceAcrossReload(GameTestHelper h){
        var mob=spawn(h,"crab");mob.configure(12,3,false);double damage=mob.getAttributeValue(Attributes.ATTACK_DAMAGE),speed=mob.getAttributeValue(Attributes.MOVEMENT_SPEED);mob.bindSupply(h.absolutePos(new BlockPos(1,1,1)));mob.applySupply(SupplyState.CUT);mob.applySupply(SupplyState.CUT);
        h.assertTrue(Math.abs(mob.getAttributeValue(Attributes.ATTACK_DAMAGE)-damage*.5)<.001&&Math.abs(mob.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed*.5)<.001,"cut debuffs are exactly half, not multiplied repeatedly");
        CompoundTag tag=new CompoundTag();mob.save(tag);var loaded=(EcologyMob)EntityType.loadEntityRecursive(tag,h.getLevel(),e->e);h.assertTrue(loaded!=null&&loaded.supply().equals(mob.supply()),"source link survives serialization");
        h.assertTrue(Math.abs(loaded.getAttributeValue(Attributes.ATTACK_DAMAGE)-damage*.5)<.001,"reload restores one transient debuff");loaded.applySupply(SupplyState.NORMAL);h.assertTrue(Math.abs(loaded.getAttributeValue(Attributes.ATTACK_DAMAGE)-damage)<.001,"restoring power removes the debuff");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="ecology")
    public static void pollutedBirthShattersArmorAndDoesNotResetHealthOnEveryTick(GameTestHelper h){var mob=spawn(h,"flash");mob.applySupply(SupplyState.TAINTED);float expected=mob.getMaxHealth()*.1f;h.assertTrue(Math.abs(mob.getHealth()-expected)<.01&&mob.getAttributeValue(Attributes.ARMOR)==0,"tainted birth has ten percent health and no armor");mob.setHealth(expected*.5f);mob.applySupply(SupplyState.TAINTED);h.assertTrue(Math.abs(mob.getHealth()-expected*.5f)<.01,"supply refresh cannot heal a poisoned spawn");h.succeed();}
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="ecology")
    public static void wasteDissolvesActualMimicWithoutConsumingTheCallerStack(GameTestHelper h){var mob=(MimicCrystalCrab)spawn(h,"crab");var player=player(h,mob.position());var held=new ItemStack(ArcanaContent.item("strange_waste"),3);h.assertTrue(!mob.awakened(),"initial disguise is closed");h.assertTrue(mob.applyPotion(player,"strange_waste")&&!mob.isAlive(),"waste destroys the disguised crystal crab");h.assertTrue(held.getCount()==3,"entity hook leaves one-time flask consumption to caller");h.succeed();}
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="ecology")
    public static void invisibleSiphonRequiresTheIndividualAttackersSight(GameTestHelper h){
        var mob=(ManaSiphonJelly)spawn(h,"siphon");var plain=player(h,mob.position());var sighted=player(h,mob.position());ManaSightServer.subscribe(sighted,true);
        try{h.assertTrue(!mob.hurt(plain.damageSources().playerAttack(plain),4)&&mob.getHealth()==mob.getMaxHealth(),"non-subscriber cannot hit invisible body");h.assertTrue(mob.hurt(sighted.damageSources().playerAttack(sighted),4)&&mob.getHealth()<mob.getMaxHealth(),"only the F8 attacker's own state unlocks the hit");}finally{ManaSightServer.subscribe(sighted,false);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="ecology")
    public static void fireSplashAtOwnFeetDislodgesAnAttachedSiphon(GameTestHelper h){var mob=(ManaSiphonJelly)spawn(h,"siphon");var player=player(h,mob.position());h.assertTrue(mob.attach(player)&&mob.attached(),"suction cup attaches to actual player");h.assertTrue(EcologyHooks.splash(player,player.position(),"flame_twist_elixir")==1&&!mob.attached()&&mob.exposedTicks()>0,"fire potion has a real splash counter without F8");h.assertTrue(!mob.attach(player),"recently dislodged jelly cannot instantly reattach");h.succeed();}
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="ecology")
    public static void siphonDrainsRealPlayerManaAndCutSupplyHalvesTheDrain(GameTestHelper h){var mob=(ManaSiphonJelly)spawn(h,"siphon");var player=player(h,mob.position());var mana=com.tnc.tnc.magic.MagicStone.getOrNull(player);h.assertTrue(mana!=null,"player owns real mana capability");mana.setMaxMana(100);mana.setMana(50);h.assertTrue(mob.drain(player)==8&&mana.getMana()==42,"normal siphon actually removes eight mana");mob.applySupply(SupplyState.CUT);h.assertTrue(mob.drain(player)==4&&mana.getMana()==38,"severing the source weakens the parasite");h.succeed();}
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="ecology")
    public static void flashChecksGazeSightAndCooldownPerPlayer(GameTestHelper h){
        var mob=(GlareGargoyle)spawn(h,"flash");var plain=player(h,mob.position().add(0,0,2));var sighted=player(h,plain.position());face(plain,mob.position().add(0,mob.getBbHeight()*.62,0));face(sighted,mob.position().add(0,mob.getBbHeight()*.62,0));ManaSightServer.subscribe(sighted,true);
        try{h.assertTrue(!mob.tryFlash(plain)&&!plain.hasEffect(MobEffects.BLINDNESS),"normal-view teammate stays unaffected");h.assertTrue(mob.tryFlash(sighted)&&sighted.getEffect(MobEffects.BLINDNESS).getDuration()==100,"gazing in F8 causes five actual seconds of blindness");h.assertTrue(!mob.tryFlash(sighted),"same gaze cannot refresh blindness every tick");sighted.setYRot(sighted.getYRot()+180);h.assertTrue(!mob.tryFlash(sighted),"looking away never triggers a flash");}finally{ManaSightServer.subscribe(sighted,false);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=50,batch="ecology")
    public static void highFloorGrubCapturesAndProducesFromOneActualCagedSpecimen(GameTestHelper h){
        var grub=(SourceCrystalGrub)spawn(h,"worm");grub.configure(30,4,false);h.assertTrue(grub.getMaxHealth()>250,"fixture exercises scaled health above old generic capture cap");grub.setHealth(grub.getMaxHealth()*.2f);var player=player(h,grub.position());var stranger=player(h,grub.position());var potion=new ItemStack(ArcanaContent.item("capture_shrink_elixir"),2);
        h.assertTrue(ArcanaEcology.applyAnimal(player,grub,"capture_shrink_elixir",potion)&&potion.getCount()==1&&grub.captureWindow(),"real shrink reagent consumes once and opens ten-second window");h.assertTrue(!grub.applyPotion(stranger,"capture_shrink_elixir"),"another player cannot steal an active capture window");
        var empty=new ItemStack(RouteContent.item("ecology_restraint_cage"));h.assertTrue(ArcanaCages.capture(player,grub,empty)&&!grub.isAlive(),"same specimen is removed and serialized to server token ledger");
        var packed=player.getInventory().items.stream().filter(s->s.is(RouteContent.item("ecology_restraint_cage"))&&s.hasTag()).findFirst().orElseThrow();var cage=node(h,new BlockPos(1,1,1),RouteKind.RESTRAINT,player.getUUID());var copy=node(h,new BlockPos(3,1,1),RouteKind.RESTRAINT,player.getUUID());var hood=node(h,new BlockPos(2,1,3),RouteKind.HOOD,player.getUUID());cage.mana=100;copy.mana=100;ArcanaCages.placed(player,cage,packed);ArcanaCages.placed(player,copy,packed.copy());ArcanaCages.tick(h.getLevel(),cage);ArcanaCages.tick(h.getLevel(),copy);
        h.assertTrue(cage.getPersistentData().hasUUID("Captive")&&!copy.getPersistentData().hasUUID("Captive"),"copying the cage NBT cannot duplicate the living battery");var actual=h.getLevel().getEntity(cage.getPersistentData().getUUID("Captive"));h.assertTrue(actual instanceof SourceCrystalGrub g&&!g.captureWindow()&&g.supply()==null,"deployed specimen has ordinary size and no stale dungeon source");
        ArcanaCages.tick(h.getLevel(),hood);h.assertTrue(hood.mana==48&&ArcanaEnergy.amount(hood,"astral")==48,"actual caged grub supplies forty-eight real typed mana");ArcanaCages.tick(h.getLevel(),hood);h.assertTrue(hood.mana==48,"same specimen cannot be drawn twice in one game tick");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="ecology")
    public static void expiredOrHealedCaptureCannotEnterTheCage(GameTestHelper h){var grub=spawn(h,"worm");var player=player(h,grub.position());grub.setHealth(8);h.assertTrue(grub.applyPotion(player,"capture_shrink_elixir"),"capture window starts");grub.heal(20);h.assertTrue(!ArcanaCages.capture(player,grub,new ItemStack(RouteContent.item("ecology_restraint_cage"))),"healing above threshold invalidates capture");grub.setHealth(8);grub.getPersistentData().putLong("CaptureUntil",h.getLevel().getGameTime()-1);h.assertTrue(!ArcanaCages.capture(player,grub,new ItemStack(RouteContent.item("ecology_restraint_cage"))),"ten-second deadline is authoritative");h.succeed();}
}
