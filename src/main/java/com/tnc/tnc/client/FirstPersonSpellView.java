package com.tnc.tnc.client;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.util.UUID;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class FirstPersonSpellView {
 private static SpellViewPolicy.Mode mode=load();
 private static SpellViewPolicy.Mode load(){try{return SpellViewPolicy.Mode.valueOf(java.nio.file.Files.readString(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("tnc-spell-view.txt")).trim());}catch(Exception e){return SpellViewPolicy.Mode.MODERATE;}}
 public static boolean own(UUID owner){var mc=Minecraft.getInstance();return owner!=null&&mc.player!=null&&owner.equals(mc.player.getUUID())&&mc.getCameraEntity()==mc.player&&mc.options.getCameraType().isFirstPerson()&&mode!=SpellViewPolicy.Mode.FULL;}
 public static boolean own(Entity owner){return owner!=null&&own(owner.getUUID());}
 public static double clearance(double radius){return SpellViewPolicy.clearance(mode,radius);}
 public static float opacity(UUID owner,net.minecraft.world.phys.Vec3 center,double radius){if(!own(owner))return 1;return SpellViewPolicy.opacity(mode,center.distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()),radius);}
 @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){var root=net.minecraft.commands.Commands.literal("tncspellview");for(var m:SpellViewPolicy.Mode.values())root.then(net.minecraft.commands.Commands.literal(m.name().toLowerCase(java.util.Locale.ROOT)).executes(c->{mode=m;try{java.nio.file.Files.writeString(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("tnc-spell-view.txt"),m.name());}catch(java.io.IOException ex){Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal("设置已生效，但未能保存到文件。"),false);}Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal("第一视角特效："+switch(m){case FULL->"完整";case MODERATE->"适中";case SIMPLE->"简洁";}),false);return 1;}));e.getDispatcher().register(root);}
}
