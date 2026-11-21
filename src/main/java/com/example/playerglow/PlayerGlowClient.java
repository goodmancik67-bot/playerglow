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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.Optional;
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
	public static boolean swordShimmer = false;
	public static boolean trajectories = false;
	public static boolean autoShift = false;

	// set by the item-render mixins while a sword is being drawn
	public static boolean shimmerActive = false;
	public static int shimmerIdx = 0;

	/** Implemented by ItemRenderState (via mixin) to remember whether the stack is a sword. */
	public interface SwordFlag {
		void playerglow$setSword(boolean sword);
	}

	public static boolean isSword(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_sword");
	}

	/** Slow black-to-gray wave, phase-shifted per quad so it ripples across the blade. */
	public static float shimmerValue(int idx) {
		double t = (System.nanoTime() / 1_000_000_000.0) * 0.8;
		double w = 0.5 + 0.5 * Math.sin(t + idx * 0.55);
		return (float) (0.10 + 0.55 * w);
	}
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
	private static int shiftTicks = 0;
	private static boolean shiftForced = false;
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
				if (autoShift) shiftTicks = 4;
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
			tickAutoShift(client);
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
		boolean doTraj = trajectories && heldThrowKind(mc.player) != 0;
		if (!doBox && !doHat && !doTraj) return;
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

		if (doTraj) {
			List<Vec3d> pts = new ArrayList<>();
			int kind = simulateThrow(mc, td, pts);
			if (kind != 0 && pts.size() > 1) {
				int rgb = kind == 1 ? 0xA78BFA : 0xF2668B;
				BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
				int n = pts.size();
				for (int i = 0; i < n - 1; i++) {
					Vec3d a = pts.get(i).subtract(cam);
					Vec3d b = pts.get(i + 1).subtract(cam);
					int alpha = Math.round(230f * (1f - 0.55f * i / n));
					ribbon(bb, m, a, b, 0.045f, rgb, alpha);
				}
				Vec3d end = pts.get(n - 1).subtract(cam);
				float ex = (float) end.x, ey = (float) end.y, ez = (float) end.z;
				int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
				UiDraw.cuboid(bb, m, ex - 0.07f, ey - 0.07f, ez - 0.07f, ex + 0.07f, ey + 0.07f, ez + 0.07f, r, g, bl, 255);
				UiDraw.cuboid(bb, m, ex - 0.2f, ey - 0.2f, ez - 0.2f, ex + 0.2f, ey + 0.2f, ez + 0.2f, r, g, bl, 70);
				RenderSystem.enableDepthTest();
				BufferRenderer.drawWithGlobalProgram(bb.end());
			}
		}

		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static void tickAutoShift(MinecraftClient client) {
		if (client.player == null || client.currentScreen != null) {
			shiftTicks = 0;
			shiftForced = false;
			return;
		}
		if (autoShift && shiftTicks > 0) {
			shiftTicks--;
			client.options.sneakKey.setPressed(true);
			shiftForced = true;
		} else if (shiftForced) {
			shiftForced = false;
			int code = KeyBindingHelper.getBoundKeyOf(client.options.sneakKey).getCode();
			client.options.sneakKey.setPressed(InputUtil.isKeyPressed(client.getWindow().getHandle(), code));
		}
	}

	// ---------- throw trajectory (ender pearl / potions) ----------

	private static int throwKind(ItemStack s) {
		if (s == null || s.isEmpty()) return 0;
		if (s.isOf(Items.ENDER_PEARL)) return 1;
		if (s.isOf(Items.SPLASH_POTION) || s.isOf(Items.LINGERING_POTION)) return 2;
		return 0;
	}

	private static int heldThrowKind(ClientPlayerEntity p) {
		int k = throwKind(p.getMainHandStack());
		return k != 0 ? k : throwKind(p.getOffHandStack());
	}

	/** Fills out with world-space points of the flight path. Returns the item kind (0 = nothing to draw). */
	private static int simulateThrow(MinecraftClient mc, float td, List<Vec3d> out) {
		ClientPlayerEntity p = mc.player;
		int kind = heldThrowKind(p);
		if (kind == 0) return 0;

		float speed = kind == 1 ? 1.5f : 0.5f;
		float roll = kind == 1 ? 0f : -20f;
		double gravity = kind == 1 ? 0.03 : 0.05;
		float pitch = p.getPitch(td), yaw = p.getYaw(td);
		double rad = Math.PI / 180.0;
		double fx = -Math.sin(yaw * rad) * Math.cos(pitch * rad);
		double fy = -Math.sin((pitch + roll) * rad);
		double fz = Math.cos(yaw * rad) * Math.cos(pitch * rad);
		Vec3d vel = new Vec3d(fx, fy, fz).normalize().multiply(speed);
		Vec3d pv = p.getVelocity();
		vel = vel.add(pv.x, p.isOnGround() ? 0.0 : pv.y, pv.z);
		Vec3d pos = p.getLerpedPos(td).add(0.0, p.getEyeY() - p.getY() - 0.1, 0.0);

		out.add(pos);
		for (int i = 0; i < 200; i++) {
			Vec3d next = pos.add(vel);
			boolean stop = false;
			double best = Double.MAX_VALUE;

			BlockHitResult bh = mc.world.raycast(new RaycastContext(pos, next,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
			if (bh.getType() != HitResult.Type.MISS) {
				next = bh.getPos();
				best = pos.squaredDistanceTo(next);
				stop = true;
			}
			for (Entity e : mc.world.getEntities()) {
				if (e == p || !(e instanceof LivingEntity) || e.isSpectator()) continue;
				Optional<Vec3d> r = e.getBoundingBox().expand(0.3).raycast(pos, next);
				if (r.isPresent()) {
					double d = pos.squaredDistanceTo(r.get());
					if (d < best) {
						best = d;
						next = r.get();
						stop = true;
					}
				}
			}
			out.add(next);
			if (stop) break;
			pos = next;
			vel = vel.multiply(0.99).subtract(0.0, gravity, 0.0);
			if (pos.y < mc.world.getBottomY() - 16) break;
		}
		return kind;
	}

	private static void ribbon(BufferBuilder bb, Matrix4f m, Vec3d a, Vec3d b, float width, int rgb, int alpha) {
		Vec3d dir = b.subtract(a);
		Vec3d mid = a.add(b).multiply(0.5);
		Vec3d side = dir.crossProduct(mid);
		double len = side.length();
		if (len < 1.0e-6) return;
		side = side.multiply(width * 0.5 / len);
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
		UiDraw.quad(bb, m,
				(float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z),
				(float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z),
				(float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z),
				(float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z),
				r, g, bl, alpha);
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
