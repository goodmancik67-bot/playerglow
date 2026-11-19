package com.example.playerglow.mixin;

import com.example.playerglow.PlayerGlowClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
	private void playerglow$forceGlow(CallbackInfoReturnable<Boolean> cir) {
		if (!PlayerGlowClient.enabled) return;
		Object self = this;
		if (self instanceof PlayerEntity && self != MinecraftClient.getInstance().player) {
			cir.setReturnValue(true);
		}
	}
}
