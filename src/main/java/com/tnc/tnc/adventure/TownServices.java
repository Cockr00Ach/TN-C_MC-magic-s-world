package com.tnc.tnc.adventure;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.npc.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.MenuProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class TownServices {
    public record Post(String role,String name,BlockPos local,VillagerProfession profession){}
    public static final java.util.List<Post> POSTS=java.util.List.of(
        new Post("guild","协会接待员 · 艾琳",new BlockPos(428,94,285),VillagerProfession.LIBRARIAN),
        new Post("smith","潮生制杖屋 · 莉娅",new BlockPos(320,87,273),VillagerProfession.LIBRARIAN),
        new Post("armorer","炉石铁匠铺 · 铎恩",new BlockPos(198,100,264),VillagerProfession.ARMORER),
        new Post("broker","归航银行 · 米洛",new BlockPos(231,94,238),VillagerProfession.CARTOGRAPHER));
    private static BlockPos origin(ServerLevel l){return SkyIslandAnchors.resolve(l,SkyIslandAnchors.Anchor.ORIGIN);}
    public static BlockPos board(ServerLevel l){var o=origin(l);return o==null?null:o.offset(426,94,285);}
    public static String directions(ServerLevel l){var o=origin(l);if(o==null)return "天空岛尚未落成。";return "29潮生制杖屋 "+o.offset(320,87,273).toShortString()+"；28炉石铁匠铺 "+o.offset(198,100,264).toShortString()+"；19归航银行 "+o.offset(231,94,238).toShortString()+"。酒馆入门上楼北侧为委托栏。";}
    public static boolean near(ServerPlayer p,String role){
        if(p.serverLevel()!=p.server.overworld()||!p.isAlive()||p.isSpectator()||com.tnc.tnc.combat.DownedCombat.isDowned(p))return false;
        return !p.serverLevel().getEntitiesOfClass(TownServiceNpc.class,new AABB(p.blockPosition()).inflate(8),n->n.isAlive()&&n.role().equals(role)&&n.distanceToSqr(p)<=64).isEmpty();
    }
    public static boolean atBoard(ServerPlayer p){var b=board(p.server.overworld());return p.serverLevel()==p.server.overworld()&&p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p)&&b!=null&&b.closerToCenterThan(p.position(),8)&&p.serverLevel().hasChunkAt(b);}
    public static boolean openBoard(ServerPlayer p){var b=board(p.serverLevel());if(!atBoard(p)||b==null)return false;var be=p.serverLevel().getBlockEntity(b);if(be instanceof MenuProvider menu){p.openMenu(menu);return true;}return false;}
    public static void open(ServerPlayer p,TownServiceNpc npc){
        if(!near(p,npc.role()))return;AdventureService.milestone(p,"met_"+npc.role());AdventureService.sync(p,true,"");
        if(npc.role().equals("guild"))p.sendSystemMessage(Component.literal("登记在这里办理；接单与交付请右键旁边的Bountiful委托栏。"));
    }
    public static com.tnc.tnc.dialogue.DialogueScript introduce(ServerPlayer p,com.tnc.tnc.dialogue.DialogueScript original){
        var profile=AdventureService.profile(p);boolean first=!profile.hasMilestone("met_self");
        if(!profile.hasMilestone("self_materials")){
            var tx=new InventoryTransaction(p.getInventory());
            if(tx.add(new ItemStack(net.minecraft.world.item.Items.STICK,4))&&tx.add(new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT,2))){tx.commit();AdventureService.milestone(p,"self_materials");p.sendSystemMessage(Component.literal("Self交给你：4木棍、2铜锭。请交给潮生制杖屋的莉娅。"));}
            else p.sendSystemMessage(Component.literal("Self的制杖材料已替你留好；背包腾出位置后再与他交谈。"));
        }
        AdventureService.milestone(p,"met_self");if(!first)return original;
        var lines=new java.util.ArrayList<com.tnc.tnc.dialogue.DialogueScript.Line>();
        lines.add(new com.tnc.tnc.dialogue.DialogueScript.Line("Self","先认认城里的路。酒馆的艾琳负责协会登记，委托接取和交付去她旁边的委托栏。",null));
        lines.add(new com.tnc.tnc.dialogue.DialogueScript.Line("Self","法杖去29号潮生制杖屋找莉娅；铠甲去28号炉石铁匠铺找铎恩。买房找19号归航银行的米洛。",null));
        lines.add(new com.tnc.tnc.dialogue.DialogueScript.Line("Self","34号茶灯会馆先留作休闲的去处。各栋建筑我给你记在任务书的城镇地图里。至于你一直惦记的那件事……",null));
        lines.addAll(original.lines());return new com.tnc.tnc.dialogue.DialogueScript(original.id(),original.next(),original.theme(),original.quests(),original.activate(),original.requires(),original.excludes(),java.util.List.copyOf(lines));
    }
    private static BlockPos standing(ServerLevel l,BlockPos center){
        for(int radius=0;radius<=5;radius++)for(int dy=-2;dy<=2;dy++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;var p=center.offset(dx,dy,dz);
            if(l.hasChunkAt(p)&&l.getBlockState(p.below()).isCollisionShapeFullBlock(l,p.below())&&l.getBlockState(p).getCollisionShape(l,p).isEmpty()&&l.getBlockState(p.above()).getCollisionShape(l,p.above()).isEmpty()&&l.getFluidState(p).isEmpty())return p;
        }return null;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var s=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(s==null||s.getTickCount()%100!=0)return;var l=s.overworld();var o=origin(l);if(o==null||!SkyIslandAnchors.isComplete(l)||!com.tnc.tnc.world.SkyLandscapeUpgrade.complete(s))return;
        var store=AdventureSavedData.get(s);if(!store.housing.contains("TownServices",10))store.housing.put("TownServices",new CompoundTag());var records=store.housing.getCompound("TownServices");
        for(var post:POSTS){var record=records.getCompound(post.role);if(record.hasUUID("UUID"))continue;
            var target=standing(l,o.offset(post.local));if(target==null)continue;var npc=TNNpcs.SERVICE_NPC.get().create(l);if(npc==null)continue;
            npc.role(post.role);npc.setVillagerData(npc.getVillagerData().setProfession(post.profession));npc.setCustomName(Component.literal(post.name));npc.setCustomNameVisible(true);npc.moveTo(target.getX()+0.5,target.getY(),target.getZ()+0.5,180,0);
            if(l.addFreshEntity(npc)){record.putUUID("UUID",npc.getUUID());record.putLong("Pos",target.asLong());records.put(post.role,record);store.setDirty();}
        }
        var b=board(l);if(b==null||!l.hasChunkAt(b)||records.getBoolean("BoardInstalled"))return;
        var block=ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("bountiful:bountyboard"));
        if(block==null||block==net.minecraft.world.level.block.Blocks.AIR)return;
        // Never replace source furniture or player work. A safe empty spot is found before placement.
        if(l.getBlockState(b).isAir()){
            if(!l.getBlockState(b.below()).isCollisionShapeFullBlock(l,b.below()))return;
            var state=block.defaultBlockState();var facing=state.getBlock().getStateDefinition().getProperty("facing");if(facing!=null)state=direction(state,facing,"south");
            if(!l.setBlock(b,state,3))return;records.putBoolean("BoardPlaced",true);store.setDirty();
        }else if(l.getBlockState(b).getBlock()!=block||!records.getBoolean("BoardPlaced"))return;
        var be=l.getBlockEntity(b);if(be==null)return;
        if(seedDecrees(be)){records.putBoolean("BoardInstalled",true);store.setDirty();}
    }
    static boolean seedDecrees(net.minecraft.world.level.block.entity.BlockEntity be){
        try{
            var dataClass=Class.forName("io.ejekta.bountiful.bounty.DecreeData");var itemClass=Class.forName("io.ejekta.bountiful.content.DecreeItem");var companion=itemClass.getField("Companion").get(null);
            var inventory=net.minecraft.core.NonNullList.withSize(3,ItemStack.EMPTY);String[] decrees={"tnc_supply","tnc_food","tnc_hunt"};
            for(int i=0;i<3;i++){var decree=dataClass.getConstructor(java.util.List.class).newInstance(java.util.List.of(decrees[i]));inventory.set(i,(ItemStack)companion.getClass().getMethod("create",dataClass).invoke(companion,decree));}
            var saved=be.saveWithoutMetadata();var inv=new CompoundTag();net.minecraft.world.ContainerHelper.saveAllItems(inv,inventory);saved.put("decree_inv",inv);be.load(saved);be.setChanged();
            be.getClass().getMethod("tryInitialPopulation").invoke(be);
            var l=be.getLevel();var b=be.getBlockPos();l.sendBlockUpdated(b,l.getBlockState(b),l.getBlockState(b),3);return true;
        }catch(ReflectiveOperationException error){com.mojang.logging.LogUtils.getLogger().error("TN-C Bountiful decree setup failed; board retained for inspection",error);return false;}
    }
    private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState direction(net.minecraft.world.level.block.state.BlockState s,net.minecraft.world.level.block.state.properties.Property<T> p,String value){return p.getValue(value).map(v->s.setValue(p,v)).orElse(s);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void use(PlayerInteractEvent.RightClickBlock e){
        if(e.getEntity() instanceof ServerPlayer p&&NativeBountiful.isTownPaper(e.getItemStack())&&(!e.getPos().equals(board(p.server.overworld()))||!profileReady(p))){e.setCanceled(true);p.sendSystemMessage(Component.literal("这张城镇委托请回酒馆原生委托栏交付；先完成协会登记。"));return;}
        if(!(e.getEntity() instanceof ServerPlayer p)||!atBoard(p)||!e.getPos().equals(board(p.serverLevel())))return;
        AdventureService.milestone(p,"visited_board");
        if(!AdventureService.profile(p).registered()){e.setCanceled(true);p.sendSystemMessage(Component.literal("先与协会接待员艾琳登记，再使用委托栏。"));}
    }
    private static boolean profileReady(ServerPlayer p){return atBoard(p)&&AdventureService.profile(p).registered();}
}
