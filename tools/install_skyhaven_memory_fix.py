"""Apply measured model memory fix, preserving unrelated config and all saves."""
import argparse,datetime,json,re,shutil,struct,xml.etree.ElementTree as ET
from pathlib import Path
from install_skyhaven_startup import ROOT,sha
from install_life_routes import running
KEY='mixin.perf.dynamic_resources'
def enable(text):
    lines=text.splitlines();out=[];seen=False
    for line in lines:
        if re.match(r'^\s*mixin\.perf\.dynamic_resources\s*=',line):
            if not seen:out.append(KEY+'=true');seen=True
        else:out.append(line)
    if not seen:out+=['','# TN-C: measured full-pack model-memory mitigation.',KEY+'=true']
    return '\n'.join(out)+'\n'
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--live',required=True);parser.add_argument('--apply',action='store_true');args=parser.parse_args();live=Path(args.live)
    jar=ROOT/'build/libs/tnc-1.0.0.jar';digest=sha(jar);folder=ROOT/'work/skyhaven-lag-real-dynamic'
    launch=json.loads((folder/'launch.json').read_text('utf-8-sig'));report=json.loads((folder/'walk-report.json').read_text('utf-8'))
    assert launch['jar_sha256']==digest and launch['dynamic_resources'] and launch['max_heap_mb']==7680
    assert len(report)==6 and all(r['framebuffer_width']==1600 and r['framebuffer_height']==900 for r in report)
    base=ROOT/'work/skyhaven-lag-real-baseline';base_launch=json.loads((base/'launch.json').read_text('utf-8-sig'));base_report=json.loads((base/'walk-report.json').read_text('utf-8'))
    assert base_launch['jar_sha256']==digest and not base_launch['dynamic_resources'] and base_launch['max_heap_mb']==7680
    assert len(base_report)==6 and all(r['framebuffer_width']==1600 and r['framebuffer_height']==900 for r in base_report)
    assert sum(r['gc_ms'] for r in report)<sum(r['gc_ms'] for r in base_report)*.25
    assert sum(r['over_200ms'] for r in report)<sum(r['over_200ms'] for r in base_report)*.25
    assert not (folder/'failed.txt').exists()
    visual=ROOT/'work/skyhaven-memory-visual';visual_launch=json.loads((visual/'launch.json').read_text('utf-8-sig'));visual_report=json.loads((visual/'report.json').read_text('utf-8'))
    assert visual_launch['jar_sha256']==digest and visual_launch['dynamic_resources'] and visual_report['portal_round_trip']
    assert len(visual_report['native_screenshots'])==9
    for index,name in enumerate(visual_report['native_screenshots']):
        data=(visual/(str(index)+'-'+name+'.png')).read_bytes();assert struct.unpack('>II',data[16:24])==(1600,900)

    assert all(r['p95_ms']<50 and r['p99_ms']<150 for r in report),'Walk still has sustained stutter'
    for r in report:
        image=folder/(r['site']+'-walk.png');data=image.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n';assert struct.unpack('>II',data[16:24])==(1600,900)
    assert 'BUILD SUCCESSFUL' in (ROOT/'work/skyhaven-lag-build.log').read_text('utf-8',errors='replace')
    tests=0
    for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
        r=ET.parse(p).getroot();assert int(r.get('failures'))==int(r.get('errors'))==0;tests+=int(r.get('tests'))
    assert tests>=562 and not running(live)
    receipt=dict(jar_sha256=digest,unit_tests=tests,route=report,production_saves_changed=False)
    if args.apply:
        backup=ROOT/'work/backups'/('skyhaven-memory-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
        for label,pack in [('live',live),('mirror',ROOT/'modpack'/live.name)]:
            dest=pack/'mods/tnc-1.0.0.jar';saved=backup/label;saved.mkdir();shutil.copy2(dest,saved/'tnc-before.jar')
            cfg=pack/'config/modernfix-mixins.properties'
            if cfg.exists():shutil.copy2(cfg,saved/'modernfix-before.properties')
            cfg.parent.mkdir(parents=True,exist_ok=True);cfg.write_text(enable(cfg.read_text('utf-8-sig') if cfg.exists() else ''),'utf-8')
            temp=dest.with_suffix('.memory-new');shutil.copy2(jar,temp);assert sha(temp)==digest;temp.replace(dest)
        receipt['backup']=str(backup);(ROOT/'work/skyhaven-lag-install.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),'utf-8')
    print(json.dumps(receipt,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
