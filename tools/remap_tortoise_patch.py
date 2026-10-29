"""Remap a tortoise class patch with complete, current mod inheritance metadata.

Pass source symbol jars from oldest to newest. Input classes take final precedence.
Run regression/check_tortoise_runtime_linkage.py against the final packaged jar afterwards.
"""
import argparse, subprocess, zipfile
from pathlib import Path

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--mapping',type=Path,required=True)
    parser.add_argument('--renamer',type=Path,required=True)
    parser.add_argument('--symbols',type=Path,action='append',required=True)
    parser.add_argument('--lib',type=Path,action='append',default=[])
    parser.add_argument('--java',default='java')
    args=parser.parse_args()
    classes={}
    for source in [*args.symbols,args.input]:
        with zipfile.ZipFile(source) as jar:
            for name in jar.namelist():
                if name.startswith('com/tnc/tnc/') and name.endswith('.class'):classes[name]=jar.read(name)
    required='com/tnc/tnc/boss/fire/TortoiseRemainsEntity.class'
    if required not in classes:parser.error('Current Mojmap symbols must include TortoiseRemainsEntity; otherwise inherited calls cannot be remapped.')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    symbols=args.output.with_suffix('.symbols.jar')
    with zipfile.ZipFile(symbols,'w',zipfile.ZIP_DEFLATED) as jar:
        for name,data in classes.items():jar.writestr(name,data)
    command=[args.java,'-jar',str(args.renamer),'--input',str(args.input),'--output',str(args.output),
             '--names',str(args.mapping),'--disable-abstract-param','--lib',str(symbols)]
    for lib in args.lib:command+=['--lib',str(lib)]
    return subprocess.run(command).returncode

if __name__=='__main__':raise SystemExit(main())
