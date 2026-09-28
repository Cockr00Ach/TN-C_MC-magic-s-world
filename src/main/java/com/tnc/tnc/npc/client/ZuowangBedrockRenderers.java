package com.tnc.tnc.npc.client;

import com.tnc.tnc.npc.TNNpcs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * 周坐望的 Bedrock 模型渲染器注册 —— 只有装了 GeckoLib 时才会被调用 ✓
 * （分流在 {@code com.tnc.tnc.npc.compat.GeoSelfSupport}，客户端和服务端用同一个判断 ✓）。
 *
 * <p>为什么要强转：实体的注册类型是 {@code EntityType<ZuowangNpcEntity>}（降级版），
 * 而渲染器是 {@code EntityRenderer<ZuowangBedrockNpcEntity>}（子类）——
 * Java 泛型不协变，这里必须用原始类型转一下 ✓
 * （与 {@link SelfBedrockRenderers} / {@link MaidNpcRenderers} 同一套写法）。
 */
public final class ZuowangBedrockRenderers {

    private ZuowangBedrockRenderers() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer((EntityType) TNNpcs.ZUOWANG.get(),
                (EntityRendererProvider) ZuowangBedrockRenderer::new);
    }
}
