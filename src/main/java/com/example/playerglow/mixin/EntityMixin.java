package com.example.playerglow.mixin;

import com.example.playerglow.PlayerGlowClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
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
		if (self == MinecraftClient.getInstance().player) return;
		if (self instanceof PlayerEntity) {
			if (PlayerGlowClient.espPlayers) cir.setReturnValue(true);
		} else if (self instanceof LivingEntity && !(self instanceof ArmorStandEntity)) {
			if (PlayerGlowClient.espMobs) cir.setReturnValue(true);
		}
	}
}
