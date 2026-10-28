package com.tnc.tnc.skyacademy;

import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.life.botanical.*;
import com.tnc.tnc.life.pasture.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Real registered machines run their ordinary production/transfer logic in these lessons. */
@Mod.EventBusSubscriber(modid="tnc")
public final class AcademyStations {
    public static BlockPos base(MinecraftServer server,String site){var p=SkyAcademyWorld.at(server,site);return p==null?null:p.offset(site.equals("farm")?-3:-5,0,site.equals("farm")?-10:-4);}
    private static RouteNodeEntity node(ServerPlayer p,BlockPos pos,RouteKind kind){
        p.serverLevel().setBlock(pos,RouteContent.NODES.get(kind.id).get().defaultBlockState(),3);
        var n=(RouteNodeEntity)p.serverLevel().getBlockEntity(pos);n.owner=p.getUUID();n.getPersistentData().putBoolean("SkyAcademy",true);n.setChanged();return n;
    }
    private static void kit(ServerPlayer p,String key,String... ids){
        var data=AcademyLessons.data(p);if(data.getBoolean(key))return;
        // A single starter kit, never replenished by replaying cinematics.
        for(String id:ids){var stack=new ItemStack(RouteContent.item(id));if(!p.getInventory().add(stack))p.drop(stack,false);}
        data.putBoolean(key,true);AcademyLessons.dirty(p);
    }
    public static boolean owned(ServerPlayer p,String site){var b=base(p.server,site);return b!=null&&p.serverLevel().getBlockEntity(site.equals("farm")?b.east(4):b) instanceof RouteNodeEntity n&&p.getUUID().equals(n.owner);}
    private static void clearPracticeSlot(ServerPlayer p,BlockPos pos){
        var l=p.serverLevel();
        if(l.getBlockEntity(pos) instanceof RouteNodeEntity old){
            // Return an actual placed practice item to its owner before a demonstration
            // replaces it. Demo-only pieces never become repeatable free starter kits.
            var tag=old.getPersistentData();
            if(tag.hasUUID("AcademyPlacedBy")){
                var who=tag.getUUID("AcademyPlacedBy");
                var refunds=new java.util.ArrayList<ItemStack>();refunds.add(new ItemStack(l.getBlockState(pos).getBlock().asItem()));
                for(int i=0;i<old.inventory.getContainerSize();i++)if(!old.inventory.getItem(i).isEmpty())refunds.add(old.inventory.removeItemNoUpdate(i));
                for(var refund:refunds){var player=p.server.getPlayerList().getPlayer(who);
                    if(player!=null){if(!player.getInventory().add(refund))player.drop(refund,false);}
                    else {var root=com.tnc.tnc.journey.JourneyData.get(p.server).player(who);var items=root.getList("AcademyRefunds",10);items.add(refund.save(new net.minecraft.nbt.CompoundTag()));root.put("AcademyRefunds",items);com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();}
                }
            }
        }
        l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
    }
    @SubscribeEvent public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;var root=com.tnc.tnc.journey.JourneyData.get(p.server).player(p.getUUID());
        for(var value:root.getList("AcademyRefunds",10)){var item=ItemStack.of((net.minecraft.nbt.CompoundTag)value);if(!p.getInventory().add(item))p.drop(item,false);}
        root.remove("AcademyRefunds");com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();
    }
    public static void setup(ServerPlayer p,String site,boolean demo){
        var l=p.serverLevel();var b=base(p.server,site);if(b==null||!l.hasChunkAt(b)||!AcademyLessons.leased(p,site))return;
        for(int x=-1;x<=8;x++)for(int z=-1;z<=3;z++){var q=b.offset(x,-1,z);if(l.getBlockState(q).isAir())l.setBlock(q,Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),3);}
        if(site.equals("farm")){
            l.setBlock(b.below(),RouteContent.SOIL.get().defaultBlockState(),3);
            l.setBlock(b.offset(0,-1,1),RouteContent.MANA_LIQUID.get().defaultBlockState(),3);
            // Stone lip contains the actual flowing fluid; no particle-only substitute.
            for(var q:java.util.List.of(b.offset(-1,-1,1),b.offset(1,-1,1),b.offset(0,-1,2),b.offset(0,-2,1)))l.setBlock(q,Blocks.STONE_BRICKS.defaultBlockState(),3);
            l.setBlock(b,BotanicalContent.BLOCKS.get("wind_sail").defaultBlockState().setValue(BotanicalBlock.AGE,3),3);
            var plant=(BotanicalPlantEntity)l.getBlockEntity(b);plant.owner=p.getUUID();plant.wild=false;plant.growth=plant.species().firstTicks;plant.setChanged();
            clearPracticeSlot(p,b.east());
            if(demo)node(p,b.east(),RouteKind.COLLECTOR);else {AcademyLessons.data(p).putBoolean("PlantPlaced",false);}
            var collector=node(p,b.east(4),RouteKind.ANIMAL_COLLECTOR);collector.mana=0;
            for(var animal:l.getEntitiesOfClass(PastureAnimal.class,new AABB(b).inflate(14),a->a.getPersistentData().getBoolean("AcademyAnimal")))animal.discard();
            var animal=PastureRegistry.TYPES.get("springhoof_strider").create(l);
            if(animal!=null){animal.setPos(b.getX()+6.5,b.getY(),b.getZ()+1.5);animal.setPersistenceRequired();animal.setInvulnerable(true);animal.getPersistentData().putBoolean("AcademyAnimal",true);l.addFreshEntity(animal);
                if(demo&&animal.bindManaWork(p,b.east(4)))collector.animal=animal.getUUID();
                animal.getNavigation().moveTo(b.getX()+7.5,b.getY(),b.getZ()+.5,.65);
            }
            if(!demo)kit(p,"FarmKit","dew_collector","pasture_staff");
        }else if(site.equals("workshop")){
            var source=node(p,b,RouteKind.POOL);source.mana=40;source.setChanged();
            node(p,b.east(),RouteKind.WIRE);node(p,b.east(3),RouteKind.WIRE);var lamp=node(p,b.east(4),RouteKind.LAMP);lamp.mana=lamp.inRate=lamp.outRate=0;lamp.setChanged();
            clearPracticeSlot(p,b.east(2));
            if(demo)node(p,b.east(2),RouteKind.WIRE);else {AcademyLessons.data(p).putBoolean("WirePlaced",false);kit(p,"WireKit","mana_thread");}
            for(int x=0;x<5;x++)if(l.getBlockState(b.east(x)).getBlock() instanceof RouteWireBlock wire)wire.refresh(l,b.east(x));
        }
    }
    public static boolean complete(ServerPlayer p,String site){
        var b=base(p.server,site);var l=p.serverLevel();if(b==null||!l.hasChunkAt(b))return false;
        var t=AcademyLessons.data(p);
        if(site.equals("farm")){
            for(var animal:l.getEntitiesOfClass(PastureAnimal.class,new AABB(b).inflate(12),a->a.getPersistentData().getBoolean("AcademyAnimal"))){
                if(animal.getNavigation().isDone()){boolean left=(l.getGameTime()/100)%2==0;animal.getNavigation().moveTo(b.getX()+(left?5.5:7.5),b.getY(),b.getZ()+1.5,.65);}
            }
            boolean plant=t.getBoolean("PlantPlaced")&&l.getBlockEntity(b.east()) instanceof RouteNodeEntity n&&p.getUUID().equals(n.owner)&&n.kind()==RouteKind.COLLECTOR&&n.mana>0;
            boolean animal=l.getBlockEntity(b.east(4)) instanceof RouteNodeEntity n&&p.getUUID().equals(n.owner)&&n.animal!=null&&n.mana>0;
            if(plant)t.putBoolean("PlantLearned",true);if(animal)t.putBoolean("AnimalLearned",true);
            return t.getBoolean("PlantLearned")&&t.getBoolean("AnimalLearned");
        }
        return t.getBoolean("WirePlaced")&&l.getBlockEntity(b.east(4)) instanceof RouteNodeEntity lamp&&lamp.kind()==RouteKind.LAMP&&p.getUUID().equals(lamp.owner)&&lamp.inRate>0&&l.getBlockState(b.east(4)).getValue(RouteNodeBlock.LIT);
    }
    @SubscribeEvent public static void placed(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!SkyAcademyWorld.available(p.server))return;
        for(String site:java.util.List.of("farm","workshop")){
            if(!AcademyLessons.leased(p,site)||!AcademyLessons.data(p).getBoolean("Practice"))continue;
            var b=base(p.server,site);var at=site.equals("farm")?b.east():b.east(2);var kind=site.equals("farm")?RouteKind.COLLECTOR:RouteKind.WIRE;
            if(e.getPos().equals(at)&&e.getPlacedBlock().is(RouteContent.NODES.get(kind.id).get())){if(p.serverLevel().getBlockEntity(at) instanceof RouteNodeEntity n)n.getPersistentData().putUUID("AcademyPlacedBy",p.getUUID());AcademyLessons.data(p).putBoolean(site.equals("farm")?"PlantPlaced":"WirePlaced",true);AcademyLessons.dirty(p);}
        }
    }
}
