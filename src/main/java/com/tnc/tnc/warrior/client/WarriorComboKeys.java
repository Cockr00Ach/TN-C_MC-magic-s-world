package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.ComboTracker;
import com.tnc.tnc.warrior.WarriorComboInputPacket;
import com.tnc.tnc.warrior.WarriorMove;
import com.tnc.tnc.warrior.WarriorStepPacket;
import com.tnc.tnc.warrior.WarriorWeapons;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 长剑连招的客户端输入。
 *
 * <p>默认键：<b>Q = 左斩击、E = 右斩击、左键 = 横斩击</b>（可在「选项 → 控制 → TN-C」改）。
 * 三个键都可改，所以界面上的连招串（QEZ…）只是"默认键"的说明，判定永远按实际绑定走。
 *
 * <p><b>为什么要"接管"Q/E</b>：Q 是原版丢弃、E 是打开背包。拿着长剑又加入磨砺时，
 * 这两个键在战斗里必须是斩击；否则连招第一下就把剑丢出去了。做法是在
 * {@link TickEvent.ClientTickEvent} 的 START（原版 {@code handleKeybinds} 之前）
 * 把原版那两个键的点击<b>吃掉</b>——只在"我们的连招键恰好绑在同一个键上"时才吃，
 * 玩家把连招改到别的键，Q/E 立刻恢复原版功能。
 *
 * <p><b>只认 {@code isDown()} 的上升沿，不认 {@code consumeClick()}</b>：GLFW 的
 * 自动重复（{@code GLFW_REPEAT}）会往点击计数里塞事件，按住 E 不放就会被当成
 * "一直短按 E"——连招缓冲里堆满 E，长按永远判不出来。点击计数仍然要清，
 * 否则会漏给别人（见 {@link #drainClicks}）。
 *
 * <p>关掉界面（{@code screen != null}）或未加入磨砺时不接管，背包和丢弃照常。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WarriorComboKeys {

    public static final KeyMapping LEFT = new KeyMapping("key.tnc.combo_left",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, "key.categories.tnc");
    public static final KeyMapping RIGHT = new KeyMapping("key.tnc.combo_right",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, "key.categories.tnc");
    /**
     * 战斗步法：<b>按下即中断自己的动作</b>、短按冲一大段、长按小步+加速跑。
     *
     * <p><b>默认左 Ctrl，不是 Shift</b>：Shift 是原版潜行键，同一个键既潜行又冲刺必然冲突
     * ——按住 Shift 想蹲，结果人冲出去了。所以步法单独占一个键，潜行保持原版，两边都不打架。
     *
     * <p>左 Ctrl 是原版疾跑键，但不冲突：两个动作的意图是一致的（都是"往前冲"），
     * 而且本 mod 不再吞任何原版按键，疾跑照常。真要分开，在「选项 → 控制」里改任意一边即可。
     */
    public static final KeyMapping STEP = new KeyMapping("key.tnc.combo_step",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.tnc");

    /** 长按期间每隔这么多 tick 续一次（服务端只用来续架势窗口，不产生输入）。 */
    public static final KeyMapping BOW_HORIZONTAL = new KeyMapping("key.tnc.bow_charge_z",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.tnc");
    private static final WarriorRightChargeInput CHARGE=new WarriorRightChargeInput();
    private static final int KEEPALIVE_TICKS=10;
    private static long tick;
    private static boolean rightWasDown,stepWasDown;
    private WarriorComboKeys(){}

    private static long rightDownAt = -1;
    private static boolean rightHeldSent;
    private static long rightKeepAlive;

    private static long stepDownAt = -1;
    private static boolean stepPending;
    private static boolean stepCharged;

    /** 现在该不该把 Q/E 当出招：在游戏里、没开界面、有战士天赋、手持武器。 */
    static boolean combatReady(Minecraft minecraft) {
        return minecraft != null && minecraft.player != null && minecraft.level != null
                && minecraft.screen == null && minecraft.getOverlay() == null && minecraft.isWindowActive()
                && TemperingClient.enrolled()
                && !com.tnc.tnc.bronze.BronzeTwinWeapon.blocked(minecraft.player)
                && WarriorWeapons.family(minecraft.player.getMainHandItem()) != null;
    }

    /** 现在这个方向键还按着吗（给"长按保持架势"用，见 {@link WarriorAnimationClient}）。 */
    public static boolean holding(WarriorMove move) {
        if (move == WarriorMove.LEFT) {
            return LEFT.isDown();
        }
        if (move == WarriorMove.RIGHT) {
            return RIGHT.isDown();
        }
        if (move == WarriorMove.HORIZONTAL) {
            return CHARGE.active();
        }
        return false;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        tick++;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            CHARGE.cancel(false);rightDownAt=-1;rightHeldSent=false;stepPending=false;rightWasDown=false;
            return;
        }
        boolean ready = combatReady(minecraft);
        if (ready || minecraft.screen==null && minecraft.getOverlay()==null && TemperingClient.enrolled()
                && com.tnc.tnc.bronze.BronzeTwinWeapon.blocked(minecraft.player)) {
            // 先于原版 handleKeybinds 吃掉同一按键上的原版动作，否则 Q 会丢剑、E 会弹背包。
            // 步法键（左 Ctrl）**不吃**：它和原版疾跑撞在一个键上，但意图一致（都是往前冲），
            // 吃掉疾跑反而亏；玩家改到别的键也不受影响。
            swallowConflicts(minecraft.options, LEFT);
            swallowConflicts(minecraft.options, RIGHT);
        }

        var action=CHARGE.update(minecraft.options.keyUse.isDown(),ready&&!minecraft.player.isShiftKeyDown(),tick);
        switch(action){
            case HOLD->WarriorComboInputPacket.send(WarriorMove.HORIZONTAL,WarriorComboInputPacket.HOLD);
            case KEEPALIVE->WarriorComboInputPacket.send(WarriorMove.HORIZONTAL,WarriorComboInputPacket.KEEPALIVE);
            case RELEASE->WarriorComboInputPacket.send(WarriorMove.HORIZONTAL,WarriorComboInputPacket.RELEASE);
            case CANCEL->WarriorStepPacket.send(WarriorStepPacket.CANCEL);
            default->{}
        }
        if(!ready){rightDownAt=-1;rightHeldSent=false;stepPending=false;}
        boolean sameKey = LEFT.getKey().equals(RIGHT.getKey());
        boolean rightNow = !sameKey && RIGHT.isDown();
        boolean stepNow = STEP.isDown();
        // 自动重复的点击一律丢掉：只认 isDown 的上升/下降沿
        drainClicks(LEFT);
        drainClicks(RIGHT);
        drainClicks(STEP);

        if (rightNow && !rightWasDown) {
            rightDownAt = ready?tick:-1;
            rightHeldSent = false;
            if (ready) {
                cancelCharge();
                WarriorComboInputPacket.send(WarriorMove.RIGHT);
            }
        }
        if (stepNow && !stepWasDown) {
            // 按下那一瞬间先发"只中断"：立刻停下自己的动作，不等松手
            stepPending = ready;
            stepDownAt = tick;
            stepCharged = false;
            if (ready) {
                CHARGE.cancel(minecraft.options.keyUse.isDown());
                WarriorStepPacket.send(WarriorStepPacket.CANCEL);
            }
        }
        rightWasDown = rightNow;
        stepWasDown = stepNow;

        // E：同上
        if (rightDownAt >= 0) {
            if (!rightNow) {
                if (rightHeldSent && ready) {
                    WarriorComboInputPacket.send(WarriorMove.RIGHT, WarriorComboInputPacket.RELEASE);
                }
                rightDownAt = -1;
                rightHeldSent = false;
            } else if (!rightHeldSent) {
                if (tick - rightDownAt >= ComboTracker.HOLD_TICKS) {
                    if (ready) {
                        WarriorComboInputPacket.send(WarriorMove.RIGHT, WarriorComboInputPacket.HOLD);
                    }
                    rightHeldSent = true;
                    rightKeepAlive = tick;
                }
            } else if (tick - rightKeepAlive >= KEEPALIVE_TICKS) {
                if (ready) {
                    WarriorComboInputPacket.send(WarriorMove.RIGHT, WarriorComboInputPacket.KEEPALIVE);
                }
                rightKeepAlive = tick;
            }
        }

        // 战斗步法：松手（短按）→ 冲一大段；按住够久（长按）→ 一小段 + 加速跑。
        if (stepPending) {
            if (!STEP.isDown()) {
                if (ready) {
                    WarriorStepPacket.send(WarriorStepPacket.TAP);
                }
                stepPending = false;
            } else if (!stepCharged && tick - stepDownAt >= ComboTracker.HOLD_TICKS) {
                if (ready) {
                    WarriorStepPacket.send(WarriorStepPacket.HOLD);
                }
                stepCharged = true;
                stepPending = false;
            }
        }
    }

    /** 清掉一个键上堆积的点击计数（自动重复会塞很多），谁都不许拿它当"按下"。 */
    private static void drainClicks(KeyMapping mapping) {
        while (mapping.consumeClick()) {
            // 故意空转：只清点击计数
        }
    }

    private static void cancelCharge(){
        if(CHARGE.active()){
            CHARGE.cancel(Minecraft.getInstance().options.keyUse.isDown());
            WarriorStepPacket.send(WarriorStepPacket.CANCEL);
        }
    }
    /** Physical press events retain very short Q/Z taps that polling isDown can miss. */
    @SubscribeEvent public static void key(InputEvent.Key event){
        if(event.getAction()!=GLFW.GLFW_PRESS || !combatReady(Minecraft.getInstance()))return;
        WarriorMove move=LEFT.matches(event.getKey(),event.getScanCode())?WarriorMove.LEFT
                :BOW_HORIZONTAL.matches(event.getKey(),event.getScanCode())?WarriorMove.HORIZONTAL:null;
        if(move!=null){cancelCharge();WarriorComboInputPacket.send(move);}
    }
    /** Run after block/entity interactions: chests and trading retain their ordinary right click. */
    @SubscribeEvent public static void use(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event){
        var mc=Minecraft.getInstance();
        if(!event.getLevel().isClientSide || event.getEntity()!=mc.player
                || event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND || !combatReady(mc)
                || mc.player.isShiftKeyDown())return;
        event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);event.setCanceled(true);
        if(mc.options.keyUse.isDown()&&CHARGE.begin(tick))
            WarriorComboInputPacket.send(WarriorMove.HORIZONTAL,WarriorComboInputPacket.CHARGE_START);
    }
    @SubscribeEvent public static void attackKey(InputEvent.InteractionKeyMappingTriggered event){
        if(event.isUseItem()&&combatReady(Minecraft.getInstance())&&(CHARGE.active()||CHARGE.blocked())){
            event.setCanceled(true);event.setSwingHand(false);
        }
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event){
        if(event.getAction()!=GLFW.GLFW_PRESS || !combatReady(Minecraft.getInstance()))return;
        int button=event.getButton();
        boolean q=LEFT.getKey().getType()==InputConstants.Type.MOUSE&&LEFT.getKey().getValue()==button;
        boolean z=BOW_HORIZONTAL.getKey().getType()==InputConstants.Type.MOUSE&&BOW_HORIZONTAL.getKey().getValue()==button;
        if(q||z||button==GLFW.GLFW_MOUSE_BUTTON_LEFT){
            cancelCharge();WarriorComboInputPacket.send(q?WarriorMove.LEFT:WarriorMove.HORIZONTAL);
            if(q||z&&button!=GLFW.GLFW_MOUSE_BUTTON_LEFT)event.setCanceled(true);
        }
    }

    /**
     * 把与连招键同键的原版按键点击全部吃掉，而不只是 Q/E 这两个默认对。
     *
     * <p>玩家把连招改到别的键（比如 F、1、聊天键）时，原版动作同样会在
     * {@code handleKeybinds} 里触发；这里统一清掉。攻击/使用两个鼠标键位不动：
     * 左键本身就是横斩，吃掉反而会让原版攻击失效。
     */
    private static void swallowConflicts(net.minecraft.client.Options options, KeyMapping combo) {
        if (combo.isUnbound()) {
            return;
        }
        for (KeyMapping mapping : options.keyMappings) {
            if (mapping == LEFT || mapping == RIGHT || mapping == STEP || mapping == combo) {
                continue;
            }
            if (mapping == options.keyAttack || mapping == options.keyUse) {
                continue;
            }
            if (!combo.getKey().equals(mapping.getKey())) {
                continue;
            }
            while (mapping.consumeClick()) {
                // 故意空转：只清点击计数
            }
        }
    }

    /** 按键注册入口，由 {@code TNMod.ClientModEvents} 显式调用（不用嵌套类自动订阅，见 WarriorPracticeKeys）。 */
    public static final class Registration {
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(LEFT);
            event.register(RIGHT);
            event.register(STEP);
            event.register(BOW_HORIZONTAL);
        }
    }
}
