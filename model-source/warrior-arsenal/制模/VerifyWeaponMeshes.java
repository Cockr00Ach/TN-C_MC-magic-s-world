import com.google.gson.JsonParser;
import com.google.common.collect.ImmutableMap;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.client.model.ExtendedBlockModelDeserializer;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.geometry.GeometryLoaderManager;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.obj.ObjLoader;
import net.minecraftforge.client.model.obj.ObjModel;
import net.minecraftforge.client.textures.UnitTextureAtlasSprite;
import sun.misc.Unsafe;

/** Parse and bake the exported geometry through Forge's real runtime code without opening a game. */
public final class VerifyWeaponMeshes {
    private static void installHeadlessClient() throws Exception {
        Field access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        Unsafe unsafe = (Unsafe) access.get(null);
        Field instance = Arrays.stream(Minecraft.class.getDeclaredFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == Minecraft.class)
            .findFirst().orElseThrow();
        unsafe.putObject(unsafe.staticFieldBase(instance), unsafe.staticFieldOffset(instance),
                         unsafe.allocateInstance(Minecraft.class));
    }

    public static void main(String[] args) throws Exception {
        try (ZipFile pack = new ZipFile(args[0]); ZipFile baseline = new ZipFile(args[1])) {
            installHeadlessClient();
            ResourceManager resources = (ResourceManager) Proxy.newProxyInstance(
                ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, input) -> {
                    if (!method.getName().equals("getResource")) throw new UnsupportedOperationException(method.getName());
                    ResourceLocation id = (ResourceLocation) input[0];
                    var entry = pack.getEntry("assets/" + id.getNamespace() + "/" + id.getPath());
                    return entry == null ? Optional.empty() : Optional.of(new Resource(null, () -> pack.getInputStream(entry)));
                });
            ObjLoader.INSTANCE.onResourceManagerReload(resources);
            Field loaders = GeometryLoaderManager.class.getDeclaredField("LOADERS");
            loaders.setAccessible(true);
            loaders.set(null, ImmutableMap.of(ResourceLocation.parse("forge:obj"), ObjLoader.INSTANCE));
            Method addQuads = ObjModel.class.getDeclaredMethod("addQuads", IGeometryBakingContext.class,
                IModelBuilder.class, ModelBaker.class, Function.class, ModelState.class, ResourceLocation.class);
            addQuads.setAccessible(true);
            int models = 0, quads = 0, peak = 0;
            for (var entry : Collections.list(pack.entries())) {
                String path = entry.getName();
                if (!path.startsWith("assets/tnc/models/item/") || !path.endsWith(".json")) continue;
                var newer = JsonParser.parseString(new String(pack.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!newer.has("loader") || !newer.get("loader").getAsString().equals("forge:obj")) continue;
                var old = JsonParser.parseString(new String(baseline.getInputStream(baseline.getEntry(path)).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : List.of("display","tnc_grip","elements","overrides"))
                    if (!Objects.equals(old.get(key),newer.get(key))) throw new AssertionError(path + " changed " + key);
                BlockModel model = ExtendedBlockModelDeserializer.INSTANCE.fromJson(newer, BlockModel.class);
                if (!(model.customData.getCustomGeometry() instanceof ObjModel obj)) throw new AssertionError(path);
                Function<Material, TextureAtlasSprite> sprites = material -> {
                    ResourceLocation texture = material.texture();
                    String tex = "assets/" + texture.getNamespace() + "/textures/" + texture.getPath() + ".png";
                    if (texture.getPath().startsWith("item/weapon_surfaces/") && pack.getEntry(tex) == null)
                        throw new AssertionError(tex);
                    return UnitTextureAtlasSprite.INSTANCE;
                };
                List<Object> baked = new ArrayList<>();
                IModelBuilder<?> collector = (IModelBuilder<?>) Proxy.newProxyInstance(
                    IModelBuilder.class.getClassLoader(), new Class<?>[]{IModelBuilder.class}, (proxy, method, input) -> {
                        if (!method.getName().equals("addUnculledFace")) throw new UnsupportedOperationException(method.getName());
                        baked.add(input[0]);
                        return proxy;
                    });
                addQuads.invoke(obj, model.customData, collector, null, sprites, BlockModelRotation.X0_Y0,
                    ResourceLocation.parse("tnc:" + path.substring("assets/tnc/models/".length(),path.length()-5)));
                int n = baked.size();
                if (n < 20 || n > 3000) throw new AssertionError(path + " unexpected quad count " + n);
                quads += n;
                peak = Math.max(peak,n);
                models++;
            }
            if (models != 59) throw new AssertionError("Baked " + models);
            System.out.println("Forge 47.4.22 baked " + models + " weapon/display states, " + quads + " quads, peak " + peak);
        }
    }
}
