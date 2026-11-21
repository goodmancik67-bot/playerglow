package com.example.playerglow.mixin;

import com.example.playerglow.PlayerGlowClient;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Animated black-to-gray shimmer on sword models (multiplies the quad colour). */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
	@ModifyArgs(method = "renderBakedItemQuads",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;FFFFII)V"),
			require = 0)
	private static void playerglow$shimmer(Args args) {
		if (!PlayerGlowClient.shimmerActive) return;
		float v = PlayerGlowClient.shimmerValue(PlayerGlowClient.shimmerIdx++);
		args.set(2, v);
		args.set(3, v);
		args.set(4, v);
	}
}
