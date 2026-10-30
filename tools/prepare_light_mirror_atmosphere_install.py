"""Scoped mirror/atmosphere install preparation; formal files are read-only here."""
import argparse, copy, hashlib, json, subprocess, zipfile
from pathlib import Path
from prepare_dungeon_mob_install import members

ROOT=Path(__file__).resolve().parents[1]
FAMILIES=["light/LightMirrorRules","light/LightMirrorItem","light/LightMirrorRegistration","light/LightMirrorSpells",
          "light/TNLightMirrorEntity","light/ApolloAcolyteEntity","light/client/LightMirrorClient","light/client/LightPrismModels",
          "light/TNLightBeamEntity","light/TNLightBeamMechanics","light/client/TNLightBeamRenderer",
          "world/ink/InkRealmClientEffects"]
ROOTS=["com/tnc/tnc/"+name for name in FAMILIES]
BEAMS=["light_beam","great_light_beam","giant_light_beam"]
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest().upper()
def selected(name):return any(name==p+".class" or name.startswith(p+"$") and name.endswith(".class") for p in ROOTS)
def dump(data):return (json.dumps(data,ensure_ascii=False,indent=2)+"\n").encode()

def prepare(live,built,out):
    out.mkdir(parents=True,exist_ok=False);base=live/"mods/tnc-1.0.0.jar";before=digest(base)
    asm=next((ROOT/".gradle-user/wrapper/dists").rglob("asm-9.7.jar"));java=ROOT/".jdk17/bin"
    subprocess.run([str(java/"javac.exe"),"-cp",str(asm),"-d",str(out),str(ROOT/"tools/WireLightMirrorInstall.java")],check=True)
    files=[];evidence={}
    with zipfile.ZipFile(base) as a,zipfile.ZipFile(built) as b:
        payload={n:b.read(n) for n in b.namelist() if selected(n)}
        for root in ROOTS:assert root+".class" in payload,root
        # Keep every installed enum ordinal, including the reserved LIGHT_MIRROR constant.
        chain="com/tnc/tnc/magic/SpellCatalog$Chain.class"
        old,_=members(a.read(chain));new,_=members(b.read(chain));desc="Lcom/tnc/tnc/magic/SpellCatalog$Chain;"
        assert [k for k in old if k[1]==desc]==[k for k in new if k[1]==desc],"Enum ordinals differ"
        for mode,name,changed in [("cast","magic/TnSpellMechanics",{"onSpellCast"}),
                                  ("catalog","magic/SpellCatalog",{"<clinit>"}),
                                  ("apollo","boss/TNApolloEntity",{"beginAcolyteSummon","m_8107_","startCast","applySpell","m_7380_","m_7378_"})]:
            n="com/tnc/tnc/"+name+".class";src=out/(mode+"-base.class");donor=out/(mode+"-donor.class");dest=out/(mode+"-patched.class")
            src.write_bytes(a.read(n));donor.write_bytes(b.read(n))
            subprocess.run([str(java/"java.exe"),"-cp",str(asm)+";"+str(out),"WireLightMirrorInstall",mode,str(src),str(donor),str(dest)],check=True)
            f,m=members(a.read(n));nf,nm=members(dest.read_bytes())
            assert all(nf[k]==v for k,v in f.items()),("Installed fields changed",n)
            assert all(nm[k]==v for k,v in m.items() if k[0] not in changed),("Unrelated methods changed",n)
            payload[n]=dest.read_bytes();evidence[n]="All unrelated installed methods and fields preserved"
        for n in b.namelist():
            if n.startswith(("data/tnc/spells/light_mirror_t","data/tnc/recipes/light_mirror_t","assets/tnc/models/item/light_mirror_t","assets/tnc/textures/spell/light_mirror_t")) or n=="assets/tnc/models/item/apollo_acolyte_spawn_egg.json":payload[n]=b.read(n)
        for spell in BEAMS:
            n="data/tnc/spells/"+spell+".json";old=json.loads(a.read(n));fresh=json.loads(b.read(n));old["cast"]["duration"]=0.0
            payload[n]=dump(old)
            assert fresh["cast"]["duration"]==0
        pool="data/tnc/spell_pools/tnc_light.json";p=json.loads(a.read(pool));p["spell_ids"]=list(dict.fromkeys(p["spell_ids"]+["tnc:light_mirror_t"+str(i) for i in range(1,6)]));payload[pool]=dump(p)
        for lang in ("zh_cn","en_us"):
            n="assets/tnc/lang/"+lang+".json";old=json.loads(a.read(n));fresh=json.loads(b.read(n));keys=[k for k in fresh if "light_mirror" in k or "apollo_acolyte" in k]
            old.update({k:fresh[k] for k in keys});payload[n]=dump(old)
        assert a.read("assets/tnc/textures/entity/beam_white.png")==b.read("assets/tnc/textures/entity/beam_white.png")
        output=out/"tnc-1.0.0.jar"
        with zipfile.ZipFile(output,"w",zipfile.ZIP_DEFLATED) as z:
            for info in a.infolist():z.writestr(copy.copy(info),payload.get(info.filename,a.read(info)))
            for n in sorted(payload.keys()-set(a.namelist())):z.writestr(n,payload[n])
        with zipfile.ZipFile(output) as z:
            assert z.testzip() is None
            unchanged=[n for n in a.namelist() if n not in payload]
            assert all(a.read(n)==z.read(n) for n in unchanged)
            assert not any(n.endswith(".class") and not n.startswith("com/tnc/tnc/") for n in z.namelist())
            for n,data in payload.items():assert z.read(n)==data
        files.append({"source":str(output),"relative":"mods/tnc-1.0.0.jar","hash":digest(output),"expectedLiveHash":before})
        # Any higher-priority KubeJS JSON receives the same narrow edits.
        for rel in ["kubejs/"+pool]+["kubejs/data/tnc/spells/"+s+".json" for s in BEAMS]:
            current=live/rel
            if not current.exists():continue
            data=json.loads(current.read_text(encoding="utf-8-sig"))
            if rel.endswith("tnc_light.json"):data["spell_ids"]=list(dict.fromkeys(data["spell_ids"]+["tnc:light_mirror_t"+str(i) for i in range(1,6)]))
            else:data["cast"]["duration"]=0.0
            target=out/("override-"+Path(rel).name);target.write_bytes(dump(data));files.append({"source":str(target),"relative":rel,"hash":digest(target),"expectedLiveHash":digest(current)})
    shader=ROOT/"exports/ComplementaryUnbound_r5.6.1-tnc-ink-clear-20261024.zip"
    report=json.loads(shader.with_suffix(".json").read_text(encoding="utf-8"));assert report["sha256"].upper()==digest(shader) and report["runtimeShaderCompilationVerified"]
    rel="shaderpacks/"+shader.name;assert not (live/rel).exists()
    files.append({"source":str(shader),"relative":rel,"hash":digest(shader),"expectedLiveHash":None})
    config=live/"config/oculus.properties";text=config.read_text(encoding="utf-8");assert "shaderPack=ComplementaryUnbound_r5.6.1\n" in text
    target=out/"oculus.properties";target.write_text(text.replace("shaderPack=ComplementaryUnbound_r5.6.1\n","shaderPack="+shader.name+"\n"),encoding="utf-8")
    files.append({"source":str(target),"relative":"config/oculus.properties","hash":digest(target),"expectedLiveHash":digest(config)})
    assert digest(base)==before,"Formal mod changed during preparation"
    manifest={"baseInstalledHash":before,"files":files,"patchEntries":sorted(payload),"unrelatedEntriesByteIdentical":len(unchanged),"sharedClassEvidence":evidence,"enumOrdinalsPreserved":True}
    (out/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
    print(json.dumps({"output":str(out),"files":len(files),"entries":len(payload),"unchangedEntries":len(unchanged),"sha256":digest(output)},indent=2))

if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--live",type=Path,required=True);p.add_argument("--built",type=Path,required=True);p.add_argument("--output",type=Path,required=True);a=p.parse_args();prepare(a.live.resolve(),a.built.resolve(),a.output.resolve())
