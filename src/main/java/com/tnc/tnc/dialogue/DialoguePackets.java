package com.tnc.tnc.dialogue;

import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 对话的网络包（两个方向各一个）。
 *
 * <p>方向是**刻意的**：交互判定在服务端（右键 Self 是服务端事件），
 * 所以由服务端决定"播哪条剧本"，把整条剧本推给客户端播放。
 * 客户端播完再回一个 {@code DONE}，服务端据此记账（哪条剧本看过了）。
 *
 * <p>反过来的做法（客户端自己查表、自己决定播什么）会和服务器脱节 ——
 * 比如任务状态在服务端变了，客户端却还在播旧的条件分支。
 */
public final class DialoguePackets {

    private DialoguePackets() {
    }

    /** 服务端 → 客户端：播放这条剧本（{@code speaker} = 说话那个 NPC 的 UUID，可为 null）。 */
    public static class OpenDialogue {

        private DialogueScript script;
        private java.util.UUID speaker;

        public OpenDialogue() {
        }

        public OpenDialogue(DialogueScript script) {
            this(script, null);
        }

        public OpenDialogue(DialogueScript script, java.util.UUID speaker) {
            this.script = script;
            this.speaker = speaker;
        }

        public void encode(net.minecraft.network.FriendlyByteBuf buf) {
            DialogueScript.write(buf, script);
            buf.writeBoolean(speaker != null);
            if (speaker != null) {
                buf.writeUUID(speaker);
            }
        }

        public static OpenDialogue decode(net.minecraft.network.FriendlyByteBuf buf) {
            DialogueScript script = DialogueScript.read(buf);
            java.util.UUID speaker = buf.readBoolean() ? buf.readUUID() : null;
            return new OpenDialogue(script, speaker);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            DialogueScript payload = this.script;
            java.util.UUID who = this.speaker;
            // ★ 客户端类必须关在嵌套类里，绝不能在这里直接写 import：
            //   本类会被**服务端**加载，若字段/方法签名或常量池里出现客户端类名，
            //   专用服务器解析本类时就会 NoClassDefFoundError。嵌套类只在第一次
            //   真正用到时才加载 —— 而那时已经确定自己在客户端了。
            //   （同样的手法：SpellEngineBridge 把 MagicWandItem 关在 EngineWand 里。）
            context.enqueueWork(() -> ClientOnly.accept(payload, who));
            context.setPacketHandled(true);
        }

        /** 客户端专用。服务端永远不会执行到这里。 */
        private static final class ClientOnly {
            static void accept(DialogueScript script, java.util.UUID speaker) {
                com.tnc.tnc.dialogue.client.DialogueClient.accept(script, speaker);
            }
        }
    }

    /**
     * 客户端 → 服务端：<b>这一行台词带动作，请让那个 NPC 做出来</b>
     * （2026-09-22，动画系统MMM）。
     *
     * <h2>为什么由客户端发起、服务端执行</h2>
     * 台词推进的时机在客户端（玩家点一下才翻页），而<b>实体动画是服务端权威的</b> ✓ ——
     * 所以客户端只负责"报点"（我播到第几行、要对谁做），
     * 真正调 {@code triggerAnim} 的是服务端：这样多人同看时，别人也看得见 ✓，
     * 而且客户端伪造不了动画（它只能请求）✓。
     *
     * <p>目标用 <b>UUID</b> 而不是实体 id —— id 在实体卸载后会复用 ✗。
     */
    public static class ActionRequest {

        private java.util.UUID npc;
        private String action;

        public ActionRequest() {
        }

        public ActionRequest(java.util.UUID npc, String action) {
            this.npc = npc;
            this.action = action;
        }

        public void encode(net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeUUID(npc);
            buf.writeUtf(action);
        }

        public static ActionRequest decode(net.minecraft.network.FriendlyByteBuf buf) {
            return new ActionRequest(buf.readUUID(), buf.readUtf());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            java.util.UUID target = this.npc;
            String actionName = this.action;
            context.enqueueWork(() -> {
                net.minecraft.server.level.ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                // 目标必须是**这个世界里真实存在**的实体（按 UUID 查实体表）；
                // 是不是"我们的 NPC"、离玩家多远，由 NpcAnimationTrigger 再核一遍 ✓。
                // 这样客户端即使伪造 UUID 也指挥不了别的生物 ✗。
                net.minecraft.world.entity.Entity npc = player.serverLevel().getEntity(target);
                if (npc == null) {
                    com.mojang.logging.LogUtils.getLogger().warn(
                            "TN-C dialogue: action '{}' -> no entity with uuid {} is loaded",
                            actionName, target);
                    return;
                }
                // GeckoLib 的类型关在下面那个嵌套类里：本类在**服务端**也会被加载，
                // 直接在方法体里写 GeckoLib 的类型会让没装 GeckoLib 的环境解析失败 ✗
                // （与 OpenDialogue 里隔离客户端类是同一个道理）。
                ActionRunner.run(player, npc, actionName);
            });
            context.setPacketHandled(true);
        }

        /** 真正去让实体做动作 —— 只有装了 GeckoLib 才会走到这里 ✓。 */
        private static final class ActionRunner {
            static void run(net.minecraft.server.level.ServerPlayer player,
                            net.minecraft.world.entity.Entity npc, String actionName) {
                com.tnc.tnc.npc.compat.NpcAnimationTrigger.trigger(player, npc, actionName);
            }
        }
    }

    /** 客户端 → 服务端：这条剧本播完了。 */
    public static class DialogueDone {
        private net.minecraft.resources.ResourceLocation id;

        public DialogueDone() {
        }

        public DialogueDone(net.minecraft.resources.ResourceLocation id) {
            this.id = id;
        }

        public void encode(net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeResourceLocation(id);
        }

        public static DialogueDone decode(net.minecraft.network.FriendlyByteBuf buf) {
            return new DialogueDone(buf.readResourceLocation());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            net.minecraft.resources.ResourceLocation payload = this.id;
            context.enqueueWork(() -> {
                net.minecraft.server.level.ServerPlayer player = context.getSender();
                if (player != null) {
                    DialogueProgress.markSeen(player, payload);
                    // ★ 接力：这段演完了，如果它写了 @next，就把下一段推过去。
                    //   放在 markSeen 之后 —— 万一 next 加载失败，至少"看过了"已经落账，
                    //   玩家再右键还是能从第一段重新走，不会卡住。
                    DialogueLoader.get(player.server.getResourceManager(), payload)
                            .ifPresent(script -> DialogueNetwork.playNext(player, script));
                }
            });
            context.setPacketHandled(true);
        }
    }
}
