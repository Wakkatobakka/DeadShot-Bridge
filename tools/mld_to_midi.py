#!/usr/bin/env python3
from __future__ import annotations
import argparse, struct
from pathlib import Path
PPQ=1920
OCTAVE=(0,12,-24,-12)
def be16(b,o): return (b[o]<<8)|b[o+1]
def be32(b,o): return (b[o]<<24)|(b[o+1]<<16)|(b[o+2]<<8)|b[o+3]
def clamp(lo,hi,x): return max(lo,min(hi,x))
def timebase_for(sel): return {0:6,1:12,2:24,3:48,4:96,5:192,6:384,8:15,9:30,10:60,11:120,12:240,13:480,14:960}.get(sel,48)
def read_file(data):
    if data[:4]!=b'melo': raise ValueError('not MLD')
    total=min(len(data),be32(data,4)+8); ne=0; ex=0; tracks=[]; pos=13
    while pos+4<=total:
        cid=data[pos:pos+4]; pos+=4
        if cid==b'trac':
            if pos+4>total: break
            size=be32(data,pos); pos+=4; size=min(size,total-pos); tracks.append(data[pos:pos+size]); pos+=size
        else:
            if pos+2>total: break
            size=be16(data,pos); pos+=2; body=data[pos:pos+size]; pos+=size
            if cid==b'note' and len(body)>=2: ne=be16(body,0)
            elif cid==b'exst' and len(body)>=2: ex=be16(body,0)
    return ne,ex,tracks
def decode_track(payload,ti,ne,ex):
    pos=0; tick=0; ext=0; order=0; out=[]
    while pos<len(payload):
        if pos+2>len(payload): break
        delta=payload[pos]+ext; ext=0; status=payload[pos+1]; pos+=2; tick+=delta
        if status in (0x3f,0x7f,0xbf):
            if pos>=len(payload): break
            cmd=payload[pos]; pos+=1
            if cmd>=0xf0:
                if pos+2>len(payload): break
                ln=be16(payload,pos); pos+=2+ln
            else: pos+=1+ex if cmd<0x80 else 1
            order+=1; continue
        if status==0xff:
            if pos>=len(payload): break
            cmd=payload[pos]; pos+=1
            if cmd<0x80:
                pos+=1+ex; order+=1; continue
            if cmd>=0xf0:
                if pos+2>len(payload): break
                ln=be16(payload,pos); pos+=2+ln; order+=1; continue
            if pos>=len(payload): break
            val=payload[pos]; pos+=1
            out.append({'kind':'sys','raw':tick,'track':ti,'order':order,'cmd':cmd,'value':val}); order+=1
            if cmd==0xdc: ext=val<<8
            if cmd==0xdf: break
            continue
        if pos>=len(payload): break
        gate=payload[pos]; pos+=1; vel=63; octave=0
        if ne>0:
            if pos>=len(payload): break
            attr=payload[pos]; pos+=1; vel=(attr>>2)&0x3f; octave=attr&3; pos+=max(0,ne-1)
        out.append({'kind':'note','raw':tick,'track':ti,'order':order,'voice':(status>>6)&3,'pitch':status&0x3f,'gate':gate,'velocity':vel,'octave':octave,'has_attr':ne>0}); order+=1
    return out
def tempo_points(events):
    tev=[e for e in events if e['kind']=='sys' and e['track']==0 and 0xc0<=e['cmd']<=0xcf]
    tev.sort(key=lambda e:(e['raw'],e['order']))
    pts=[{'raw':0,'midi':0,'tb':48,'tempo':125}]; cr=0; cm=0; tb=48
    for e in tev:
        if e['raw']<cr: continue
        cm+=(e['raw']-cr)*PPQ//tb; cr=e['raw']; tb=timebase_for(e['cmd']&15); tempo=clamp(20,255,e['value'])
        q={'raw':cr,'midi':cm,'tb':tb,'tempo':tempo}
        if pts[-1]['raw']==cr: pts[-1]=q
        else: pts.append(q)
    return pts
def raw_to_midi(raw,pts):
    p=pts[0]
    for q in pts[1:]:
        if q['raw']>raw: break
        p=q
    return p['midi']+(raw-p['raw'])*PPQ//p['tb']
def vlq(n):
    n=max(0,int(n)); a=[n&0x7f]; n>>=7
    while n: a.append(0x80|(n&0x7f)); n>>=7
    return bytes(reversed(a))
def trans_program(program,bank):
    program&=0x3f; bank&=0x3f
    if (bank&0x3e)==0:
        m={0:0,1:9,2:16,3:24,4:13,5:74}
        if program in m: return m[program]
    return (program|(bank<<6))&0x7f
def convert(src,dst):
    data=src.read_bytes(); ne,ex,tracks=read_file(data); events=[]
    for ti,t in enumerate(tracks): events.extend(decode_track(t,ti,ne,ex))
    events.sort(key=lambda e:(e['raw'],e['track'],e['order'])); pts=tempo_points(events)
    midi=[]; serial=0
    def emit(tick,priority,msg):
        nonlocal serial; midi.append((int(tick),priority,serial,bytes(msg))); serial+=1
    seen=set()
    for p in pts:
        k=(p['midi'],p['tempo'])
        if k in seen: continue
        seen.add(k); us=max(1,round(60000000/p['tempo'])); emit(p['midi'],-30,b'\xff\x51\x03'+us.to_bytes(3,'big'))
    state=[{'bank':0,'program':0,'level':63,'pan':32,'coarse':32,'fine':32,'range':2} for _ in range(16)]
    active={}
    def logical(track,part): return track*4+part
    def bend(ch,tick):
        st=state[ch]; v=clamp(0,16383,(8*(st['fine']+32*st['coarse']))-256); emit(tick,0,[0xe0|ch,v&0x7f,(v>>7)&0x7f])
    def prog(ch,tick): emit(tick,0,[0xc0|ch,trans_program(state[ch]['program'],state[ch]['bank'])])
    for ch in range(16): emit(0,-10,[0xb0|ch,7,126]); emit(0,-10,[0xb0|ch,10,64])
    for e in events:
        expired=[k for k,r in active.items() if r['raw_end']<=e['raw']]
        for k in expired:
            r=active.pop(k); emit(r['end'],5,[0x80|r['ch'],r['note'],0])
        tick=raw_to_midi(e['raw'],pts)
        if e['kind']=='sys':
            cmd,val=e['cmd'],e['value']
            if 0xc0<=cmd<=0xcf or cmd in (0xb0,0xd0,0xdc,0xde,0xdf): continue
            if not 0xe0<=cmd<=0xef: continue
            part=(val>>6)&3; ch=logical(e['track'],part)
            if not 0<=ch<16: continue
            v=val&0x3f; st=state[ch]
            if cmd==0xe0: st['program']=v; prog(ch,tick)
            elif cmd==0xe1: st['bank']=v
            elif cmd==0xe2: st['level']=v; emit(tick,0,[0xb0|ch,7,clamp(0,127,v*2)])
            elif cmd==0xe3: st['pan']=v; emit(tick,0,[0xb0|ch,10,clamp(0,127,v*2)])
            elif cmd==0xe4: st['coarse']=v; bend(ch,tick)
            elif cmd==0xe6: st['level']=clamp(0,63,st['level']+(v-32)); emit(tick,0,[0xb0|ch,7,st['level']*2])
            elif cmd==0xe7 and v<=24:
                st['range']=v; emit(tick,0,[0xb0|ch,101,0]); emit(tick,0,[0xb0|ch,100,0]); emit(tick,0,[0xb0|ch,6,v]); emit(tick,0,[0xb0|ch,38,0])
            elif cmd in (0xe8,0xe9): st['fine']=v; bend(ch,tick)
            elif cmd==0xea: emit(tick,0,[0xb0|ch,1,clamp(0,127,v*2)])
            continue
        ch=logical(e['track'],e['voice'])
        if not 0<=ch<16: continue
        pitchoff=e['pitch']+OCTAVE[e['octave']]; note=clamp(0,127,(35 if ch==9 else 45)+pitchoff); vel=clamp(1,127,e['velocity']*2 if e['has_attr'] else 126)
        endraw=e['raw']+e['gate']; endtick=max(tick+1,raw_to_midi(endraw,pts)); key=(ch,45+pitchoff)
        if key in active: active[key]['raw_end']=endraw; active[key]['end']=endtick
        else:
            active[key]={'ch':ch,'note':note,'raw_end':endraw,'end':endtick}; emit(tick,10,[0x90|ch,note,vel])
    for r in active.values(): emit(r['end'],5,[0x80|r['ch'],r['note'],0])
    midi.sort(key=lambda x:(x[0],x[1],x[2])); tr=bytearray(); prev=0
    for tick,prio,ser,msg in midi: tr+=vlq(tick-prev); tr+=msg; prev=tick
    tr+=b'\x00\xff\x2f\x00'; out=b'MThd'+struct.pack('>IHHH',6,0,1,PPQ)+b'MTrk'+struct.pack('>I',len(tr))+tr; dst.write_bytes(out)
    return len(out)
if __name__=='__main__':
    ap=argparse.ArgumentParser(); ap.add_argument('src',type=Path); ap.add_argument('dst',type=Path); a=ap.parse_args(); print(convert(a.src,a.dst))
