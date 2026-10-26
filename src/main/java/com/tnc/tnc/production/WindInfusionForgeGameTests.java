package com.tnc.tnc.production;

import com.tnc.tnc.warrior.WindInfusion;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class WindInfusionForgeGameTests {
    private static ManaForgeRecipe recipe(GameTestHelper h) {
        return (ManaForgeRecipe)h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("tnc:wind_weapon_infusion")).orElseThrow();
    }
    private static ItemStack weapon() {
        var w = new ItemStack(Items.DIAMOND_SWORD); w.setDamageValue(37);
        w.setHoverName(Component.literal("Homeward")); w.enchant(Enchantments.SHARPNESS, 3);
        w.getOrCreateTag().putString("OtherModData", "retain"); return w;
    }
    private static void inputs(net.minecraft.world.Container c, ItemStack w) {
        c.setItem(0, new ItemStack(Items.FEATHER, 8)); c.setItem(1, w);
        c.setItem(2, new ItemStack(Items.PHANTOM_MEMBRANE)); c.setItem(3, new ItemStack(Items.AMETHYST_SHARD, 4));
    }
    @GameTest(template="building_test_empty", timeoutTicks=40)
    public static void forgeInfusionIsAtomicAndPreservesWeapon(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(3,3,3));
        var forge = MagicForgeGameTests.build(h, pos, Direction.NORTH);
        h.assertTrue(forge.chooseRecipe(forge.recipes().indexOf(recipe(h))), "Infusion appears in real forge recipes");
        var original = weapon(); inputs(forge, original.copy());
        forge.addCharge(119);
        for(int i=0;i<161;i++) MagicForgeBlockEntity.serverTick(h.getLevel(),pos,h.getLevel().getBlockState(pos),forge);
        h.assertTrue(forge.charge()==119 && forge.getItem(0).getCount()==8 && forge.getItem(4).isEmpty(), "Insufficient mana spends nothing");
        forge.addCharge(1); forge.setItem(4,new ItemStack(Items.COBBLESTONE,64));
        for(int i=0;i<161;i++) MagicForgeBlockEntity.serverTick(h.getLevel(),pos,h.getLevel().getBlockState(pos),forge);
        h.assertTrue(forge.charge()==120 && forge.getItem(1).is(Items.DIAMOND_SWORD), "Blocked output preserves weapon and mana");
        forge.setItem(4,ItemStack.EMPTY);
        for(int i=0;i<160;i++) MagicForgeBlockEntity.serverTick(h.getLevel(),pos,h.getLevel().getBlockState(pos),forge);
        var result=forge.getItem(4);
        h.assertTrue(WindInfusion.isWind(result), "Real forge produces an infused weapon");
        var stripped=result.copy(); stripped.getTag().remove(WindInfusion.ELEMENT_KEY);
        h.assertTrue(ItemStack.isSameItemSameTags(stripped,original), "Damage, name, enchantments and foreign NBT remain exact");
        h.assertTrue(forge.charge()==0 && forge.getItem(0).isEmpty() && forge.getItem(1).isEmpty()
                && forge.getItem(2).isEmpty() && forge.getItem(3).isEmpty(), "Consumes one complete recipe exactly once");
        h.succeed();
    }
    @GameTest(template="building_test_empty", timeoutTicks=40)
    public static void infusionRecipeSurvivesNetworkAndRejectsDuplicate(GameTestHelper h) {
        var recipe=recipe(h); var c=new SimpleContainer(4); var w=weapon(); inputs(c,w);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            var serializer=new ManaForgeRecipe.Serializer(); serializer.toNetwork(buffer,recipe);
            var decoded=serializer.fromNetwork(recipe.getId(),buffer);
            h.assertTrue(decoded.costs(c)!=null && WindInfusion.isWind(decoded.assemble(c,h.getLevel().registryAccess())), "Network recipe retains dynamic infusion");
            h.assertTrue(!WindInfusion.hasElement(w), "Preview/assembly never mutates source");
            c.setItem(1,WindInfusion.infuse(w)); h.assertTrue(decoded.costs(c)==null, "Existing element cannot be overwritten");
            c.setItem(1,w); c.setItem(3,new ItemStack(Items.AMETHYST_SHARD,3));
            h.assertTrue(decoded.costs(c)==null && decoded.assemble(c,h.getLevel().registryAccess()).isEmpty(), "Missing material cannot craft");
        } finally {buffer.release();}
        h.succeed();
    }
}
