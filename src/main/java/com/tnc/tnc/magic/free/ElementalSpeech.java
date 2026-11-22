package com.tnc.tnc.magic.free;

import com.tnc.tnc.magic.Element;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Context-bounded Mandarin correction, using the same pinned reading source as water. */
public final class ElementalSpeech {
    private record Word(String spelling,String canonical,Element element){}
    private static final Map<Character,String> READINGS=readings();
    private static final List<Word> WORDS=words();
    private static List<Word> words(){
        List<Word> result=new ArrayList<>();
        for(var ability:ElementalIntent.Ability.values())for(String alias:ability.aliases)
            result.add(new Word(alias,ability.label,ability.element()));
        for(String word:List.of("巨大","急速","强化","持久","震荡","连续","齐发"))result.add(new Word(word,word,null));
        result.sort(Comparator.comparingInt((Word w)->w.spelling.length()).reversed());return List.copyOf(result);
    }
    public static String resolve(String text,String hint,Element context){
        if(text==null)text="";if(ElementalIntent.parse(text,context).isPresent()||hint==null||hint.isBlank())return text;
        var candidate=ElementalIntent.parse(hint,context);
        return candidate.isPresent()&&candidate.get().ability().element()==context?(text+" "+candidate.get().ability().label).strip():text;
    }
    public static String normalize(String raw,Element context){
        if(raw==null||raw.isEmpty())return "";
        var text=raw.replaceAll("\\s","");var output=new StringBuilder();
        for(int at=0;at<text.length();){
            Word exact=null,best=null;int bestCost=2;boolean ambiguous=false;
            for(Word word:WORDS){
                if(word.element!=null&&word.element!=context&&!ElementalIntent.explicit(word.spelling))continue;
                if(text.startsWith(word.spelling,at)){exact=word;break;}
            }
            int exactSize=exact==null?0:exact.spelling.length();
            for(Word word:WORDS){
                if(word.element!=null&&word.element!=context)continue;
                int n=word.spelling.length();
                if(n<=exactSize||at+n>text.length()||(best!=null&&n<best.spelling.length()))continue;
                int cost=distance(text,at,word.spelling);
                if(cost<bestCost){best=word;bestCost=cost;ambiguous=false;}
                else if(cost==bestCost&&best!=null&&!word.canonical.equals(best.canonical))ambiguous=true;
            }
            Word match=best!=null&&!ambiguous?best:exact;
            if(match==null)output.append(text.charAt(at++));
            else {output.append(match.canonical);at+=match.spelling.length();}
        }
        return output.toString();
    }
    private static int distance(String text,int start,String expected){
        int cost=0;
        for(int i=0;i<expected.length();i++){
            char actual=text.charAt(start+i),wanted=expected.charAt(i);
            if(actual==wanted)continue;
            String a=READINGS.get(actual),b=READINGS.get(wanted);
            if(a==null||b==null||!dialect(a).equals(dialect(b)))return 2;
            if(!a.equals(b)&&++cost>1)return 2;
        }
        return cost;
    }
    private static String dialect(String value){
        if(value.startsWith("zh")||value.startsWith("ch")||value.startsWith("sh"))value=value.charAt(0)+value.substring(2);
        if(value.endsWith("ang")||value.endsWith("eng")||value.endsWith("ing"))value=value.substring(0,value.length()-1);
        return value;
    }
    private static Map<Character,String> readings(){
        var input=ElementalSpeech.class.getResourceAsStream("/data/tnc/free_magic/elemental_speech_pinyin.tsv");
        if(input==null)throw new IllegalStateException("Missing elemental speech readings");
        var result=new HashMap<Character,String>();
        try(var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){
            reader.lines().filter(line->!line.isBlank()&&!line.startsWith("#")).forEach(line->{String[] columns=line.split("\\t");result.put(columns[0].charAt(0),columns[1]);});
        }catch(IOException failure){throw new UncheckedIOException(failure);}
        return Map.copyOf(result);
    }
    private ElementalSpeech(){}
}
