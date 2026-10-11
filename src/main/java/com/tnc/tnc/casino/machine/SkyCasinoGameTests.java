package com.tnc.tnc.casino.machine;

import com.tnc.tnc.tavern.TavernUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.List;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class SkyCasinoGameTests {
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoMapPreflightConflictPreservesAllBlocks(GameTestHelper h) throws Exception {
        var local=new BlockPos(83,101,250);var p=h.absolutePos(new BlockPos(1,1,1));var origin=p.subtract(local);
        h.getLevel().setBlockAndUpdate(p,Blocks.GOLD_BLOCK.defaultBlockState());
        var c=new TavernUpgrade.Change(local,Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState(),null);
        boolean refused=false;
        try{SkyCasinoUpgrade.validate(h.getLevel(),origin,List.of(c));}catch(java.io.IOException expected){refused=true;}
        h.assertTrue(refused&&h.getLevel().getBlockState(p).is(Blocks.GOLD_BLOCK),"Preflight must preserve player construction");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoMapRecoveryAcceptsMixedBeforeAndAfter(GameTestHelper h) throws Exception {
        var local=new BlockPos(83,101,250);var p=h.absolutePos(new BlockPos(1,1,1));var origin=p.subtract(local);
        var a=Blocks.STONE.defaultBlockState();var b=Blocks.AIR.defaultBlockState();
        h.getLevel().setBlockAndUpdate(p,a);h.getLevel().setBlockAndUpdate(p.east(),b);
        SkyCasinoUpgrade.validate(h.getLevel(),origin,List.of(new TavernUpgrade.Change(local,b,a,null),new TavernUpgrade.Change(local.east(),b,a,null)));
        var closed=Blocks.SPRUCE_DOOR.defaultBlockState();
        var opened=closed.setValue(net.minecraft.world.level.block.DoorBlock.OPEN,true);
        h.assertTrue(SkyCasinoUpgrade.compatible(opened,new TavernUpgrade.Change(local,closed,b,null)),"Opening an imported door must not block relocation");
        var rotated=opened.setValue(net.minecraft.world.level.block.DoorBlock.FACING,net.minecraft.core.Direction.EAST);
        h.assertTrue(!SkyCasinoUpgrade.compatible(rotated,new TavernUpgrade.Change(local,closed,b,null)),"Changing a door orientation must still preserve player construction");
        h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoMapRecoveryPreservesMachinePayout(GameTestHelper h) throws Exception {
        var p=h.absolutePos(new BlockPos(1,1,1));var state=TNCasinoRegistry.block("slot_machine").defaultBlockState();
        h.getLevel().setBlockAndUpdate(p,state);var machine=(TNSlotMachineEntity)h.getLevel().getBlockEntity(p);machine.owed=23;
        var c=new TavernUpgrade.Change(new BlockPos(83,101,250),Blocks.AIR.defaultBlockState(),state,null);
        TavernUpgrade.place(h.getLevel(),p.subtract(c.local()),c);
        h.assertTrue(machine.owed()==23&&h.getLevel().getBlockEntity(p)==machine,"Resume must not replace an existing machine or erase its payout");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoMapResourceIsBoundedAndContainsSevenMachines(GameTestHelper h) throws Exception {
        var res=h.getLevel().getServer().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("tnc","casino/island_v3.nbt")).orElseThrow();
        net.minecraft.nbt.CompoundTag root;try(var in=res.open()){root=NbtIo.readCompressed(in);}
        var palette=root.getList("Palette",10);int machines=0;
        var seen=new java.util.HashSet<BlockPos>();
        for(var raw:root.getList("Changes",10)) {
            var t=(net.minecraft.nbt.CompoundTag)raw;var xyz=t.getIntArray("Pos");var p=new BlockPos(xyz[0],xyz[1],xyz[2]);
            h.assertTrue(SkyCasinoUpgrade.inside(p)&&seen.add(p),"Import coordinates must be unique and bounded");
            String id=palette.getCompound(t.getInt("After")).getString("Name");if(id.startsWith("tnc:"))machines++;
        }
        for(var raw:root.getList("BlockEntities",10)) {
            var data=((net.minecraft.nbt.CompoundTag)raw).getCompound("Data");
            h.assertTrue(!data.contains("Items")&&!data.contains("LootTable"),"Source storage contents must not be imported");
        }
        h.assertTrue(machines==7,"Three slots and four different table/wheel devices");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoMigrationSnapshotSurvivesRecovery(GameTestHelper h) {
        var s=new SkyCasinoUpgrade.State();s.phase=1;s.migration=true;s.captured=true;s.hash="revision2";
        var data=new net.minecraft.nbt.CompoundTag();data.putInt("Owed",23);s.carried.put("123",data);
        var copy=SkyCasinoUpgrade.State.load(s.save(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(copy.phase==0&&copy.migration&&copy.captured&&copy.carried.getCompound("123").getInt("Owed")==23,"Restart preserves captured winnings and revalidates the scene");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoAllMachineBodiesAreClickableBeyondAnchorCell(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(1,1,1));
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"FullBody"));
        for(String id:List.of("roulette_table","card_table","gamble_table","lucky_wheel","slot_machine")) {
            var block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("tnc",id));
            for(var facing:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST)) {
                var state=block.defaultBlockState().setValue(TNCasinoMachineBlock.FACING,facing);h.getLevel().setBlockAndUpdate(pos,state);
                var m=(TNMachineEntity)h.getLevel().getBlockEntity(pos);var b=state.getShape(h.getLevel(),pos).bounds();
                player.moveTo(pos.getX()+b.maxX+.6,pos.getY(),pos.getZ()+(b.minZ+b.maxZ)/2,90,0);
                player.setXRot((float)Math.toDegrees(Math.atan2(player.getEyePosition().y-pos.getY()-b.maxY/2,.6)));
                h.assertTrue(MachineInteraction.hit(player,m)!=null,"Full body reachable: "+id+" "+facing);
            }
        }
        h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void tavernMissingDependencyRecoversWithoutResettingInstalledInterior(GameTestHelper h) {
        var s=new TavernUpgrade.State();s.phase=-1;s.error="酒馆缺少方块：minecraft:barrel";
        h.assertTrue(TavernUpgrade.recoverMissingDependency(h.getLevel(),s)&&s.phase==0,"Restored dependency retries a never-started interior");
        s.phase=-1;s.hash="installed";s.error="酒馆缺少方块：minecraft:barrel";
        h.assertTrue(!TavernUpgrade.recoverMissingDependency(h.getLevel(),s)&&s.phase==-1,"Existing completed or partial interiors cannot be reset this way");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoNavigationBudgetBoundsSimultaneousGuests(GameTestHelper h) {
        int accepted=0;for(int i=0;i<24;i++)if(CasinoGuestEntity.reservePath(h.getLevel()))accepted++;
        h.assertTrue(accepted<=2,"Twenty-four guests must not start more than two paths per tick");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoMoveRestoresMatchingDestinationExactlyOnce(GameTestHelper h)throws Exception {
        var p=h.absolutePos(new BlockPos(1,1,1));var block=TNCasinoRegistry.block("slot_machine").defaultBlockState();
        h.getLevel().setBlockAndUpdate(p,block);var machine=(TNSlotMachineEntity)h.getLevel().getBlockEntity(p);machine.owed=23;
        var s=new SkyCasinoUpgrade.State();var local=new BlockPos(83,90,250);s.carried.put(Long.toString(local.asLong()),machine.saveWithFullMetadata());
        machine.owed=99;var c=new TavernUpgrade.Change(local,block,block,null);
        SkyCasinoUpgrade.restoreCarried(h.getLevel(),p.subtract(local),c,s);
        h.assertTrue(machine.owed()==23,"A matching block must receive the inventory of its relocated source");
        machine.owed=7;SkyCasinoUpgrade.restoreCarried(h.getLevel(),p.subtract(local),c,s);
        h.assertTrue(machine.owed()==7,"Recovery must not replay a previously restored snapshot");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=160)
    public static void casinoGuestsActuallyWalkAndReceptionRemainsFixed(GameTestHelper h) {
        for(int x=1;x<=10;x++)for(int z=1;z<=10;z++){h.setBlock(new BlockPos(x,0,z),Blocks.STONE);h.setBlock(new BlockPos(x,1,z),Blocks.AIR);h.setBlock(new BlockPos(x,2,z),Blocks.AIR);}
        var home=h.absolutePos(new BlockPos(2,1,2));var frontHome=h.absolutePos(new BlockPos(5,1,2));
        var guest=CasinoResidents.TYPE.get().create(h.getLevel());var front=CasinoResidents.TYPE.get().create(h.getLevel());
        guest.configure(false,1,home,List.of(home,home.south(6)));front.configure(true,0,frontHome,List.of(frontHome));
        h.getLevel().addFreshEntity(guest);h.getLevel().addFreshEntity(front);var start=guest.position();var fixed=front.position();
        var walked=new java.util.concurrent.atomic.AtomicBoolean();
        h.onEachTick(()->{if(guest.position().distanceToSqr(start)>.25)walked.set(true);});
        h.runAfterDelay(120,()->{h.assertTrue(walked.get(),"Guest must walk using navigation");h.assertTrue(front.position().distanceToSqr(fixed)<.01,"Reception stays at the desk");h.succeed();});
    }
}
