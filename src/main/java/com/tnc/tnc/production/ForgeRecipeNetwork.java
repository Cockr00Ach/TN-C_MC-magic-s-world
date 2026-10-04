package com.tnc.tnc.production;

import com.tnc.tnc.TNMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Separate channel ID keeps arbitrary datapack recipe counts out of vanilla's byte menu buttons. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeRecipeNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "forge_recipes"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private ForgeRecipeNetwork() {}

    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CHANNEL.messageBuilder(SelectRecipe.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectRecipe::encode).decoder(SelectRecipe::decode)
                .consumerMainThread(SelectRecipe::handle).add());
    }

    public static void select(int containerId, ResourceLocation recipeId) {
        CHANNEL.sendToServer(new SelectRecipe(containerId, recipeId));
    }

    private record SelectRecipe(int containerId, ResourceLocation recipeId) {
        void encode(FriendlyByteBuf buf) { buf.writeVarInt(containerId); buf.writeResourceLocation(recipeId); }
        static SelectRecipe decode(FriendlyByteBuf buf) {
            return new SelectRecipe(buf.readVarInt(), buf.readResourceLocation());
        }
        void handle(java.util.function.Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            var player = context.getSender();
            if (player != null) context.enqueueWork(() -> {
                if (player.containerMenu instanceof MagicForgeMenu menu && menu.containerId == containerId
                        && menu.stillValid(player)) menu.selectRecipe(player, recipeId);
            });
            context.setPacketHandled(true);
        }
    }
}
