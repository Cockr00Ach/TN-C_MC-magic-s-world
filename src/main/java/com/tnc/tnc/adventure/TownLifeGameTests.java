package com.tnc.tnc.adventure;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import com.tnc.tnc.home.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class TownLifeGameTests {
    @GameTest(template="building_test_empty",batch="market",timeoutTicks=30)
    public static void marketPreservesSpecialProduceAndReturnsContainers(GameTestHelper h){
        var a=AdventureGameTests.player(h);var p=AdventureService.profile(a);int wheatPrice=TownMarketDay.price(ShopCatalog.find(ShopCatalog.PRODUCE,"wheat"),TownMarketDay.featured(a.server)),milkPrice=TownMarketDay.price(ShopCatalog.find(ShopCatalog.PRODUCE,"milk"),TownMarketDay.featured(a.server));var named=new ItemStack(Items.WHEAT,16);named.setHoverName(net.minecraft.network.chat.Component.literal("纪念麦穗"));a.getInventory().setItem(0,named);a.getInventory().setItem(1,new ItemStack(Items.WHEAT,15));ShopService.sell(a,"wheat");h.assertTrue(p.coins()==0&&a.getInventory().getItem(1).getCount()==15,"Incomplete ordinary produce cannot spend either stack");
        a.getInventory().getItem(1).grow(1);ShopService.sell(a,"wheat");h.assertTrue(p.coins()==wheatPrice&&a.getInventory().getItem(0).getCount()==16&&a.getInventory().getItem(1).isEmpty(),"Only ordinary produce sold");a.getInventory().setItem(1,new ItemStack(Items.MILK_BUCKET));ShopService.sell(a,"milk");h.assertTrue(p.coins()==wheatPrice+milkPrice&&a.getInventory().getItem(1).is(Items.BUCKET),"Milk pays and returns bucket");
        for(int i=0;i<36;i++)a.getInventory().setItem(i,new ItemStack(Items.STONE,64));a.getInventory().setItem(1,new ItemStack(Items.HONEY_BOTTLE,16));String fail=ShopService.sell(a,"honey");h.assertTrue(fail.contains("腾出")&&a.getInventory().getItem(1).getCount()==16&&p.coins()==wheatPrice+milkPrice,"No room for bottles preserves produce and money");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="market",timeoutTicks=30)
    public static void marketQuotaPersistsPerPlayerAndFullBagDoesNotCharge(GameTestHelper h){
        var a=AdventureGameTests.player(h);var b=AdventureGameTests.player(h);var p=AdventureService.profile(a);int price=TownMarketDay.price(ShopCatalog.find(ShopCatalog.PRODUCE,"wheat"),TownMarketDay.featured(a.server)),batches=500/price;for(int i=0;i<batches;i++){a.getInventory().setItem(0,new ItemStack(Items.WHEAT,16));ShopService.sell(a,"wheat");}a.getInventory().setItem(0,new ItemStack(Items.WHEAT,16));ShopService.sell(a,"wheat");h.assertTrue(p.coins()==batches*price&&a.getInventory().getItem(0).getCount()==16,"500 cap blocks overflow transaction");
        b.getInventory().setItem(0,new ItemStack(Items.WHEAT,16));ShopService.sell(b,"wheat");h.assertTrue(AdventureService.profile(b).coins()==price,"Each player has independent quota");var saved=AdventureSavedData.load(AdventureSavedData.get(a.server).save(new CompoundTag()));h.assertTrue(saved.housing.getCompound("Market").getCompound(a.getUUID().toString()).getLong("Sold")==batches*price,"Quota survives reload");
        p.credit(100,"fixture");for(int i=0;i<36;i++)a.getInventory().setItem(i,new ItemStack(Items.STONE,64));long before=p.coins();ShopService.buy(a,"bed");h.assertTrue(before==p.coins()&&a.getInventory().getItem(0).is(Items.STONE),"Full bag never charged");a.getInventory().setItem(0,ItemStack.EMPTY);ShopService.buy(a,"bed");h.assertTrue(p.coins()==before-60&&a.getInventory().getItem(0).is(Items.BLUE_BED),"Bed is a real delivered item");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="native_life",timeoutTicks=30)
    public static void nativeFurnitureHasSeatsStorageLightAndSurvivalPacketGuard(GameTestHelper h)throws Exception{
        if(!net.minecraftforge.fml.ModList.get().isLoaded("immersive_furniture")){h.succeed();return;}
        Object chair=null;for(var goods:ShopCatalog.FURNITURE){var stack=FurnitureCompat.stack(goods);h.assertTrue(!stack.isEmpty(),"Available furniture: "+goods.id());if(!goods.item().startsWith("model:"))continue;var model=stack.getItem().getClass().getMethod("getData",ItemStack.class).invoke(null,stack);var cls=model.getClass();
            if(goods.id().contains("chair")||goods.id().equals("sofa"))h.assertTrue((Boolean)cls.getMethod("canSit").invoke(model),"Real chair/sofa seat: "+goods.id());if(goods.id().equals("cabinet"))h.assertTrue(cls.getField("inventorySize").getInt(model)==9,"Real nine-slot cabinet");if(goods.id().equals("lamp"))h.assertTrue(cls.getField("lightLevel").getInt(model)==12,"Real light source");if(goods.id().equals("oak_chair"))chair=model;
        }
        var p=AdventureGameTests.player(h);var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse("immersive_furniture:crafting_material"));p.getInventory().setItem(0,new ItemStack(item,64));var request=Class.forName("net.conczin.immersive_furniture.network.c2s.CraftRequest");var packet=request.getConstructor(chair.getClass(),boolean.class).newInstance(chair,false);request.getMethod("handle",net.minecraft.world.entity.player.Player.class).invoke(packet,p);h.assertTrue(p.getInventory().getItem(0).getCount()==64&&p.getInventory().items.stream().noneMatch(s->s.getItem().getClass().getSimpleName().equals("FurnitureItem")),"Survival direct craft request cannot bypass store or consume materials");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="native_life",timeoutTicks=30)
    public static void nativeMcaResidentAndHomeUseInstalledApi(GameTestHelper h)throws Exception{
        if(!McaResidents.available()){h.succeed();return;}var stand=h.absolutePos(new BlockPos(4,3,4));var bed=stand.offset(2,0,0);var npc=McaResidents.create(h.getLevel(),ResidentService.ALL.get(0),stand,java.util.UUID.randomUUID());h.assertTrue(McaResidents.nativeResident(npc)&&!npc.isBaby()&&!npc.isNoAi(),"MCA adult with native AI");h.assertTrue(!((String)npc.getClass().getMethod("getHair").invoke(npc)).isBlank()&&!((String)npc.getClass().getMethod("getClothes").invoke(npc)).isBlank(),"Authored MCA appearance initialized from native resources");McaResidents.setHome(npc,bed);var residency=npc.getClass().getMethod("getResidency").invoke(npc);var home=(java.util.Optional<?>)residency.getClass().getMethod("getHome").invoke(residency);h.assertTrue(home.isPresent()&&((net.minecraft.core.GlobalPos)home.get()).pos().equals(bed),"Home written into MCA brain");var relationships=npc.getClass().getMethod("getRelationships").invoke(npc);h.assertTrue(!(Boolean)relationships.getClass().getMethod("isMarried").invoke(relationships),"Spawning does not choose marriage for the player");h.succeed();
    }
}
