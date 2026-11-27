(function sculptSeaGarden(repo, deliver, audit) {
    const fs = require('fs'), path = require('path'), T = THREE;
    const asset = path.join(repo, 'src/main/resources/assets/tnc');
    const roots = [
        ['tidebreath_lantern', '潮息灯囊', ['蓄气', '吐泡', '回缩']],
        ['undertow_bindweed', '退潮束带草', ['伏流', '举梢', '卷缚']],
        ['waking_tide_fan', '醒潮扇', ['半卷', '舒展', '送流']],
        ['moonreef_blossom', '月汐珊花', ['闭花', '半开', '月下盛开']],
        ['veilspore_anemone', '幕孢滤食花', ['滤食', '合拢', '散孢']],
        ['titan_tidekelp', '巨潮穗藻', ['嫩芽', '展叶', '结穗']]
    ];
    const resources=[], counts=[];
    function writeRel(rel,obj) {
        const dst=path.join(asset,rel);fs.mkdirSync(path.dirname(dst),{recursive:true});
        fs.writeFileSync(dst,JSON.stringify(obj));resources.push('src/main/resources/assets/tnc/'+rel);
    }
    const v=(x,y,z)=>new T.Vector3(x,y,z), TAU=2*Math.PI;
    const lift=(center, angle, radius, y)=>v(center.x+Math.cos(angle)*radius,center.y+y,center.z+Math.sin(angle)*radius);
    for (const [id,title,stages] of roots) {
        newProject(Formats.free);Project.name=title;Project.texture_width=96;Project.texture_height=64;
        const texturePath='textures/block/seagarden/'+id+'.png';resources.push('src/main/resources/assets/tnc/'+texturePath);
        const img=fs.readFileSync(path.join(asset,texturePath)).toString('base64');
        const texture=new Texture({name:id+'.png',uv_width:96,uv_height:64}).fromDataURL('data:image/png;base64,'+img).add(false);
        const states={}, modelDir='block/seagarden/', saved=[];
        Undo.initEdit({elements:[],outliner:true});
        for(const part of (id==='titan_tidekelp'?['stem','tip']:[''])) for(let stage=0;stage<3;stage++){
            const index=stage+(part==='tip'?3:0),ox=(index%3)*19,oy=part==='tip'?20:0;
            const group=new Group({name:(part==='tip'?'顶穗 · ':part==='stem'?'藻茎 · ':'')+stages[stage],origin:[ox,oy,0]}).init();
            const surfaces=[],organs=[];
            const base=v(8,.05,8);
            function surface(name, rings, tile, glow=false, double=false, closed=true) {
                const mesh=new Mesh({name,origin:[ox,oy,0],vertices:{}}).addTo(group);
                const keys=rings.map((ring,i)=>ring.map((point,j)=>{
                    const key='v'+i+'_'+j;mesh.vertices[key]=point.toArray().map(n=>+n.toFixed(4));return key;
                }));
                const width=rings[0].length,sides=closed?width:width-1;
                for(let i=0;i+1<keys.length;i++)for(let j=0;j<sides;j++){
                    let k=(j+1)%width,verts=[keys[i][j],keys[i+1][j],keys[i+1][k],keys[i][k]],uv={};
                    [[j/sides,i/(keys.length-1)],[j/sides,(i+1)/(keys.length-1)],[(j+1)/sides,(i+1)/(keys.length-1)],[(j+1)/sides,i/(keys.length-1)]].forEach(([u,y],h)=>{uv[verts[h]]=[tile%3*32+1+30*u,Math.floor(tile/3)*32+1+30*y]});
                    mesh.addFaces(new MeshFace(mesh,{vertices:verts.slice(),uv,texture:texture.uuid}));
                    if(double)mesh.addFaces(new MeshFace(mesh,{vertices:verts.slice().reverse(),uv:Object.fromEntries(Object.entries(uv).map(([key,p])=>[key,p.slice()])),texture:texture.uuid}));
                }
                mesh.init();saved.push(mesh);organs.push([mesh,glow]);
            }
            function bulb(name, c, rx,ry,rz,tile,glow=false,n=8) {
                const rings=[-1.557,-.93,-.34,.34,.93,1.557].map(lat=>Array.from({length:n},(_,j)=>v(
                    c.x+rx*Math.cos(lat)*Math.cos(TAU*j/n),c.y+ry*Math.sin(lat),c.z+rz*Math.cos(lat)*Math.sin(TAU*j/n))));
                surface(name,rings,tile,glow);
            }
            function tube(name,points,radii,tile,glow=false,n=5) {
                let side=null;
                const rings=points.map((p,i)=>{
                    const prev=points[Math.max(0,i-1)],next=points[Math.min(i+1,points.length-1)],axis=next.clone().sub(prev).normalize();
                    if(!side)side=Math.abs(axis.y)<.93?v(0,1,0):v(0,0,1);
                    side=side.clone().addScaledVector(axis,-side.dot(axis)).normalize();const other=side.clone().cross(axis).normalize();
                    return Array.from({length:n},(_,j)=>p.clone().addScaledVector(side,radii[i]*Math.cos(TAU*j/n)).addScaledVector(other,radii[i]*Math.sin(TAU*j/n)));
                });surface(name,rings,tile,glow);
            }
            function leaf(name,path,width,tile,roll=0,glow=false){
                const rings=path.map((p,i)=>{
                    const t=i/(path.length-1),axis=path[Math.min(path.length-1,i+1)].clone().sub(path[Math.max(0,i-1)]).normalize();
                    let side=axis.clone().cross(v(Math.sin(roll)*.35,.9,Math.cos(roll)*.35));if(side.lengthSq()<.002)side=axis.clone().cross(v(1,0,0));side.normalize().applyAxisAngle(axis,roll*.24);
                    const chord=width*Math.max(.025,Math.sin(Math.PI*t))**.72, ridge=axis.clone().cross(side).normalize();
                    return [p.clone().addScaledVector(side,-chord),p.clone().addScaledVector(ridge,.21*chord),p.clone().addScaledVector(side,chord)];
                });surface(name,rings,tile,glow,true,false);
            }
            function radial(name,c,a,len,h,w,curve,tile,bend=0){
                const ps=Array.from({length:8},(_,i)=>{let t=i/7,u=a+curve*t;return lift(c,u,len*t,h*t+bend*Math.sin(Math.PI*t))});leaf(name,ps,w,tile,a);
            }
            function holdfast(n=5,spread=2.8){
                for(let j=0;j<n;j++){
                    const a=TAU*j/n+.38; tube('固着根 '+j,[lift(base,a,spread,.15),lift(base,a+.17,spread*.6,.4),lift(base,a,.3,.7)],[.045,.24,.5],0,false,5);
                }
            }
            if(id==='tidebreath_lantern'){
                holdfast();const inflate=[1,.88,.67][stage]; 
                for(let j=0;j<3;j++){
                    const a=.35+TAU*j/3, small=j!==0;
                    const c=lift(base,a,small?3.15:1.1,small?5.6:8.8);
                    tube('分叉虹管 '+j,[base.clone(),lift(base,a-.3,1.1,2.2),c.clone().add(v(0,-2.4,0))],[.53,.39,.32],0);
                    const ry=(small?1.8:2.8)*inflate,rx=(small?1.2:2.1)*inflate;
                    bulb('通气灯囊 '+j,c,rx,ry,rx*.93,5,false,9);
                    for(let k=0;k<4;k++){
                        const u=a+k*TAU/4;const rib=Array.from({length:7},(_,h)=>{const t=h/6,angle=u+.4*t;return lift(c,angle,.11+rx*1.04*Math.sin(Math.PI*t),ry*(t*2-1))});
                        tube('珠囊外筋 '+j+'-'+k,rib,[.08,.105,.12,.13,.1,.072,.035],2,false,4);
                    }
                    bulb('泡口 '+j,lift(c,a,.02,ry),small?.28:.49,.15,small?.28:.49,4,stage!==2);
                }
                for(let j=0;j<4;j++)radial('托叶 '+j,base,TAU*j/4,4.3,3.5,1.0,.56,1,1.2);
            }else if(id==='undertow_bindweed'){
                holdfast(7,3.8);
                for(let j=0;j<6;j++){
                    const a=TAU*j/6+.17,L=3.7+(j%3)*.85,H=[2.8,6.8,7.9][stage]+j%2;
                    const ps=Array.from({length:12},(_,i)=>{const t=i/11,angle=a+(.43+stage*.97)*t;let r=.3+L*t;if(stage===2)r=.3+5.0*Math.sin(t*2.4);return lift(base,angle,r,H*t+(stage===0?1.6:1.15)*Math.sin(Math.PI*t))});
                    leaf('卷带 '+j,ps,.70+(j%2)*.22,j%2?1:2,a);
                    if(j%2===0)tube('束缚感应脊 '+j,ps.slice(4),ps.slice(4).map((_,k)=>.11-.008*k),stage?4:3,stage===1,4);
                    const end=ps[11];bulb('卷须尖珠 '+j,end,.14,.20,.14,stage===1?4:3,stage===1,5);
                }
            }else if(id==='waking_tide_fan'){
                holdfast(4);const hinge=v(8,2.35,8.4);tube('扇铰立茎',[base,v(8.3,1.28,8),hinge],[.8,.63,.43],0);
                const spread=[.52,.91,1.13][stage];
                for(let j=0;j<9;j++){
                    const a=(-1+2*j/8)*spread, len=9+2.3*Math.sin(Math.PI*(j+1)/10);
                    const ps=Array.from({length:8},(_,i)=>{const t=i/7;return v(hinge.x+Math.sin(a)*len*t*.65,hinge.y+Math.cos(a)*len*t,hinge.z+Math.sin(t*Math.PI)*(.45+j%2*.38)-t*t*(.85+stage*.6))});
                    tube('扇骨 '+j,ps,[.18,.17,.17,.15,.13,.12,.095,.06],3);
                    if(j<8){
                        const b=a+2*spread/8;const fan=[];for(let i=2;i<8;i++){const t=i/7;fan.push([a+.014,(a+b)/2,b-.014].map((ang,k)=>v(hinge.x+Math.sin(ang)*len*t*.65,hinge.y+Math.cos(ang)*len*t+(k===1?.32*Math.sin(Math.PI*t):0),hinge.z+.45*Math.sin(t*Math.PI)-t*t*(.85+stage*.6)+(k===1?.36:0))))};surface('折扇翼膜 '+j,fan,j%3===1?2:1,false,true,false)}
                    if(j%2===0)bulb('扇端光芽 '+j,ps[7],.16,.26,.13,4,stage>0,5);
                }
            }else if(id==='moonreef_blossom'){
                holdfast();const corolla=v(8,4.2,8);tube('珊枝',[base,v(8.36,2.25,8.1),corolla],[.82,.62,.47],0);
                for(let j=0;j<8;j++){const a=TAU*j/8+.14;radial('礁花大瓣 '+j,corolla,a,[1.45,3.8,5.75][stage],[7.1,5,2.65][stage],1.15+(j%2)*.2,.11,3,1.25);}
                for(let j=0;j<5;j++)radial('内冠 '+j,corolla,TAU*j/5+.57,[1,2.3,3.1][stage],[5.3,4.6,3.1][stage],.69,-.24,stage===2?4:2,.5);
                bulb('月汐花心',v(8,5.4,8),.82,1.0,.82,4,stage===2,8);
                for(let j=0;j<4;j++)radial('裙叶 '+j,base,TAU*j/4+.75,4.7,2.25,1.35,.48,1);
            }else if(id==='veilspore_anemone'){
                holdfast(5);const baseCup=v(8,2.5,8);
                for(let j=0;j<7;j++){const a=TAU*j/7+.2, bend=[.62,1.36,.96][stage],r=[4.3,2.5,3.0][stage],ht=7+(j%3);
                    const ps=Array.from({length:9},(_,k)=>{const t=k/8;return lift(baseCup,a+bend*t,.55+r*t,ht*t+Math.sin(t*Math.PI)*1.0)});tube('滤羽中轴 '+j,ps,[.24,.22,.21,.18,.15,.13,.1,.075,.045],2);
                    for(let k=2;k<=6;k++){const t=k/8,c=ps[k],reach=1.23*Math.sin(Math.PI*t)+.44;for(const sign of [-1,1]){const ang=a+bend*t+sign*1.29;const pts=[c,lift(c,ang,reach*.76,.33),lift(c,ang+.14*sign,reach,.87)];leaf('羽丝 '+j+'-'+k+'-'+sign,pts,.115,stage===2?3:1,ang);}}
                    if(j%2===0)bulb('孢囊 '+j,ps[7],.3,.40,.3,stage===1?4:5,stage===1,6);
                }bulb('滤食杯',baseCup,1.55,1.8,1.55,5,false,9);
            }else{
                const isTip=part==='tip',swell=.63+stage*.18;const height=isTip?10.7:16.0;
                const ps=Array.from({length:10},(_,j)=>{let t=j/9;return v(8+Math.sin(TAU*t)*.85,t*height,8+.67*Math.sin(Math.PI*t))});
                tube('中空藻轴',ps,ps.map((_,j)=>isTip?.61-j*.042:.65),0,false,7);
                for(let j=0;j<(isTip?4:5);j++){
                    const c=ps[Math.min(7,1+j)],a=TAU*(j*.382)+(isTip?.87:0);radial('潮纹宽叶 '+j,c,a,(isTip?4.35:5.35)*swell,(isTip?4.65:5.3)*swell,1.15*swell,.69,1+(j%2),1.18);
                    if(j%2===1)bulb('浮力小囊 '+j,lift(c,a+.3,1.5,.55),.52,.86,.47,5);
                }
                if(isTip)for(let j=0;j<5;j++){
                    const a=TAU*j/5;let c=lift(ps[8],a,1.15,1.25+.5*(j%2));tube('藻穗梗 '+j,[ps[7],lift(ps[7],a,1.05,.83),c],[.18,.13,.095],3);
                    bulb('成熟活穗 '+j,c,.36*swell,.81*swell,.36*swell,stage===2?4:5,stage===2,6);
                }
            }
            function round(n){return +n.toFixed(5)}
            for(const [mesh,glow] of organs){for(const f of Object.values(mesh.faces)){
                surfaces.push({vertices:f.vertices.map(key=>mesh.vertices[key]),uv:f.vertices.map(key=>[round(f.uv[key][0]/6),round(f.uv[key][1]/4)]),glow});
            }}
            const variant=id+(part?'_'+part:'')+'_'+stage, meshPath='seagarden_meshes/'+variant+'.json',atlas='tnc:block/seagarden/'+id;
            writeRel(meshPath,surfaces);
            writeRel('models/'+modelDir+variant+'.json',{loader:'tnc:botanical_mesh',render_type:'minecraft:cutout',ambientocclusion:false,glow_enabled:true,textures:{plant:atlas,particle:atlas},geometry:'tnc:'+meshPath});
            states[(part?'part='+part+',':'')+'stage='+stage]={model:'tnc:'+modelDir+variant};
            counts.push({id,part,stage,faces:surfaces.length,emissive:surfaces.filter(f=>f.glow).length});
        }
        Undo.finishEdit('雕刻海底魔植在不同状态下的器官',{elements:saved,outliner:true});Canvas.updateAll();
        writeRel('blockstates/'+id+'.json',{variants:states});
        writeRel('models/item/'+id+'.json',{parent:'tnc:'+modelDir+id+(id==='titan_tidekelp'?'_tip':'')+'_'+(id==='undertow_bindweed'?1:id==='moonreef_blossom'||id==='waking_tide_fan'?2:0),display:{gui:{rotation:[24,33,0],translation:[0,1,0],scale:[1.05,1.05,1.05]},ground:{scale:[.45,.45,.45]},fixed:{scale:[.86,.86,.86]}}});
        fs.mkdirSync(deliver,{recursive:true});const file=path.join(deliver,id+'.bbmodel');fs.writeFileSync(file,Codecs.project.compile());Project.save_path=file;Project.saved=true;
    }
    fs.writeFileSync(path.join(audit,'seagarden-art.json'),JSON.stringify({resources,counts},null,1));return counts;
})
