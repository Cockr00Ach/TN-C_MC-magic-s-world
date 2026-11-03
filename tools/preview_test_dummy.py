"""Offline preview of the standard-humanoid wooden dummy used by TestDummyRenderer."""
from pathlib import Path
from PIL import Image
import preview_geo_anim as r
ROOT=Path(__file__).resolve().parents[1]
def main():
    bones={
      'body':{'name':'body','pivot':[0,12,0],'cubes':[{'origin':[-4,12,-2],'size':[8,12,4],'uv':[16,16]}]},
      'head':{'name':'head','pivot':[0,24,0],'cubes':[{'origin':[-4,24,-4],'size':[8,8,8],'uv':[0,0]}]},
      'post':{'name':'post','pivot':[0,12,0],'cubes':[{'origin':[-2,0,-2],'size':[4,12,4],'uv':[0,16]}]},
      'right':{'name':'right','pivot':[-5,22,0],'rotation':[0,0,-90],'cubes':[{'origin':[-8,10,-2],'size':[4,12,4],'uv':[40,16]}]},
      'left':{'name':'left','pivot':[5,22,0],'rotation':[0,0,90],'cubes':[{'origin':[4,10,-2],'size':[4,12,4],'uv':[32,48]}]},
    }
    r._GEO=bones;r._CACHE.clear();r._TEX=Image.open(ROOT/'src/main/resources/assets/tnc/textures/entity/test_dummy.png').convert('RGBA');r.FRONT='-Z'
    images=[r.render(bones,list(bones),{'animation_length':1,'bones':{}},0,420,a,10,7,'WOODEN COMBAT TARGET / RIG PREVIEW',center=(0,16,0)) for a in (0,35,180)]
    out=Image.new('RGB',(1260,420));
    for i,image in enumerate(images):out.paste(image,(i*420,0))
    out.save(ROOT/'docs/previews/test-dummy-20261104.png')
if __name__=='__main__':main()
