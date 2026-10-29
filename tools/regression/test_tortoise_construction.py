"""Regression for the actual client spawn null-pointer during superclass initialization."""
from pathlib import Path
import subprocess,sys,os,json
sys.stdout.reconfigure(encoding='utf-8')
# Input methods are extracted unchanged; only the heavy Minecraft parents are stubbed.
import argparse,shutil,tempfile
parser=argparse.ArgumentParser(description='Check actual Boss methods during parent-constructor virtual calls (Mojmap classes).')
parser.add_argument('input',type=Path,help='Mojmap jar or compiled classes directory')
parser.add_argument('clock_dependency',type=Path,nargs='?',help='Mojmap dependency jar if the input patch lacks TortoiseDeathClock')
parser.add_argument('--java-home',type=Path,default=os.environ.get('JAVA_HOME'))
parser.add_argument('--asm-classpath',help='Override ASM and asm-tree jars, separated with the platform classpath separator')
parser.add_argument('--output-dir',type=Path)
args=parser.parse_args()
W=Path(__file__).resolve().parent
R=W.parents[1]
temporary=tempfile.TemporaryDirectory(prefix='tnc-spawn-regression-')
LOGS=args.output_dir.resolve() if args.output_dir else Path(temporary.name)
LOGS.mkdir(parents=True,exist_ok=True)
STAGE=LOGS/'classes';STAGE.mkdir(exist_ok=True)
if args.java_home:
    JAVA=args.java_home/'bin'
elif shutil.which('javac'):
    JAVA=Path(shutil.which('javac')).resolve().parent
else:
    parser.error('Set JAVA_HOME or pass --java-home with a Java 17 JDK.')
jar=args.input.resolve()
if jar.is_dir():
    import zipfile
    source_dir=jar;jar=LOGS/'input.jar'
    with zipfile.ZipFile(jar,'w') as archive:
        for name in ('VolcanicTortoiseEntity.class','TortoiseDeathClock.class'):
            relative='com/tnc/tnc/boss/fire/'+name
            archive.write(source_dir/relative,relative)
clock_jar=args.clock_dependency.resolve() if args.clock_dependency else jar
asm=args.asm_classpath or os.pathsep.join(str(p) for package in ('asm','asm-tree')
        for p in (R/'.gradle-home/caches/modules-2/files-2.1/org.ow2.asm'/package).glob('*/*/*.jar'))
if not asm:parser.error('ASM jars were not found; pass --asm-classpath.')
stubs={
'net/minecraft/network/syncher/EntityDataAccessor.java':'''package net.minecraft.network.syncher; public class EntityDataAccessor<T>{public T value;public EntityDataAccessor(T value){this.value=value;}}''',
'net/minecraft/network/syncher/SynchedEntityData.java':'''package net.minecraft.network.syncher; public class SynchedEntityData{public <T>T get(EntityDataAccessor<T> key){return key.value;}}''',
'net/minecraft/world/level/Level.java':'''package net.minecraft.world.level; public class Level{public final boolean isClientSide;public Level(boolean client){isClientSide=client;}}''',
'net/minecraft/world/entity/monster/Monster.java':'''package net.minecraft.world.entity.monster;
import net.minecraft.network.syncher.*; import net.minecraft.world.level.Level;
public class Monster{
 public SynchedEntityData entityData=new SynchedEntityData();private final Level level;
 public float health; public float constructorAge;public int tickCount;
 public Monster(boolean client){this.level=new Level(client);this.setHealth(900);this.probeDuringConstruction();}
 public Level level(){return level;} public void setHealth(float value){health=value;}
 public void onSyncedDataUpdated(EntityDataAccessor<?> key){} public void refreshDimensions(){}
 public void probeDuringConstruction(){}
}'''
}
for name,content in stubs.items():
    p=LOGS/'stubs'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8')
def invoke(args,log):
    task_env=os.environ.copy();task_env['JAVA_TOOL_OPTIONS']='-Dfile.encoding=UTF-8 -Duser.language=en'
    run=subprocess.run(list(map(str,args)),capture_output=True,env=task_env)
    output=run.stdout+run.stderr;(LOGS/log).write_bytes(output)
    decoded=output.decode('utf-8',errors='replace')
    if run.returncode==0 and 'AccessDeniedException' in decoded:
        print('Known JDK ZIP-close warning recorded in '+str(LOGS/log),flush=True)
    else: print(decoded,flush=True)
    if run.returncode:raise SystemExit(run.returncode)
invoke([JAVA/'javac.exe','-encoding','UTF-8','-proc:none','-cp',asm,'-d',STAGE,W/'ExtractSpawnMethods.java',*[LOGS/'stubs'/name for name in stubs]],'extract-compile.log')
invoke([JAVA/'java.exe','-cp',str(STAGE)+';'+asm,'ExtractSpawnMethods',jar,STAGE,clock_jar],'extract.log')
# javac's directory symbol lookup is unreliable under this sandbox, so zip the extracted symbols.
import zipfile
symbols=LOGS/'symbols.jar'
with zipfile.ZipFile(symbols,'w') as z:
    for p in STAGE.rglob('*.class'):z.write(p,p.relative_to(STAGE).as_posix())
invoke([JAVA/'javac.exe','-encoding','UTF-8','-proc:none','-cp',symbols,'-d',STAGE,W/'SpawnConstructorTest.java'],'test-compile.log')
invoke([JAVA/'java.exe','-cp',STAGE,'SpawnConstructorTest'],'run.log')
