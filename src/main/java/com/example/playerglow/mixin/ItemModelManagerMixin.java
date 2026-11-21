package com.example.playerglow.mixin;

import com.example.playerglow.PlayerGlowClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Remembers, per item render state, whether the stack being drawn is a sword. */
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {
	@Inject(method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",
			at = @At("HEAD"), require = 0)
	private void playerglow$mark7(ItemRenderState state, ItemStack stack, ModelTransformationMode mode,
			boolean leftHand, World world, LivingEntity entity, int seed, CallbackInfo ci) {
		((PlayerGlowClient.SwordFlag) (Object) state).playerglow$setSword(PlayerGlowClient.isSword(stack));
	}

	@Inject(method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",
			at = @At("HEAD"), require = 0)
	private void playerglow$mark6(ItemRenderState state, ItemStack stack, ModelTransformationMode mode,
			World world, LivingEntity entity, int seed, CallbackInfo ci) {
		((PlayerGlowClient.SwordFlag) (Object) state).playerglow$setSword(PlayerGlowClient.isSword(stack));
	}

	// Glow Hand: swap the model of swords for our own purple one (7-arg variant)
	@ModifyVariable(method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack playerglow$glow7(ItemStack stack) {
		return PlayerGlowClient.glowStack(stack);
	}

	// ... and the 6-arg variant
	@ModifyVariable(method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack playerglow$glow6(ItemStack stack) {
		return PlayerGlowClient.glowStack(stack);
	}
}
