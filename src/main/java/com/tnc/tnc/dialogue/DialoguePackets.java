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

    /** 服务端 → 客户端：播放这条剧本。 */
    public static class OpenDialogue {

        private DialogueScript script;

        public OpenDialogue() {
        }

        public OpenDialogue(DialogueScript script) {
            this.script = script;
        }

        public void encode(net.minecraft.network.FriendlyByteBuf buf) {
            DialogueScript.write(buf, script);
        }

        public static OpenDialogue decode(net.minecraft.network.FriendlyByteBuf buf) {
            return new OpenDialogue(DialogueScript.read(buf));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            DialogueScript payload = this.script;
            // ★ 客户端类必须关在嵌套类里，绝不能在这里直接写 import：
            //   本类会被**服务端**加载，若字段/方法签名或常量池里出现客户端类名，
            //   专用服务器解析本类时就会 NoClassDefFoundError。嵌套类只在第一次
            //   真正用到时才加载 —— 而那时已经确定自己在客户端了。
            //   （同样的手法：SpellEngineBridge 把 MagicWandItem 关在 EngineWand 里。）
            context.enqueueWork(() -> ClientOnly.accept(payload));
            context.setPacketHandled(true);
        }

        /** 客户端专用。服务端永远不会执行到这里。 */
        private static final class ClientOnly {
            static void accept(DialogueScript script) {
                com.tnc.tnc.dialogue.client.DialogueClient.accept(script);
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
                }
            });
            context.setPacketHandled(true);
        }
    }
}
