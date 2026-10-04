#!/usr/bin/env python3
"""Host checks for the final public-data DeadShot Bridge release. Phone validation is recorded separately."""
from pathlib import Path
from hashlib import sha256
import json, os, struct, subprocess, tempfile, xml.etree.ElementTree as ET, zipfile

ROOT=Path(__file__).resolve().parents[1]
ANDROID='{http://schemas.android.com/apk/res/android}'
FORBIDDEN_PUBLIC_NAMES={'deadshot.jar','deadshot.jam','deadshot.sp','game.dex','deadshot_0.mid','deadshot_1.mid','deadshot_2.mid','deadshot_3.mid'}
GAME_DESCRIPTORS={'LDEADSHOTN505;','LInitAll;','Lconnect;'}

def digest(p): return sha256(Path(p).read_bytes()).hexdigest()

def dex_classes(data):
    def u32(offset): return struct.unpack_from('<I',data,offset)[0]
    strings=[]
    for i in range(u32(56)):
        offset=u32(u32(60)+i*4)
        while data[offset]&128: offset+=1
        offset+=1; end=data.index(0,offset)
        strings.append(data[offset:end].decode('utf-8','replace'))
    types=[strings[u32(u32(68)+i*4)] for i in range(u32(64))]
    return {types[u32(u32(100)+i*32)] for i in range(u32(96))}

def main():
    java=os.environ.get('JAVA','java')
    with tempfile.TemporaryDirectory(prefix='deadshot-public-checks-') as t:
        files=['shared/src/com/wakka/bridge/InputLatch.java','shared/src/com/wakka/bridge/SessionGate.java',
               'profiles/deadshot/src/com/wakka/deadshot/NativeEventRouter.java',
               'runtime/deadshot/src/com/wakka/deadshot/DeadshotDiagnostics.java','tests/BehaviorChecks.java']
        subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-d',t,*[str(ROOT/f) for f in files]],check=True)
        subprocess.run([java,'-cp',t,'BehaviorChecks'],check=True)

    project=json.loads((ROOT/'bridge-project.json').read_text())
    manifest=ET.parse(ROOT/'app/AndroidManifest.xml').getroot()
    assert project['package']=='com.wakka.omnibridge.deadshot'
    assert project['version']=='0.1.3'
    assert manifest.attrib['package']==project['package']
    assert manifest.attrib[ANDROID+'versionName']==project['version']
    assert manifest.attrib[ANDROID+'versionCode']=='5'
    app=manifest.find('application'); assert app.attrib[ANDROID+'allowBackup']=='false'
    assert not manifest.findall('uses-permission')

    # Public tree must not carry original game data. The local signing folder is intentionally
    # allowed during private release construction; it is stripped from the exported public package.
    for p in ROOT.rglob('*'):
        if not p.is_file(): continue
        rel=p.relative_to(ROOT).as_posix()
        if rel.startswith(('build/','dist/','signing/')): continue
        assert p.name.lower() not in FORBIDDEN_PUBLIC_NAMES, 'Game data present in public source tree: '+rel
        assert p.suffix.lower() not in {'.jks','.keystore','.p12','.pem'}, 'Private key-like file present: '+rel

    gameview=(ROOT/'profiles/deadshot/src/com/wakka/deadshot/GameView.java').read_text()
    assert 'float navSize=Math.min(164f,Math.max(154f,logicalW*0.455f));' in gameview
    assert 'float actionSize=130f;' in gameview and 'float actionRightInset=12f;' in gameview
    assert 'return (mode==1||mode==3)?KEY_SELECT:KEY_POUND;' in gameview
    assert 'return controlModeValue()>=2?"DIR LOCK":"ATTACK";' in gameview
    assert 'PayloadStore.gameClass(getContext(),"InitAll")' in gameview
    assert 'drawDirgeBox(c,starRect' not in gameview and 'drawDirgeBox(c,poundRect' not in gameview

    backend=(ROOT/'profiles/deadshot/src/com/wakka/deadshot/DeadshotBackend.java').read_text()
    assert 'implements BridgeBackend,BridgeImportBackend,Host.Events' in backend
    assert 'PayloadStore.gameClass(app,"DEADSHOTN505")' in backend
    assert 'public boolean ready() { return PayloadStore.ready(app); }' in backend
    payload=(ROOT/'profiles/deadshot/src/com/wakka/deadshot/PayloadStore.java').read_text()
    for h in ['e39d8316234adfb3b9239b37ee6f59dec16c964d27c3889b0b54491bb1ba221e',
              '29d89f662798c8ce76d9a7fe4d664eb06a6a17b46886c0b01bd8436d3583c66d']:
        assert h in payload
    assert 'DexClassLoader' in payload and 'getCodeCacheDir()' in payload
    assert 'if("game.dex".equals(name) && !out.setReadOnly())' in payload
    assert 'if(!dex.setReadOnly()) throw new IOException("Could not mark game.dex read-only before loading.")' in payload

    apk=ROOT/'dist'/project['apk_name']; assert apk.is_file()
    with zipfile.ZipFile(apk) as z:
        assert z.testzip() is None
        names=set(z.namelist())
        assert not any(n.endswith(('deadshot.sp','game.dex','.jar','.jam')) for n in names)
        assert not any('/deadshot_' in n and n.endswith('.mid') for n in names)
        build=json.loads(z.read('assets/bridge-build.json'))
        assert build['inputs'].get('payloads')=={}
        classes=set()
        for name in names:
            if name.endswith('.dex'): classes.update(dex_classes(z.read(name)))
            assert not any(secret in name.lower() for secret in ['keystore','prototype-identity','store_password'])
    assert not (GAME_DESCRIPTORS & classes), 'Original game classes embedded in APK: '+str(GAME_DESCRIPTORS & classes)
    required={'Lcom/wakka/deadshot/PayloadStore;','Lcom/wakka/bridge/BridgeImportBackend;',
              'Lcom/wakka/deadshot/DeadshotBackend;','Lcom/wakka/omnideadshot/LauncherActivity;'}
    assert required<=classes, required-classes

    print('PASS: behavioral helper checks')
    print('PASS: public source tree excludes original game payloads and key-like files outside private build staging')
    print('PASS: accepted bespoke DeadShot control geometry/mapping remains present')
    print('PASS: importer uses verified app-private payload storage and dynamic game DEX loading')
    print('PASS: release APK contains no DeadShot JAR/JAM/SP, converted MIDI payload, or original game class definitions')
    print('PASS: release APK requires no Android permissions')
    print('PHONE RECORD: v0.1.3-rc1 import -> boot -> gameplay path was owner-confirmed on Samsung Galaxy S25 Ultra / Android 16; final app code is unchanged apart from release metadata/status text')

if __name__=='__main__': main()
