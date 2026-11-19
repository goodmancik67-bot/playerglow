package com.example.playerglow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class PlayerGlowClient implements ClientModInitializer {
	public static boolean enabled = false;
	public static boolean xray = false;
	public static boolean aimbot = false;
	public static boolean aimOnlyWhileAttacking = true;
	public static boolean aimPlayers = true;
	public static boolean aimMobs = true;

	public static double aimRange = 5.0;
	public static float aimFov = 90f;
	public static float aimSmooth = 6f;

	private static KeyBinding menuKey;
	private static boolean nightVisionApplied = false;

	@Override
	public void onInitializeClient() {
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.playerglow.menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				"key.categories.playerglow"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new PlayerGlowScreen());
				}
			}
			tickNightVision(client);
			tickAimbot(client);
		});
	}

	private static void tickNightVision(MinecraftClient client) {
		ClientPlayerEntity p = client.player;
		if (p == null) {
			nightVisionApplied = false;
			return;
		}
		if (xray) {
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
			nightVisionApplied = true;
		} else if (nightVisionApplied) {
			p.removeStatusEffect(StatusEffects.NIGHT_VISION);
			nightVisionApplied = false;
		}
	}

	private static void tickAimbot(MinecraftClient client) {
		if (!aimbot || client.player == null || client.world == null || client.currentScreen != null) return;
		if (aimOnlyWhileAttacking && !client.options.attackKey.isPressed()) return;

		ClientPlayerEntity me = client.player;
		Vec3d eye = me.getEyePos();
		boolean found = false;
		float bestDelta = Float.MAX_VALUE;
		float bestYaw = 0f, bestPitch = 0f;

		for (Entity other : client.world.getEntities()) {
			if (other == me || !(other instanceof LivingEntity living) || !living.isAlive()) continue;
			if (other instanceof ArmorStandEntity || other.isSpectator()) continue;
			boolean isPlayer = other instanceof PlayerEntity;
			if (isPlayer ? !aimPlayers : !aimMobs) continue;

			Vec3d t = other.getBoundingBox().getCenter();
			double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist > aimRange) continue;

			float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
			float dYaw = MathHelper.wrapDegrees(yaw - me.getYaw());
			if (Math.abs(dYaw) > aimFov / 2f) continue;

			float delta = Math.abs(dYaw) + Math.abs(pitch - me.getPitch());
			if (delta < bestDelta) {
				bestDelta = delta;
				bestYaw = yaw;
				bestPitch = pitch;
				found = true;
			}
		}
		if (!found) return;

		float k = 1f / Math.max(1f, aimSmooth);
		me.setYaw(me.getYaw() + MathHelper.wrapDegrees(bestYaw - me.getYaw()) * k);
		me.setPitch(MathHelper.clamp(me.getPitch() + (bestPitch - me.getPitch()) * k, -90f, 90f));
	}
}
