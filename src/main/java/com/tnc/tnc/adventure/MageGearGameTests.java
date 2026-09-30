package com.tnc.tnc.adventure;

import com.tnc.tnc.equipment.*;
import com.tnc.tnc.magic.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class MageGearGameTests {
    @GameTest(templateNamespace="tnc",template="building_test_empty",timeoutTicks=40)
    public static void registeredCatalogHasActualArmorAndTwoNativeSlots(GameTestHelper h){
        var p=AdventureGameTests.player(h);h.assertTrue(MageGear.ALL.size()==34,"32 regular and two divine items");
        for(var d:MageGear.ALL){var s=MageGear.stack(d);
            h.assertTrue(s.getItem() instanceof ArmorItem a&&a.getEquipmentSlot()==d.slot(),"Registered wearable slot "+d.id());
            double armor=s.getAttributeModifiers(d.slot()).get(Attributes.ARMOR).stream().mapToDouble(m->m.getAmount()).sum();
            h.assertTrue(armor==d.armor(),"Native armor attribute "+d.id());
        }
        var m=p.inventoryMenu;
        h.assertTrue(m.slots.size()==46&&m.slots.get(5).y==17&&m.slots.get(6).y==53,"Native layout preserves protocol slot count");
        h.assertTrue(!m.slots.get(7).mayPlace(new ItemStack(Items.IRON_LEGGINGS))&&!m.slots.get(8).mayPlace(new ItemStack(Items.IRON_BOOTS))&&m.slots.get(7).x<0,"Hidden lower slots cannot receive shift clicks");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_BOOTS));
        Items.IRON_BOOTS.use(p.level(),p,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).isEmpty()&&p.getMainHandItem().is(Items.IRON_BOOTS)&&!p.canTakeItem(new ItemStack(Items.IRON_LEGGINGS)),"Right click keeps boots in hand and dispenser cannot equip lower armor");h.succeed();
    }
    @GameTest(templateNamespace="tnc",template="building_test_empty",timeoutTicks=40)
    public static void equipChangesManaWithoutFreeRefillAndCostMatchesCharge(GameTestHelper h){
        var p=AdventureGameTests.player(h);var d=MagicStone.getOrNull(p);d.assignDefaultAffinities(3);MagicStone.refreshMaxMana(p,d);int base=d.getMaxMana();d.setMana(7);
        p.setItemSlot(EquipmentSlot.CHEST,MageGear.stack(MageGear.find("divine_outfit_5")));p.setItemSlot(EquipmentSlot.HEAD,MageGear.stack(MageGear.find("divine_hat_5")));GearEvents.refresh(p);
        h.assertTrue(d.getMaxMana()==base+300&&d.getMana()==7&&MageGear.regen(p)==7,"Actual equipped bonuses do not refill mana");
        int cost=MageGear.spellCost(p,100);d.setMana(cost);var result=ManaCharge.applyCost(d,cost);
        h.assertTrue(cost==92&&result.spent()==92&&!result.exhausted()&&d.getMana()==0,"Discount and charging agree");
        d.setMana(base+250);GearEvents.refresh(p);h.assertTrue(d.getMana()==base+250,"Refreshing same gear preserves bonus-held mana");
        p.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);GearEvents.refresh(p);
        h.assertTrue(d.getMaxMana()==base&&d.getMana()==base&&MageGear.regen(p)==0,"Removing gear clamps to base");h.succeed();
    }
    @GameTest(templateNamespace="tnc",template="building_test_empty",timeoutTicks=40)
    public static void newOrderConsumesExactMaterialsAndSurvivesFullClaim(GameTestHelper h){
        var p=AdventureGameTests.player(h);AdventureService.register(p);var a=AdventureService.profile(p);
        p.getInventory().setItem(0,new ItemStack(Items.LEATHER,8));p.getInventory().setItem(1,new ItemStack(Items.STRING,12));p.getInventory().setItem(2,new ItemStack(Items.LAPIS_LAZULI,4));
        AdventureService.order(p,"astral_outfit_1");h.assertTrue(a.smithReady>=0&&a.smithFree&&p.getInventory().isEmpty(),"First regular armor uses its recipe with waived fee");
        var store=AdventureSavedData.get(p.server);store.activeTicks=a.smithReady;for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        AdventureService.claim(p);h.assertTrue(a.smithReady>=0,"Full claim retains paid order");p.getInventory().setItem(0,ItemStack.EMPTY);AdventureService.claim(p);AdventureService.claim(p);
        h.assertTrue(a.armorCrafted&&a.smithReady<0&&p.getInventory().getItem(0).is(MageGear.ITEMS.get("astral_outfit_1").get()),"Exactly one new garment delivered");
        AdventureService.order(p,"divine_outfit_5");h.assertTrue(a.smithReady<0,"Divine repair cannot bypass level and relic requirements");h.succeed();
    }
    @GameTest(templateNamespace="tnc",template="building_test_empty",timeoutTicks=40)
    public static void divineDeathProtectionHasSavedCooldownAndIgnoresVoid(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setItemSlot(EquipmentSlot.CHEST,MageGear.stack(MageGear.find("divine_outfit_5")));p.setItemSlot(EquipmentSlot.HEAD,MageGear.stack(MageGear.find("divine_hat_5")));
        p.setHealth(10);var hit=new LivingDamageEvent(p,p.damageSources().generic(),15);GearEvents.damage(hit);var a=AdventureService.profile(p);
        h.assertTrue(hit.isCanceled()&&p.getHealth()==1&&a.divineReadyAt>AdventureSavedData.get(p.server).activeTicks,"Pair rescues lethal damage");
        var second=new LivingDamageEvent(p,p.damageSources().generic(),15);GearEvents.damage(second);h.assertTrue(!second.isCanceled(),"Cooldown blocks a second rescue");
        h.assertTrue(AdventureSavedData.readProfile(AdventureSavedData.writeProfile(a)).divineReadyAt==a.divineReadyAt,"Cooldown survives save");
        a.divineReadyAt=0;var voidHit=new LivingDamageEvent(p,p.damageSources().fellOutOfWorld(),15);GearEvents.damage(voidHit);h.assertTrue(!voidHit.isCanceled(),"Void damage cannot be bypassed");h.succeed();
    }
}
