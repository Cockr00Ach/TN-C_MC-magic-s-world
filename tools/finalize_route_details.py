"""Small, repeatable corrections after native runtime review."""
from pathlib import Path
R=Path(__file__).resolve().parents[1]
def edit(path, old, new):
 p=R/path;s=p.read_text(encoding='utf-8-sig')
 if new in s:return
 if old in s:p.write_text(s.replace(old,new),encoding='utf-8')
 else:raise RuntimeError(f'Missing anchor: {path}: {old[:70]}')
edit('src/main/java/com/tnc/tnc/client/TNSpellClientVisuals.java',
 'tickFogDarkness(minecraft);',
 'if (net.minecraftforge.fml.ModList.get().isLoaded("spell_engine")) tickFogDarkness(minecraft);')
edit('src/main/java/com/tnc/tnc/life/routes/RoutePlantEntity.java','/3000','/1500')
edit('src/main/java/com/tnc/tnc/life/routes/RoutePlantBlock.java','be.growth=12000','be.growth=6000')
edit('src/main/java/com/tnc/tnc/life/routes/RoutePlantBlock.java','s.setValue(AGE,2)','s.setValue(AGE,1)')
edit('src/main/java/com/tnc/tnc/life/routes/RouteSources.java','distance<=9','distance<=64')
edit('src/main/java/com/tnc/tnc/life/routes/RouteAccessory.java','GUN("mech_spellgun","法术机枪模块",1600)','GUN("mech_spellgun","法术机枪模块",0)')
edit('src/main/java/com/tnc/tnc/life/routes/RouteAccessory.java','"矿物回声："+found.toShortString()',
 '"矿物回声来自"+(found.getY()<p.getBlockY()-2?"下方":found.getY()>p.getBlockY()+2?"上方":Math.abs(found.getX()-p.getBlockX())>Math.abs(found.getZ()-p.getBlockZ())?(found.getX()>p.getBlockX()?"东侧":"西侧"):(found.getZ()>p.getBlockZ()?"南侧":"北侧"))')
edit('src/main/java/com/tnc/tnc/life/routes/RouteAccessory.java','lines.add(Component.literal("储魔 "+',
 'if(kind==Kind.GUN){lines.add(Component.literal("穿戴魔道机甲后长按右键：每发20机甲魔力与一支箭；连射最多8秒，冷却4秒"));return;}lines.add(Component.literal("储魔 "+')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNodeEntity.java',
 'if(!(stack.getItem() instanceof ManaCharged accessory))',
 'if(!(stack.getItem() instanceof ManaCharged accessory)||accessory.manaCapacity()<=0)')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNodeEntity.java','!s.isAir()&&s.getFluidState().isEmpty()',
 '!s.getCollisionShape(l,at).isEmpty()&&s.getFluidState().isEmpty()')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNodeEntity.java','l.getBlockState(q).isAir()',
 'l.getBlockState(q).getCollisionShape(l,q).isEmpty()')
edit('src/main/java/com/tnc/tnc/life/pasture/PastureAnimal.java',
 'resource-=actual;nextMana=level().getGameTime()+600;',
 'resource-=actual;com.tnc.tnc.life.routes.RouteProgress.award(p,"harvest/dewbound_whale");nextMana=level().getGameTime()+600;')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNodeEntity.java',
 'if(got>0){RouteProgress.awardOwner(l,owner,"network/collected");',
 'if(got>0){RouteProgress.awardOwner(l,owner,"harvest/"+beast.speciesId());RouteProgress.awardOwner(l,owner,"network/collected");')
edit('src/main/java/com/tnc/tnc/life/routes/RouteMenu.java',
 '20+(i%2)*20,48+(i/2)*20','20+i*20,60')
edit('src/main/java/com/tnc/tnc/life/routes/RouteMenu.java','113,59','113,60')
edit('src/main/java/com/tnc/tnc/life/routes/RouteMenu.java','153,59','153,60')
edit('tools/generate_route_chapters.py',"if p['product']=='stored_mana':key='network/collected'", "if p['product']=='stored_mana':key='harvest/'+p['id']")
edit('tools/generate_route_chapters.py','空手潜行右键注能五秒，每秒扣自身5魔力；容器放第一个槽。',
 '空手按住右键，每秒注入自身5魔力，松开停止；潜行右键开界面放取第一个槽的容器，界面按钮也可注入五秒。')
edit('tools/generate_route_chapters.py',"f'容量{cap}。先在饰品充能台充能，再使用。'",
 "('由穿戴的魔道机甲供能；模块不单独充能。' if id=='mech_spellgun' else f'容量{cap}。先在饰品充能台充能，再使用。')")
edit('src/main/java/com/tnc/tnc/life/routes/RouteNodeEntity.java','public int mana,inRate,outRate,priority,progress,targetColor;','public int mana,inRate,outRate,priority,progress,targetColor,reserve;')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNodeEntity.java','var p=be.getBlockPos();if(!RouteAccess.allowed','String previous=be.status;var p=be.getBlockPos();if(!RouteAccess.allowed')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNodeEntity.java','if(be.kind()==RouteKind.INFUSER||be.kind()==RouteKind.CHARGER)l.sendBlockUpdated','if(!previous.equals(be.status)||be.kind()==RouteKind.INFUSER||be.kind()==RouteKind.CHARGER)l.sendBlockUpdated')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNodeEntity.java','t.putInt("Mana",mana);','t.putInt("Reserve",reserve);t.putString("Status",status);t.putInt("Mana",mana);')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNodeEntity.java','targetColor=Math.floorMod','reserve=Math.max(0,Math.min(kind().capacity,t.getInt("Reserve")));status=t.getString("Status");targetColor=Math.floorMod')
edit('src/main/java/com/tnc/tnc/life/routes/RouteClient.java','g.drawString(font,"输入",20,78','if(minecraft.level!=null&&minecraft.level.getBlockEntity(menu.pos) instanceof RouteNodeEntity be)g.drawString(font,font.plainSubstrByWidth(be.status,164),18,48,0xff285652,false);g.drawString(font,"输入",20,79')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNetwork.java','rank(l,p,source.priority)','rank(l,p,pathPriority(l,paths.get(p),source.priority))')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNetwork.java','if(source.mana<=0)break;var path=paths.get(p);int amount=source.mana;','if(source.mana<=(source.kind().storage()?source.reserve:0))break;var path=paths.get(p);int amount=Math.max(0,source.mana-(source.kind().storage()?source.reserve:0));')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNetwork.java','private static int acceptedAmount(int value){return value;}','private static int pathPriority(ServerLevel l,List<BlockPos> path,int fallback){for(var p:path)if(l.getBlockEntity(p) instanceof RouteNodeEntity n&&n.kind()==RouteKind.VALVE)return n.priority;return fallback;}')
edit('src/main/java/com/tnc/tnc/life/routes/RouteNodeBlock.java','if(kind==RouteKind.VALVE&&player.isShiftKeyDown())','if(kind.storage()&&player.isShiftKeyDown()&&player.getItemInHand(hand).isEmpty()){be.reserve=be.reserve==0?kind.capacity/4:be.reserve==kind.capacity/4?kind.capacity/2:0;be.setChanged();player.displayClientMessage(net.minecraft.network.chat.Component.literal("储池保留 "+be.reserve+" 魔力；空手潜行右键切换0/25%/50%"),true);return InteractionResult.CONSUME;}if(kind==RouteKind.VALVE&&player.isShiftKeyDown())')
edit('src/main/java/com/tnc/tnc/life/HomewardFlower.java','可种在普通野外或自己的田地；','在获准野外或自家田地的魔法土上种植，八格内放流动魔力；')
edit('src/main/java/com/tnc/tnc/life/botanical/BotanicalPlantEntity.java','if(species()==BotanicalSpecies.HONEY_CLUSTER&&pollinations<3)','if(species()==BotanicalSpecies.HONEY_CLUSTER&&pollinations<3&&!canHarvest())')
edit('src/main/java/com/tnc/tnc/life/botanical/BotanicalPlantEntity.java','var s=species();ready=false;returns=0;pollinations=0;','var s=species();getPersistentData().putInt("ManaBuffer",0);ready=false;returns=0;pollinations=0;')
edit('src/main/java/com/tnc/tnc/life/routes/RouteBooks.java','var out=new ArrayList<String>();out.add(fallback.get(0));','var out=new ArrayList<String>();out.add(fallback.get(0));if(fallback.size()>1)out.add(fallback.get(1));if(id.equals("mana_workshop_book"))out.add(fallback.get(fallback.size()-1));')
edit('src/main/java/com/tnc/tnc/life/routes/RouteBooks.java','料理配方按JEI查看当前实际配方。','原模组配方例："+(e.has("recipes")?e.get("recipes").toString():"无")+"。以JEI当前启用配方为准。')
