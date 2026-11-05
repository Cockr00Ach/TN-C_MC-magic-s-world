package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncwarrior")
@PrefixGameTestTemplate(false)
public final class TraditionalWeaponGameTests {
    private TraditionalWeaponGameTests() {}

    @GameTest(template="building_test_empty", timeoutTicks=20)
    public static void traditionalWeaponsLoadWithCombatFamiliesAndSurvivalRecipes(GameTestHelper h) {
        var items = new net.minecraft.world.item.Item[]{TNMod.YUE_BRONZE_JIAN.get(), TNMod.WILLOW_LEAF_DAO.get(),
                TNMod.CRESCENT_GUANDAO.get(), TNMod.FANGTIAN_JI.get(), TNMod.RED_TASSEL_SPEAR.get(),
                TNMod.RING_POMMEL_DAO.get(), TNMod.HAN_IRON_JIAN.get(), TNMod.IRON_JIAN_MACE.get(), TNMod.MONK_SPADE.get(), TNMod.QIMEI_STAFF.get()};
        var families = new WarriorWeaponFamily[]{WarriorWeaponFamily.LONGSWORD, WarriorWeaponFamily.LONGSWORD,
                WarriorWeaponFamily.GREATSWORD, WarriorWeaponFamily.SPEAR, WarriorWeaponFamily.SPEAR,
                WarriorWeaponFamily.LONGSWORD, WarriorWeaponFamily.LONGSWORD, WarriorWeaponFamily.HAMMER, WarriorWeaponFamily.GREATSWORD, WarriorWeaponFamily.SPEAR};
        for (int i=0;i<items.length;i++) {
            var stack=new ItemStack(items[i]);
            var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(items[i]);
            h.assertTrue(WarriorWeaponFamily.of(stack)==families[i], "Combat family: "+id);
            h.assertTrue(stack.isDamageableItem() && stack.getMaxStackSize()==1, "Weapon durability: "+id);
            var recipe=h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("tnc",id.getPath()));
            h.assertTrue(recipe.isPresent(), "Survival recipe loaded: "+id);
            h.assertTrue(recipe.orElseThrow().getResultItem(h.getLevel().registryAccess()).is(items[i]), "Recipe output: "+id);
        }
        h.succeed();
    }

    @GameTest(template="building_test_empty", timeoutTicks=40)
    public static void spearThrustMissesFlankWhileStaffSweepHits(GameTestHelper h) {
        var p=com.tnc.tnc.adventure.AdventureGameTests.player(h);
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(5,3,5));
        p.setPos(pos.getX(),pos.getY(),pos.getZ());p.setYRot(0);p.setXRot(0);
        var target=h.spawn(WarriorEntities.TEST_DUMMY.get(),new net.minecraft.core.BlockPos(7,3,5));
        target.setPos(p.getX()+2,p.getY(),p.getZ());
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(TNMod.RED_TASSEL_SPEAR.get()));
        var thrust=WarriorPerformance.forWeapon(WarriorCombos.bySlot(WarriorWeaponFamily.SPEAR,0),p.getMainHandItem());
        WarriorComboService.melee(p,thrust,WarriorComboService.state(p.getUUID()),WarriorComboService.gameTime(p));
        h.assertTrue(target.hitCount()==0,"Narrow spear thrust must miss an enemy 90 degrees to the side");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(TNMod.QIMEI_STAFF.get()));
        var sweep=WarriorPerformance.forWeapon(WarriorCombos.bySlot(WarriorWeaponFamily.SPEAR,4),p.getMainHandItem());
        WarriorComboService.melee(p,sweep,WarriorComboService.state(p.getUUID()),WarriorComboService.gameTime(p));
        h.assertTrue(target.hitCount()==1,"Wide staff sweep must hit the flank target");
        target.discard();p.discard();h.succeed();
    }
}
