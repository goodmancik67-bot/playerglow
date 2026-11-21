package com.example.playerglow.mixin;

import com.example.playerglow.PlayerGlowClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Switches the sword shimmer on only while a sword is actually being drawn. */
@Mixin(ItemRenderState.class)
public abstract class ItemRenderStateMixin implements PlayerGlowClient.SwordFlag {
	@Unique
	private boolean playerglow$sword = false;

	@Override
	public void playerglow$setSword(boolean sword) {
		this.playerglow$sword = sword;
	}

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
			at = @At("HEAD"), require = 0)
	private void playerglow$begin(MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay, CallbackInfo ci) {
		PlayerGlowClient.shimmerActive = PlayerGlowClient.swordShimmer && this.playerglow$sword;
		PlayerGlowClient.shimmerIdx = 0;
	}

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
			at = @At("RETURN"), require = 0)
	private void playerglow$end(MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay, CallbackInfo ci) {
		PlayerGlowClient.shimmerActive = false;
	}
}
