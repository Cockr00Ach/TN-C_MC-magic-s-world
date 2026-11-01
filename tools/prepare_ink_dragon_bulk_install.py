"""Install only lossless bulk vertex submission and ink sky height; preserve everything else."""
import argparse,copy,hashlib,json,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
FAMILIES=('com/tnc/tnc/world/ink/InkDragonVertexPacking',
          'com/tnc/tnc/world/ink/client/InkDragonBulkSubmit',
          'com/tnc/tnc/world/ink/client/InkDragonBufferedMesh',
          'com/tnc/tnc/world/ink/mixin/InkSkyHeightMixin')
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest().upper()
def selected(name):return any(name==root+'.class' or name.startswith(root+'$') and name.endswith('.class') for root in FAMILIES)
def prepare(live,built,out):
    baseline=json.loads((ROOT/'work/ink-dragon-bulk-20261024-native-report.json').read_text())
    visual=json.loads((ROOT/'work/ink-dragon-bulk-20261024-visual-report.json').read_text())
    sky=json.loads((ROOT/'work/ink-sky-height-20261024-native-report.json').read_text())
    assert not baseline['failures'] and not visual['failures'] and not sky['failures']
    assert visual['shadersActive'] and sky['shaderAndVanillaSkyChecked']
    assert baseline['structure']['originalVisibleTriangles']==152306
    assert baseline['bulk']['bulkVertices']>0
    assert all(p['bulkVertices']>0 and p['legacyVertices']==0 for p in baseline['phases'] if p['mode']=='bulk')
    assert baseline['horizon']<=baseline['groundY'] and baseline['clearColorScale']==1
    base=live/'mods/tnc-1.0.0.jar';before=digest(base);out.mkdir(parents=True,exist_ok=False)
    with zipfile.ZipFile(base) as a,zipfile.ZipFile(built) as b:
        # The optimized submitter must see exactly the same topology, LOD, renderer and editable mesh.
        protected=['com/tnc/tnc/world/ink/InkDragonTopology.class','com/tnc/tnc/world/ink/InkDragonMeshData.class',
                   'com/tnc/tnc/world/ink/InkDragonRenderBudget.class','com/tnc/tnc/world/ink/client/InkCelestialDragonRenderer.class',
                   'assets/tnc/meshes/ink_celestial_dragon.mesh']
        for name in protected:assert a.read(name)==b.read(name),('Protected renderer/asset differs',name)
        payload={n:b.read(n) for n in b.namelist() if selected(n)}
        assert len(payload)==6,list(payload)
        config='tnc.ink.mixins.json';settings=json.loads(a.read(config));clients=settings.setdefault('client',[])
        assert 'InkSkyHeightMixin' not in clients,'Sky fix already installed'
        clients.append('InkSkyHeightMixin');payload[config]=(json.dumps(settings,indent=2)+'\n').encode()
        output=out/'tnc-1.0.0.jar'
        with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
            for entry in a.infolist():z.writestr(copy.copy(entry),payload.get(entry.filename,a.read(entry.filename)))
            for name in sorted(payload.keys()-set(a.namelist())):z.writestr(name,payload[name])
        with zipfile.ZipFile(output) as z:
            assert z.testzip() is None
            unchanged=[n for n in a.namelist() if n not in payload]
            assert all(a.read(n)==z.read(n) for n in unchanged)
            for name,data in payload.items():assert z.read(name)==data
            for required in ['com/tnc/tnc/light/TNLightMirrorEntity.class','com/tnc/tnc/light/ApolloAcolyteEntity.class','com/tnc/tnc/world/ink/InkRealmClientEffects$FogEvents.class']:
                assert z.read(required)==a.read(required),'Previous installation regressed'
            assert not any(n.endswith('.class') and not n.startswith('com/tnc/tnc/') for n in z.namelist())
    assert digest(base)==before,'Formal jar changed during preparation'
    manifest={'baseInstalledHash':before,'files':[{'source':str(output),'relative':'mods/tnc-1.0.0.jar','hash':digest(output),'expectedLiveHash':before}],
              'patchEntries':sorted(payload),'unrelatedEntriesByteIdentical':len(unchanged),'meshAndAllVisibleDetailUnchanged':True,
              'previousMirrorAndAtmosphereInstallationPreserved':True,'nativeBaseline':baseline['phases']}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'entries':len(payload),'unchanged':len(unchanged),'hash':digest(output),'manifest':str(out/'manifest.json')},indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.live.resolve(),a.built.resolve(),a.output.resolve())
