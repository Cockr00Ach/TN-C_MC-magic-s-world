"""Hold the exact world's session byte lock throughout backup and narrow manifest installation."""
import argparse,json,msvcrt,subprocess,shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
LIVE=Path(r'E:\download\正式版 2.12.6.1\.minecraft\versions\元素觉醒1.4.3-魔改版-20260915')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--manifest',type=Path,required=True);a=p.parse_args()
    manifest=json.loads(a.manifest.read_text(encoding='utf-8'));world=Path(manifest['recoveryWorld']).resolve()
    assert world==LIVE/'saves'/'新的世界 (15)','Unexpected world lock target'
    for file in manifest['files']:
        target=(LIVE/file['relative']).resolve();assert target.is_relative_to(LIVE)
        assert target==LIVE/'mods'/'tnc-1.0.0.jar' or target==world/'level.dat' or target.parent==world/'playerdata' and target.suffix=='.dat'
    with (world/'session.lock').open('r+b') as lock:
        lock.seek(0)
        try:msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,3)
        except OSError:raise SystemExit('World is still open; no files were changed. Exit the save first.')
        try:
            shell=shutil.which('pwsh.exe') or shutil.which('pwsh');assert shell,'PowerShell 7 is required for the UTF-8 installation script'
            subprocess.run([shell,'-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/install_light_mirror_atmosphere.ps1'),'-ManifestPath',str(a.manifest.resolve()),'-BackupPrefix','tnc-canglan-nan-animation-recovery'],check=True)
        finally:lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_UNLCK,3)
