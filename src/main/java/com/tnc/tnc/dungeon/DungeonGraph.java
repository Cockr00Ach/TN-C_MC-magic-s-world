package com.tnc.tnc.dungeon;

import java.util.*;

/** Seeded finite floor; rooms are real adjacent cells, never rerolled on door use. */
public final class DungeonGraph {
    public enum Kind { COMBAT,TREASURE,EVENT,REST,EXIT,GUARDIAN }
    public record Cell(int x,int z) { public int distance(Cell b){return Math.abs(x-b.x)+Math.abs(z-b.z);} }
    public record Room(int id,Cell cell,Kind kind,int layout,List<Integer> links) {}
    public final List<Room> rooms;
    private DungeonGraph(List<Room> rooms){this.rooms=List.copyOf(rooms);}
    public static DungeonGraph generate(long seed,int floor){
        var random=new Random(seed^(floor*0x9e3779b97f4a7c15L));
        var cells=new ArrayList<Cell>();var links=new ArrayList<List<Integer>>();cells.add(new Cell(0,0));links.add(new ArrayList<>());
        int[][] dirs={{1,0},{0,1},{-1,0},{0,-1}};
        while(cells.size()<8){
            var options=new ArrayList<int[]>();
            for(int i=0;i<cells.size();i++)if(links.get(i).size()<3)for(var dir:dirs){var c=new Cell(cells.get(i).x+dir[0],cells.get(i).z+dir[1]);if(Math.abs(c.x)>3||Math.abs(c.z)>3||cells.contains(c))continue;options.add(new int[]{i,c.x,c.z});}
            if(options.isEmpty())throw new IllegalStateException("No connected frontier");
            var pick=options.get(random.nextInt(options.size()));int id=cells.size();cells.add(new Cell(pick[1],pick[2]));links.add(new ArrayList<>());links.get(pick[0]).add(id);links.get(id).add(pick[0]);
        }
        var order=new ArrayList<Integer>();for(int i=1;i<8;i++)order.add(i);Collections.shuffle(order,random);
        Kind[] kinds=new Kind[8];Arrays.fill(kinds,Kind.COMBAT);kinds[order.get(0)]=Kind.TREASURE;kinds[order.get(1)]=Kind.TREASURE;
        if(floor%5==0){
            // A guardian is always a leaf: its expanded arena cannot be a corridor.
            int guard=order.stream().filter(i->links.get(i).size()==1).findFirst().orElseThrow();kinds[guard]=Kind.GUARDIAN;
            for(int i:order)if(i!=guard&&kinds[i]==Kind.COMBAT){kinds[i]=Kind.REST;break;}
            // If the leaf was a treasure, restore the exact two-treasure quota.
            while(Arrays.stream(kinds).filter(k->k==Kind.TREASURE).count()<2)for(int i:order)if(kinds[i]==Kind.COMBAT){kinds[i]=Kind.TREASURE;break;}
        }else{kinds[order.get(2)]=Kind.EXIT;if(random.nextInt(4)==0)kinds[order.get(3)]=Kind.EVENT;}
        var rooms=new ArrayList<Room>();for(int i=0;i<8;i++)rooms.add(new Room(i,cells.get(i),kinds[i],random.nextInt(6),List.copyOf(links.get(i))));return new DungeonGraph(rooms);
    }
    public boolean valid(){
        Set<Integer> seen=new HashSet<>();Deque<Integer> todo=new ArrayDeque<>();todo.add(0);
        while(!todo.isEmpty()){int i=todo.removeFirst();if(!seen.add(i))continue;var a=rooms.get(i);if(a.links.isEmpty()||a.links.size()>3)return false;for(int j:a.links){var b=rooms.get(j);if(a.cell.distance(b.cell)!=1||!b.links.contains(i))return false;todo.add(j);}}
        return seen.size()==8&&rooms.get(0).kind==Kind.COMBAT&&rooms.stream().filter(r->r.kind==Kind.EXIT||r.kind==Kind.GUARDIAN).count()==1;
    }
}
