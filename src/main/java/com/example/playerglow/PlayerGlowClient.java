package com.example.playerglow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class PlayerGlowClient implements ClientModInitializer {
	public static boolean enabled = false;
	public static boolean xray = false;
	public static boolean aimbot = false;
	public static boolean crystalAura = false;
	public static boolean triggerbot = false;
	public static boolean criticals = false;
	public static boolean targetHud = false;

	public static boolean crystalAutoPlace = true;
	public static boolean crystalAutoBreak = true;
	public static double crystalRange = 4.5;
	public static float crystalDelayTicks = 2f;

	public static boolean hitMarker = false;
	public static boolean infoHud = false;

	public static boolean espPlayers = true;
	public static boolean espMobs = true;
	public static boolean espBox = true;
	public static int espColor = 0x3A9BFF;
	public static float espAlpha = 0.85f;

	public static boolean chinaHat = false;
	public static boolean hatPlayers = true;
	public static boolean hatMobs = true;
	public static int hatColor = 0xF2C14E;
	public static float hatAlpha = 0.9f;
	public static boolean aimOnlyWhileAttacking = true;
	public static boolean aimPlayers = true;
	public static boolean aimMobs = true;

	public static double aimRange = 5.0;
	public static float aimFov = 90f;
	public static float aimSmooth = 6f;

	private static KeyBinding menuKey;
	private static boolean nightVisionApplied = false;
	private static int crystalDelayLeft = 0;
	private static int recentCrystal = 0;
	private static int pendingTicks = 0;
	private static BlockPos pendingObsidian = null;

	@Override
	public void onInitializeClient() {
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.playerglow.menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				"key.categories.playerglow"));

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient && entity instanceof LivingEntity living) {
				UiDraw.noteHit(living);
				if (criticals) doCrit(player);
			}
			return ActionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient && crystalAura && crystalAutoPlace
					&& player.getStackInHand(hand).isOf(Items.OBSIDIAN)) {
				pendingObsidian = hit.getBlockPos().offset(hit.getSide());
				pendingTicks = 20;
			}
			return ActionResult.PASS;
		});

		WorldRenderEvents.AFTER_TRANSLUCENT.register(PlayerGlowClient::renderWorld);
		HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
			UiDraw.drawInfoHud(ctx);
			UiDraw.drawTargetHud(ctx);
			UiDraw.drawHitMarker(ctx);
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new PlayerGlowScreen());
				}
			}
			tickNightVision(client);
			tickAimbot(client);
			tickTriggerbot(client);
			tickCrystalAura(client);
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

	private static void tickTriggerbot(MinecraftClient client) {
		if (!triggerbot || client.player == null || client.world == null
				|| client.interactionManager == null || client.currentScreen != null) return;
		if (!(client.crosshairTarget instanceof EntityHitResult hit)) return;

		ClientPlayerEntity me = client.player;
		Entity target = hit.getEntity();
		if (!(target instanceof LivingEntity living) || !living.isAlive()) return;
		if (target instanceof ArmorStandEntity || target.isSpectator()) return;
		boolean isPlayer = target instanceof PlayerEntity;
		if (isPlayer ? !aimPlayers : !aimMobs) return;

		if (me.getAttackCooldownProgress(0.5f) < 0.95f) return;
		client.interactionManager.attackEntity(me, target);
		me.swingHand(Hand.MAIN_HAND);
	}

	private static Hand crystalHand(ClientPlayerEntity me) {
		if (me.getMainHandStack().isOf(Items.END_CRYSTAL)) return Hand.MAIN_HAND;
		if (me.getOffHandStack().isOf(Items.END_CRYSTAL)) return Hand.OFF_HAND;
		return null;
	}

	private static boolean tryPlaceCrystal(MinecraftClient client, ClientPlayerEntity me, Hand hand, BlockPos pos) {
		Vec3d top = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
		if (me.getEyePos().squaredDistanceTo(top) > 4.5 * 4.5) return false;
		if (!client.world.getBlockState(pos.up()).isAir() || !client.world.getBlockState(pos.up(2)).isAir()) return false;
		Box space = new Box(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1);
		if (!client.world.getOtherEntities(null, space).isEmpty()) return false;

		BlockHitResult bhr = new BlockHitResult(top, Direction.UP, pos, false);
		ActionResult r = client.interactionManager.interactBlock(me, hand, bhr);
		if (r.isAccepted()) {
			me.swingHand(hand);
			return true;
		}
		return false;
	}

	private static void tickCrystalAura(MinecraftClient client) {
		if (!crystalAura || client.player == null || client.world == null
				|| client.interactionManager == null || client.currentScreen != null) {
			pendingObsidian = null;
			return;
		}
		ClientPlayerEntity me = client.player;
		if (recentCrystal > 0) recentCrystal--;
		if (crystalDelayLeft > 0) {
			crystalDelayLeft--;
			return;
		}
		int delay = Math.round(crystalDelayTicks);

		// 1) auto place on freshly placed obsidian
		if (crystalAutoPlace && pendingObsidian != null) {
			pendingTicks--;
			if (pendingTicks < 0) {
				pendingObsidian = null;
			} else if (client.world.getBlockState(pendingObsidian).isOf(Blocks.OBSIDIAN)) {
				Hand hand = crystalHand(me);
				if (hand != null && tryPlaceCrystal(client, me, hand, pendingObsidian)) {
					pendingObsidian = null;
					recentCrystal = 40;
					crystalDelayLeft = delay;
					return;
				}
			}
		}

		// 2) auto break
		if (!crystalAutoBreak) return;
		boolean holdBreak = client.options.useKey.isPressed() && crystalHand(me) != null;
		if (recentCrystal <= 0 && !holdBreak) return;

		EndCrystalEntity best = null;
		double bestDist = crystalRange * crystalRange;
		for (EndCrystalEntity crystal : client.world.getEntitiesByClass(
				EndCrystalEntity.class, me.getBoundingBox().expand(crystalRange + 1.0), e -> e.isAlive())) {
			double d = me.getEyePos().squaredDistanceTo(crystal.getPos());
			if (d <= bestDist) {
				bestDist = d;
				best = crystal;
			}
		}
		if (best == null) return;

		client.interactionManager.attackEntity(me, best);
		me.swingHand(Hand.MAIN_HAND);
		crystalDelayLeft = delay;
	}

	// ---------- 3D ESP box + China hat ----------

	private static boolean wanted(Entity e, boolean players, boolean mobs) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (e == mc.player || !(e instanceof LivingEntity le) || !le.isAlive()) return false;
		if (e instanceof ArmorStandEntity || e.isSpectator()) return false;
		if (e.squaredDistanceTo(mc.player) > 96.0 * 96.0) return false;
		return (e instanceof PlayerEntity) ? players : mobs;
	}

	private static void renderWorld(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return;
		boolean doBox = enabled && espBox;
		boolean doHat = chinaHat;
		if (!doBox && !doHat) return;
		MatrixStack ms = ctx.matrixStack();
		if (ms == null) return;

		Vec3d cam = ctx.camera().getPos();
		float td = mc.getRenderTickCounter().getTickDelta(false);
		Matrix4f m = ms.peek().getPositionMatrix();

		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();

		if (doBox) {
			List<Entity> targets = new ArrayList<>();
			for (Entity e : mc.world.getEntities()) {
				if (wanted(e, espPlayers, espMobs)) targets.add(e);
			}
			if (!targets.isEmpty()) {
				BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
				for (Entity e : targets) {
					Vec3d p = e.getLerpedPos(td);
					Vec3d d = p.subtract(e.getPos());
					Box b = e.getBoundingBox().offset(d.x - cam.x, d.y - cam.y, d.z - cam.z);
					float dist = (float) Math.sqrt(e.squaredDistanceTo(mc.player));
					float t = 0.012f + dist * 0.0016f;
					UiDraw.espBox(bb, m, (float) b.minX, (float) b.minY, (float) b.minZ,
							(float) b.maxX, (float) b.maxY, (float) b.maxZ, espColor, espAlpha, t);
				}
				RenderSystem.disableDepthTest();
				BufferRenderer.drawWithGlobalProgram(bb.end());
				RenderSystem.enableDepthTest();
			}
		}

		if (doHat) {
			List<Entity> targets = new ArrayList<>();
			for (Entity e : mc.world.getEntities()) {
				if (wanted(e, hatPlayers, hatMobs)) targets.add(e);
			}
			if (!targets.isEmpty()) {
				BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
				float spin = (System.currentTimeMillis() % 9000L) / 9000f * (float) (Math.PI * 2);
				for (Entity e : targets) {
					Vec3d p = e.getLerpedPos(td);
					float bx = (float) (p.x - cam.x);
					float by = (float) (p.y - cam.y) + e.getHeight() + 0.04f;
					float bz = (float) (p.z - cam.z);
					float radius = Math.max(0.34f, e.getWidth() * 0.9f);
					UiDraw.chinaHat(bb, m, bx, by, bz, radius, spin, hatColor, hatAlpha);
				}
				RenderSystem.enableDepthTest();
				BufferRenderer.drawWithGlobalProgram(bb.end());
			}
		}

		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static void doCrit(PlayerEntity p) {
		if (!p.isOnGround() || p.isTouchingWater() || p.isInLava() || p.isClimbing() || p.hasVehicle()) return;
		ClientPlayNetworkHandler h = MinecraftClient.getInstance().getNetworkHandler();
		if (h == null) return;
		double x = p.getX(), y = p.getY(), z = p.getZ();
		h.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, false));
		h.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
	}
}
