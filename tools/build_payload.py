#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, os, shutil, struct, subprocess, sys, tempfile, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
GAME_JAR_SHA256="e2d4dfdcb1fe7274078e5c92ce28a88996ea4e320797a908fa15d8b2500e6c56"
SP_SHA256="29d89f662798c8ce76d9a7fe4d664eb06a6a17b46886c0b01bd8436d3583c66d"
GAME_DEX_SHA256="e39d8316234adfb3b9239b37ee6f59dec16c964d27c3889b0b54491bb1ba221e"
MIDI_SHA256=[
    "d5531c2d876fe183c20d3b8bb0653577ffb21aca7fe1393a54e253c9ecbe3fa0",
    "6d91dffcaaa096c91fc712a0e42a8730ecb1b22764c863082b8e809749e85ff1",
    "c411f95a00b694c9872d514e081807556dcd33d395de17c0ccd1a3062e807670",
    "8daeed817ba0ff9ff37e382e700f186e4e4d247fe65ad1c2bc8996bdfabf545b",
]
SOUND_POSITIONS=[71730,74277,77432,78597]


def sha256(path:Path)->str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()

def ask_file(label:str)->Path:
    while True:
        value=input(label).strip().strip('"')
        p=Path(value).expanduser()
        if p.is_file(): return p.resolve()
        print("File not found. Try again.")

def find_sdk(explicit:str|None)->Path:
    candidates=[]
    if explicit: candidates.append(Path(explicit))
    for key in ('ANDROID_HOME','ANDROID_SDK_ROOT'):
        if os.environ.get(key): candidates.append(Path(os.environ[key]))
    for base in candidates:
        if (base/'build-tools/35.0.0/lib/d8.jar').is_file() and (base/'platforms/android-35/android.jar').is_file(): return base.resolve()
    print("\nAndroid SDK Platform 35 and Build-Tools 35.0.0 are required.")
    while True:
        p=Path(input("Paste your Android SDK folder: ").strip().strip('"')).expanduser()
        if (p/'build-tools/35.0.0/lib/d8.jar').is_file() and (p/'platforms/android-35/android.jar').is_file(): return p.resolve()
        print("That folder does not contain Platform 35 + Build-Tools 35.0.0.")

def extract_mld(sp:bytes,pos:int)->bytes:
    base=64 if len(sp)==84064 else 0
    off=base+pos
    if sp[off:off+4]!=b'melo': raise ValueError(f"Expected MLD at scratchpad position {pos}")
    if off+8>len(sp): raise ValueError("Truncated MLD header")
    size=struct.unpack('>I',sp[off+4:off+8])[0]+8
    end=off+size
    if end>len(sp): raise ValueError("Truncated MLD payload")
    return sp[off:end]

def run_d8(java:str,sdk:Path,jar:Path,out_dir:Path)->Path:
    bt=sdk/'build-tools/35.0.0'
    android_jar=sdk/'platforms/android-35/android.jar'
    cmd=[java,'-cp',str(bt/'lib/d8.jar'),'com.android.tools.r8.D8','--min-api','26','--lib',str(android_jar),'--output',str(out_dir),str(jar)]
    subprocess.run(cmd,check=True)
    dex=out_dir/'classes.dex'
    if not dex.is_file(): raise RuntimeError("D8 did not produce classes.dex")
    return dex

def write_zip(out:Path,files:list[tuple[str,Path]],manifest:dict):
    out.parent.mkdir(parents=True,exist_ok=True)
    stamp=(2026,10,4,0,0,0)
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in files:
            info=zipfile.ZipInfo(name,stamp); info.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(info,path.read_bytes())
        info=zipfile.ZipInfo('payload.json',stamp); info.compress_type=zipfile.ZIP_DEFLATED
        z.writestr(info,json.dumps(manifest,indent=2,sort_keys=True)+'\n')
        info=zipfile.ZipInfo('README_PAYLOAD.txt',stamp); info.compress_type=zipfile.ZIP_DEFLATED
        z.writestr(info,
            'DeadShot Bridge user payload\n\nCreated locally from user-supplied DeadShot data.\n'
            'This ZIP contains copyrighted game data and is for personal use. Do not upload it to the public repository.\n')

def main():
    ap=argparse.ArgumentParser(description='Build a DeadShot Bridge import ZIP from your own DeadShot JAR/SP files.')
    ap.add_argument('--jar',type=Path)
    ap.add_argument('--sp',type=Path)
    ap.add_argument('--sdk')
    ap.add_argument('--out',type=Path)
    args=ap.parse_args()
    jar=args.jar.resolve() if args.jar else ask_file('Paste the path to your DeadShot .jar: ')
    sp=args.sp.resolve() if args.sp else ask_file('Paste the path to your DeadShot .sp: ')
    if sha256(jar)!=GAME_JAR_SHA256: raise SystemExit('The selected JAR does not match the supported DeadShot game JAR.')
    if sha256(sp)!=SP_SHA256: raise SystemExit('The selected SP file does not match the supported DeadShot scratchpad package.')
    java=shutil.which('java')
    if not java: raise SystemExit('Java 17+ was not found on PATH.')
    sdk=find_sdk(args.sdk)
    out=args.out.resolve() if args.out else Path.cwd()/'DeadShot_Data_for_DeadShot_Bridge_v0.1.3.zip'
    sys.path.insert(0,str(ROOT/'tools'))
    from mld_to_midi import convert
    with tempfile.TemporaryDirectory(prefix='deadshot-payload-') as td:
        td=Path(td); dex_dir=td/'dex'; dex_dir.mkdir()
        dex=run_d8(java,sdk,jar,dex_dir)
        if sha256(dex)!=GAME_DEX_SHA256: raise SystemExit('Generated game.dex did not match the verified Build-Tools 35.0.0 result.')
        sp_data=sp.read_bytes(); mids=[]
        for i,pos in enumerate(SOUND_POSITIONS):
            mld=td/f'deadshot_{i}.mld'; midi=td/f'deadshot_{i}.mid'
            mld.write_bytes(extract_mld(sp_data,pos)); convert(mld,midi)
            if sha256(midi)!=MIDI_SHA256[i]: raise SystemExit(f'Converted MIDI {i} failed verification.')
            mids.append(midi)
        manifest={
            'format':'deadshot-bridge-payload-v1',
            'game_jar_sha256':GAME_JAR_SHA256,
            'game_dex_sha256':GAME_DEX_SHA256,
            'deadshot_sp_sha256':SP_SHA256,
            'midi_sha256':MIDI_SHA256,
            'build_tools':'35.0.0',
            'min_api':26,
        }
        files=[('game.dex',dex),('deadshot.sp',sp)]+[(f'deadshot_{i}.mid',m) for i,m in enumerate(mids)]
        write_zip(out,files,manifest)
    print('\nCreated:',out)
    print('This payload is personal game data. Import it into DeadShot Bridge; do not upload it to GitHub.')

if __name__=='__main__': main()
