"""Inspect the five sets of drawable string states from their actual item models."""
from gallery import *
def main():
    ids=[i for t,i in all_ids() if t=='longbows'];w,h=227,253
    page=Image.new('RGB',(4*w+176,len(ids)*h+72),'#161e26');d=ImageDraw.Draw(page)
    for c,title in enumerate(('待机','初拉','半满','满弦')):
        d.text((169+c*w,20),title,font=font(25,True),fill='#e8e3d8')
    for row,k in enumerate(ids):
        y=row*h+64;d.text((15,y+h//2-13),file_name(k),font=font(18),fill='#c9c8bd')
        for c,n in enumerate([k]+[k+'_pulling_'+str(i) for i in range(3)]):
            page.paste(panel(n,True,(w-7,h-7),yaw=-7,pitch=2,roll=0),(163+c*w,y))
    page.save(ROOT/'预览/五弓拉弦四态.jpg',quality=92,subsampling=0)
if __name__=='__main__':main()
