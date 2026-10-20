package com.tnc.tnc.journey;
import com.tnc.tnc.home.*;
import com.tnc.tnc.magic.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.network.chat.Component;
import java.util.*;
/** MCA owns relationships. This class observes actual family IDs and adds voluntary home moments. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class FamilyLife {
    private static boolean warned;
    private static void error(Exception e){if(!warned){warned=true;com.mojang.logging.LogUtils.getLogger().error("MCA journey adapter",e);}}
    public static Object mca(ServerPlayer p)throws ReflectiveOperationException{return Class.forName("forge.net.mca.server.world.data.PlayerSaveData").getMethod("get",ServerPlayer.class).invoke(null,p);}
    public static Object entry(ServerPlayer p)throws ReflectiveOperationException{var d=mca(p);return d.getClass().getMethod("getFamilyEntry").invoke(d);}
    public static UUID partner(ServerPlayer p){if(!McaResidents.available())return null;try{var t=entry(p);var state=t.getClass().getMethod("getRelationshipState").invoke(t);if(!(Boolean)state.getClass().getMethod("isMarried").invoke(state))return null;var id=(UUID)t.getClass().getMethod("partner").invoke(t);return id==null||id.equals(new UUID(0,0))?null:id;}catch(Exception ex){error(ex);return null;}}
    public static int children(ServerPlayer p){if(!McaResidents.available())return 0;try{var t=entry(p);return ((Set<?>)t.getClass().getMethod("children").invoke(t)).size();}catch(Exception ex){error(ex);return 0;}}
    public static int presentChildren(ServerPlayer p){try{int count=0;var node=entry(p);for(var id:(Set<?>)node.getClass().getMethod("children").invoke(node)){var e=p.serverLevel().getEntity((UUID)id);if(e instanceof LivingEntity child&&child.isAlive()&&p.distanceToSqr(child)<=256)count++;}return count;}catch(Exception ex){error(ex);return 0;}}
    public static LivingEntity spouse(ServerPlayer p){var id=partner(p);if(id==null)return null;var e=p.serverLevel().getEntity(id);return e instanceof LivingEntity l&&l.isAlive()?l:null;}
    public static boolean home(ServerPlayer p,LivingEntity spouse){if(spouse==null||p.distanceToSqr(spouse)>144)return false;
        var plot=HousingService.mine(p,PlotCatalog.Kind.HOME);if(plot!=null&&p.serverLevel()==p.server.overworld()&&HousingService.owned(p)&&plot.contains(p.blockPosition().subtract(net.minecraft.core.BlockPos.of(HousingService.home(p).getLong("Origin")))))return true;
        try{var r=spouse.getClass().getMethod("getResidency").invoke(spouse);var h=(Optional<?>)r.getClass().getMethod("getHome").invoke(r);return h.isPresent()&&h.get() instanceof net.minecraft.core.GlobalPos g&&g.dimension().equals(p.level().dimension())&&g.pos().closerToCenterThan(p.position(),12);}catch(Exception ex){error(ex);return false;}
    }
    public static void initialize(ServerPlayer p){if(!McaResidents.available())return;try{
        var config=Class.forName("forge.net.mca.Config").getMethod("getInstance").invoke(null);config.getClass().getField("launchIntoDestiny").setBoolean(config,false);config.getClass().getField("allowDestinyTeleportation").setBoolean(config,false);
        var d=mca(p);d.getClass().getMethod("setEntityDataSet",boolean.class).invoke(d,true);
    }catch(Exception ex){error(ex);}}
    public static CompoundTag profile(ServerPlayer p){var t=new CompoundTag();var saved=JourneyData.get(p.server).player(p.getUUID());var magic=MagicStone.getOrNull(p);
        t.putString("Name",saved.getString("Name").isBlank()?p.getGameProfile().getName():saved.getString("Name"));t.putBoolean("Named",saved.getBoolean("Named"));t.putDouble("Attack",p.getAttributeValue(Attributes.ATTACK_DAMAGE));t.putDouble("Health",p.getMaxHealth());t.putDouble("Armor",p.getArmorValue());t.putDouble("Speed",p.getAttributeValue(Attributes.MOVEMENT_SPEED));t.putInt("Mana",magic==null?0:magic.getMana());t.putInt("MaxMana",magic==null?0:magic.getMaxMana());var a=new CompoundTag();for(var e:Element.values())a.putInt(e.id(),magic==null?0:magic.getAffinity(e));t.put("Affinity",a);return t;
    }
    public static void openProfile(ServerPlayer p){JourneyNetwork.open(p,"profile",profile(p));}
    public static void name(ServerPlayer p,String value){String n=value.strip().replaceAll("[\\p{Cntrl}§]","");if(n.isBlank()||n.codePointCount(0,n.length())>20){CommissionService.message(p,"名字请使用1—20个字符。");return;}var t=JourneyData.get(p.server).player(p.getUUID());t.putString("Name",n);t.putBoolean("Named",true);p.setCustomName(Component.literal(n));p.refreshDisplayName();p.refreshTabListName();initialize(p);
        if(McaResidents.available())try{var node=entry(p);node.getClass().getMethod("setName",String.class).invoke(node,n);}catch(Exception ex){error(ex);}JourneyData.get(p.server).setDirty();CommissionService.message(p,"愿旅途记住你的名字，"+n+"。");openProfile(p);
    }
    private static void familyIdentity(ServerPlayer p,UUID partner){var d=JourneyData.get(p.server);var t=d.player(p.getUUID());String key=partner==null?"":partner.toString();if(t.getString("FamilyPartner").equals(key))return;for(String event:List.of("depart","success","death","miss","child"))t.remove("Family_"+event);t.putString("FamilyPartner",key);t.putInt("Away",0);t.putBoolean("Dinner",false);t.putBoolean("ChildWasHome",false);d.setDirty();}
    public static void notice(ServerPlayer p,String event){var partner=partner(p);familyIdentity(p,partner);if(partner==null)return;var t=JourneyData.get(p.server).player(p.getUUID());t.putBoolean("Family_"+event,true);JourneyData.get(p.server).setDirty();}
    private static final Map<String,List<String>> LINES=Map.of(
        "depart",List.of("接下新的委托了？武运昌隆。我在家等你。","一路平安，别忘了带药，也别逞强。","路上看到有趣的东西，回来慢慢讲给我听。","出门前吃点热的吧，行囊我替你看过了。"),
        "success",List.of("回来啦！任务怎么样，累不累？今晚一起吃饭吧。","听见你的脚步我就安心了。坐下来歇一会。","辛苦啦！今天的冒险，愿意讲给我听吗？","委托做完了？比起报酬，我更高兴你平安回家。"),
        "death",List.of("哪里还痛？先别急着再出门，让我看看。","你回来了……吓坏我了。今天就在家休息一下吧。","装备还能修，你可要好好照顾自己。"),
        "miss",List.of("我好想你呀。今晚可不可以多待一会？","留给你的那盏灯，一直没熄。欢迎回家。","在外面那么久，一定有很多故事吧？慢慢说，我听着。"),
        "child",List.of("孩子一直等你回来讲冒险故事，今晚一起吃吧。","小家伙今天又问你什么时候回家。我们都很想你。","孩子说要给你做一枚平安符，歪歪扭扭的，可认真了。"));
    private static void say(ServerPlayer p,LivingEntity npc,String type){var options=LINES.get(type);String line=options.get(p.getRandom().nextInt(options.size()));p.sendSystemMessage(Component.literal("「"+npc.getName().getString()+"」 "+line));var t=new CompoundTag();t.putInt("Entity",npc.getId());t.putString("Line",line);JourneyNetwork.open(p,"greeting",t);}
    public static void family(ServerPlayer p){var npc=spouse(p);if(npc==null||p.distanceToSqr(npc)>144){CommissionService.message(p,"请来到伴侣身边。普通右键仍使用凡家物语互动。");return;}var t=new CompoundTag();t.putString("Spouse",npc.getName().getString());t.putInt("Children",children(p));t.putInt("PresentChildren",presentChildren(p));t.putBoolean("Home",home(p,npc));t.putString("Line",JourneyData.get(p.server).player(p.getUUID()).getBoolean("Dinner")?"想听听你的旅途故事，也想和你一起吃饭。":"今天过得怎么样？坐下来慢慢说吧。");JourneyNetwork.open(p,"family",t);}
    public static void dinner(ServerPlayer p){var npc=spouse(p);if(!home(p,npc)){CommissionService.message(p,"和伴侣一起回到住处后再共餐。");return;}var t=JourneyData.get(p.server).player(p.getUUID());long now=p.server.overworld().getGameTime();if(t.contains("DinnerAt")&&now-t.getLong("DinnerAt")<24000){CommissionService.message(p,"刚刚吃过家庭餐，稍后再一起吃吧。");return;}
        for(int i=0;i<36;i++){var food=p.getInventory().getItem(i);if(food.isEmpty()||food.getFoodProperties(p)==null||food.getFoodProperties(p).getNutrition()<4)continue;
            int need=2;var transaction=new com.tnc.tnc.adventure.InventoryTransaction(p.getInventory());for(int j=i;j<36&&need>0;j++){var same=p.getInventory().getItem(j);if(!net.minecraft.world.item.ItemStack.isSameItemSameTags(food,same))continue;int amount=Math.min(need,same.getCount());transaction.takeFromSlot(j,amount);need-=amount;}if(need>0)continue;
            var container=com.tnc.tnc.life.FoodContainers.remainder(food);if(!container.isEmpty()&&!transaction.add(container.copyWithCount(container.getCount()*2))){CommissionService.message(p,"请给餐具留一点背包空间。");return;}transaction.commit();p.getFoodData().eat(8,.8f);p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,200,0));npc.heal(4);t.putLong("DinnerAt",now);t.putBoolean("Dinner",false);JourneyData.get(p.server).setDirty();say(p,npc,presentChildren(p)>0?"child":"success");return;}
        CommissionService.message(p,"带两份同种、饱食度至少4的食物，留给自己和家人。");
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){initialize(p);var t=JourneyData.get(p.server).player(p.getUUID());if(t.getBoolean("Named"))p.setCustomName(Component.literal(t.getString("Name")));}}
    @SubscribeEvent public static void displayName(PlayerEvent.NameFormat e){if(e.getEntity().getCustomName()!=null)e.setDisplayname(e.getEntity().getCustomName());}
    @SubscribeEvent public static void tabName(PlayerEvent.TabListNameFormat e){if(e.getEntity().getCustomName()!=null)e.setDisplayName(e.getEntity().getCustomName());}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p){var t=JourneyData.get(p.server).player(p.getUUID());if(t.getBoolean("Named")){p.setCustomName(Component.literal(t.getString("Name")));p.refreshDisplayName();p.refreshTabListName();}if(!e.isEndConquered())notice(p,"death");}}
    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract e){if(e.getEntity() instanceof ServerPlayer p&&p.isShiftKeyDown()&&e.getTarget().getUUID().equals(partner(p))){e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);family(p);}}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||e.getServer().getTickCount()%40!=0)return;for(var p:e.getServer().getPlayerList().getPlayers())tickPlayer(p);}
    static void tickPlayer(ServerPlayer p){
        if(!p.isAlive()||p.isSpectator()||com.tnc.tnc.combat.DownedCombat.isDowned(p))return;var d=JourneyData.get(p.server);var t=d.player(p.getUUID());if(!t.getBoolean("Named")&&!t.getBoolean("WelcomeShown")&&p.tickCount>=80){t.putBoolean("WelcomeShown",true);d.setDirty();openProfile(p);return;}var partner=partner(p);familyIdentity(p,partner);if(partner==null)return;var npc=spouse(p);boolean atHome=home(p,npc);if(atHome){if(t.getInt("Away")>=24000)t.putBoolean("Family_miss",true);t.putInt("Away",0);}else t.putInt("Away",Math.min(72000,t.getInt("Away")+40));boolean childHere=atHome&&presentChildren(p)>0;if(childHere&&!t.getBoolean("ChildWasHome"))t.putBoolean("Family_child",true);t.putBoolean("ChildWasHome",childHere);d.setDirty();
        if(npc==null||p.distanceToSqr(npc)>100||p.server.overworld().getGameTime()-t.getLong("SpeechAt")<600)return;
        String event=null;if(atHome&&t.getBoolean("Family_death"))event="death";else if(atHome&&t.getBoolean("Family_success"))event="success";else if(atHome&&t.getBoolean("Family_miss"))event="miss";else if(atHome&&childHere&&t.getBoolean("Family_child"))event="child";else if(t.getBoolean("Family_depart"))event="depart";
        if(event==null)return;t.remove("Family_"+event);t.putLong("SpeechAt",p.server.overworld().getGameTime());if(atHome){t.putInt("Away",0);if(event.equals("success"))t.putBoolean("Dinner",true);}d.setDirty();say(p,npc,event.equals("success")&&childHere?"child":event);
    }
}
