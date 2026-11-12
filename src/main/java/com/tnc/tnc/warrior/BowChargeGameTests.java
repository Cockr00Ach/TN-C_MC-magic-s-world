package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class BowChargeGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=120)
    public static void rightChargeConsumesOneArrowPerRelease(GameTestHelper h){
        var p=AdventureGameTests.player(h);var data=TemperingData.get(p.server);
        data.setTalent(p.getUUID(),6);data.enroll(p.getUUID());
        p.setPos(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(5,4,5))));
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TNMod.BOW.get()));
        p.getInventory().add(new ItemStack(Items.ARROW,8));
        WarriorMove[] moves={WarriorMove.HORIZONTAL,WarriorMove.HORIZONTAL,WarriorMove.HORIZONTAL};
        for(int i=0;i<3;i++){
            final int n=i;int start=1+i*30;
            h.runAfterDelay(start,()->WarriorComboService.input(p,moves[n],WarriorComboInputPacket.HOLD));
            h.runAfterDelay(start+12,()->WarriorComboService.input(p,moves[n],WarriorComboInputPacket.KEEPALIVE));
            h.runAfterDelay(start+18,()->{
                int ammo=WarriorComboService.ammo(p).getCount();
                int before=p.serverLevel().getEntitiesOfClass(Arrow.class,p.getBoundingBox().inflate(100),a->a.getOwner()==p).size();
                WarriorComboService.input(p,moves[n],WarriorComboInputPacket.RELEASE);
                var arrows=p.serverLevel().getEntitiesOfClass(Arrow.class,p.getBoundingBox().inflate(100),a->a.getOwner()==p);
                h.assertTrue(arrows.size()-before==BowChargeRules.arrows(moves[n]),"charged arrow mode did not fire its projectile count");
                h.assertTrue(WarriorComboService.ammo(p).getCount()==ammo-1,"each release consumes exactly one inventory arrow");
                h.assertTrue(arrows.stream().anyMatch(a->a.getPersistentData().getBoolean("TncImpactArrow")),"Right charge carries the impact behavior");
            });
        }
        h.runAfterDelay(90,h::succeed);
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void swappingBowCancelsChargeWithoutConsumingAmmo(GameTestHelper h){
        var p=AdventureGameTests.player(h);var d=TemperingData.get(p.server);d.setTalent(p.getUUID(),6);d.enroll(p.getUUID());
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TNMod.BOW.get()));p.getInventory().add(new ItemStack(Items.ARROW,4));
        WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.HOLD);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        WarriorBowCharge.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.RELEASE);
        h.assertTrue(WarriorComboService.ammo(p).getCount()==4,"switching weapons must not spend ammo");h.succeed();
    }
}
