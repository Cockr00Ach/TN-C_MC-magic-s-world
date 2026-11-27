import java.io.*;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.*;
import com.google.gson.*;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverride;

import net.minecraftforge.client.model.ExtendedBlockModelDeserializer;
import net.minecraftforge.client.model.SeparateTransformsModel;
import net.minecraftforge.client.model.geometry.GeometryLoaderManager;

/** Offline Forge 47.4.22 model decoding and parent resolution. Run through check_native.py. */
public final class NativePackCheck {
    private final Path pack;
    private final Path resources;
    private final Map<ResourceLocation, BlockModel> cache = new HashMap<>();
    private final Set<ResourceLocation> resolved = new HashSet<>();
    // In ModelBakery these two empty builtin models are identity markers for later GPU baking.
    // This headless check covers loading and inheritance only.
    private final BlockModel generatedMarker=BlockModel.fromString("{}");
    private final BlockModel entityMarker=BlockModel.fromString("{}");
    private int wrappers;
    private int resolvedPerspectives;
    private int modelFiles;

    NativePackCheck(Path pack, Path resources) { this.pack=pack; this.resources=resources; }

    private BlockModel load(ResourceLocation id) {
        if (cache.containsKey(id)) return cache.get(id);
        if (id.toString().equals("minecraft:builtin/generated")) return generatedMarker;
        if (id.toString().equals("minecraft:builtin/entity")) return entityMarker;
        String ref = "assets/"+id.getNamespace()+"/models/"+id.getPath()+".json";
        InputStream stream=null;
        try {
            if (Files.isRegularFile(pack.resolve(ref))) stream=Files.newInputStream(pack.resolve(ref));
            else if (Files.isRegularFile(resources.resolve(ref))) stream=Files.newInputStream(resources.resolve(ref));
            else stream=NativePackCheck.class.getClassLoader().getResourceAsStream(ref);
            if (stream==null) throw new IllegalStateException("Missing resource "+id);
            try (Reader reader=new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)) {
                JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
                BlockModel model=ExtendedBlockModelDeserializer.INSTANCE.fromJson(json,BlockModel.class);
                if(json.has("loader") && !(model.customData.getCustomGeometry() instanceof SeparateTransformsModel))
                    throw new IllegalStateException("Unloaded geometry loader "+id);
                model.name=id.toString();
                cache.put(id,model);
                return model;
            }
        } catch (Exception e) { throw new IllegalStateException("Decoding "+id,e); }
    }

    private void resolve(ResourceLocation id) {
        if (!resolved.add(id)) return;
        BlockModel model=load(id);
        model.resolveParents(this::load);
        for (ItemOverride override:model.getOverrides()) resolve(override.getModel());
        if (model.customData.getCustomGeometry() instanceof SeparateTransformsModel separate) {
            wrappers++;
            try {
                Field base=SeparateTransformsModel.class.getDeclaredField("baseModel");
                Field variants=SeparateTransformsModel.class.getDeclaredField("perspectives");
                base.setAccessible(true);variants.setAccessible(true);
                LinkedList<BlockModel> children=new LinkedList<>();
                children.add((BlockModel)base.get(separate));
                for(Object value:((Map<?,?>)variants.get(separate)).values()) children.add((BlockModel)value);
                for (BlockModel child:children) {
                    if (!child.isResolved()) throw new IllegalStateException("Unresolved child of "+id);
                    if (child.getRootModel()!=generatedMarker && child.getRootModel()!=entityMarker
                        && child.getElements().isEmpty()) throw new IllegalStateException("Empty perspective "+id);
                    resolvedPerspectives++;
                }
            } catch (ReflectiveOperationException e) {throw new AssertionError(e);}
        }
    }

    private void run() throws Exception {
        Path assets=pack.resolve("assets");
        try(var files=Files.walk(assets)) {
            for(Path file:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".json")).toList()){
                Path rel=assets.relativize(file);
                if(rel.getNameCount()<4||!rel.getName(1).toString().equals("models")) continue;
                String path=rel.subpath(2,rel.getNameCount()).toString().replace('\\','/');
                ResourceLocation id=ResourceLocation.fromNamespaceAndPath(rel.getName(0).toString(),path.substring(0,path.length()-5));
                resolve(id);modelFiles++;
            }
        }
        Set<String> roster=new HashSet<>();
        for(String tag:List.of("longswords","greatswords","spears","hammers","longbows")){
            Path list=resources.resolve("data/tnc/tags/items/"+tag+".json");
            try(Reader reader=Files.newBufferedReader(list)){
                for (JsonElement item:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values")){
                    if(!item.isJsonPrimitive()||!item.getAsString().startsWith("tnc:")) continue;
                    String shortId=item.getAsString().substring(4);
                    if(!Files.isRegularFile(pack.resolve("assets/tnc/models/item/"+shortId+".json")))
                        throw new AssertionError("Missing candidate for registered weapon "+shortId);
                    resolve(ResourceLocation.fromNamespaceAndPath("tnc","item/"+shortId));roster.add(shortId);
                }
            }
        }
        int bows=0;
        for (String bow:List.of("bow","moon_bow","storm_bow","ember_bow","laminated_horn_bow")) {
            ResourceLocation id=ResourceLocation.fromNamespaceAndPath("tnc","item/"+bow);
            BlockModel base=load(id);
            if (base.getOverrides().size()!=3) throw new AssertionError(bow+" missing its three native override states");
            Set<ResourceLocation> stages=new HashSet<>();
            float[] thresholds={0f,.65f,.9f};
            for(int index=0;index<3;index++) {
                ItemOverride override=base.getOverrides().get(index);
                Map<String,Float> predicates=new HashMap<>();
                override.getPredicates().forEach(p->predicates.put(p.getProperty().toString(),p.getValue()));
                if(!predicates.getOrDefault("minecraft:pulling",0f).equals(1f) ||
                    !predicates.getOrDefault("minecraft:pull",0f).equals(thresholds[index]))
                    throw new AssertionError(bow+" incorrect pull threshold in native stage "+index);
                resolve(override.getModel());stages.add(override.getModel());
            }
            if(stages.size()!=3) throw new AssertionError(bow+" duplicate pull stage");
            bows++;
        }
        System.out.println("Native Forge parse and resolve OK | weapon roots="+roster.size()+" | files="+modelFiles+" | dependency cache="+cache.size()+
            " | separate transforms="+wrappers+" | resolved base+perspectives="+resolvedPerspectives+" | 4-state bows="+bows);
    }

    public static void main(String[] args) throws Exception {
        // In the client this entry is registered by RegisterGeometryLoaders on its mod event bus.
        // There is no client lifecycle here. Install the real Forge loader and exercise its normal codec.
        Field loaders=GeometryLoaderManager.class.getDeclaredField("LOADERS");loaders.setAccessible(true);
        loaders.set(null,ImmutableMap.of(ResourceLocation.fromNamespaceAndPath("forge","separate_transforms"),SeparateTransformsModel.Loader.INSTANCE));
        new NativePackCheck(Path.of(args[0]),Path.of(args[1])).run();
    }
}
