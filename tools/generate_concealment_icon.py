"""Generate matching pixel-art concealment spell and status-effect icons only."""
import argparse
from pathlib import Path
from PIL import Image

MASK = (
    '................',
    '......####......',
    '.....#hh++#.....',
    '....#h+++++#....',
    '....#+####+#....',
    '....#+#smm+#..a.',
    '....#+#ssm+#....',
    '....#h#sss+#.a..',
    '.....#h++h#.....',
    '....#h++++h#..a.',
    '...#h++++++h#...',
    '..#h+++m++++#a..',
    '..#+++mm++a...a.',
    '..###mm+a..a....',
    '.....a...a...a..',
    '................',
)
PALETTE = {'#': (13,26,38,255), 's': (22,43,58,255), 'm': (47,77,92,255),
           '+': (83,130,145,255), 'h': (170,221,219,255), 'a': (144,207,211,160)}

def create():
    assert len(MASK)==16 and all(len(row)==16 for row in MASK)
    image=Image.new('RGBA',(16,16))
    for y,row in enumerate(MASK):
        for x,value in enumerate(row):
            if value=='.':continue
            r,g,b,a=PALETTE[value]
            if x>=9 and value!='a':
                a=round(a*max(.24,1-(x-8)*.18))
                if y>=10 and (x+y)%3==0:continue
            image.putpixel((x,y),(r,g,b,a))
    return image

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    args=parser.parse_args()
    textures=args.root/'src/main/resources/assets/tnc/textures'
    small=create()
    spell=small.resize((32,32),Image.Resampling.NEAREST)
    effect=Image.new('RGBA',(18,18));effect.paste(small,(1,1))
    for relative,image in (('spell/concealment.png',spell),('mob_effect/concealment.png',effect)):
        target=textures/relative;target.parent.mkdir(parents=True,exist_ok=True)
        image.save(target)
        print(str(target))

if __name__=='__main__':main()
