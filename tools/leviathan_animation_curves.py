"""Smooth deterministic animation curves for the Bluegold Abyss Beast."""
import math

def curve(axis, amplitude, phase, duration, base=0):
    out={}
    for i in range(17):
        v=[0,0,0];v[axis]=round(base+amplitude*math.sin(i/16*math.tau+phase),4)
        out[f"{duration*i/16:.4f}"]=v
    return out

def refine_animations(data):
    for name,duration,amps in [("swim",3.2,(9,16,23)),("glide",4.4,(5,10,15)),("channel_idle",3.8,(3,6,9))]:
        bones={f"tail{i+1}":{"rotation":curve(1,amp,-i*.8,duration)} for i,amp in enumerate(amps)}
        for side in (-1,1):
            for part,amp,phase in [("front",8,0),("rear",5,-.65)]:
                bones[f"fin_{part}_{side}"]={"rotation":curve(2,side*amp,phase,duration)}
        if name!="channel_idle":bones["body"]={"position":curve(1,.45,0,duration)}
        else:
            bones["body"]={"position":[0,0,0],"rotation":[0,0,0]}
            bones["head"]={"rotation":[0,0,0]}
        data["animations"][name]={"loop":True,"animation_length":duration,"bones":bones}
    data["animations"]["mouth_open"]={"loop":True,"animation_length":3.8,"bones":{"jaw":{"rotation":curve(0,1.5,0,3.8,68)}}}
