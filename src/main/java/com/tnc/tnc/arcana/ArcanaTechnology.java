package com.tnc.tnc.arcana;

import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.life.pasture.PastureAnimal;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Six purpose-built workshops; inventories, energy and produced materials are authoritative. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class ArcanaTechnology {
    public static boolean device(RouteNodeEntity n){return n.kind().ordinal()>=RouteKind.EXTRACTOR.ordinal();}
    public static String filter(ItemStack s){if(s.isEmpty())return "";String id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath();return switch(id){
        case "hearth_pepper_fruit","magma_cream"->"fire";case "rainletter_berry","blue_ice"->"water";
        case "storm_crown_tip","storm_crystal"->"lightning";case "dance_petal","phantom_membrane"->"wind";
        case "stone_pattern_leaf","ore_dream_flake"->"earth";case "shadow_hour_petal","shadow_mana_crystal"->"dark";
        case "dawn_honey","glowstone_dust"->"light";default->"";};}
    public static void tick(ServerLevel l,RouteNodeEntity n){switch(n.kind()){
        case EXTRACTOR->extract(l,n);case IGNITER->n.status="空手左拍升温 / 右拍降温；5M一次，贴药锅放置";
        case LEYLINE_COMPASS->n.status="空手右键：200M，F8寻迹30秒，500格真实未探索目标";
        case RESONANCE->resonate(l,n);case FUMIGATOR->fumigate(l,n);case PHASE_SHIFTER->phase(n);default->{}
    }}
    public static boolean interact(ServerPlayer p,RouteNodeEntity n,InteractionHand hand){
        if(n.kind()==RouteKind.IGNITER&&p.getItemInHand(hand).isEmpty()&&!p.isShiftKeyDown()){heat(p,n,false);return true;}
        if(n.kind()==RouteKind.LEYLINE_COMPASS&&p.getItemInHand(hand).isEmpty()&&!p.isShiftKeyDown()){LeylineDiscoveries.activate(p,n);return true;}
        return false;
    }
    @SubscribeEvent public static void tap(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock e){
        if(e.getEntity().getMainHandItem().isEmpty()&&e.getLevel().getBlockEntity(e.getPos()) instanceof RouteNodeEntity n&&n.kind()==RouteKind.IGNITER){e.setCanceled(true);if(e.getEntity() instanceof ServerPlayer p&&n.mayUse(p))heat(p,n,true);}
    }
    public static boolean heat(ServerPlayer p,RouteNodeEntity n,boolean hot){
        if(!n.mayUse(p)||p.distanceToSqr(Vec3.atCenterOf(n.getBlockPos()))>36)return false;
        long now=p.level().getGameTime();var t=n.getPersistentData();if(t.contains("LastTap")&&now-t.getLong("LastTap")<10)return false;
        for(var at:BlockPos.betweenClosed(n.getBlockPos().offset(-1,-1,-1),n.getBlockPos().offset(1,1,1))){var pot=com.tnc.tnc.alchemy.AlchemyBlock.master(p.level(),at);if(pot==null||!pot.mayUse(p)||pot.water==0||!pot.ready.isEmpty())continue;
            if(!n.spend(5))break;t.putLong("LastTap",now);pot.temperature=Math.max(0,Math.min(1000,pot.temperature+(hot?60:-60)));if(hot){pot.hasIgnited=true;pot.fire=2;pot.burn=1600;}pot.changed();
            com.tnc.tnc.mana.sight.ManaSightServer.record(p.serverLevel(),Vec3.atCenterOf(n.getBlockPos()),pot.center(),5,hot?0xff8452:0x65cfff,4);return true;
        }
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("引火桩需要5M，并紧贴有水的自家四方药锅。"),true);return false;
    }
    private static ItemStack refined(ItemStack s){
        if(s.is(ArcanaContent.item("giant_mana_fruit"))){var product=ArcanaEcology.giantProduct(s);return product==null?ItemStack.EMPTY:new ItemStack(product,8);}
        String id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).toString();
        if(id.equals("minecraft:raw_iron")||id.equals("minecraft:raw_gold")||id.equals("tnc:ore_dream_flake")||id.equals("tnc:stone_pattern_leaf"))return new ItemStack(ArcanaContent.item("mana_root_core"));
        if(id.equals("tnc:sail_fiber")||id.equals("tnc:mist_cotton_fiber")||id.equals("tnc:page_fern_fiber"))return new ItemStack(ArcanaContent.item("botanical_mana_filament"));
        if(id.startsWith("tnc:")&&(ArcanaMeals.allowed(s)||com.tnc.tnc.life.botanical.BotanicalContent.PRODUCTS.containsValue(s.getItem())||java.util.Arrays.stream(NewPlantKind.values()).anyMatch(k->s.is(RouteContent.item(k.product)))))return new ItemStack(ArcanaContent.item("pure_medicinal_powder"));return ItemStack.EMPTY;
    }
    private static boolean fits(ItemStack out,ItemStack made){return out.isEmpty()||ItemStack.isSameItemSameTags(out,made)&&out.getCount()+made.getCount()<=out.getMaxStackSize();}
    private static void put(RouteNodeEntity n,ItemStack made){var out=n.inventory.getItem(4);if(out.isEmpty())n.inventory.setItem(4,made);else out.grow(made.getCount());}
    private static void extract(ServerLevel l,RouteNodeEntity n){var in=n.inventory.getItem(0);var made=refined(in);if(made.isEmpty()||!fits(n.inventory.getItem(4),made)){n.progress=0;n.status="左一放魔植产物、初矿或巨果；右侧需有空间";return;}
        String key=in.getItem().toString()+in.getOrCreateTag();if(!key.equals(n.job)){n.job=key;n.progress=0;}if(n.mana<15){n.status="提纯需要15M";return;}if(++n.progress<3){n.status="剥离魔力丝 "+n.progress+"/3秒";return;}if(n.spend(15)){in.shrink(1);put(n,made);n.progress=0;n.status="提纯完成";RouteProgress.awardOwner(l,n.owner,"technology/extracted");}}
    public static void phase(RouteNodeEntity n){String type=filter(n.inventory.getItem(0));if(type.isEmpty()){n.status="滤镜槽放元素信物（不消耗）";return;}
        int total=0;for(String source:new String[]{"solar","lunar"}){int part=Math.min(256-total,ArcanaEnergy.amount(n,source));if(part>0){ArcanaEnergy.retype(n,source,type,part);total+=part;}}
        n.status="1:1转相 "+type+" · "+total+" M/s · 信物不消耗";
    }
    public static void fed(PastureAnimal a){var t=a.getPersistentData();long now=a.level().getGameTime();if(t.contains("ManualFeedAt")&&now-t.getLong("ManualFeedAt")<200)return;t.putLong("ManualFeedAt",now);t.putLong("FeedWave",t.getLong("FeedWave")+1);a.getPersistentData().putBoolean("ManaWorkEnabled",true);}
    public static void emitted(PastureAnimal a,int amount){if(amount>0&&a.getPersistentData().getLong("FeedWave")>0&&a.level().getGameTime()-a.getPersistentData().getLong("ManualFeedAt")<=1200)a.getPersistentData().putLong("EmittedWave",a.getPersistentData().getLong("FeedWave"));}
    private static void resonate(ServerLevel l,RouteNodeEntity n){
        boolean collector=false;for(var side:Direction.values())if(l.getBlockEntity(n.getBlockPos().relative(side)) instanceof RouteNodeEntity c&&java.util.Objects.equals(n.owner,c.owner)&&(c.kind()==RouteKind.ANIMAL_COLLECTOR||c.kind()==RouteKind.COLOR_TARGET))collector=true;
        if(!collector){n.status="紧邻自己的集息器或色靶";return;}
        for(var a:l.getEntitiesOfClass(PastureAnimal.class,new AABB(n.getBlockPos()).inflate(12),a->!a.isBaby()&&java.util.Objects.equals(n.owner,a.owner()))){var t=a.getPersistentData();long wave=t.getLong("EmittedWave");if(wave==0||wave<=t.getLong("CondensedWave"))continue;
            String id=switch(a.speciesId()){case "mirrorwing_moth"->"mirror_scale";case "prismatic_antelope"->"prism_horn_shard";case "starfelt_hare"->"soft_down";case "wirecall_lizard"->"storm_crystal";default->"";};if(id.isEmpty())continue;var made=new ItemStack(ArcanaContent.item(id));if(!fits(n.inventory.getItem(4),made)||!n.spend(16))return;t.putLong("CondensedWave",wave);put(n,made);n.status="照料与真实产魔凝成馈赠";return;
        }n.status="等待亲手喂养后实际产魔的愉悦波长";
    }
    private static void fumigate(ServerLevel l,RouteNodeEntity n){var s=n.inventory.getItem(0);String id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath();var bottles=n.inventory.getItem(5);
        if(!java.util.Set.of("flame_twist_elixir","gigantism_elixir","fission_elixir","elixir_shell").contains(id)||!bottles.isEmpty()&&(!bottles.is(Items.GLASS_BOTTLE)||bottles.getCount()>=64)){n.progress=0;n.status="左一放扭曲／巨人／分裂／坚肤药，留空瓶格";return;}
        if(n.mana<40){n.status="点化需要40M";return;}if(!id.equals(n.job)){n.job=id;n.progress=0;}if(++n.progress<10){n.status="药雾交织 "+n.progress+"/10秒";return;}
        boolean done=false;
        if(id.equals("elixir_shell")){var ore=n.inventory.getItem(1);if((ore.is(Items.GOLD_INGOT)||ore.is(Items.DIAMOND))&&!ore.getOrCreateTag().getBoolean("ManaPretempered")){var made=ore.copyWithCount(1);made.getOrCreateTag().putBoolean("ManaPretempered",true);if(fits(n.inventory.getItem(4),made)){ore.shrink(1);put(n,made);done=true;}}}
        else if(id.equals("fission_elixir")){for(var a:l.getEntitiesOfClass(PastureAnimal.class,new AABB(n.getBlockPos()).inflate(1.5),a->a.isBaby()&&java.util.Objects.equals(n.owner,a.owner())&&!a.getPersistentData().getBoolean("EliteMana"))){a.getPersistentData().putBoolean("EliteMana",true);done=true;break;}}
        else for(var at:BlockPos.betweenClosed(n.getBlockPos().offset(-1,-1,-1),n.getBlockPos().offset(1,1,1))){var be=l.getBlockEntity(at);if(be==null||!java.util.Objects.equals(n.owner,ArcanaEcology.owner(be))||!RouteAccess.allowed(l,at,n.owner))continue;
            if(id.equals("gigantism_elixir")&&be.getPersistentData().getFloat("ArcanaScale")<2){be.getPersistentData().putFloat("ArcanaScale",3);be.setChanged();l.sendBlockUpdated(at,be.getBlockState(),be.getBlockState(),3);done=true;break;}
            if(id.equals("flame_twist_elixir")&&(be instanceof RoutePlantEntity r&&r.kind()==NewPlantKind.DEW||be instanceof com.tnc.tnc.life.botanical.BotanicalPlantEntity b&&(b.species()==com.tnc.tnc.life.botanical.BotanicalSpecies.MIRROR_LOTUS||b.species()==com.tnc.tnc.life.botanical.BotanicalSpecies.FROST_CHIME))){
                if(l.random.nextFloat()<.8f){l.setBlock(at,RouteContent.PLANTS.get("steam_lotus").get().defaultBlockState().setValue(RoutePlantBlock.AGE,3),3);var next=(RoutePlantEntity)l.getBlockEntity(at);next.owner=n.owner;next.growth=18000;next.setChanged();}else{l.destroyBlock(at,false);net.minecraft.world.Containers.dropItemStack(l,at.getX()+.5,at.getY()+.5,at.getZ()+.5,new ItemStack(ArcanaContent.item("mana_ash")));}done=true;break;
            }
        }
        if(done){n.spend(40);s.shrink(1);if(bottles.isEmpty())n.inventory.setItem(5,new ItemStack(Items.GLASS_BOTTLE));else bottles.grow(1);n.progress=0;n.status="一份药雾完成一次真实点化";}else{n.progress=9;n.status="无合适目标，药与魔力未消耗";}
    }
    private ArcanaTechnology(){}
}
