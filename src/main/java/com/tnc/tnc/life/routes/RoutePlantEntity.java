package com.tnc.tnc.life.routes;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
public final class RoutePlantEntity extends BlockEntity {
    public UUID owner;public int growth,mana;private double remainder;private int daytime,nighttime,cycle,shadowEvents,feedSeconds;private long epoch=-1,lastPage=-100;private int lastShadow=-1;private final Set<String> organic=new HashSet<>(),pages=new HashSet<>();
    public RoutePlantEntity(BlockPos p,BlockState s){super(RouteContent.PLANT_ENTITY.get(),p,s);}
    public NewPlantKind kind(){return ((RoutePlantBlock)getBlockState().getBlock()).kind;}
    public boolean feed(ServerPlayer p,ItemStack held){if(owner!=null&&!owner.equals(p.getUUID()))return false;if(kind()==NewPlantKind.COMPOST&&feedSeconds<90&&organic.size()<3&&(held.isEdible()||held.is(Items.WHEAT)||held.is(Items.BROWN_MUSHROOM)||held.is(Items.RED_MUSHROOM)||held.is(net.minecraft.tags.ItemTags.LEAVES))){String id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(held.getItem()).toString();organic.add(id);if(!p.isCreative())held.shrink(1);feedSeconds+=30;setChanged();return true;}return false;}
    public static void tick(ServerLevel l,BlockPos p,RoutePlantEntity be){if(l.getGameTime()%20!=0)return;var state=l.getBlockState(p);boolean wild=state.getValue(RoutePlantBlock.WILD);boolean growing=wild||MagicSoilBlock.growing(l,p);if(!growing)return;
        if(be.growth<18000){be.growth=Math.min(18000,be.growth+20);int age=be.growth*3/18000;if(state.getValue(RoutePlantBlock.AGE)!=age)l.setBlock(p,state.setValue(RoutePlantBlock.AGE,age),3);be.setChanged();return;}
        if(wild||!RouteAccess.allowed(l,p,be.owner))return;
        int room=be.kind().capacity-be.mana;int packet=switch(be.kind()){case SHADOW->60;case PAGE->30;case CYCLE,STORM->6;default->4;};if(room<packet)return;
        long day=Math.floorDiv(l.getGameTime(),24000);if(be.epoch<day){be.epoch=day;be.shadowEvents=0;be.pages.clear();}
        double generated=switch(be.kind()){
            case COMPOST->{if(be.feedSeconds<=0)yield 0;be.feedSeconds--;double rate=be.organic.size()>=3?300./90:180./90;if(be.feedSeconds==0)be.organic.clear();yield rate;}
            case THERMAL->{boolean heat=false,cold=false;for(var d:Direction.Plane.HORIZONTAL){var at=p.relative(d);var s=l.getBlockState(at);heat|=s.getBlock() instanceof AbstractFurnaceBlock&&s.hasProperty(AbstractFurnaceBlock.LIT)&&s.getValue(AbstractFurnaceBlock.LIT);cold|=s.is(Blocks.ICE)||s.is(Blocks.PACKED_ICE)||l.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER);}yield heat&&cold?2:0;}
            case SHADOW->{int shadow=(int)(Math.floorMod(l.getDayTime(),24000)/1500);boolean natural=l.getBrightness(net.minecraft.world.level.LightLayer.SKY,p)>0&&l.isDay()&&!l.canSeeSky(p);if(natural&&shadow!=be.lastShadow&&be.shadowEvents<8){be.lastShadow=shadow;be.shadowEvents++;yield 60;}yield 0;}
            case PAGE->{double got=0;for(var d:Direction.Plane.HORIZONTAL)if(l.getBlockEntity(p.relative(d)) instanceof LecternBlockEntity book&&!book.getBook().isEmpty()&&book.getBook().hasTag()&&book.getBook().getTag().getList("pages",8).size()>0){var tag=book.saveWithoutMetadata();String page=tag.getInt("Page")+"/"+book.getBook().getTag().getList("pages",8).toString();if(tag.getInt("Page")<0||tag.getInt("Page")>=book.getBook().getTag().getList("pages",8).size())continue;if(!be.pages.contains(page)&&be.pages.size()<8&&l.getGameTime()-be.lastPage>=200){for(var entity:l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(p).inflate(1),e->e.getItem().is(Items.PAPER)&&RouteAccess.allowed(l,e.blockPosition(),be.owner))){entity.getItem().shrink(1);if(entity.getItem().isEmpty())entity.discard();be.pages.add(page);be.lastPage=l.getGameTime();got=30;break;}}}yield got;}
            case CYCLE->{if(l.isDay())be.daytime=Math.min(60,be.daytime+1);else be.nighttime=Math.min(60,be.nighttime+1);if(be.daytime>=60&&be.nighttime>=60&&be.cycle==0){be.cycle=60;be.daytime=0;be.nighttime=0;}yield be.cycle>0&&be.cycle-->0?6:0;}
            case DEW->l.isRainingAt(p.above())?2:0;
            case PRISM->{Set<String> colors=new HashSet<>();if(l.isDay()&&l.getBrightness(net.minecraft.world.level.LightLayer.SKY,p)>=12)for(var d:Direction.Plane.HORIZONTAL){var id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(l.getBlockState(p.relative(d)).getBlock());if(id!=null&&id.getPath().endsWith("_stained_glass"))colors.add(id.toString());}yield colors.size()>=3?3:0;}
            case ORE->RouteOreLedger.get(l).drawNatural(l,p,2);
            case STORM->0;
        };
        be.remainder+=generated;int whole=(int)be.remainder;be.remainder-=whole;be.mana=Math.min(be.kind().capacity,be.mana+whole);be.setChanged();
    }
    public int draw(int maximum){int amount=Math.min(mana,Math.min(maximum,kind()==NewPlantKind.SHADOW||kind()==NewPlantKind.PAGE?4:(int)Math.ceil(kind().rate)));mana-=amount;if(amount>0)setChanged();return amount;}
    public void naturalLightning(){mana=Math.min(kind().capacity,mana+2400);setChanged();}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);if(owner!=null)t.putUUID("Owner",owner);t.putInt("Growth",growth);t.putInt("Mana",mana);t.putDouble("Remainder",remainder);t.putInt("Day",daytime);t.putInt("Night",nighttime);t.putInt("Cycle",cycle);t.putLong("Epoch",epoch);t.putInt("Shadow",shadowEvents);t.putInt("LastShadow",lastShadow);t.putInt("Feed",feedSeconds);t.putLong("LastPage",lastPage);var o=new ListTag();organic.forEach(s->o.add(StringTag.valueOf(s)));t.put("Organic",o);var list=new ListTag();pages.forEach(s->list.add(StringTag.valueOf(s)));t.put("Pages",list);}
    @Override public void load(CompoundTag t){super.load(t);owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;growth=Math.max(0,Math.min(18000,t.getInt("Growth")));mana=Math.max(0,Math.min(kind().capacity,t.getInt("Mana")));remainder=Math.max(0,Math.min(.999,t.getDouble("Remainder")));daytime=Math.min(60,t.getInt("Day"));nighttime=Math.min(60,t.getInt("Night"));cycle=Math.min(60,t.getInt("Cycle"));epoch=t.getLong("Epoch");shadowEvents=t.getInt("Shadow");lastShadow=t.getInt("LastShadow");feedSeconds=Math.max(0,Math.min(90,t.getInt("Feed")));lastPage=t.getLong("LastPage");organic.clear();for(Tag raw:t.getList("Organic",8))if(organic.size()<3)organic.add(raw.getAsString());pages.clear();for(Tag raw:t.getList("Pages",8))if(pages.size()<8)pages.add(raw.getAsString());}
}
