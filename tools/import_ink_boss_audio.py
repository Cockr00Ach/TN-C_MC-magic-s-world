"""Import the author's three full songs with measured, two-pass loudness normalization.

Derived OGG files remain local. Source recordings are never modified.
"""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
SONGS=[('crane','大漠行','*大漠行*.aac'),('tiger','火焰帝皇以骸 BOSS战','火焰帝皇*.aac'),
       ('dragon','骤雨狂风 · 基塔布鲁 BOSS战','*骤雨狂风*.aac')]

def run(command):
    result=subprocess.run(command,capture_output=True,encoding='utf-8',errors='replace')
    if result.returncode:raise RuntimeError(result.stderr[-4000:])
    return result.stderr

def measure(ffmpeg,path,target=-18):
    log=run([ffmpeg,'-hide_banner','-nostats','-i',str(path),'-af',f'loudnorm=I={target}:TP=-2:LRA=20:print_format=json','-f','null','-'])
    blocks=re.findall(r'\{\s*"input_i".*?\}',log,re.S)
    if not blocks:raise RuntimeError('No EBU R128 loudness result for '+str(path))
    return json.loads(blocks[-1])

def normalize(ffmpeg,source,target,channels=2,prefilter=None,lufs=-18):
    target.parent.mkdir(parents=True,exist_ok=True)
    # Measure the exact edited SFX, rather than normalizing its untrimmed source.
    staged=source
    if prefilter:
        staged=ROOT/'work/ink-audio'/f'{target.stem}-edited.wav'
        run([ffmpeg,'-y','-hide_banner','-i',str(source),'-af',prefilter,'-ac',str(channels),str(staged)])
    m=measure(ffmpeg,staged,lufs)
    filter=(f'loudnorm=I={lufs}:TP=-2:LRA=20:measured_I={m["input_i"]}:measured_TP={m["input_tp"]}:'
            f'measured_LRA={m["input_lra"]}:measured_thresh={m["input_thresh"]}:offset={m["target_offset"]}:linear=true')
    run([ffmpeg,'-y','-hide_banner','-i',str(staged),'-vn','-map_metadata','-1','-af',filter,
         '-ac',str(channels),'-ar','44100','-c:a','libvorbis','-q:a','5',str(target)])
    final=measure(ffmpeg,target,lufs)
    # Sub-three-second creature calls have unstable gated LUFS; songs must meet the strict target.
    assert abs(float(final['input_i'])-lufs)<(.5 if channels==2 else 2),(target,final)
    assert float(final['input_tp'])<=-1.0,(target,final)
    duration_log=run([ffmpeg,'-hide_banner','-i',str(target),'-f','null','-'])
    h,mi,s=re.search(r'Duration: (\d+):(\d+):([\d.]+)',duration_log).groups()
    return dict(source=source.name,sourceSHA256=hashlib.sha256(source.read_bytes()).hexdigest(),
                duration=3600*int(h)+60*int(mi)+float(s),inputLUFS=float(m['input_i']),
                outputLUFS=float(final['input_i']),truePeakDBTP=float(final['input_tp']),bytes=target.stat().st_size)

def main():
    p=argparse.ArgumentParser();p.add_argument('--ffmpeg',required=True);p.add_argument('--inbox',required=True,type=Path)
    p.add_argument('--roars',type=Path);a=p.parse_args();report={'targetLUFS':-18,'truePeakTargetDBTP':-2,'songs':[],'roars':[]}
    sounds=json.loads((ASSETS/'sounds.json').read_text(encoding='utf-8'))
    for phase,(slug,title,pattern) in enumerate(SONGS,1):
        sources=list(a.inbox.glob(pattern));assert len(sources)==1,(pattern,sources)
        entry=normalize(a.ffmpeg,sources[0],ASSETS/f'sounds/music/ink/{slug}.ogg')
        entry.update(phase=phase,title=title,event=f'tnc:music.ink.{slug}')
        report['songs'].append(entry)
        sounds[f'music.ink.{slug}']={'sounds':[{'name':f'tnc:music/ink/{slug}','stream':True}]}
        print(json.dumps(entry,ensure_ascii=True),flush=True)
    if a.roars:
        for slug,filename,edit in [
                ('crane','bird.flac','atrim=duration=3.2,asetpts=PTS-STARTPTS,highpass=f=160,asetrate=44100*0.89,aresample=44100,afade=t=in:d=0.015,afade=t=out:st=0.86:d=0.16'),
                ('tiger','tiger.mp3','atrim=start=2:duration=4.5,asetpts=PTS-STARTPTS,highpass=f=65,afade=t=out:st=4.1:d=0.35'),
                ('dragon','dragon.wav','atrim=duration=5,asetpts=PTS-STARTPTS,highpass=f=45,lowpass=f=5500,afade=t=out:st=4.5:d=0.4')]:
            source=a.roars/filename;assert source.is_file(),source
            entry=normalize(a.ffmpeg,source,ASSETS/f'sounds/ink/{slug}_cast.ogg',1,edit,-20)
            entry['event']=f'tnc:ink.{slug}.cast';report['roars'].append(entry)
            sounds[f'ink.{slug}.cast']={'subtitle':f'subtitles.tnc.ink.{slug}.cast','sounds':[{'name':f'tnc:ink/{slug}_cast'}]}
    (ASSETS/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (ASSETS/'ink_boss_audio.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (ROOT/'work/ink-audio/normalization-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':main()
