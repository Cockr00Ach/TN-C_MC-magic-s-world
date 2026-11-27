from gallery import *
def main():
    picks=[('dragon_sword','翼状剑格与红黑刃面',.62),('bronze_halberd','主刃开窗与副刃回钩',.46),('moon_bow','银紫月弧与弓臂镂空',.5)]
    W=480;page=Image.new('RGB',(3*W+40,830),'#18222b')
    d=ImageDraw.Draw(page);d.text((24,14),'战士兵器 · 实模细节',font=font(34,True),fill='#e8e3d8')
    for i,(k,cap,yfrac) in enumerate(picks):
        x=20+i*W;large=render(k,True,850,1200,yaw=-22,pitch=8,roll=-12);rect=large.getbbox();fig=large.crop(rect)
        pane=Image.new('RGBA',(W-10,425),'#26343e');overall=fig.copy();overall.thumbnail((W-32,399),Image.Resampling.LANCZOS)
        pane.alpha_composite(overall,((pane.width-overall.width)//2,(pane.height-overall.height)//2));page.paste(pane,(x,72))
        cut_h=315;cy=int(fig.height*yfrac);cx=fig.width//2
        zoom=fig.crop((cx-260,cy-cut_h//2,cx+260,cy+cut_h//2));zoom.thumbnail((W-10,278),Image.Resampling.LANCZOS)
        detail=Image.new('RGBA',(W-10,245),'#202d37');detail.alpha_composite(zoom,((detail.width-zoom.width)//2,(detail.height-zoom.height)//2))
        page.paste(detail,(x,503));d.text((x+3,752),file_name(k),font=font(25,True),fill='#ece5d6')
        d.text((x+3,795),cap,font=font(17),fill='#b3c2c6')
    page.save(ROOT/'预览/三件兵器实模细节.jpg',quality=94,subsampling=0)
if __name__=='__main__':main()
