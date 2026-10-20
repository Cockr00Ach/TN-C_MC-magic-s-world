"""Make a separate complete runtime on the same drive; never write live saves/configs."""
from pathlib import Path
import argparse, datetime, json, os, shutil, subprocess
ROOT=Path(__file__).resolve().parents[1]

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--live',required=True);ap.add_argument('--destination',required=True);ap.add_argument('--jdk',default=os.environ.get('JAVA_HOME'));a=ap.parse_args()
    live=Path(a.live).resolve();dest=Path(a.destination).resolve()
    javac=str(Path(a.jdk)/'bin/javac.exe') if a.jdk else shutil.which('javac')
    jar_tool=str(Path(a.jdk)/'bin/jar.exe') if a.jdk else shutil.which('jar')
    assert javac and jar_tool, 'Set JAVA_HOME or supply --jdk for the offscreen test helper'
    helper=ROOT/'work/offscreen-agent';helper.mkdir(parents=True,exist_ok=True)
    subprocess.run([javac,'--release','17','-encoding','UTF-8','-d',str(helper),str(ROOT/'tools/OffscreenAuditAgent.java')],check=True)
    manifest=helper/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nPremain-Class: OffscreenAuditAgent\n\n','ascii')
    command=[jar_tool,'cfm',str(helper/'offscreen-audit.jar'),str(manifest)]
    for name in ['OffscreenAuditAgent.class','OffscreenAuditAgent$1.class','OffscreenAuditAgent$Reader.class']:command+=['-C',str(helper),name]
    subprocess.run(command,check=True)
    assert live!=dest and not dest.exists(), 'A fresh explicitly named audit directory is required'
    dest.mkdir(parents=True);(dest/'mods').mkdir()
    for src in (live/'mods').glob('*.jar'):
        out=dest/'mods'/src.name
        if src.name=='tnc-1.0.0.jar':shutil.copy2(ROOT/'build/libs/tnc-1.0.0.jar',out)
        else:os.link(src,out)  # Read-only input jars; Connector writes its own separate cache below.
    for name in ['config','defaultconfigs','kubejs']:
        if (live/name).is_dir():shutil.copytree(live/name,dest/name)
    if (live/'mods/.connector').is_dir():shutil.copytree(live/'mods/.connector',dest/'mods/.connector')
    for name in ['resourcepacks','shaderpacks']:
        if (live/name).is_dir():shutil.copytree(live/name,dest/name,copy_function=os.link)
    for name in ['options.txt','optionsof.txt','optionsshaders.txt']:
        if (live/name).is_file():shutil.copy2(live/name,dest/name)
    opts=dest/'options.txt'
    if opts.is_file():opts.write_text('\n'.join('fullscreen:false' if row.startswith('fullscreen:') else 'enableVsync:false' if row.startswith('enableVsync:') else row for row in opts.read_text('utf8').splitlines())+'\n','utf8')
    mca=dest/'config/mca.json'
    if mca.is_file():
        value=json.loads(mca.read_text('utf8'));value['enableGenderCheckForPlayers']=False
        mca.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n','utf8')
    save=dest/'saves/zero-forge';save.mkdir(parents=True)
    # Only level metadata from the disposable dev fixture, not any user world or island blocks.
    shutil.copy2(ROOT/'run-client-ecology-audit/saves/ecology-gui-feedback/level.dat',save/'level.dat')
    info={'prepared_at':datetime.datetime.now().isoformat(),'live':str(live),'audit_root':str(dest),'mods':len(list((dest/'mods').glob('*.jar'))),'world':'zero-forge','live_saves_modified':False}
    (ROOT/'work/zero-native-sandbox.json').write_text(json.dumps(info,ensure_ascii=False,indent=2)+'\n','utf8');print(json.dumps(info,ensure_ascii=False))
if __name__=='__main__':main()
