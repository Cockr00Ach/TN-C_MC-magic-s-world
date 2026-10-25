package com.tnc.tnc.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.equipment.MageGear;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

/** Opt-in disposable full-pack client. Hand shots use the real ItemInHandRenderer. */
public final class EquipmentNativeAudit {
    private record Scene(String label, String wand, String style, int tier, boolean offhand, int fov, String pose) {
        boolean firstPerson() { return wand != null && style == null; }
    }
    private static final List<Scene> SCENES = scenes();
    private static final List<String> failures = new ArrayList<>(), shots = new ArrayList<>();
    private static boolean started, saving;
    private static volatile boolean ready;
    private static int scene, ticks, startup;
    private static net.minecraft.world.entity.decoration.ArmorStand seat;
    private static Path folder() { return Path.of(System.getProperty("tnc.dungeonAuditOutput")); }

    private static List<Scene> scenes() {
        var result = new ArrayList<Scene>();
        for (int tier=5; tier>=1; tier--) for (var d : ElementWands.ALL) if (d.tier()==tier)
            result.add(new Scene("hand-"+d.id(),d.id(),null,tier,false,70,"idle"));
        result.add(new Scene("hand-magic_wand","magic_wand",null,1,false,70,"idle"));
        for (var d : ElementWands.ALL) if (d.tier()==5)
            result.add(new Scene("offhand-"+d.id(),d.id(),null,5,true,70,"idle"));
        for (int fov : new int[]{60,90}) result.add(new Scene("fov-"+fov,"water_wand_5",null,5,false,fov,"idle"));
        for (String style : List.of("bastion","astral","runic","wanderer","divine"))
            for (int tier : style.equals("divine")?new int[]{5}:new int[]{1,2,3,4})
                result.add(new Scene("worn-"+style+"-"+tier,null,style,tier,false,70,"idle"));
        for (String pose : List.of("walk","crouch","ride")) for (String style : List.of("bastion","astral","runic","wanderer","divine")) {
            int tier=style.equals("divine")?5:4;
            result.add(new Scene(pose+"-"+style,null,style,tier,false,70,pose));
        }
        for (var d : ElementWands.ALL) if (d.tier()==5)
            result.add(new Scene("third-"+d.id(),d.id(),"astral",2,false,70,"idle"));
        result.add(new Scene("inventory-icons",null,"icons",0,false,70,"idle"));
        return List.copyOf(result);
    }

    public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (event.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance(); org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if (mc.level==null || mc.player==null || mc.getOverlay()!=null) return;
        if (mc.screen!=null && mc.screen.getClass().getName().contains(".mca.")) {
            for (var child:mc.screen.children()) if (child instanceof net.minecraft.client.gui.components.Button b && b.active
                    && Set.of("完成","接受","某处","下一步","Done","Next","Accept").contains(b.getMessage().getString())) {b.onPress();break;}
            return;
        }
        if (!started && ++startup<400) return;
        if (!started) {
            started=true; Files.createDirectories(folder()); mc.options.pauseOnLostFocus=false;
            mc.options.guiScale().set(2); mc.options.renderDistance().set(4); mc.options.framerateLimit().set(60);
            mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false); mc.options.hideGui=true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            validateBakedResources(mc); prepare(mc); return;
        }
        if (!ready || saving) return;
        var s=SCENES.get(scene);
        mc.player.getInventory().selected=0;
        if (!inventoryArrived(mc,s)) return;
        // F1 also disables ItemInHandRenderer; keep the real HUD enabled for hand shots.
        mc.options.hideGui=!s.firstPerson();
        if (s.firstPerson()) mc.setScreen(null);
        else if (!(mc.screen instanceof Gallery)) mc.setScreen(new Gallery(s));
        if (++ticks==45) {
            if (s.pose.equals("crouch") && !mc.player.isCrouching()) failures.add("Crouch input did not reach player: "+s.label);
            if (s.pose.equals("walk") && mc.player.walkAnimation.speed()<.05f) failures.add("Walk input did not reach player: "+s.label);
            if (s.pose.equals("ride") && !mc.player.isPassenger()) failures.add("Mounted pose did not reach player: "+s.label);
            saving=true; String name=String.format(Locale.ROOT,"%02d-%s.png",scene,s.label);
            Screenshot.grab(mc.gameDirectory,"equipment-"+name,mc.getMainRenderTarget(),message->{
                try {Files.copy(mc.gameDirectory.toPath().resolve("screenshots/equipment-"+name),folder().resolve(name),StandardCopyOption.REPLACE_EXISTING);shots.add(name);}
                catch(Exception error){failures.add(error.toString());} finally {saving=false;}
            }); return;
        }
        if (ticks<50) return;
        if (++scene>=SCENES.size()) {
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(mc.gameDirectory.toPath().resolve("mods/tnc-1.0.0.jar"))));
            Files.writeString(folder().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "jar_sha256",sha,"failures",failures,"screenshots",shots,"expected",SCENES.size(),"wands",36,"wearables",34,
                    "scope","Real full-pack first-person hand rendering; actual equipped local-player models in three inventory cameras. Walking/crouching use input-driven fixture poses, not a full combat playthrough.")));
            com.tnc.tnc.dungeon.client.WorldFeedbackNativeAudit.finished(mc); return;
        }
        ticks=0; prepare(mc);
    }

    private static ItemStack wand(String id) {
        return id.equals("magic_wand")?new ItemStack(TNMod.WAND.get()):ElementWands.stack(ElementWands.find(id));
    }
    private static boolean inventoryArrived(Minecraft mc,Scene s) {
        if (s.wand!=null && !(s.offhand?mc.player.getOffhandItem():mc.player.getMainHandItem()).is(wand(s.wand).getItem())) return false;
        return s.style==null || s.style.equals("icons") || mc.player.getItemBySlot(EquipmentSlot.CHEST).is(MageGear.ITEMS.get(s.style+"_outfit_"+s.tier).get());
    }
    private static void prepare(Minecraft mc) {
        ready=false; var s=SCENES.get(scene); mc.setScreen(null); mc.options.fov().set(s.fov);
        // A gallery screen releases KeyMapping states. Supply actual movement input to the
        // disposable player, so LocalPlayer still performs its normal pose/physics update.
        mc.player.input=new Input() {
            @Override public void tick(boolean slowed,float scale) {
                up=s.pose.equals("walk");shiftKeyDown=s.pose.equals("crouch");
                forwardImpulse=up?(slowed?scale:1):0;leftImpulse=0;jumping=false;
            }
        };
        mc.getSingleplayerServer().execute(()->{try {
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0); var level=p.server.overworld();
            p.stopRiding();if(seat!=null){seat.discard();seat=null;}
            if (scene==0) {
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
                for (var pos:BlockPos.betweenClosed(1010,219,1000,1055,228,1050))
                    level.setBlock(pos,pos.getY()==219?Blocks.STONE.defaultBlockState():pos.getY()==220?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            level.setDayTime(6000); level.setWeatherParameters(6000,0,false,false);
            p.setGameMode(GameType.CREATIVE); p.getAbilities().flying=false; p.onUpdateAbilities();
            p.teleportTo(level,1032.5,221,1030.5,180,0); p.setShiftKeyDown(false);p.getInventory().clearContent();p.getInventory().selected=0;
            for (var slot:EquipmentSlot.values()) p.setItemSlot(slot,ItemStack.EMPTY);
            if (s.style!=null && !s.style.equals("icons")) {
                p.setItemSlot(EquipmentSlot.HEAD,MageGear.stack(MageGear.find(s.style+"_hat_"+s.tier)));
                p.setItemSlot(EquipmentSlot.CHEST,MageGear.stack(MageGear.find(s.style+"_outfit_"+s.tier)));
            }
            if (s.wand!=null) p.setItemInHand(s.offhand?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,wand(s.wand));
            if (s.pose.equals("ride")) {
                seat=new net.minecraft.world.entity.decoration.ArmorStand(level,1032.5,221,1030.5);
                seat.setNoGravity(true);seat.setInvisible(true);level.addFreshEntity(seat);p.startRiding(seat,true);
            }
            p.inventoryMenu.broadcastChanges(); ready=true;
        } catch(Throwable error){failures.add(error.toString());ready=true;}});
    }

    private static void validateBakedResources(Minecraft mc) throws Exception {
        var models=mc.getModelManager(); var items=new ArrayList<ItemStack>();
        ElementWands.ALL.forEach(d->items.add(ElementWands.stack(d)));items.add(new ItemStack(TNMod.WAND.get()));
        for (var stack:items) {
            var id=ForgeRegistries.ITEMS.getKey(stack.getItem());var model=models.getModel(new ModelResourceLocation(id,"inventory"));
            if (model==models.getMissingModel()) failures.add("Missing wand model "+id);
            var held=model.applyTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,new PoseStack(),false);
            if (held.getQuads(null,null,RandomSource.create(1)).size()<90) failures.add("Hand model still flat or incomplete "+id);
            var gui=model.applyTransform(ItemDisplayContext.GUI,new PoseStack(),false);
            String expected="item/"+(id.getPath().equals("magic_wand")?"magic_wand":"wands/"+id.getPath());
            if (!gui.getParticleIcon().contents().name().getPath().equals(expected)) failures.add("Inventory icon replaced by geometry "+id);
        }
        var location=ResourceLocation.fromNamespaceAndPath("tnc","equipment/icon-integrity.json");
        try (var reader=mc.getResourceManager().getResource(location).orElseThrow().openAsReader()) {
            var hashes=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for (var e:hashes.entrySet()) try(var in=mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("tnc",e.getKey())).orElseThrow().open()) {
                var sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                if (!sha.equals(e.getValue().getAsString())) failures.add("Approved icon bytes changed: "+e.getKey());
            }
        }
    }

    private static final class Gallery extends Screen {
        private final Scene scene;
        Gallery(Scene scene){super(Component.literal(scene.label));this.scene=scene;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float dt) {
            g.fill(0,0,width,height,0xff18212b);g.drawCenteredString(font,"TN-C · "+scene.label+" · 原生穿戴检查",width/2,16,0xe4cfaa);
            if (scene.style.equals("icons")) {icons(g);return;}
            var p=Minecraft.getInstance().player;float by=p.yBodyRot,yr=p.getYRot(),xr=p.getXRot(),hy=p.yHeadRot,hyo=p.yHeadRotO;
            int panel=width/3;
            for(int i=0;i<3;i++) {
                int x=panel*i;g.fill(x+6,39,x+panel-6,height-25,0xff2b3844);
                p.yBodyRot=new float[]{165,90,0}[i];p.setYRot(p.yBodyRot);p.yHeadRot=p.yBodyRot;p.yHeadRotO=p.yBodyRot;p.setXRot(0);
                if(p.getVehicle() instanceof LivingEntity mount)mount.yBodyRot=p.yBodyRot;
                InventoryScreen.renderEntityInInventory(g,x+panel/2,height-53,Math.min(122,(height-100)/3),new Quaternionf().rotateZ((float)Math.PI),null,p);
                g.drawCenteredString(font,List.of("正面","侧面","背面").get(i),x+panel/2,height-40,0xd9e2e5);
            }
            p.yBodyRot=by;p.setYRot(yr);p.setXRot(xr);p.yHeadRot=hy;p.yHeadRotO=hyo;
        }
        private void icons(GuiGraphics g) {
            var items=new ArrayList<ItemStack>();ElementWands.ALL.forEach(d->items.add(ElementWands.stack(d)));items.add(new ItemStack(TNMod.WAND.get()));
            MageGear.ALL.forEach(d->items.add(MageGear.stack(d)));
            for(int i=0;i<items.size();i++) {
                int x=38+(i%10)*70,y=50+(i/10)*52;g.fill(x-3,y-3,x+37,y+37,0xff354450);
                g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(2,2,1);g.renderItem(items.get(i),0,0);g.pose().popPose();
            }
        }
    }
    private EquipmentNativeAudit(){}
}
