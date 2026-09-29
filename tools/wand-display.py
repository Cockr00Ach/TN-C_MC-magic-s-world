"""Anchor the handle to the palm and orient the head using Minecraft's hand renderer."""
import json, math
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]

def grip_axis(name):
    image=Image.open(ROOT/f'src/main/resources/assets/tnc/textures/item/wands/{name}.png').convert('RGBA')
    points=[(x+.5,y+.5) for y in range(32) for x in range(32) if image.getpixel((x,y))[3]>=128]
    low=min(y for x,y in points);high=max(y for x,y in points)
    def center(sample):
        return (sum(x for x,y in sample)/len(sample)/2-8,8-sum(y for x,y in sample)/len(sample)/2)
    grip=center([(x,y) for x,y in points if y>=high-5])
    tip=center([(x,y) for x,y in points if y<=low+7])
    angle=math.degrees(math.atan2(tip[1]-grip[1],tip[0]-grip[0]))
    return grip,angle

def held_pose(grip,angle,scale,sign,target_angle):
    z=target_angle-angle
    radians=math.radians(z)
    x=grip[0]*math.cos(radians)-grip[1]*math.sin(radians)
    y=grip[0]*math.sin(radians)+grip[1]*math.cos(radians)
    # Ry(90) sends the rotated handle's X to -Z. Undo its scaled position,
    # instead of placing the center of the whole sprite in the player's palm.
    return {'rotation':[0,sign*90,round(sign*z,6)],
            'translation':[0,round(-scale*y,6),round(scale*x,6)],'scale':[scale]*3}

def first_person_pose(grip,angle,scale,sign):
    # Vanilla's idle hand adds a 45 degree yaw. Keep the sprite's face toward
    # the camera and its long axis nearly vertical in the camera plane.
    z=82-angle
    rz=math.radians(z)
    yaw=math.radians(-25)
    x=grip[0]*math.cos(rz)-grip[1]*math.sin(rz)
    y=grip[0]*math.sin(rz)+grip[1]*math.cos(rz)
    # ItemTransform mirrors Y/Z rotation and X translation for the left hand.
    # Cancel the rotated grip in both hands without pushing the head into depth.
    return {'rotation':[0,-25,round(sign*z,6)],
            'translation':[round(-sign*scale*x*math.cos(yaw),6),
                           round(-scale*y,6),round(sign*scale*x*math.sin(yaw),6)],
            'scale':[scale]*3}

def model(name, tier):
    scale = [0, 1.3, 1.55, 1.7, 1.85, 2.0][tier]
    grip,angle=grip_axis(name)
    display = {}
    for hand, sign in [('righthand', 1), ('lefthand', -1)]:
        # The player model flips Y; ItemInHandLayer adds Rx(-90), Ry(180).
        # A head at local angle 170 degrees therefore points upward in-world.
        display['thirdperson_' + hand] = held_pose(grip,angle,scale,sign,170)
        display['firstperson_' + hand] = first_person_pose(grip,angle,scale,sign)
    return {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'tnc:item/wands/'+name}, 'display': display}

if __name__ == '__main__':
    root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/tnc/models/item'
    for element in ['water','fire','lightning','wind','earth','light','dark']:
        for tier in range(1,6):
            name=f'{element}_wand_{tier}'
            (root/(name+'.json')).write_text(json.dumps(model(name,tier),indent=2)+'\n',encoding='utf-8')
