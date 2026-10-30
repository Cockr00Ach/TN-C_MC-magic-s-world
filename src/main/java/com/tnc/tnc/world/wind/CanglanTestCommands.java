package com.tnc.tnc.world.wind;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Operator-only, deterministic attack rehearsal; never changes the normal encounter by default. */
@Mod.EventBusSubscriber(modid="tnc")
public final class CanglanTestCommands {
    private static final Map<String,Integer> SKILLS=Map.of("thrust",1,"sweep",5,"tornado",9,"gale_cut",10,"arsenal_t4",11,"arsenal_t5",12,"execution",CanglanExecutionPose.MOVE);
    private static final SimpleCommandExceptionType NO_BOSS=new SimpleCommandExceptionType(Component.literal("128 格内未找到苍岚战将，可在指令末尾指定 Boss 选择器。"));
    private static final SimpleCommandExceptionType BAD_SKILL=new SimpleCommandExceptionType(Component.literal("可用招式：thrust、sweep、execution、tornado、gale_cut、arsenal_t4、arsenal_t5"));
    private static final SimpleCommandExceptionType ENTRANCE=new SimpleCommandExceptionType(Component.literal("Boss 正在入场或变身，请等动作结束后再测试。"));
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        var branch=Commands.literal("canglan").executes(c->{c.getSource().sendSuccess(()->Component.literal("/tnc canglan attack <招式> 单次；loop <招式> 循环；stop 暂停；resume 恢复战斗。招式：thrust 突刺、sweep 横扫、tornado 旋转龙卷、gale_cut 风刃、arsenal_t4 / arsenal_t5 武器链。"),false);return 1;});
        for(String mode:List.of("attack","loop"))branch.then(Commands.literal(mode)
            .then(Commands.argument("skill",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(SKILLS.keySet(),b))
                .executes(c->run(c,mode,false))
                .then(Commands.argument("boss",EntityArgument.entity()).executes(c->run(c,mode,true)))));
        for(String mode:List.of("stop","resume"))branch.then(Commands.literal(mode).executes(c->run(c,mode,false))
            .then(Commands.argument("boss",EntityArgument.entity()).executes(c->run(c,mode,true))));
        event.getDispatcher().register(Commands.literal("tnc").requires(s->s.hasPermission(2)).then(branch));
    }
    private static int run(CommandContext<CommandSourceStack> c,String mode,boolean explicit)throws CommandSyntaxException {
        var source=c.getSource();CanglanWarlordEntity boss;
        if(explicit){var e=EntityArgument.getEntity(c,"boss");if(!(e instanceof CanglanWarlordEntity b))throw NO_BOSS.create();boss=b;}
        else boss=source.getLevel().getEntitiesOfClass(CanglanWarlordEntity.class,new net.minecraft.world.phys.AABB(source.getPosition(),source.getPosition()).inflate(128),CanglanWarlordEntity::isAlive)
            .stream().min(Comparator.comparingDouble(b->b.position().distanceToSqr(source.getPosition()))).orElseThrow(NO_BOSS::create);
        if(mode.equals("resume")){boss.battle().resumeTest();source.sendSuccess(()->Component.literal("苍岚战将已恢复正常战斗。"),false);return 1;}
        if(mode.equals("stop")){boss.battle().pauseTest();source.sendSuccess(()->Component.literal("测试已暂停，当前招式、龙卷风和锚点已清理。用 resume 恢复正常战斗。"),false);return 1;}
        Integer skill=SKILLS.get(StringArgumentType.getString(c,"skill"));if(skill==null)throw BAD_SKILL.create();
        if(boss.rising()||boss.shifting())throw ENTRANCE.create();
        var player=source.getPlayerOrException();
        if((skill==5||skill==CanglanExecutionPose.MOVE)&&!boss.battle().canSweep(player))throw new SimpleCommandExceptionType(Component.literal("横扫及处决只能在近距离释放，请靠近 Boss 后再测试。 ")).create();
        if(skill==CanglanExecutionPose.MOVE&&(player.isCreative()||player.isSpectator()||player.isInvulnerable()||!CanglanWarGodRules.executionHealth(player.getHealth(),player.getAbsorptionAmount(),player.getMaxHealth())))throw new SimpleCommandExceptionType(Component.literal("处决需要生存模式，生命加吸收生命严格低于最大生命的一半。 ")).create();
        boss.battle().testAttack(skill,player,mode.equals("loop"));
        source.sendSuccess(()->Component.literal(mode.equals("loop")?"正在循环测试该招式；用 /tnc canglan stop 暂停。":"开始单次测试，完成后待机；用 /tnc canglan resume 恢复正常战斗。"),false);return 1;
    }
}
