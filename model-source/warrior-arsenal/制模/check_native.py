"""Run the real Forge model codec against pack assets without starting a game or touching its instance."""
import argparse
import os
import subprocess
import tempfile
from pathlib import Path

def classpath():
    for args in sorted((Path.home()/'.gradle/.tmp').glob('gradle-worker-classpath*'),key=lambda p:p.stat().st_mtime,reverse=True):
        for enc in ('gbk','utf8'):
            try:lines=args.read_bytes().decode(enc).splitlines()
            except UnicodeError:continue
            paths=next((s.strip('"').replace(chr(92)*2,chr(92)).split(';') for s in lines if ';' in s),[])
            if any('forge-1.20.1-47.4.22_mapped_official_1.20.1.jar' in s for s in paths) and all(Path(s).exists() for s in paths):
                return os.pathsep.join(paths)
    raise RuntimeError('Forge 1.20.1-47.4.22 mapped worker classpath was not found')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('repo',type=Path)
    args=parser.parse_args();here=Path(__file__).resolve().parent;cp=classpath()
    with tempfile.TemporaryDirectory(prefix='tnc-weapon-check-') as temp:
        subprocess.run(['javac','-proc:none','-encoding','UTF-8','-cp',cp,'-d',temp,str(here/'NativePackCheck.java')],cwd=temp,check=True)
        subprocess.run(['java','-Xmx1152m','-cp',temp+os.pathsep+cp,'NativePackCheck',str(here.parent/'资源包'),str(args.repo/'src/main/resources')],cwd=temp,check=True)

if __name__=='__main__':main()
