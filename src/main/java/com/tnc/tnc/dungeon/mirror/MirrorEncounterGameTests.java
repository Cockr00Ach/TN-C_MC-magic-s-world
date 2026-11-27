package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.dungeon.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("tncmirror") @PrefixGameTestTemplate(false)
public final class MirrorEncounterGameTests {
    @GameTest(template="building_test_empty",batch="echo_preview",timeoutTicks=1200)
    public static void operatorPreviewBuildsWalkableFloorAndResumesOriginalEncounter(GameTestHelper h) throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var player=AdventureGameTests.player(h);
        var stone=com.tnc.tnc.magic.MagicStone.getOrNull(player);
        if(stone!=null)for(int i=0;i<18;i++)stone.clearSlot(i);
        var server=player.server;
        var dispatcher=server.getCommands().getDispatcher();
        var command=dispatcher.getRoot().getChild("dungeon").getChild("mirror");
        var source=player.createCommandSourceStack();
        h.assertTrue(command!=null && !command.canUse(source.withPermission(0)) && command.canUse(source.withPermission(2)),"Dedicated server preview requires operator permissions");
        h.assertTrue(dispatcher.execute("dungeon mirror",source.withPermission(2))==1,"Registered preview command opened");
        var run=Objects.requireNonNull(DungeonService.run(player));
        var room=run.graph().rooms.stream().filter(r->r.kind()==DungeonGraph.Kind.GUARDIAN).findFirst().orElseThrow();
        var at=MirrorPreview.entry(run,room);
        var level=Objects.requireNonNull(server.getLevel(DungeonRealm.KEY));
        h.startSequence().thenWaitUntil(()->{
            h.assertTrue(run.state().built && player.level()==level && player.blockPosition().equals(at),"Waiting for floor preparation and the command's teleport");
            h.assertTrue(level.areEntitiesLoaded(new ChunkPos(DungeonRealm.center(run,room)).toLong()),"Waiting for guardian entity storage");
            player.baseTick();
            h.assertTrue(!player.isInWater() && level.getFluidState(at).isEmpty()
                    && level.getBlockState(at.below()).isFaceSturdy(level,at.below(),net.minecraft.core.Direction.UP)
                    && level.noCollision(player,player.getBoundingBox()),"Command placement has dry footing and headroom");
            h.assertTrue(MirrorFight.begin(level,run,room,List.of(player)),"The same unmoved player awakens the actual encounter");
        }).thenExecute(()->{
            var s=run.state().events.getCompound("EchoingMirror");
            var row=s.getCompound("People").getCompound(player.getStringUUID());
            h.assertTrue(s.getInt("Phase")==MirrorFight.MAGIC && level.getEntity(row.getUUID("Entity")) instanceof EchoDouble,"The player's living magic reflection rises");
            var copy=(EchoDouble)level.getEntity(row.getUUID("Entity"));
            var towardCore=net.minecraft.world.phys.Vec3.atBottomCenterOf(DungeonRealm.center(run,room)).subtract(player.position()).multiply(1,0,1);
            var sight=copy.position().subtract(player.position()).multiply(1,0,1).normalize();
            h.assertTrue(towardCore.cross(sight).length()>3 && towardCore.normalize().dot(sight)>Math.cos(Math.toRadians(38)),
                    "At entry the sightline to the solo reflection clears the shell and remains in the forward view");
            h.assertTrue(copy.getLookAngle().dot(sight.scale(-1))>.99,"The solo reflection faces its owner");
            h.assertTrue(row.getCompound("Original").getCompound("ForgeData").getCompound("MirrorSpellbook")
                    .getList("Pool",Tag.TAG_STRING).isEmpty(),"An empty player loadout produces an empty preview reflection");
            var actor=row.getUUID("Entity");
            var before=player.position();
            var profile=DungeonData.get(server).profile(player.getUUID());
            profile.putBoolean("Recovering",true);
            profile.putLong("RecoverPos",at.east(5).asLong());
            h.assertTrue(server.getCommands().performPrefixedCommand(source.withPermission(2),"dungeon mirror")==0 && DungeonService.recovering(player),"Cannot acknowledge a recovery before arriving at its destination");
            profile.putLong("RecoverPos",at.asLong());
            server.getCommands().performPrefixedCommand(source.withPermission(2),"dungeon mirror");
            h.assertTrue(!DungeonService.recovering(player) && player.invulnerableTime==100 && DungeonService.run(player)==run
                    && player.position().equals(before) && row.getUUID("Entity").equals(actor),"One preview command resumes in place with normal entry protection");
            MirrorFight.cleanup(server,run);
            run.closed=true;
        }).thenSucceed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=80)
    public static void returnedStackFillsOneSlotAndMailsOnlyRemainder(GameTestHelper h){
        var player=AdventureGameTests.player(h);
        var inventory=player.getInventory();
        for(int i=0;i<36;i++) inventory.setItem(i,new ItemStack(Items.DIRT,64));
        inventory.setItem(13,new ItemStack(Items.APPLE,63));
        MirrorKit.equip(player,UUID.randomUUID());
        inventory.setItem(20,new ItemStack(Items.APPLE,3));
        MirrorKit.restore(player);
        MirrorKit.restore(player);
        h.assertTrue(inventory.getItem(13).is(Items.APPLE) && inventory.getItem(13).getCount()==64
                && inventory.countItem(Items.APPLE)==64,"The only free apple in the original stack was filled");
        var mail=DungeonData.get(player.server).profile(player.getUUID()).getList("LeylineMail",Tag.TAG_COMPOUND);
        h.assertTrue(mail.size()==1,"Only one undeliverable stack was mailed to the same player");
        var remainder=ItemStack.of(mail.getCompound(0));
        h.assertTrue(remainder.is(Items.APPLE) && remainder.getCount()==2,"Exactly two real apples remain in personal mail after repeated restoration");
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=80)
    public static void fullBackpackAndOriginalOffhandSurviveRepeatLoan(GameTestHelper h){
        var p=AdventureGameTests.player(h);var inv=p.getInventory();var run=UUID.randomUUID();
        for(int i=0;i<36;i++)inv.setItem(i,new ItemStack(Items.PAPER,1+i));
        var old=new ItemStack(Items.DIAMOND_HELMET);old.setDamageValue(12);old.getOrCreateTag().putString("OwnerMark","before");
        p.setItemSlot(EquipmentSlot.HEAD,old);
        p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.TOTEM_OF_UNDYING));
        inv.selected=3;var before=inv.save(new ListTag());
        MirrorKit.equip(p,run);MirrorKit.equip(p,run);
        h.assertTrue(p.getMainHandItem().is(Items.IRON_SWORD)&&p.getOffhandItem().is(Items.SHIELD),"Sword and shield actually equipped");
        h.assertTrue(p.getInventory().countItem(Items.GOLDEN_APPLE)==10
                && p.getInventory().countItem(Items.ENDER_PEARL)==10,"Both temporary consumables have a ten-item allowance");
        inv.getItem(6).shrink(1);inv.getItem(8).shrink(1);
        MirrorKit.equip(p,run);
        h.assertTrue(p.getInventory().countItem(Items.GOLDEN_APPLE)==9
                && p.getInventory().countItem(Items.ENDER_PEARL)==9,"Recurring equip cannot refill the combat supplies");
        for(var slot:List.of(EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD))
            h.assertTrue(p.getItemBySlot(slot).getItem() instanceof ArmorItem armor && armor.getMaterial()==ArmorMaterials.IRON,"Full iron armour");
        p.getMainHandItem().setDamageValue(29);MirrorKit.restore(p);MirrorKit.restore(p);
        h.assertTrue(before.equals(inv.save(new ListTag()))&&inv.selected==3,"Original full inventory, NBT and selected slot restored exactly once");
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=80)
    public static void reconnectAfterAbandonRecoversOriginalAndDeliveredItems(GameTestHelper h){
        var departing=AdventureGameTests.player(h);
        var original=new ItemStack(Items.FISHING_ROD);
        original.setDamageValue(19);original.getOrCreateTag().putString("OwnerInscription","岸边来信");
        departing.getInventory().setItem(6,original);
        departing.getInventory().selected=6;
        MirrorKit.equip(departing,UUID.randomUUID());
        // This is the player save from before the party abandoned. Its new session has no active run.
        var playerData=departing.saveWithoutId(new CompoundTag());
        var returning=AdventureGameTests.player(h);returning.load(playerData);
        returning.getInventory().setItem(15,new ItemStack(Items.EMERALD));
        returning.tickCount=10;
        MirrorKit.reconcile(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,returning));
        h.assertTrue(ItemStack.matches(returning.getMainHandItem(),original),"Logged-out participant got the original rod, damage, slot and inscription");
        h.assertTrue(returning.getInventory().countItem(Items.EMERALD)==1,"Mail delivered during the duel survives restoration");
        h.assertTrue(returning.getInventory().countItem(Items.IRON_SWORD)==0,"Loan kit has been collected");
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=80)
    public static void dryInlaidFloorAndExitMatchAllFourEntrances(GameTestHelper h){
        var seen=EnumSet.noneOf(net.minecraft.core.Direction.class);
        for(int seed=0;seed<100 && seen.size()<4;seed++){
            var r=new DungeonData.Run(UUID.randomUUID(),UUID.randomUUID(),seed,0,DungeonTheme.ASTRAL);r.floor=30;
            var room=r.graph().rooms.stream().filter(a->a.kind()==DungeonGraph.Kind.GUARDIAN).findFirst().orElseThrow();
            if(!seen.add(MirrorArena.entrance(r,room)))continue;
            var cells=new ArrayList<DungeonRealm.Cell>();MirrorArena.build(cells,r,room);
            h.assertTrue(cells.stream().anyMatch(a->a.pos().equals(MirrorArena.exit(r,room))&&a.state().is(DungeonContent.WAYSTONE)),"Exit present opposite entry");
            var center=DungeonRealm.center(r,room);
            var floor=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            for(var cell:cells) floor.put(cell.pos(),cell.state());
            h.assertTrue(cells.stream().noneMatch(a->!a.state().getFluidState().isEmpty()),"No environmental water in the guardian court");
            var palette=new HashSet<>();
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
                if(x*x+z*z>8.5*8.5)continue;
                var pos=center.offset(x,0,z);
                var paving=floor.get(pos);
                h.assertTrue(paving!=null && paving.isCollisionShapeFullBlock(h.getLevel(),pos)
                        && !floor.containsKey(pos.above()) && !floor.containsKey(pos.above(2)),"Continuous level paving with space for sprinting and jumping");
                palette.add(paving.getBlock());
            }
            h.assertTrue(palette.size()>=6,"The central inlay uses an actual material pattern");
        }
        h.assertTrue(seen.size()==4,"All four guardian orientations exercised");
        h.succeed();
    }

    @GameTest(template="building_test_empty",batch="echo_room",timeoutTicks=280)
    public static void twoRealPlayerProfilesShareOneCoreAndOneSettlement(GameTestHelper h){
        var server=h.getLevel().getServer();var l=Objects.requireNonNull(server.getLevel(DungeonRealm.KEY));
        var a=AdventureGameTests.player(h);var b=AdventureGameTests.player(h);var party=List.of(a,b);
        var d=DungeonData.get(server);var r=new DungeonData.Run(UUID.randomUUID(),a.getUUID(),617,d.nextSlot++,DungeonTheme.ASTRAL);
        r.members.add(b.getUUID());r.floor=30;r.multiplayer=true;d.runs.put(r.id,r);
        var room=r.graph().rooms.stream().filter(v->v.kind()==DungeonGraph.Kind.GUARDIAN).findFirst().orElseThrow();
        var c=DungeonRealm.center(r,room);var chunk=new ChunkPos(c);
        var tickets=new ArrayList<ChunkPos>();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            var cp=new ChunkPos(chunk.x+dx,chunk.z+dz);
            if(l.setChunkForced(cp.x,cp.z,true))tickets.add(cp);
            l.getChunk(cp.x,cp.z);
        }
        var cells=new ArrayList<DungeonRealm.Cell>();MirrorArena.build(cells,r,room);
        DungeonRealm.write(()->cells.forEach(v->l.setBlock(v.pos(),v.state(),2)));
        for(var p:party){
            d.profile(p.getUUID()).putUUID("Run",r.id);
            p.teleportTo(l,c.getX()+2.5,c.getY()+1,c.getZ()+.5,0,0);
            p.getInventory().setItem(0,new ItemStack(p==a?Items.STICK:Items.BONE));p.baseTick();
        }
        h.succeedWhen(()->{
            h.assertTrue(l.areEntitiesLoaded(chunk.toLong()),"Waiting for mirror entities to load");
            var s=r.state().events.getCompound("EchoingMirror");
            int phase=s.getInt("Phase");
            if(phase==0){
                h.assertTrue(MirrorFight.begin(l,r,room,party),"Inlaid court admits whole party");
                throw new GameTestAssertException("Seal has awakened");
            }
            var core=(EchoCore)l.getEntity(s.getUUID("Core"));
            var members=s.getCompound("People");
            if(phase==MirrorFight.MAGIC || phase==MirrorFight.DUEL){
                h.assertTrue(members.size()==2,"Exactly one real reflection per participant");
                h.assertTrue(!core.hurt(l.damageSources().playerAttack(a),99),"Stone seals the mirror");
                for(var p:party){
                    var row=members.getCompound(p.getStringUUID());
                    var copy=(EchoDouble)l.getEntity(row.getUUID("Entity"));
                    h.assertTrue(copy!=null&&copy.ownerId().equals(p.getUUID())&&copy.getName().getString().equals(p.getGameProfile().getName()),"Correct profile and name");
                    if(phase==MirrorFight.MAGIC)h.assertTrue(copy.getMainHandItem().is(p==a?Items.STICK:Items.BONE),"Real held equipment reflected");
                    else h.assertTrue(copy.getOffhandItem().is(Items.SHIELD)&&p.getOffhandItem().is(Items.SHIELD)&&MirrorFight.silent(p),"Both sides have duel kit and silence");
                    copy.die(l.damageSources().playerAttack(p));
                    if(p==a)h.assertTrue(s.getInt("Phase")==phase,"One player's victory cannot advance the whole party");
                }
                throw new GameTestAssertException("Wait for next change");
            }
            if(phase==MirrorFight.RETURNING){
                MirrorFight.tick(l,r,room,party);
                throw new GameTestAssertException("Reflections returning");
            }
            h.assertTrue(core.exposed(),"Wait for shutters");
            h.assertTrue(core.hurt(l.damageSources().playerAttack(b),1),"One point breaks the exposed mirror");
            h.assertTrue(!core.hurt(l.damageSources().playerAttack(a),1)&&r.state().clear(room.id()),"Exactly one settlement");
            for(var p:party){
                h.assertTrue(!MirrorFight.silent(p),"Silence released");
                MirrorKit.restore(p);
                h.assertTrue(p.getMainHandItem().is(p==a?Items.STICK:Items.BONE),"Original hand restored");
                h.assertTrue(d.profile(p.getUUID()).getBoolean("Complete_astral"),"Both participants earned completion");
            }
            for(var cp:tickets)l.setChunkForced(cp.x,cp.z,false);
            r.closed=true;
        });
    }
}
