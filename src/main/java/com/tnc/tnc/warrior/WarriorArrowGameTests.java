package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class WarriorArrowGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void ornateArrowStyleSurvivesSaveAndAmmoOverridesBow(GameTestHelper h){
        var player=AdventureGameTests.player(h);
        for(var item:java.util.List.of(TNMod.MOON_BOW.get(),TNMod.STORM_BOW.get(),TNMod.EMBER_BOW.get())) {
            var arrow=WarriorArrowEntity.create(player.level(),player,new ItemStack(Items.ARROW),new ItemStack(item));
            int style=WarriorArrowEntity.bowStyle(new ItemStack(item));
            h.assertTrue(arrow.style()==style,"Bow chooses its own visible arrow");
            arrow.setBaseDamage(42);arrow.setPierceLevel((byte)3);
            CompoundTag saved=new CompoundTag();arrow.saveWithoutId(saved);
            var restored=new WarriorArrowEntity(WarriorEntities.ORNATE_ARROW.get(),player.level());restored.load(saved);
            h.assertTrue(restored.style()==style && restored.getBaseDamage()==42 && restored.getPierceLevel()==3,"Style and vanilla combat survive save");
            h.assertTrue(arrow.getAddEntityPacket()!=null,"Custom arrow has Forge spawn packet");
        }
        var ammo=new ItemStack(TNMod.MOON_ARROW.get());
        var chosen=WarriorArrowEntity.create(player.level(),player,ammo,new ItemStack(TNMod.EMBER_BOW.get()));
        h.assertTrue(chosen.style()==1,"Special ammo keeps its own identity");
        player.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void ordinaryBowConversionPreservesCombatAndTippedArrowEffects(GameTestHelper h){
        var player=AdventureGameTests.player(h);
        var bow=(WarriorOrnateBowItem)TNMod.EMBER_BOW.get();
        var plain=new net.minecraft.world.entity.projectile.Arrow(player.level(),player);
        plain.setBaseDamage(17);plain.setPierceLevel((byte)2);plain.setCritArrow(true);
        var converted=bow.customArrow(plain);
        h.assertTrue(converted instanceof WarriorArrowEntity a && a.style()==2,"Right-click shot uses matching block arrow");
        h.assertTrue(converted.getBaseDamage()==17 && converted.getPierceLevel()==2 && converted.isCritArrow(),"Vanilla damage and enchantments remain");
        var tipped=new net.minecraft.world.entity.projectile.Arrow(player.level(),player);
        tipped.setEffectsFromItem(net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW),net.minecraft.world.item.alchemy.Potions.POISON));
        h.assertTrue(bow.customArrow(tipped)==tipped,"Tipped arrows retain potion and pickup semantics");
        player.discard();h.succeed();
    }
}
