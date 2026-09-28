import java.nio.file.*;
import java.util.*;

/** Native reading chapters have no rewards; the older story validator requires rewards. */
public final class FtbGuideCheck {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        Map<String,Object> group=(Map<String,Object>)SnbtCheck.parse(Files.readString(root.resolve("group.snbt")));
        Set<String> ids=new HashSet<>();ids.add((String)group.get("id"));
        int chapters=0,nodes=0;
        try(var paths=Files.list(root.resolve("chapters"))) {
            for(Path file:paths.filter(p->p.toString().endsWith(".snbt")).sorted().toList()) {
                Map<String,Object> chapter=(Map<String,Object>)SnbtCheck.parse(Files.readString(file));
                require(Objects.equals(group.get("id"),chapter.get("group")),"group reference");
                add(ids,chapter.get("id"));
                require(file.getFileName().toString().equals(chapter.get("filename")+".snbt"),"filename");
                for(Object value:(List<?>)chapter.get("quests")) {
                    Map<String,Object> q=(Map<String,Object>)value;add(ids,q.get("id"));nodes++;
                    require(q.get("description") instanceof List<?> d&&!d.isEmpty(),"description");
                    require(((List<?>)q.get("dependencies")).isEmpty(),"no reading lock");
                    require(((List<?>)q.get("rewards")).isEmpty(),"no fake gameplay reward");
                    for(Object item:(List<?>)q.get("tasks")) {
                        Map<String,Object> task=(Map<String,Object>)item;add(ids,task.get("id"));
                        require("checkmark".equals(task.get("type")),"reading type");
                        require(String.valueOf(task.get("title")).contains("阅读"),"reading-only label");
                    }
                }
                chapters++;
            }
        }
        require(chapters==9,"nine chapters");
        if(args.length>1) {
            var merged=(Map<String,Object>)SnbtCheck.parse(Files.readString(Path.of(args[1])));
            long found=((List<?>)merged.get("chapter_groups")).stream().filter(g->
                    Objects.equals(((Map<?,?>)g).get("id"),group.get("id"))).count();
            require(found==1,"one installed native guide group");
        }
        System.out.println("PASS: "+chapters+" native chapters, "+nodes+" reading nodes, "+ids.size()+" unique stable IDs.");
    }
    private static void add(Set<String> ids,Object id) {
        require(id instanceof String s&&s.matches("[0-9A-F]{16}")&&ids.add(s),"duplicate/invalid ID: "+id);
    }
    private static void require(boolean pass,String message) {if(!pass)throw new IllegalStateException(message);}
}
