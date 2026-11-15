package com.tnc.tnc.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.equipment.MageGear;
import com.tnc.tnc.warrior.InstructorEntity;
import com.tnc.tnc.warrior.WarriorEntities;
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
    private static final boolean GRIP_AUDIT = Boolean.getBoolean("tnc.weaponGripAudit");
    private record Scene(String label, String wand, String style, int tier, boolean offhand, int fov, String pose, String npc) {
        Scene(String label, String wand, String style, int tier, boolean offhand, int fov, String pose) {
            this(label, wand, style, tier, offhand, fov, pose, null);
        }
        boolean firstPerson() { return npc == null && wand != null && style == null; }
        boolean outfit() { return style != null && !Set.of("icons", "plain", "npc").contains(style); }
    }
    private static final List<Scene> SCENES = scenes();
    private static final List<String> failures = Collections.synchronizedList(new ArrayList<>()), shots = new ArrayList<>();
    private static final Map<String,Object> gripResources = new LinkedHashMap<>();
    private static final List<Map<String,Object>> gripShots = new ArrayList<>();
    private static boolean started, saving;
    private static volatile boolean ready;
    private static int scene, ticks, startup, waiting;
    private static volatile int actorId = -1;
    private static InstructorEntity serverActor;
    private static net.minecraft.world.entity.decoration.ArmorStand seat;
    private static Path folder() { return Path.of(System.getProperty("tnc.dungeonAuditOutput")); }

    private static List<Scene> scenes() {
        var result = new ArrayList<Scene>();
        if (GRIP_AUDIT) {
            for (String id : List.of("magic_wand", "lightning_wand_5")) for (boolean offhand : new boolean[]{false,true}) {
                String hand = offhand ? "left" : "right";
                result.add(new Scene("grip-first-"+id+"-"+hand,id,null,5,offhand,70,"idle"));
                result.add(new Scene("grip-third-"+id+"-"+hand,id,"plain",5,offhand,70,"idle"));
            }
            result.add(new Scene("grip-third-qimei_staff-reference", "qimei_staff", "plain", 0, false, 70, "idle"));
            for (String kind : List.of("normal", "tutorial")) {
                var poses = kind.equals("normal") ? List.of("idle", "attack") : List.of("idle", "attack", "tutorial_thrust");
                for (String pose : poses) result.add(new Scene("grip-lia-"+kind+"-"+pose,null,"npc",0,false,70,pose,kind));
            }
            return List.copyOf(result);
        }
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
        if (!GRIP_AUDIT) { tickScenes(event); return; }
        try { tickScenes(event); }
        catch (Throwable error) {
            failures.add("Native grip audit exception in scene "+scene+": "+error);
            com.mojang.logging.LogUtils.getLogger().error("Native weapon grip audit failed",error);
            finish(Minecraft.getInstance());
        }
    }

    private static void tickScenes(TickEvent.ClientTickEvent event) throws Exception {
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
            if (GRIP_AUDIT) validateGripResources(mc); else validateBakedResources(mc);
            prepare(mc); return;
        }
        if (!ready || saving) return;
        var s=SCENES.get(scene);
        mc.player.getInventory().selected=0;
        if(GRIP_AUDIT)mc.player.setMainArm(HumanoidArm.RIGHT);
        if (!inventoryArrived(mc,s)) {
            if (GRIP_AUDIT && ++waiting>400) throw new IllegalStateException("Equipment or instructor did not reach client: "+s.label);
            return;
        }
        waiting=0;
        // F1 also disables ItemInHandRenderer; keep the real HUD enabled for hand shots.
        mc.options.hideGui=!s.firstPerson();
        if (s.firstPerson()) mc.setScreen(null);
        else if (!(mc.screen instanceof Gallery)) mc.setScreen(new Gallery(s));
        ++ticks;
        if (s.npc!=null && !s.pose.equals("idle") && ticks==(s.pose.equals("tutorial_thrust")?40:38)) {
            // Client-side trigger uses the real GeckoLib controller without network timing jitter.
            var actor = actor(mc);
            var controller = actor.getAnimatableInstanceCache().getManagerForId(actor.getId()).getAnimationControllers().get("action");
            if (controller==null) throw new IllegalStateException("Instructor action controller missing");
            controller.setTransitionLength(0);
            controller.setAnimationSpeed(s.pose.equals("tutorial_thrust")?.25:1);
            actor.playAnimation(s.pose);
        }
        if (ticks==45) {
            if(s.npc!=null && !s.pose.equals("idle")) {
                var actor=actor(mc);
                var controller=actor.getAnimatableInstanceCache().getManagerForId(actor.getId()).getAnimationControllers().get("action");
                if(controller==null||!controller.isPlayingTriggeredAnimation())failures.add("Instructor clip did not remain active at capture: "+s.label);
            }
            if (s.pose.equals("crouch") && !mc.player.isCrouching()) failures.add("Crouch input did not reach player: "+s.label);
            if (s.pose.equals("walk") && mc.player.walkAnimation.speed()<.05f) failures.add("Walk input did not reach player: "+s.label);
            if (s.pose.equals("ride") && !mc.player.isPassenger()) failures.add("Mounted pose did not reach player: "+s.label);
            saving=true; String name=String.format(Locale.ROOT,"%02d-%s.png",scene,s.label);
            Screenshot.grab(mc.gameDirectory,"equipment-"+name,mc.getMainRenderTarget(),message->{
                try {
                    Files.copy(mc.gameDirectory.toPath().resolve("screenshots/equipment-"+name),folder().resolve(name),StandardCopyOption.REPLACE_EXISTING);shots.add(name);
                    if (GRIP_AUDIT) gripShots.add(Map.of("file",name,"subject",s.npc==null?s.wand:"lia-"+s.npc,
                            "hand",s.offhand?"left":"right","pose",s.pose,"views",s.firstPerson()?List.of("first_person"):List.of("front","side","back"),
                            "renderer",s.npc==null?(s.firstPerson()?"ItemInHandRenderer":"PlayerRenderer"):"InstructorRenderer / GeoEntityRenderer",
                            "animation_speed",s.pose.equals("tutorial_thrust")?.25:1));
                }
                catch(Exception error){failures.add(error.toString());} finally {saving=false;}
            }); return;
        }
        if (ticks<50) return;
        if (++scene>=SCENES.size()) {
            finish(mc);return;
        }
        ticks=0; prepare(mc);
    }

    private static ItemStack wand(String id) {
        return switch (id) {
            case "magic_wand" -> new ItemStack(TNMod.WAND.get());
            case "qimei_staff" -> new ItemStack(TNMod.QIMEI_STAFF.get());
            default -> ElementWands.stack(ElementWands.find(id));
        };
    }
    private static boolean inventoryArrived(Minecraft mc,Scene s) {
        if (s.wand!=null && !(s.offhand?mc.player.getOffhandItem():mc.player.getMainHandItem()).is(wand(s.wand).getItem())) return false;
        if (s.npc!=null) {
            var actor=actor(mc);
            return actor!=null && actor.tutorialActor()==s.npc.equals("tutorial") && actor.getMainHandItem().is(s.npc.equals("tutorial")?TNMod.DRAGON_SWORD.get():Items.IRON_SWORD);
        }
        return !s.outfit() || mc.player.getItemBySlot(EquipmentSlot.CHEST).is(MageGear.ITEMS.get(s.style+"_outfit_"+s.tier).get());
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
            if(serverActor!=null){serverActor.discard();serverActor=null;}actorId=-1;
            if (scene==0) {
                if(GRIP_AUDIT)p.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
                for (var pos:BlockPos.betweenClosed(1010,219,1000,1055,228,1050))
                    level.setBlock(pos,pos.getY()==219?Blocks.STONE.defaultBlockState():pos.getY()==220?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            level.setDayTime(6000); level.setWeatherParameters(6000,0,false,false);
            p.setGameMode(GameType.CREATIVE); p.getAbilities().flying=false; p.onUpdateAbilities();
            p.teleportTo(level,1032.5,221,1030.5,180,0); p.setShiftKeyDown(false);p.getInventory().clearContent();p.getInventory().selected=0;
            if(GRIP_AUDIT)p.setMainArm(HumanoidArm.RIGHT);
            for (var slot:EquipmentSlot.values()) p.setItemSlot(slot,ItemStack.EMPTY);
            if (s.outfit()) {
                p.setItemSlot(EquipmentSlot.HEAD,MageGear.stack(MageGear.find(s.style+"_hat_"+s.tier)));
                p.setItemSlot(EquipmentSlot.CHEST,MageGear.stack(MageGear.find(s.style+"_outfit_"+s.tier)));
            }
            if (s.wand!=null) p.setItemInHand(s.offhand?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,wand(s.wand));
            if (s.npc!=null) {
                var actor=WarriorEntities.INSTRUCTOR.get().create(level);
                if(actor==null)throw new IllegalStateException("Instructor registry returned null");
                actor.setVariant(InstructorEntity.VARIANT_BLADE);actor.equipForRole();
                actor.setTutorialActor(s.npc.equals("tutorial"));
                if(s.npc.equals("tutorial")) {
                    actor.getPersistentData().putString(InstructorEntity.ROLE,"tutorial_actor");
                    actor.getPersistentData().putUUID(InstructorEntity.OWNER,p.getUUID());
                }
                actor.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(s.npc.equals("tutorial")?TNMod.DRAGON_SWORD.get():Items.IRON_SWORD));
                actor.setNoAi(true);actor.setNoGravity(true);actor.setInvulnerable(true);
                actor.moveTo(1034.5,221,1030.5,180,0);
                if(!level.addFreshEntity(actor))throw new IllegalStateException("Instructor fixture spawn failed");
                serverActor=actor;actorId=actor.getId();
            }
            if (s.pose.equals("ride")) {
                seat=new net.minecraft.world.entity.decoration.ArmorStand(level,1032.5,221,1030.5);
                seat.setNoGravity(true);seat.setInvisible(true);level.addFreshEntity(seat);p.startRiding(seat,true);
            }
            p.inventoryMenu.broadcastChanges(); ready=true;
        } catch(Throwable error){failures.add(error.toString());ready=true;}});
    }

    private static InstructorEntity actor(Minecraft mc) {
        return mc.level!=null && mc.level.getEntity(actorId) instanceof InstructorEntity actor ? actor:null;
    }

    private static void finish(Minecraft mc) throws Exception {
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(mc.gameDirectory.toPath().resolve("mods/tnc-1.0.0.jar"))));
        var report=new LinkedHashMap<String,Object>();report.put("jar_sha256",sha);report.put("failures",List.copyOf(failures));
        report.put("screenshots",shots);report.put("expected",SCENES.size());
        if(GRIP_AUDIT) {
            report.put("weapon_grip_audit",true);report.put("scene_details",gripShots);report.put("models",gripResources);
            report.put("wands",2);report.put("instructor_scenes",5);
            report.put("scope","Real full-pack ItemInHandRenderer and three inventory camera views of actual PlayerRenderer / InstructorRenderer entities. Tutorial thrust is slowed to 0.25 solely in the disposable audit controller for a readable frame; no player saves are used.");
        } else {
            report.put("wands",36);report.put("wearables",34);
            report.put("scope","Real full-pack first-person hand rendering; actual equipped local-player models in three inventory cameras. Walking/crouching use input-driven fixture poses, not a full combat playthrough.");
        }
        Files.writeString(folder().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        com.tnc.tnc.dungeon.client.WorldFeedbackNativeAudit.finished(mc);
    }

    private static void validateGripResources(Minecraft mc) {
        var models=mc.getModelManager();
        for(String id:List.of("magic_wand","lightning_wand_5")) {
            var stack=wand(id);var key=ForgeRegistries.ITEMS.getKey(stack.getItem());
            var model=models.getModel(new ModelResourceLocation(key,"inventory"));
            boolean missing=model==models.getMissingModel();var views=new LinkedHashMap<String,Object>();views.put("missing",missing);
            if(missing)failures.add("Missing grip wand model "+key);
            for(var context:List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                var held=model.applyTransform(context,new PoseStack(),context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
                int quads=held.getQuads(null,null,RandomSource.create(1)).size();views.put(context.name(),quads);
                if(held==models.getMissingModel()||quads<90)failures.add("Missing or flat grip model "+key+" / "+context);
            }
            gripResources.put(key.toString(),views);
        }
        for(var item:List.of(Items.IRON_SWORD,TNMod.DRAGON_SWORD.get())) {
            var key=ForgeRegistries.ITEMS.getKey(item);boolean missing=models.getModel(new ModelResourceLocation(key,"inventory"))==models.getMissingModel();
            gripResources.put(key.toString(),Map.of("missing",missing));if(missing)failures.add("Missing instructor weapon "+key);
        }
        var renderer=mc.getEntityRenderDispatcher().getRenderer(WarriorEntities.INSTRUCTOR.get().create(mc.level));
        gripResources.put("instructor_renderer",renderer.getClass().getName());
        if(!(renderer instanceof com.tnc.tnc.warrior.client.InstructorRenderer))failures.add("Instructor did not resolve to its real GeoEntityRenderer: "+renderer.getClass().getName());
        for(String path:List.of("geo/entity/instructor.geo.json","animations/entity/instructor.animation.json","animations/entity/instructor_tutorial.animation.json","textures/entity/instructor_blade.png")) {
            boolean missing=mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("tnc",path)).isEmpty();
            gripResources.put(path,Map.of("missing",missing));if(missing)failures.add("Missing instructor resource "+path);
        }
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
            try { renderGallery(g,mx,my,dt); }
            catch(Throwable error) {
                if(!GRIP_AUDIT)throw error;
                String message="Gallery render exception "+scene.label+": "+error;
                if(!failures.contains(message)){failures.add(message);com.mojang.logging.LogUtils.getLogger().error(message,error);}
                g.drawString(font,Component.literal(message),12,42,0xffff6666,false);
            }
        }
        private void renderGallery(GuiGraphics g,int mx,int my,float dt) {
            g.fill(0,0,width,height,0xff18212b);g.drawCenteredString(font,"TN-C · "+scene.label+" · "+(GRIP_AUDIT?"原生握持检查":"原生穿戴检查"),width/2,16,0xe4cfaa);
            if (scene.style.equals("icons")) {icons(g);return;}
            LivingEntity p=scene.npc==null?Minecraft.getInstance().player:actor(Minecraft.getInstance());
            if(p==null)throw new IllegalStateException("Gallery entity not loaded: "+scene.label);
            float by=p.yBodyRot,yr=p.getYRot(),xr=p.getXRot(),hy=p.yHeadRot,hyo=p.yHeadRotO;
            int panel=width/3;
            try {
                for(int i=0;i<3;i++) {
                    int x=panel*i;g.fill(x+6,39,x+panel-6,height-25,0xff2b3844);
                    p.yBodyRot=new float[]{GRIP_AUDIT?180:165,90,0}[i];p.setYRot(p.yBodyRot);p.yHeadRot=p.yBodyRot;p.yHeadRotO=p.yBodyRot;p.setXRot(0);
                    if(p.getVehicle() instanceof LivingEntity mount)mount.yBodyRot=p.yBodyRot;
                    InventoryScreen.renderEntityInInventory(g,x+panel/2,height-53,Math.min(GRIP_AUDIT?105:122,(height-100)/3),new Quaternionf().rotateZ((float)Math.PI),null,p);
                    g.drawCenteredString(font,List.of("正面","侧面","背面").get(i),x+panel/2,height-40,0xd9e2e5);
                }
            } finally {
                p.yBodyRot=by;p.setYRot(yr);p.setXRot(xr);p.yHeadRot=hy;p.yHeadRotO=hyo;
            }
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
