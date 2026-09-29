// Exercises our orchestration against the exact 3.2 API contract separately verified by javap.
// This does not pretend to execute the third-party mod or edit a real save.
const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/server_scripts/world/tnc_quest_start.js'),'utf8');
const old='tnc:main/self_talk',fresh='tnc:main/s1_self';
function javaSet(){const s=new Set();return {contains:v=>s.has(v),add:v=>s.add(v),remove:v=>s.delete(v),raw:s};}
function scenario(mode){
  const active=javaSet(),completed=javaSet(),progress=new Map(),seeds=new Map(),claims=new Map(),flags=new Map();let callback,initializations=0;
  const state={activeQuests:()=>active,completedQuests:()=>completed,getProgress:(id,key)=>progress.get(id+':'+key)||0,setProgress:(id,key,n)=>progress.set(id+':'+key,n),markCompletedThisCycle:id=>seeds.set(id,7),completionSeed:id=>seeds.get(id)??'NONE'};
  if(mode==='active0'||mode==='active1'){active.add(old);if(mode==='active1')progress.set(old+':talk_to_self',1);}
  if(mode==='completed_unclaimed'||mode==='completed_claimed'){completed.add(old);seeds.set(old,3);if(mode==='completed_claimed')claims.set(old,3);}
  const owner={questState:()=>state,markRewardClaimed:()=>{}};
  const manager={getPlayerState:()=>({isPresent:()=>true,get:()=>owner}),getTeamState:()=>{initializations++;if(mode==='legacy_team'){completed.add(old);seeds.set(old,3);}return state;},requestFullSync:()=>{}};
  const api={startQuest:(p,id)=>{if(active.contains(id)||completed.contains(id))return false;active.add(id);return true;}};
  const saved={get:()=>({markPlayerClaimedReward:(uuid,id,seed)=>claims.set(id,seed)})};
  const classes={'com.lirxowo.whisperingquests.api.WhisperingQuestsApi':api,'net.minecraft.resources.ResourceLocation':{parse:s=>s},'com.lirxowo.whisperingquests.quest.QuestManager':manager,'com.lirxowo.whisperingquests.quest.QuestSavedData':saved};
  const player={serverLevel:()=>({}),getUUID:()=> 'test-uuid',persistentData:{getBoolean:k=>flags.get(k)||false,putBoolean:(k,v)=>flags.set(k,v)}};
  vm.runInNewContext(source,{Java:{loadClass:n=>classes[n]},PlayerEvents:{loggedIn:f=>callback=f},console:{info:()=>{},warn:()=>{},error:e=>{throw new Error(e);}}});
  callback({player});callback({player});
  assert.ok(initializations>0,'personal legacy state initialized before reading');
  assert.ok(completed.contains(old),'old nonrepeatable entry is retired');assert.equal(api.startQuest(player,old),false,'old entry cannot restart');
  if(mode.startsWith('completed')||mode==='legacy_team'){
    assert.ok(completed.contains(fresh));assert.equal(claims.get(fresh),7,'actual reward table suppresses renamed reward');assert.equal(seeds.get(old),3,'old real completion seed retained');assert.equal(claims.get(old),mode==='completed_claimed'?3:undefined,'old unclaimed entitlement preserved');
  }else{
    assert.ok(active.contains(fresh));assert.equal(seeds.has(old),false,'retirement creates no reward seed');assert.equal(claims.has(old),false,'retirement creates no claimed reward record');assert.equal(progress.get(fresh+':talk_to_self')||0,mode==='active1'?1:0,'pending dialogue progress migrates once');
  }
}
for(const mode of ['new','active0','active1','completed_unclaimed','completed_claimed','legacy_team'])scenario(mode);
console.log('Opening migration: 6 scenarios passed; repeated login, legacy initialization, retirement and reward preservation verified.');
