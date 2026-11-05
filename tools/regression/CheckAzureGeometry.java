import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.jar.*;
import mod.azure.azurelib.loading.json.raw.Model;
import mod.azure.azurelib.loading.object.GeometryTree;
import mod.azure.azurelib.util.JsonUtil;
public class CheckAzureGeometry {
 public static void main(String[] args) throws Exception {
  int count=0, failed=0;
  try(JarFile jar=new JarFile(args[0])) {
   var entries=jar.entries();
   while(entries.hasMoreElements()) {
    var entry=entries.nextElement();
    if(!entry.getName().matches("assets/tnc/geo/.*\\.json")) continue;
    count++;
    try(var reader=new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
     var model=JsonUtil.GEO_GSON.fromJson(reader, Model.class);
     GeometryTree.fromModel(model);
    } catch(Throwable error) {
     failed++;
     System.out.println("FAIL "+entry.getName()+": "+error);
     for(int i=0;i<Math.min(8,error.getStackTrace().length);i++) System.out.println("  at "+error.getStackTrace()[i]);
    }
   }
  }
  System.out.println("Actual AzureLib model parser + GeometryTree: "+count+" resources, "+failed+" failures");
  if(count==0 || failed!=0) System.exit(1);
 }
}
