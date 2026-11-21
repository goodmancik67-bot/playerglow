package com.example.playerglow.mixin;

import com.example.playerglow.PlayerGlowClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Swaps sword quads over to the animated purple sword texture and draws them fullbright. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
	@ModifyArgs(method = "renderBakedItemQuads",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;FFFFII)V"),
			require = 0)
	private static void playerglow$shimmer(Args args) {
		if (!PlayerGlowClient.shimmerActive) return;
		BakedQuad q = args.get(1);
		BakedQuad n = PlayerGlowClient.swordQuad(q);
		if (n != null && n != q) {
			args.set(1, n);
			args.set(2, 1.0f);
			args.set(3, 1.0f);
			args.set(4, 1.0f);
			args.set(6, 15728880);
		}
	}
}
