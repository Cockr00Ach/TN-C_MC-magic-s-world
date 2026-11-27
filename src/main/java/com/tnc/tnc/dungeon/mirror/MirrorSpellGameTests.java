package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.water.TNWaterBoltEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("tncmirror") @PrefixGameTestTemplate(false)
public final class MirrorSpellGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void copiesBoundPagesAndCastsOwnersActualWaterBall(GameTestHelper h) {
        var player = AdventureGameTests.player(h);
        var stone = MagicStone.getOrNull(player);
        var water = ResourceLocation.fromNamespaceAndPath("tnc", "water_ball");
        var fire = ResourceLocation.fromNamespaceAndPath("tnc", "fireball");
        var reserve = ResourceLocation.fromNamespaceAndPath("tnc", "lightning_recharge");
        stone.learn(fire); stone.learn(water); stone.learn(reserve);
        stone.setSlot(0,fire); stone.setSlot(9,water);
        for(int i=0;i<18;i++) if(i!=0 && i!=9) stone.clearSlot(i);
        stone.setLoadoutPage(1);
        stone.setMaxMana(300); stone.setMana(127);
        for(int i=0;i<40;i++) MirrorSpells.record(player,water);
        var center = h.absolutePos(new BlockPos(1,2,1));
        var echo = MirrorEntities.DOUBLE.get().create(h.getLevel());
        echo.moveTo(center,0,0);
        echo.bind(player,UUID.randomUUID(),center);
        var book = echo.getPersistentData().getCompound("MirrorSpellbook");
        var pool = book.getList("Pool",Tag.TAG_STRING);
        h.assertTrue(pool.size()==2 && pool.getString(0).equals(water.toString()) && pool.getString(1).equals(fire.toString()),"Only bound spells from actual pages, active page first");
        h.assertTrue(book.getList("Recent",Tag.TAG_STRING).size()==32,"Actual successful cast history bounded");
        book.putFloat("Mana",100); book.putInt("Recovery",20);
        var cooldowns=book.getCompound("Cooldowns");
        cooldowns.putInt(water.toString(),25);
        for(int tick=0;tick<20;tick++) {
            echo.tickCount=tick;
            MirrorSpells.tick(echo,player);
        }
        h.assertTrue(cooldowns.getInt(water.toString())==5 && Math.abs(book.getFloat("Mana")-103.75f)<.001f,
                "Twenty ticks spend exactly one second of cooldown and give ordinary mana regeneration");
        stone.forget(water);
        book.remove("Source"); // A first-phase snapshot from a world created without a wand.
        cooldowns.remove(water.toString());
        player.moveTo(center.getX()+.5,echo.getY(),center.getZ()+4.5,180,0);
        for (int i=0;i<60 && !book.contains("Last");i++) {
            echo.tickCount=i;
            MirrorSpells.tick(echo,player);
        }
        h.assertTrue(water.toString().equals(book.getString("Last")),
                "The AI finishes its equipped water spell from an old snapshot. Book=" + book + " LOS=" + echo.hasLineOfSight(player));
        var bolts=h.getLevel().getEntitiesOfClass(TNWaterBoltEntity.class,echo.getBoundingBox().inflate(4),
                b->b.getOwner()!=null && b.getOwner().getUUID().equals(echo.getUUID()));
        h.assertTrue(bolts.size()==1,"Real water projectile belongs to this reflection");
        h.assertTrue(stone.getMana()==127 && !stone.hasLearned(water),"Original caster's mana and loadout remain independent");
        bolts.forEach(b->b.discard()); MirrorSpells.clear(echo);
        h.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=80)
    public static void emptyOrUnboundLoadoutCannotReleaseWater(GameTestHelper h) {
        var player=AdventureGameTests.player(h);
        player.getInventory().clearContent();
        var stone=MagicStone.getOrNull(player);
        var water=ResourceLocation.fromNamespaceAndPath("tnc","water_ball");
        var center=h.absolutePos(new BlockPos(1,2,1));
        for (boolean learned : new boolean[]{false,true}) {
            if (learned) {
                stone.learn(water);
                for (int slot=0;slot<com.tnc.tnc.magic.MagicStoneData.LOADOUT_SLOTS;slot++) stone.clearSlot(slot);
            }
            var echo=MirrorEntities.DOUBLE.get().create(h.getLevel());
            echo.moveTo(center,0,0);
            echo.bind(player,UUID.randomUUID(),center);
            var book=echo.getPersistentData().getCompound("MirrorSpellbook");
            h.assertTrue(book.getList("Pool",Tag.TAG_STRING).isEmpty(),"Only bound skills enter the reflection's spell pool");
            player.moveTo(center.getX()+.5,echo.getY(),center.getZ()+4.5,180,0);
            for (int i=0;i<65;i++) {
                echo.tickCount=i;
                MirrorSpells.tick(echo,player);
            }
            h.assertTrue(!book.contains("Casting") && !book.contains("Last")
                            && !MirrorSpellHand.release(echo,water,player,true,book),
                    "With an empty pool neither AI nor a forced release can cast a water ball");
            h.assertTrue(h.getLevel().getEntitiesOfClass(TNWaterBoltEntity.class,echo.getBoundingBox().inflate(6),
                    b->b.getOwner()!=null && b.getOwner().getUUID().equals(echo.getUUID())).isEmpty(),
                    "No projectile was spawned for an unbound skill");
            h.assertTrue(stone.hasLearned(water)==learned && player.getInventory().isEmpty(),
                    "The reflection preserves the real player's learned spells and inventory");
            MirrorSpells.clear(echo);
        }
        h.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void unselectedPageUsesActualBackpackWand(GameTestHelper h) {
        var player=AdventureGameTests.player(h);
        var water=ResourceLocation.fromNamespaceAndPath("tnc","water_ball");
        var stone=MagicStone.getOrNull(player);
        stone.learn(water);
        stone.setSlot(9,water);
        stone.setLoadoutPage(0);
        var wand=new net.minecraft.world.item.ItemStack(com.tnc.tnc.TNMod.WAND.get());
        wand.setHoverName(net.minecraft.network.chat.Component.literal("原杖"));
        player.getInventory().setItem(17,wand);
        var before=wand.save(new net.minecraft.nbt.CompoundTag());
        var center=h.absolutePos(new BlockPos(1,2,1));
        var echo=MirrorEntities.DOUBLE.get().create(h.getLevel());
        echo.moveTo(center,0,0);
        echo.bind(player,UUID.randomUUID(),center);
        var book=echo.getPersistentData().getCompound("MirrorSpellbook");
        var hand=MirrorSpellHand.hand(echo);
        h.assertTrue(hand.prepareMedium(water,book),
                "The reserve page's bound water spell is available");
        h.assertTrue(hand.getMainHandItem().getHoverName().getString().equals("原杖")
                && before.equals(wand.save(new net.minecraft.nbt.CompoundTag()))
                && echo.getMainHandItem().isEmpty() && player.getMainHandItem().isEmpty(),
                "Only the detached caster holds the captured backpack wand; both visible hands and source NBT stay intact");
        MirrorSpells.clear(echo);
        h.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void silenceStopsSpendingAndExternalRecovery(GameTestHelper h) {
        var stone = MagicStone.getOrNull(AdventureGameTests.player(h));
        stone.setMaxMana(200); stone.setMana(80); stone.setMirrorFrozen(true);
        stone.addMana(15); stone.setMana(0);
        h.assertTrue(!stone.spendMana(20) && stone.getMana()==80,"No spend, refill or delayed mutation inside the field");
        stone.setMirrorFrozen(false);
        h.assertTrue(stone.spendMana(20) && stone.getMana()==60,"Normal costs resume at the preserved amount");
        h.succeed();
    }
}
