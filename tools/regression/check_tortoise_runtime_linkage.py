"""Check inherited calls in the final SRG package against the actual Minecraft runtime jar."""
import argparse, os, shutil, subprocess, tempfile
from pathlib import Path

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mod_jar',type=Path)
    parser.add_argument('minecraft_jar',type=Path)
    parser.add_argument('--runtime-lib',type=Path,action='append',default=[])
    parser.add_argument('--java-home',type=Path,default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--asm-classpath')
    args=parser.parse_args()
    if args.java_home: java=args.java_home/'bin'
    elif shutil.which('javac'): java=Path(shutil.which('javac')).resolve().parent
    else: parser.error('Set JAVA_HOME or pass --java-home with a Java 17 JDK.')
    here=Path(__file__).resolve().parent
    root=here.parents[1]
    asm=args.asm_classpath or os.pathsep.join(str(p) for package in ('asm','asm-tree')
            for p in (root/'.gradle-home/caches/modules-2/files-2.1/org.ow2.asm'/package).glob('*/*/*.jar'))
    if not asm: parser.error('Pass --asm-classpath or populate the local Gradle ASM cache.')
    with tempfile.TemporaryDirectory(prefix='tnc-runtime-linkage-') as stage:
        result=subprocess.run([str(java/'javac'),'-encoding','UTF-8','-proc:none','-cp',asm,'-d',stage,
                               str(here/'CheckTortoiseRemainsLinkage.java')])
        if result.returncode:return result.returncode
        return subprocess.run([str(java/'java'),'-cp',stage+os.pathsep+asm,'CheckTortoiseRemainsLinkage',
            str(args.mod_jar.resolve()),str(args.minecraft_jar.resolve()),*[str(p.resolve()) for p in args.runtime_lib]]).returncode

if __name__=='__main__':raise SystemExit(main())
