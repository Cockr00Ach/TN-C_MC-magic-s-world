import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.nio.charset.StandardCharsets;

/** Test JVM only: retain normal game rendering/event polling, omit hidden-window presentation. */
public final class OffscreenAuditAgent {
    public static void premain(String ignored,Instrumentation instrumentation){
        if(!Boolean.getBoolean("tnc.zeroAudit"))throw new IllegalStateException("Offscreen agent requires the isolated Zero audit flag");
        instrumentation.addTransformer(new ClassFileTransformer(){
            @Override public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes){
                if(!"com/mojang/blaze3d/systems/RenderSystem".equals(name))return null;
                try{return omitSwap(bytes);}catch(Exception ex){throw new IllegalStateException("Could not prepare offscreen audit",ex);}
            }
        });
    }
    private static final class Reader {
        final byte[] b;int pos=8;Reader(byte[] b){this.b=b;}
        int u1(){return b[pos++]&255;}int u2(){return u1()*256+u1();}int u4(){return (u2()<<16)|u2();}
        void skip(int n){pos+=n;}
    }
    private static byte[] omitSwap(byte[] input){
        byte[] output=input.clone();Reader r=new Reader(output);int count=r.u2();String[] text=new String[count];int[] tag=new int[count],a=new int[count],b=new int[count];
        for(int i=1;i<count;i++){
            tag[i]=r.u1();switch(tag[i]){
                case 1->{int n=r.u2();text[i]=new String(output,r.pos,n,StandardCharsets.UTF_8);r.skip(n);}
                case 3,4->r.skip(4);case 5,6->{r.skip(8);i++;}
                case 7,8,16,19,20->a[i]=r.u2();
                case 9,10,11,12,17,18->{a[i]=r.u2();b[i]=r.u2();}
                case 15->r.skip(3);default->throw new IllegalArgumentException("Unknown constant pool tag");
            }
        }
        int target=-1;
        for(int i=1;i<count;i++)if(tag[i]==10&&"org/lwjgl/glfw/GLFW".equals(text[a[a[i]]])&&"glfwSwapBuffers".equals(text[a[b[i]]])&&"(J)V".equals(text[b[b[i]]]))target=i;
        if(target<0)throw new IllegalStateException("Swap reference missing");
        r.skip(6);r.skip(r.u2()*2);
        for(int fields=r.u2();fields>0;fields--){r.skip(6);for(int attrs=r.u2();attrs>0;attrs--){r.skip(2);int size=r.u4();r.skip(size);}}
        int changed=0;
        for(int methods=r.u2();methods>0;methods--){
            r.skip(2);String name=text[r.u2()];r.skip(2);
            for(int attrs=r.u2();attrs>0;attrs--){String attr=text[r.u2()];int length=r.u4(),start=r.pos;
                if("flipFrame".equals(name)&&"Code".equals(attr)){
                    r.skip(4);int codeLength=r.u4();
                    for(int p=r.pos;p<r.pos+codeLength-2;p++)if((output[p]&255)==184&&(output[p+1]&255)==target/256&&(output[p+2]&255)==target%256){
                        // INVOKESTATIC (J)V -> POP2,NOP,NOP: same byte length and stack effect.
                        output[p]=88;output[p+1]=0;output[p+2]=0;changed++;
                    }
                }
                r.pos=start+length;
            }
        }
        if(changed!=1)throw new IllegalStateException("Expected exactly one flipFrame swap, got "+changed);
        System.err.println("TN-C isolated audit: offscreen presentation enabled");return output;
    }
}
