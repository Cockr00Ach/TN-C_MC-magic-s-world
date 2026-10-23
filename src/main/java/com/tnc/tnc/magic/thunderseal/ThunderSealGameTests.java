package com.tnc.tnc.magic.thunderseal;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
@GameTestHolder("tncthunderseal") @PrefixGameTestTemplate(false)
public final class ThunderSealGameTests {
 private static net.minecraftforge.common.util.FakePlayer player(GameTestHelper h){var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"SealTest"));p.setHealth(20);p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,2,2)));return p;}
 @GameTest(template="empty",timeoutTicks=160)
 public static void chargePulseFinishAndCleanupAreReal(GameTestHelper h){
  var p=player(h);var victim=h.spawn(EntityType.IRON_GOLEM,new BlockPos(2,2,7));victim.setNoAi(true);victim.setNoGravity(true);victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);victim.setHealth(1000);
  var cow=h.spawn(EntityType.COW,new BlockPos(3,2,7));cow.setNoAi(true);cow.setNoGravity(true);float friendly=cow.getHealth();
  var field=ThunderSealContent.SEAL.create(h.getLevel());field.configure(p,5,victim.position(),victim);h.getLevel().addFreshEntity(field);
  h.runAfterDelay(30,()->h.assertTrue(victim.getHealth()==1000,"Damage happened before charge completed"));
  h.runAfterDelay(100,()->{h.assertTrue(victim.getHealth()<1000&&field.hits()>=5,"Pulses or final impact missing");h.assertTrue(cow.getHealth()==friendly,"Neutral animal was hit by area spell");});
  h.runAfterDelay(125,()->{h.assertTrue(field.isRemoved(),"Spell persisted after lifetime");h.succeed();});
 }
 @GameTest(template="empty",timeoutTicks=100)
 public static void wallsBlockDamageAndCasterLossCancels(GameTestHelper h){
  var p=player(h);var target=h.spawn(EntityType.ZOMBIE,new BlockPos(2,2,9));target.setNoAi(true);target.setNoGravity(true);target.setInvulnerable(false);target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
  for(int x=-5;x<=8;x++)for(int y=1;y<=7;y++)h.setBlock(new BlockPos(x,y,7),Blocks.STONE);
  var field=ThunderSealContent.SEAL.create(h.getLevel());field.configure(p,3,h.absoluteVec(new net.minecraft.world.phys.Vec3(2,2,5)),target);h.getLevel().addFreshEntity(field);float before=target.getHealth();
  h.runAfterDelay(75,()->{h.assertTrue(target.getHealth()==before,"Spell hit through a solid wall");p.setHealth(0);});
  h.runAfterDelay(78,()->{h.assertTrue(field.isRemoved(),"Spell survived caster death");h.succeed();});
 }
 @GameTest(template="empty",timeoutTicks=30)
 public static void catalogAndMarkerBridgeAreConnected(GameTestHelper h){
  var catalog=com.tnc.tnc.magic.SpellCatalog.of(com.tnc.tnc.magic.Element.LIGHTNING,com.tnc.tnc.magic.SpellCatalog.Chain.THUNDER_SEAL);
  h.assertTrue(catalog.size()==5,"Missing five-tier catalog chain");for(int i=0;i<5;i++)h.assertTrue(catalog.get(i).tier()==i+1,"Tier order broken");
  var p=player(h);p.setYRot(0);p.setXRot(0);
  var effect=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(ThunderSealContent.id("thunder_seal_cast_1"));h.assertTrue(effect!=null,"Marker was not registered");p.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,1));
  h.assertTrue(!h.getLevel().getEntitiesOfClass(ThunderSealEntity.class,p.getBoundingBox().inflate(50)).isEmpty(),"Status-effect spell release bridge failed");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=30)
 public static void learningAndSaveProgressRemainIndependent(GameTestHelper h){
  var data=new com.tnc.tnc.magic.MagicStoneData();data.assignDefaultAffinities(6);data.recomputeMaxMana(0,10,10,0);data.addBonusPoints(10000);
  var entries=com.tnc.tnc.magic.SpellCatalog.of(com.tnc.tnc.magic.Element.LIGHTNING,com.tnc.tnc.magic.SpellCatalog.Chain.THUNDER_SEAL);
  for(var entry:entries)h.assertTrue(com.tnc.tnc.magic.MagicStoneLearning.unlock(data,entry)==com.tnc.tnc.magic.MagicStoneLearning.Result.OK,"Cannot learn tier "+entry.tier());
  h.assertTrue(data.getProgress(com.tnc.tnc.magic.Element.LIGHTNING,com.tnc.tnc.magic.SpellCatalog.Chain.CORE)==0,"Learning new chain changed old chain");
  var restored=new com.tnc.tnc.magic.MagicStoneData();restored.deserializeNBT(data.serializeNBT());
  h.assertTrue(com.tnc.tnc.magic.SpellCatalog.topLearned(restored,com.tnc.tnc.magic.Element.LIGHTNING,com.tnc.tnc.magic.SpellCatalog.Chain.THUNDER_SEAL).tier()==5,"Saved chain progress lost");h.succeed();
 }
}
