package com.tnc.tnc.sanctum.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import static com.tnc.tnc.sanctum.SanctumTimings.*;
import com.tnc.tnc.world.ink.InkDragonVertexPacking;
import com.tnc.tnc.world.ink.client.InkDragonBulkSubmit;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

/** Indexed rigid geometry. Standard entity vertices carry UV, overlay, light and normals to the active shader. */
final class SanctumMesh {
    private static final Set<String> KEYS=Set.of("angel","bleeding","demon","gate","gargoyle","shade","angel_view","bleeding_view","demon_view","gate_view");
    private static final Map<String,CompletableFuture<SanctumMesh>> READING=new HashMap<>();
    private static final Map<String,SanctumMesh> READY=new HashMap<>();
    private static final Set<String> FAILED=new HashSet<>();
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    record Bone(String name,int parent,float x,float y,float z,List<Layer> layers) {}
    static final class Layer {
        final boolean blend;
        final float[] source, transformed;
        final float cx,cy,cz,radius;
        float[] clearance;
        float[][] cutVertices;
        final int[] corners;
        final Matrix4f previous=new Matrix4f();
        ByteBuffer packed;
        boolean fresh=true, dark;
        int packedLight=-1;
        Layer(boolean blend,float[] data,int[] triangles){
            this.blend=blend;source=data;transformed=new float[data.length/STRIDE*6];
            float minX=data[0],maxX=data[0],minY=data[1],maxY=data[1],minZ=data[2],maxZ=data[2];
            for(int i=STRIDE;i<data.length;i+=STRIDE){
                minX=Math.min(minX,data[i]);maxX=Math.max(maxX,data[i]);
                minY=Math.min(minY,data[i+1]);maxY=Math.max(maxY,data[i+1]);
                minZ=Math.min(minZ,data[i+2]);maxZ=Math.max(maxZ,data[i+2]);
            }
            cx=(maxX+minX)*.5F;cy=(maxY+minY)*.5F;cz=(maxZ+minZ)*.5F;
            float dx=maxX-cx,dy=maxY-cy,dz=maxZ-cz;
            radius=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            int[] result=new int[triangles.length/3*4];int size=0;
            for(int i=0;i<triangles.length;i+=3){
                int a=triangles[i],b=triangles[i+1],c=triangles[i+2];
                result[size++]=a;result[size++]=b;result[size++]=c;
                // NEW_ENTITY splits a quad on its 0→2 diagonal, preserving authored winding and UVs.
                if(i+5<triangles.length && triangles[i+3]==a && triangles[i+4]==c){
                    result[size++]=triangles[i+5];i+=3;
                }else result[size++]=c;
            }
            corners=Arrays.copyOf(result,size);
        }
    }
    private static final int STRIDE=10;
    private final ResourceLocation atlas;
    private final List<Bone> bones;
    private SanctumMesh(String texture,List<Bone> bones){
        atlas=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/sanctum/"+texture+".png");
        this.bones=bones;
    }
    static void request(String key){
        if(READY.containsKey(key)||FAILED.contains(key)||!(KEYS.contains(key)||SanctumFaceAnimation.isFrameKey(key)))return;
        var resources=Minecraft.getInstance().getResourceManager();
        READING.computeIfAbsent(key,k->CompletableFuture.supplyAsync(()->{
            var path=ResourceLocation.fromNamespaceAndPath("tnc","models/sanctum/"+k+".mesh.gz");
            try(var input=resources.open(path);var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(input)))){
                return read(in);
            }catch(IOException ex){throw new UncheckedIOException(ex);}
        },net.minecraft.Util.backgroundExecutor()));
    }
    static SanctumMesh get(String key){
        request(key);var f=READING.get(key);
        if(f!=null&&f.isDone()){
            READING.remove(key);
            try{READY.put(key,f.join());}
            catch(RuntimeException error){FAILED.add(key);LOG.error("Cannot load sanctuary sculpture {}",key,error);}
        }
        return READY.get(key);
    }
    private static String text(DataInputStream input)throws IOException{
        return new String(input.readNBytes(input.readUnsignedShort()),java.nio.charset.StandardCharsets.UTF_8);
    }
    private static SanctumMesh read(DataInputStream in)throws IOException{
        if(in.readInt()!=0x53434D32)throw new IOException("Invalid sculpture header");
        String atlas=text(in);int count=in.readUnsignedShort();List<Bone> parts=new ArrayList<>(count);
        for(int i=0;i<count;i++){
            String name=text(in);int parent=in.readShort();float x=in.readFloat(),y=in.readFloat(),z=in.readFloat();
            if(parent>=i)throw new IOException("Invalid bone parent");
            var layers=new ArrayList<Layer>();parts.add(new Bone(name,parent,x,y,z,layers));
            int materialCount=in.readUnsignedByte();
            for(int k=0;k<materialCount;k++){
                boolean blend=in.readBoolean();int vertices=in.readInt(),indices=in.readInt();
                if(vertices<1||vertices>1_000_000||indices<3||indices%3!=0||indices>3_000_000)throw new IOException("Invalid mesh size");
                float[] data=new float[vertices*STRIDE];
                for(int j=0;j<data.length;j+=STRIDE){
                    data[j]=in.readFloat();data[j+1]=in.readFloat();data[j+2]=in.readFloat();
                    data[j+3]=in.readUnsignedShort()/65535F;data[j+4]=in.readUnsignedShort()/65535F;
                    data[j+5]=in.readUnsignedByte()/255F;
                    data[j+6]=in.readByte()/127F;data[j+7]=in.readByte()/127F;data[j+8]=in.readByte()/127F;
                    data[j+9]=in.readUnsignedByte();
                }
                int[] corners=new int[indices];
                for(int j=0;j<indices;j++){
                    corners[j]=in.readInt();if(corners[j]<0||corners[j]>=vertices)throw new IOException("Invalid triangle index");
                }
                layers.add(new Layer(blend,data,corners));
            }
        }
        if(in.read()!=-1)throw new IOException("Unexpected sculpture suffix");
        return new SanctumMesh(atlas,parts);
    }
    void draw(Matrix4f base,MultiBufferSource buffers,SanctumPose pose,String subtree,int light){
        draw(base,buffers,pose,subtree,light,null);
    }
    void draw(Matrix4f base,MultiBufferSource buffers,SanctumPose pose,String subtree,int light,SanctumProjectedBlade.Strike lock){
        draw(base,buffers,pose,subtree,light,lock,null);
    }
    void draw(Matrix4f base,MultiBufferSource buffers,SanctumPose pose,String subtree,int light,SanctumProjectedBlade.Strike lock,SanctumDemonReach reach){
        draw(base,buffers,pose,subtree,light,lock,reach,null);
    }

    void draw(Matrix4f base,MultiBufferSource buffers,SanctumPose pose,String subtree,int light,
              SanctumProjectedBlade.Strike lock,SanctumDemonReach reach,SanctumRupture rupture) {
        var face = subtree == null && pose.action().equals("awaken")
                ? SanctumFaceAnimation.at(pose.t() * AWAKEN, this == READY.get("bleeding") || this == READY.get("angel"))
                : null;
        Matrix4f faceAnchor = null;
        var transforms=new Matrix4f[bones.size()];
        boolean[] visible=new boolean[bones.size()],selected=new boolean[bones.size()];
        for(int i=0;i<bones.size();i++){
            var b=bones.get(i);boolean root=b.parent()<0;
            visible[i]=(root||visible[b.parent()])&&pose.visible(b.name());
            selected[i]=subtree==null||b.name().equals(subtree)||(!root&&selected[b.parent()]);
            if(!visible[i])continue;
            Matrix4f parent=root?base:transforms[b.parent()];
            // The sculptor parented both feet under the gown. Let the upper body fold at the waist
            // while the skirt and feet retain the exact rest transform above the stone plinth.
            if(pose.action().equals("combo")&&b.name().equals("skirts")){
                var waist=bones.get(b.parent());
                parent=new Matrix4f(transforms[waist.parent()]).translate(waist.x(),waist.y(),waist.z());
            }
            transforms[i]=new Matrix4f(parent).translate(b.x(),b.y(),b.z());
            pose.animate(b.name(),transforms[i]);
            if(face != null && b.name().equals("head")) faceAnchor = transforms[i];
        }
        if(pose.form().equals("demon")&&!pose.handOnly())SanctumDemonMotion.feet(base,bones,transforms);
        Matrix4f extremity=reach==null?null:reach.solve(bones,transforms);
        if(pose.form().equals("demon")&&subtree==null&&!pose.handOnly())SanctumDemonMotion.guardClaws(base,bones,transforms);
        for(int pass=0;pass<2;pass++){
            boolean blend=pass==1;VertexConsumer consumer=null;
            for(int i=0;i<bones.size();i++){
                if(!visible[i]||!selected[i])continue;
                String bone = bones.get(i).name();
                if (faceAnchor != null && SanctumFaceAnimation.replaces(bone)) continue;
                SanctumMeshCut cut = rupture == null ? null : rupture.cut(bone, pose.handOnly(), transforms[i]);
                if (pose.action().equals("awaken") && bone.equals("head_tears")) {
                    float length = SanctumPose.ease(pose.t() * AWAKEN, TEARS_START, TEARS_END);
                    cut = SanctumMeshCut.tears(length);
                }
                for(var layer:bones.get(i).layers())if(layer.blend==blend){
                    if(consumer==null)consumer=buffers.getBuffer(blend?RenderType.entityTranslucent(atlas):RenderType.entityCutoutNoCull(atlas));
                    var near=reach!=null&&SanctumNearClaw.broad(bones.get(i).name())?reach.near():null;
                    if (cut == null) submit(consumer,layer,transforms[i],light,subtree!=null&&subtree.startsWith("wing_"),near);
                    else cut.draw(consumer, layer, transforms[i], light);
                }
            }
        }
        if(faceAnchor != null) face.draw(faceAnchor,buffers,new SanctumPose("awaken_face","",0,0,0,false),null,light);
        if(reach!=null)reach.edge(buffers,extremity);
        if(lock!=null || pose.form().equals("demon")){
            for(int i=0;i<bones.size();i++)if(visible[i]&&selected[i]){
                String name=bones.get(i).name();
                if(lock!=null&&(name.equals("hand_L")||name.equals("hand_R")))
                    SanctumProjectedBlade.draw(buffers,base,transforms[i],name.equals("hand_L")?-1:1,pose.t()*com.tnc.tnc.sanctum.SanctumTimings.COMBO,lock);
                if(rupture==null && pose.form().equals("demon")&&!(name.startsWith("hand_")&&reach!=null&&reach.near()!=null
                        &&transforms[i].getTranslation(new org.joml.Vector3f()).distanceSquared(reach.near().eye())<15*15))
                    SanctumRelics.embers(buffers,transforms[i],name);
            }
        }
    }
    private static void submit(VertexConsumer out,Layer layer,Matrix4f m,int light,boolean darkWing,SanctumDemonReach.Near near){
        float[] source=layer.source,result=layer.transformed;
        boolean moved=layer.fresh||!layer.previous.equals(m);
        if(moved){
            layer.previous.set(m);layer.fresh=false;
            var normal=m.normal(new Matrix3f());
            for(int a=0,b=0;a<source.length;a+=STRIDE,b+=6){
                float x=source[a],y=source[a+1],z=source[a+2];
                result[b]=m.m00()*x+m.m10()*y+m.m20()*z+m.m30();
                result[b+1]=m.m01()*x+m.m11()*y+m.m21()*z+m.m31();
                result[b+2]=m.m02()*x+m.m12()*y+m.m22()*z+m.m32();
                x=source[a+6];y=source[a+7];z=source[a+8];
                float nx=normal.m00()*x+normal.m10()*y+normal.m20()*z;
                float ny=normal.m01()*x+normal.m11()*y+normal.m21()*z;
                float nz=normal.m02()*x+normal.m12()*y+normal.m22()*z;
                float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                float inv=length>1e-6F?1/length:1;
                result[b+3]=nx*inv;result[b+4]=ny*inv;result[b+5]=nz*inv;
            }
        }
        if(near!=null&&SanctumNearClaw.clipped(out,layer,m,near,light,darkWing)){
            if(moved&&layer.packed!=null)layer.fresh=true;
            return;
        }
        var writer=InkDragonBulkSubmit.writer(out);
        float alpha=layer.blend?.67F:1F;
        if(writer!=null){
            boolean allocate=layer.packed==null;
            if(allocate)layer.packed=ByteBuffer.allocateDirect(layer.corners.length*InkDragonVertexPacking.STRIDE).order(ByteOrder.nativeOrder());
            boolean recolor=allocate||layer.packedLight!=light||layer.dark!=darkWing;
            if(allocate||moved||recolor){
                for(int j=0;j<layer.corners.length;j++){
                    int index=layer.corners[j],a=index*STRIDE,b=index*6,off=j*InkDragonVertexPacking.STRIDE;
                    var target=layer.packed;
                    if(allocate||moved){
                        target.putFloat(off,result[b]);target.putFloat(off+4,result[b+1]);target.putFloat(off+8,result[b+2]);
                        target.putInt(off+32,InkDragonVertexPacking.normal(result[b+3],result[b+4],result[b+5]));
                    }
                    if(allocate){
                        target.putFloat(off+16,source[a+3]);target.putFloat(off+20,source[a+4]);
                        target.putInt(off+24,OverlayTexture.NO_OVERLAY);
                    }
                    if(recolor){
                        int value=Math.round(source[a+5]*255);
                        int r=darkWing?Math.round(value*.69F):value,g=darkWing?Math.round(value*.37F):value,bv=darkWing?Math.round(value*.56F):value;
                        target.putInt(off+12,r|(g<<8)|(bv<<16)|(Math.round(alpha*255)<<24));
                        target.putInt(off+28,LightTexture.pack(Math.max((int)source[a+9],LightTexture.block(light)),LightTexture.sky(light)));
                    }
                }
                layer.packedLight=light;layer.dark=darkWing;
            }
            InkDragonBulkSubmit.push(writer,layer.packed,layer.corners.length);
            return;
        }
        // Some shader wrappers only implement vanilla VertexConsumer. Invalidate the cached packed positions
        // if such a pass moved this shared layer before a later pass switches back to the bulk writer.
        if(moved&&layer.packed!=null)layer.fresh=true;
        for(int index:layer.corners){
            int a=index*STRIDE,b=index*6;
            float value=source[a+5];int packed=LightTexture.pack(Math.max((int)source[a+9],LightTexture.block(light)),LightTexture.sky(light));
            out.vertex(result[b],result[b+1],result[b+2],darkWing?value*.69F:value,darkWing?value*.37F:value,
                    darkWing?value*.56F:value,alpha,source[a+3],source[a+4],OverlayTexture.NO_OVERLAY,packed,result[b+3],result[b+4],result[b+5]);
        }
    }
    static void forget(String key) {
        var task=READING.remove(key);
        if (task != null) task.cancel(false);
        READY.remove(key);
        FAILED.remove(key);
    }
    static void forgetFaceFrames() {
        var keys = new HashSet<>(READING.keySet());
        keys.addAll(READY.keySet());
        keys.addAll(FAILED);
        for (String key : keys) if (SanctumFaceAnimation.isFrameKey(key)) forget(key);
    }
    static void clear(){SanctumFaceAnimation.resourcesChanged();READING.clear();READY.clear();FAILED.clear();}
}
