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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.BowItem;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
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
		return glowKind(stack) != null;
	}

	/** sword / pickaxe / axe / shovel -> which glow model to use (null = not a glow tool). */
	private static String glowKind(ItemStack stack) {
		String path = Registries.ITEM.getId(stack.getItem()).getPath();
		if (path.endsWith("_sword")) return "sword";
		if (path.endsWith("_pickaxe")) return "pickaxe";
		if (path.endsWith("_axe")) return "axe";
		if (path.endsWith("_shovel")) return "shovel";
		return null;
	}

	/** Glow Hand: draws swords with our own purple animated item model instead of the vanilla one. */
	public static ItemStack glowStack(ItemStack stack) {
		try {
			if (!swordShimmer || stack == null || stack.isEmpty()) return stack;
			String kind = glowKind(stack);
			if (kind == null) return stack;
			ItemStack c = stack.copy();
			c.set(net.minecraft.component.DataComponentTypes.ITEM_MODEL,
					net.minecraft.util.Identifier.of("playerglow", "glow_" + kind));
			return c;
		} catch (Throwable t) {
			return stack;
		}
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

	public static boolean itemEsp = false;
	public static int itemColor = 0xFFD84A;
	public static float itemAlpha = 0.85f;
	public static boolean blockOutline = false;
	public static boolean tracers = false;
	public static boolean autoTotem = false;
	public static boolean fastPlace = false;
	public static float fastPlaceDelay = 0f;
	public static boolean autoSprint = false;
	public static boolean sprintStopOnHit = true;
	public static boolean autoWeapon = false;
	public static boolean autoBlock = false;
	public static float abRange = 4.0f;
	public static boolean abPlayers = true;
	public static boolean abMobs = true;
	public static boolean velocity = false;
	public static float velH = 0f;   // % of horizontal knockback that is kept
	public static float velV = 100f; // % of vertical knockback that is kept
	public static boolean armorHud = false;
	public static boolean cyclone = false, cycRing = true, cycPlayers = true, cycMobs = true;
	public static int cycColor = 0xA78BFA;
	public static float cycRange = 4.2f;
	private static int cycIdx = 0;
	public static boolean targetEsp = false;
	public static boolean espArrows = false, arrowPlayers = true, arrowMobs = true;
	public static int arrowColor = 0xFFFFFF;
	public static float arrowAlpha = 1.0f;
	public static int targetColor = 0xFFFFFF;
	public static float targetAlpha = 1.0f;
	public static float targetSpeed = 1.2f;
	private static net.minecraft.entity.LivingEntity teTarget = null;
	private static float teFade = 0f;
	private static long teLast = 0L;
	private static boolean abHolding = false;

	// hotkeys: module name -> GLFW key code
	public static final java.util.LinkedHashMap<String, Integer> binds = new java.util.LinkedHashMap<>();
	private static final java.util.HashSet<String> heldBinds = new java.util.HashSet<>();

	private static final class Tg {
		final String name;
		final java.util.function.BooleanSupplier get;
		final java.util.function.Consumer<Boolean> set;

		Tg(String name, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
			this.name = name;
			this.get = get;
			this.set = set;
		}
	}

	private static final java.util.List<Tg> TOGGLES = new java.util.ArrayList<>();

	private static void regToggles() {
		TOGGLES.add(new Tg("Aimbot", () -> aimbot, v -> aimbot = v));
		TOGGLES.add(new Tg("CrystalAura", () -> crystalAura, v -> crystalAura = v));
		TOGGLES.add(new Tg("TriggerBot", () -> triggerbot, v -> triggerbot = v));
		TOGGLES.add(new Tg("Auto Totem", () -> autoTotem, v -> autoTotem = v));
		TOGGLES.add(new Tg("Criticals", () -> criticals, v -> criticals = v));
		TOGGLES.add(new Tg("Auto Shift", () -> autoShift, v -> autoShift = v));
		TOGGLES.add(new Tg("Player Glow", () -> enabled, v -> enabled = v));
		TOGGLES.add(new Tg("China Hat", () -> chinaHat, v -> chinaHat = v));
		TOGGLES.add(new Tg("X-Ray", () -> xray, v -> {
			xray = v;
			MinecraftClient.getInstance().worldRenderer.reload();
		}));
		TOGGLES.add(new Tg("Target HUD", () -> targetHud, v -> targetHud = v));
		TOGGLES.add(new Tg("Hit Marker", () -> hitMarker, v -> hitMarker = v));
		TOGGLES.add(new Tg("Info HUD", () -> infoHud, v -> infoHud = v));
		TOGGLES.add(new Tg("Glow Hand", () -> swordShimmer, v -> swordShimmer = v));
		TOGGLES.add(new Tg("Item ESP", () -> itemEsp, v -> itemEsp = v));
		TOGGLES.add(new Tg("Block Outline", () -> blockOutline, v -> blockOutline = v));
		TOGGLES.add(new Tg("Tracers", () -> tracers, v -> tracers = v));
		TOGGLES.add(new Tg("Trajectories", () -> trajectories, v -> trajectories = v));
		TOGGLES.add(new Tg("Fast Place", () -> fastPlace, v -> fastPlace = v));
		TOGGLES.add(new Tg("Auto Sprint", () -> autoSprint, v -> autoSprint = v));
		TOGGLES.add(new Tg("Auto Weapon", () -> autoWeapon, v -> autoWeapon = v));
		TOGGLES.add(new Tg("Auto Block", () -> autoBlock, v -> autoBlock = v));
		TOGGLES.add(new Tg("Velocity", () -> velocity, v -> velocity = v));
		TOGGLES.add(new Tg("Armor HUD", () -> armorHud, v -> armorHud = v));
		TOGGLES.add(new Tg("Target ESP", () -> targetEsp, v -> targetEsp = v));
		TOGGLES.add(new Tg("ESP Arrows", () -> espArrows, v -> espArrows = v));
		TOGGLES.add(new Tg("Cyclone Aura", () -> cyclone, v -> cyclone = v));
	}

	/** Short label for a key shown on the module card. */
	public static String keyLabel(int key) {
		switch (key) {
			case GLFW.GLFW_KEY_LEFT_SHIFT: return "LShift";
			case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RShift";
			case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCtrl";
			case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCtrl";
			case GLFW.GLFW_KEY_LEFT_ALT: return "LAlt";
			case GLFW.GLFW_KEY_RIGHT_ALT: return "RAlt";
			case GLFW.GLFW_KEY_SPACE: return "Space";
			case GLFW.GLFW_KEY_TAB: return "Tab";
			case GLFW.GLFW_KEY_CAPS_LOCK: return "Caps";
			default:
				try {
					String t = InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
					return t.length() > 6 ? t.substring(0, 6) : t;
				} catch (Throwable e) {
					return "?";
				}
		}
	}

	private static void tickBinds(MinecraftClient c) {
		if (c.player == null || c.currentScreen != null || hidden) {
			heldBinds.clear();
			return;
		}
		long h = c.getWindow().getHandle();
		for (Tg t : TOGGLES) {
			Integer k = binds.get(t.name);
			if (k == null || k <= 0) continue;
			boolean down = InputUtil.isKeyPressed(h, k);
			if (down) {
				if (heldBinds.add(t.name)) {
					boolean now = !t.get.getAsBoolean();
					t.set.accept(now);
					c.player.sendMessage(net.minecraft.text.Text.literal(t.name + ": " + (now ? "ON" : "OFF")), true);
				}
			} else {
				heldBinds.remove(t.name);
			}
		}
	}

	// block outline animation state (world coordinates)
	private static double[] boBox = null;
	private static float boFade = 0f;
	private static long boLast = 0L;
	private static BlockPos boLastPos = null;

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
	private static boolean hidden = false;
	private static final java.util.LinkedHashMap<String, Boolean> hiddenSnap = new java.util.LinkedHashMap<>();

	/** Called from the chat mixin: returns true when the message was our command. */
	public static boolean chatCommand(String msg) {
		if (msg == null || !msg.trim().equalsIgnoreCase(".playboi")) return false;
		toggleHidden();
		return true;
	}

	public static void toggleHidden() {
		MinecraftClient mc = MinecraftClient.getInstance();
		setHidden(!hidden);
		if (mc.player != null) {
			mc.player.sendMessage(net.minecraft.text.Text.literal(hidden ? "Playboi: hidden" : "Playboi: visible"), true);
		}
	}

	/** .playboi: switch every module off (and the menu/binds) or bring the previous state back. */
	private static void setHidden(boolean h) {
		if (h == hidden) return;
		if (h) {
			hiddenSnap.clear();
			for (Tg t : TOGGLES) hiddenSnap.put(t.name, t.get.getAsBoolean());
			for (Tg t : TOGGLES) t.set.accept(false);
		} else {
			for (Tg t : TOGGLES) {
				Boolean v = hiddenSnap.get(t.name);
				if (v != null) t.set.accept(v);
			}
		}
		hidden = h;
	}
	private static boolean nightVisionApplied = false;
	private static int shiftTicks = 0;
	private static boolean shiftForced = false;
	private static int crystalDelayLeft = 0;
	private static int recentCrystal = 0;
	private static int pendingTicks = 0;
	private static BlockPos pendingObsidian = null;

	// ---------- config (saved to .minecraft/config/playboi_carti_client.properties) ----------

	private static Path cfgPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("playboi_carti_client.properties");
	}

	private static boolean gb(Properties p, String k, boolean d) {
		String v = p.getProperty(k);
		return v == null ? d : Boolean.parseBoolean(v);
	}

	private static double gd(Properties p, String k, double d) {
		try {
			String v = p.getProperty(k);
			return v == null ? d : Double.parseDouble(v);
		} catch (Exception e) {
			return d;
		}
	}

	private static int gi(Properties p, String k, int d) {
		try {
			String v = p.getProperty(k);
			return v == null ? d : Integer.parseInt(v);
		} catch (Exception e) {
			return d;
		}
	}

	public static void loadConfig() {
		try {
			Path f = cfgPath();
			if (!Files.exists(f)) return;
			Properties p = new Properties();
			try (InputStream in = Files.newInputStream(f)) {
				p.load(in);
			}
			enabled = gb(p, "enabled", enabled);
			xray = gb(p, "xray", xray);
			aimbot = gb(p, "aimbot", aimbot);
			crystalAura = gb(p, "crystalAura", crystalAura);
			triggerbot = gb(p, "triggerbot", triggerbot);
			criticals = gb(p, "criticals", criticals);
			autoShift = gb(p, "autoShift", autoShift);
			targetHud = gb(p, "targetHud", targetHud);
			hitMarker = gb(p, "hitMarker", hitMarker);
			infoHud = gb(p, "infoHud", infoHud);
			swordShimmer = gb(p, "swordShimmer", swordShimmer);
			itemEsp = gb(p, "itemEsp", itemEsp);
			itemColor = gi(p, "itemColor", itemColor);
			itemAlpha = (float) gd(p, "itemAlpha", itemAlpha);
			blockOutline = gb(p, "blockOutline", blockOutline);
			tracers = gb(p, "tracers", tracers);
			autoTotem = gb(p, "autoTotem", autoTotem);
			fastPlace = gb(p, "fastPlace", fastPlace);
			fastPlaceDelay = (float) gd(p, "fastPlaceDelay", fastPlaceDelay);
			sprintStopOnHit = gb(p, "sprintStopOnHit", sprintStopOnHit);
			autoWeapon = gb(p, "autoWeapon", autoWeapon);
			autoBlock = gb(p, "autoBlock", autoBlock);
			abRange = (float) gd(p, "abRange", abRange);
			abPlayers = gb(p, "abPlayers", abPlayers);
			abMobs = gb(p, "abMobs", abMobs);
			velocity = gb(p, "velocity", velocity);
			velH = (float) gd(p, "velH", velH);
			velV = (float) gd(p, "velV", velV);
			armorHud = gb(p, "armorHud", armorHud);
			targetEsp = gb(p, "targetEsp", targetEsp);
			cyclone = gb(p, "cyclone", cyclone);
			cycRing = gb(p, "cycRing", cycRing);
			cycPlayers = gb(p, "cycPlayers", cycPlayers);
			cycMobs = gb(p, "cycMobs", cycMobs);
			cycColor = gi(p, "cycColor", cycColor);
			cycRange = (float) gd(p, "cycRange", cycRange);
			espArrows = gb(p, "espArrows", espArrows);
			arrowPlayers = gb(p, "arrowPlayers", arrowPlayers);
			arrowMobs = gb(p, "arrowMobs", arrowMobs);
			arrowColor = gi(p, "arrowColor", arrowColor);
			arrowAlpha = (float) gd(p, "arrowAlpha", arrowAlpha);
			targetColor = gi(p, "targetColor", targetColor);
			targetAlpha = (float) gd(p, "targetAlpha", targetAlpha);
			targetSpeed = (float) gd(p, "targetSpeed", targetSpeed);
			binds.clear();
			for (String key : p.stringPropertyNames()) {
				if (!key.startsWith("bind.")) continue;
				try {
					binds.put(key.substring(5).replace('_', ' '), Integer.parseInt(p.getProperty(key).trim()));
				} catch (Exception ignored) {
				}
			}
			autoSprint = gb(p, "autoSprint", autoSprint);
			trajectories = gb(p, "trajectories", trajectories);
			crystalAutoPlace = gb(p, "crystalAutoPlace", crystalAutoPlace);
			crystalAutoBreak = gb(p, "crystalAutoBreak", crystalAutoBreak);
			crystalRange = gd(p, "crystalRange", crystalRange);
			crystalDelayTicks = (float) gd(p, "crystalDelayTicks", crystalDelayTicks);
			espPlayers = gb(p, "espPlayers", espPlayers);
			espMobs = gb(p, "espMobs", espMobs);
			espBox = gb(p, "espBox", espBox);
			espColor = gi(p, "espColor", espColor);
			espAlpha = (float) gd(p, "espAlpha", espAlpha);
			chinaHat = gb(p, "chinaHat", chinaHat);
			hatPlayers = gb(p, "hatPlayers", hatPlayers);
			hatMobs = gb(p, "hatMobs", hatMobs);
			hatColor = gi(p, "hatColor", hatColor);
			hatAlpha = (float) gd(p, "hatAlpha", hatAlpha);
			aimOnlyWhileAttacking = gb(p, "aimOnlyWhileAttacking", aimOnlyWhileAttacking);
			aimPlayers = gb(p, "aimPlayers", aimPlayers);
			aimMobs = gb(p, "aimMobs", aimMobs);
			aimRange = gd(p, "aimRange", aimRange);
			aimFov = (float) gd(p, "aimFov", aimFov);
			aimSmooth = (float) gd(p, "aimSmooth", aimSmooth);
			PlayerGlowScreen.menuScale = (float) gd(p, "menuScale", PlayerGlowScreen.menuScale);
			PlayerGlowScreen.pendingScale = PlayerGlowScreen.menuScale;
			PlayerGlowScreen.menuOpacity = (float) gd(p, "menuOpacity", PlayerGlowScreen.menuOpacity);
			PlayerGlowScreen.offX = (float) gd(p, "menuX", PlayerGlowScreen.offX);
			PlayerGlowScreen.offY = (float) gd(p, "menuY", PlayerGlowScreen.offY);
		} catch (Exception ignored) {
		}
	}

	public static void saveConfig() {
		try {
			Properties p = new Properties();
			p.setProperty("enabled", String.valueOf(enabled));
			p.setProperty("xray", String.valueOf(xray));
			p.setProperty("aimbot", String.valueOf(aimbot));
			p.setProperty("crystalAura", String.valueOf(crystalAura));
			p.setProperty("triggerbot", String.valueOf(triggerbot));
			p.setProperty("criticals", String.valueOf(criticals));
			p.setProperty("autoShift", String.valueOf(autoShift));
			p.setProperty("targetHud", String.valueOf(targetHud));
			p.setProperty("hitMarker", String.valueOf(hitMarker));
			p.setProperty("infoHud", String.valueOf(infoHud));
			p.setProperty("swordShimmer", String.valueOf(swordShimmer));
			p.setProperty("itemEsp", String.valueOf(itemEsp));
			p.setProperty("itemColor", String.valueOf(itemColor));
			p.setProperty("itemAlpha", String.valueOf(itemAlpha));
			p.setProperty("blockOutline", String.valueOf(blockOutline));
			p.setProperty("tracers", String.valueOf(tracers));
			p.setProperty("autoTotem", String.valueOf(autoTotem));
			p.setProperty("fastPlace", String.valueOf(fastPlace));
			p.setProperty("fastPlaceDelay", String.valueOf(fastPlaceDelay));
			p.setProperty("sprintStopOnHit", String.valueOf(sprintStopOnHit));
			p.setProperty("autoWeapon", String.valueOf(autoWeapon));
			p.setProperty("autoBlock", String.valueOf(autoBlock));
			p.setProperty("abRange", String.valueOf(abRange));
			p.setProperty("abPlayers", String.valueOf(abPlayers));
			p.setProperty("abMobs", String.valueOf(abMobs));
			p.setProperty("velocity", String.valueOf(velocity));
			p.setProperty("velH", String.valueOf(velH));
			p.setProperty("velV", String.valueOf(velV));
			p.setProperty("armorHud", String.valueOf(armorHud));
			p.setProperty("targetEsp", String.valueOf(targetEsp));
			p.setProperty("cyclone", String.valueOf(cyclone));
			p.setProperty("cycRing", String.valueOf(cycRing));
			p.setProperty("cycPlayers", String.valueOf(cycPlayers));
			p.setProperty("cycMobs", String.valueOf(cycMobs));
			p.setProperty("cycColor", String.valueOf(cycColor));
			p.setProperty("cycRange", String.valueOf(cycRange));
			p.setProperty("espArrows", String.valueOf(espArrows));
			p.setProperty("arrowPlayers", String.valueOf(arrowPlayers));
			p.setProperty("arrowMobs", String.valueOf(arrowMobs));
			p.setProperty("arrowColor", String.valueOf(arrowColor));
			p.setProperty("arrowAlpha", String.valueOf(arrowAlpha));
			p.setProperty("targetColor", String.valueOf(targetColor));
			p.setProperty("targetAlpha", String.valueOf(targetAlpha));
			p.setProperty("targetSpeed", String.valueOf(targetSpeed));
			for (java.util.Map.Entry<String, Integer> e : binds.entrySet()) {
				p.setProperty("bind." + e.getKey().replace(' ', '_'), String.valueOf(e.getValue()));
			}
			p.setProperty("autoSprint", String.valueOf(autoSprint));
			p.setProperty("trajectories", String.valueOf(trajectories));
			p.setProperty("crystalAutoPlace", String.valueOf(crystalAutoPlace));
			p.setProperty("crystalAutoBreak", String.valueOf(crystalAutoBreak));
			p.setProperty("crystalRange", String.valueOf(crystalRange));
			p.setProperty("crystalDelayTicks", String.valueOf(crystalDelayTicks));
			p.setProperty("espPlayers", String.valueOf(espPlayers));
			p.setProperty("espMobs", String.valueOf(espMobs));
			p.setProperty("espBox", String.valueOf(espBox));
			p.setProperty("espColor", String.valueOf(espColor));
			p.setProperty("espAlpha", String.valueOf(espAlpha));
			p.setProperty("chinaHat", String.valueOf(chinaHat));
			p.setProperty("hatPlayers", String.valueOf(hatPlayers));
			p.setProperty("hatMobs", String.valueOf(hatMobs));
			p.setProperty("hatColor", String.valueOf(hatColor));
			p.setProperty("hatAlpha", String.valueOf(hatAlpha));
			p.setProperty("aimOnlyWhileAttacking", String.valueOf(aimOnlyWhileAttacking));
			p.setProperty("aimPlayers", String.valueOf(aimPlayers));
			p.setProperty("aimMobs", String.valueOf(aimMobs));
			p.setProperty("aimRange", String.valueOf(aimRange));
			p.setProperty("aimFov", String.valueOf(aimFov));
			p.setProperty("aimSmooth", String.valueOf(aimSmooth));
			p.setProperty("menuScale", String.valueOf(PlayerGlowScreen.menuScale));
			p.setProperty("menuOpacity", String.valueOf(PlayerGlowScreen.menuOpacity));
			p.setProperty("menuX", String.valueOf(PlayerGlowScreen.offX));
			p.setProperty("menuY", String.valueOf(PlayerGlowScreen.offY));
			Path f = cfgPath();
			try (OutputStream out = Files.newOutputStream(f)) {
				p.store(out, "Playboi Carti Client settings");
			}
		} catch (Exception ignored) {
		}
	}

	@Override
	public void onInitializeClient() {
		regToggles();
		loadConfig();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (hidden) setHidden(false); // never save the "everything off" state
			saveConfig();
		});
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_CHAT.register(msg -> !chatCommand(msg));
		// fallback: /playboi
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("playboi")
						.executes(cmd -> {
							toggleHidden();
							return 1;
						})));
		// hide the vanilla black outline while our own is on
		WorldRenderEvents.BLOCK_OUTLINE.register((wrc, boc) -> !blockOutline);

		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.playerglow.menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				"key.categories.playerglow"));

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient && entity instanceof LivingEntity living) {
				UiDraw.noteHit(living);
				if (autoShift) shiftTicks = 4;
				if (criticals) {
					// a crit needs the player to NOT be sprinting: drop sprint right before the hit
					if (sprintStopOnHit && player.isSprinting()) {
						player.setSprinting(false);
						ClientPlayNetworkHandler nh = MinecraftClient.getInstance().getNetworkHandler();
						if (nh != null) {
							nh.sendPacket(new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(player,
									net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.STOP_SPRINTING));
						}
					}
					doCrit(player);
				}
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
			if (hidden) return;
			UiDraw.drawInfoHud(ctx);
			UiDraw.drawTargetHud(ctx);
			UiDraw.drawHitMarker(ctx);
			drawArmorHud(ctx);
			UiDraw.drawEspArrows(ctx);
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.wasPressed()) {
				if (client.currentScreen == null && !hidden) {
					client.setScreen(new PlayerGlowScreen());
				}
			}
			tickNightVision(client);
			tickAimbot(client);
			tickTriggerbot(client);
			tickAutoShift(client);
			tickCrystalAura(client);
			tickCyclone(client);
			tickFastPlace(client);
			tickAutoSprint(client);
			tickAutoTotem(client);
			tickAutoWeapon(client);
			tickAutoBlock(client);
			tickBinds(client);
		});
	}

	// ---------- utilities ----------

	private static int fastPlaceLeft = 0;
	private static int totemCd = 0;

	/** Fast Place: places the held block again every N ticks while use is held (vanilla: every 4). */
	private static void tickFastPlace(MinecraftClient c) {
		ClientPlayerEntity p = c.player;
		if (!fastPlace || p == null || c.currentScreen != null || c.interactionManager == null) {
			fastPlaceLeft = 0;
			return;
		}
		if (!c.options.useKey.isPressed()) {
			fastPlaceLeft = 0;
			return;
		}
		if (!(p.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem)) return;
		if (fastPlaceLeft > 0) {
			fastPlaceLeft--;
			return;
		}
		if (c.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
			ActionResult r = c.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
			if (r.isAccepted()) p.swingHand(Hand.MAIN_HAND);
			fastPlaceLeft = Math.max(0, Math.round(fastPlaceDelay));
		}
	}

	private static void tickAutoSprint(MinecraftClient c) {
		ClientPlayerEntity p = c.player;
		if (!autoSprint || p == null || c.currentScreen != null) return;
		if (c.options.forwardKey.isPressed() && !p.isSneaking() && !p.horizontalCollision
				&& !p.isUsingItem() && p.getHungerManager().getFoodLevel() > 6) {
			p.setSprinting(true);
		}
	}

	/** Auto Totem: keeps a totem of undying in the off hand. */
	private static void tickAutoTotem(MinecraftClient c) {
		ClientPlayerEntity p = c.player;
		if (!autoTotem || p == null || c.interactionManager == null || c.currentScreen != null) return;
		if (totemCd > 0) {
			totemCd--;
			return;
		}
		if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
		for (int i = 0; i < 36; i++) {
			if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
				int slot = i < 9 ? 36 + i : i;
				c.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 40,
						net.minecraft.screen.slot.SlotActionType.SWAP, p);
				totemCd = 6;
				return;
			}
		}
	}

	// ---------- Auto Weapon / Auto Block / Armor HUD ----------

	private static int weaponScore(ItemStack st) {
		if (st == null || st.isEmpty()) return 0;
		String path = Registries.ITEM.getId(st.getItem()).getPath();
		int tier = path.startsWith("netherite") ? 6 : path.startsWith("diamond") ? 5 : path.startsWith("iron") ? 4
				: path.startsWith("stone") ? 3 : path.startsWith("golden") ? 2 : 1;
		if (path.endsWith("_sword")) return 100 + tier;
		if (path.endsWith("_axe")) return 50 + tier;
		return 0;
	}

	/** Auto Weapon: when you look at a mob/player, the best sword (or axe) in the hotbar is selected. */
	private static void tickAutoWeapon(MinecraftClient c) {
		ClientPlayerEntity p = c.player;
		if (!autoWeapon || p == null || c.currentScreen != null) return;
		if (!(c.crosshairTarget instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof LivingEntity)) return;
		int best = weaponScore(p.getMainHandStack());
		int slot = -1;
		for (int i = 0; i < 9; i++) {
			int sc = weaponScore(p.getInventory().getStack(i));
			if (sc > best) {
				best = sc;
				slot = i;
			}
		}
		if (slot >= 0) p.getInventory().setSelectedSlot(slot);
	}

	/** Auto Block: raises the shield while an enemy is close. */
	private static void tickAutoBlock(MinecraftClient c) {
		ClientPlayerEntity p = c.player;
		if (!autoBlock || p == null || c.world == null || c.currentScreen != null) {
			if (abHolding) {
				c.options.useKey.setPressed(false);
				abHolding = false;
			}
			return;
		}
		ItemStack main = p.getMainHandStack(), off = p.getOffHandStack();
		boolean shieldMain = main.isOf(Items.SHIELD);
		String k = glowKind(main);
		boolean onBlock = c.crosshairTarget instanceof BlockHitResult;
		boolean mainHarmless = main.isEmpty() || (k != null && ("sword".equals(k) || "pickaxe".equals(k) || !onBlock));
		boolean want = false;
		if (shieldMain || (off.isOf(Items.SHIELD) && mainHarmless)) {
			double r2 = (double) abRange * abRange;
			for (Entity e : c.world.getEntities()) {
				if (e == p || !(e instanceof LivingEntity le) || !le.isAlive() || e.isSpectator()) continue;
				boolean ok = (abPlayers && e instanceof PlayerEntity) || (abMobs && e instanceof net.minecraft.entity.mob.HostileEntity);
				if (ok && p.squaredDistanceTo(e) <= r2) {
					want = true;
					break;
				}
			}
		}
		if (want && (!p.isUsingItem() || abHolding)) {
			c.options.useKey.setPressed(true);
			abHolding = true;
		} else if (abHolding) {
			c.options.useKey.setPressed(false);
			abHolding = false;
		}
	}

	private static void drawArmorHud(net.minecraft.client.gui.DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!armorHud || mc.player == null || mc.options.hudHidden) return;
		net.minecraft.entity.EquipmentSlot[] slots = { net.minecraft.entity.EquipmentSlot.HEAD,
				net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.entity.EquipmentSlot.LEGS,
				net.minecraft.entity.EquipmentSlot.FEET };
		int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
		int x0 = w - 4 * 22 - 6, y = h - 44;
		for (int i = 0; i < 4; i++) {
			ItemStack st = mc.player.getEquippedStack(slots[i]);
			if (st.isEmpty()) continue;
			int x = x0 + i * 22;
			ctx.drawItem(st, x, y);
			ctx.drawStackOverlay(mc.textRenderer, st, x, y);
			if (st.isDamageable() && st.getMaxDamage() > 0) {
				int pct = Math.round((st.getMaxDamage() - st.getDamage()) * 100f / st.getMaxDamage());
				String t = pct + "%";
				int col = 0xFF000000 | UiDraw.hsv(Math.max(0, Math.min(100, pct)) * 1.2f, 0.8f, 1f);
				UiDraw.txt(ctx, mc.textRenderer, t, x + 8 - UiDraw.width(mc.textRenderer, t, 0.7f) / 2f, y + 18, 0.7f, col);
			}
		}
	}

	/** Target ESP: glowing crystals orbiting the mob/player you are aiming at. */
	private static void drawTargetEsp(MinecraftClient mc, WorldRenderContext ctx, Vec3d cam, Matrix4f m, float td) {
		long now = System.nanoTime();
		float dt = teLast == 0L ? 0.016f : Math.min(0.1f, (now - teLast) / 1_000_000_000f);
		teLast = now;

		// pick the entity closest to the crosshair line
		Vec3d look = Vec3d.fromPolar(ctx.camera().getPitch(), ctx.camera().getYaw());
		net.minecraft.entity.LivingEntity best = null;
		double bestDist = 1e9;
		for (Entity e : mc.world.getEntities()) {
			if (!(e instanceof net.minecraft.entity.LivingEntity le) || e == mc.player || !le.isAlive() || e.isSpectator()) continue;
			Vec3d c = e.getLerpedPos(td).add(0, e.getHeight() * 0.5, 0).subtract(cam);
			double dist = c.length();
			if (dist < 0.5 || dist > 40.0) continue;
			double dot = c.dotProduct(look) / dist;
			double need = Math.cos(Math.atan2(Math.max(e.getWidth(), e.getHeight()) * 0.5 + 0.25, dist));
			if (dot >= need && dist < bestDist) {
				best = le;
				bestDist = dist;
			}
		}
		if (best != null) teTarget = best;
		float goal = best != null ? 1f : 0f;
		teFade += (goal - teFade) * (1f - (float) Math.exp(-dt * 9f));
		if (teTarget == null || teTarget.isRemoved() || teFade < 0.03f) return;

		net.minecraft.entity.LivingEntity e = teTarget;
		Vec3d p = e.getLerpedPos(td);
		float cx = (float) (p.x - cam.x), cy = (float) (p.y - cam.y), cz = (float) (p.z - cam.z);
		float h = e.getHeight(), baseR = Math.max(0.55f, e.getWidth() * 0.5f + 0.5f);
		double t = now / 1_000_000_000.0;
		int rgb = targetColor & 0xFFFFFF;
		float fade = teFade * targetAlpha;
		int n = 12;
		float[][] pos = new float[n][3];
		float[] ln = new float[n], ang = new float[n], tw = new float[n], spin = new float[n], tilt = new float[n];
		float twv = 0.85f + 0.15f * (float) Math.sin(t * 4);
		float spinV = (float) (t * 2.5);
		for (int i = 0; i < n; i++) {
			// perfectly even ring; two alternating layers (low / high), identical size and tilt
			ang[i] = (float) (t * targetSpeed + i * Math.PI * 2 / n);
			boolean hi = (i & 1) == 1;
			float y = h * (hi ? 0.70f : 0.32f) + 0.06f * (float) Math.sin(t * 1.5 + (hi ? Math.PI : 0.0));
			pos[i][0] = cx + (float) Math.cos(ang[i]) * baseR;
			pos[i][1] = cy + y;
			pos[i][2] = cz + (float) Math.sin(ang[i]) * baseR;
			ln[i] = 0.14f;
			tw[i] = twv;
			spin[i] = spinV;
			tilt[i] = 0.0f;
		}
		BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		for (int i = 0; i < n; i++) { // solid cores first
			UiDraw.crystal(bb, m, pos[i][0], pos[i][1], pos[i][2], ln[i], ln[i] * 0.42f, spin[i], tilt[i],
					ang[i] + 1.5708f, rgb, Math.round(255 * fade), true);
		}
		for (int i = 0; i < n; i++) { // glow halos
			UiDraw.crystal(bb, m, pos[i][0], pos[i][1], pos[i][2], ln[i] * 1.5f, ln[i] * 0.7f, spin[i], tilt[i],
					ang[i] + 1.5708f, rgb, Math.round(255 * fade * 0.20f * tw[i]), false);
			UiDraw.crystal(bb, m, pos[i][0], pos[i][1], pos[i][2], ln[i] * 2.3f, ln[i] * 1.1f, spin[i], tilt[i],
					ang[i] + 1.5708f, rgb, Math.round(255 * fade * 0.09f * tw[i]), false);
		}
		RenderSystem.enableDepthTest();
		BufferRenderer.drawWithGlobalProgram(bb.end());
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

	/** Cyclone Aura: hits every enemy in range one after another (round robin), one hit per full cooldown. */
	private static void tickCyclone(MinecraftClient c) {
		if (!cyclone || c.player == null || c.world == null || c.interactionManager == null || c.currentScreen != null) return;
		ClientPlayerEntity p = c.player;
		if (!p.isAlive() || p.getAttackCooldownProgress(0.5f) < 0.95f) return;
		List<LivingEntity> ts = new ArrayList<>();
		for (Entity e : c.world.getEntities()) {
			if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || e.isSpectator()) continue;
			boolean pl = e instanceof PlayerEntity;
			if (pl ? !cycPlayers : !cycMobs) continue;
			if (p.distanceTo(e) > cycRange) continue;
			ts.add(le);
		}
		if (ts.isEmpty()) return;
		ts.sort(java.util.Comparator.comparingInt(Entity::getId));
		cycIdx = (cycIdx + 1) % ts.size();
		LivingEntity target = ts.get(cycIdx);
		c.interactionManager.attackEntity(p, target);
		p.swingHand(Hand.MAIN_HAND);
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

	/** Translucent whitish cube with thin light edges; glides between blocks, fades and pulses. */
	private static void drawBlockOutline(MinecraftClient mc, Vec3d cam, Matrix4f m) {
		long now = System.nanoTime();
		float dt = boLast == 0L ? 0.016f : Math.min(0.1f, (now - boLast) / 1_000_000_000f);
		boLast = now;

		BlockPos pos = null;
		double[] target = null;
		if (mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
			pos = bhr.getBlockPos();
			net.minecraft.util.shape.VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
			Box sb = shape.isEmpty() ? new Box(0, 0, 0, 1, 1, 1) : shape.getBoundingBox();
			target = new double[] { pos.getX() + sb.minX, pos.getY() + sb.minY, pos.getZ() + sb.minZ,
					pos.getX() + sb.maxX, pos.getY() + sb.maxY, pos.getZ() + sb.maxZ };
		}

		if (target != null) {
			if (boBox == null || boFade < 0.02f) {
				boBox = target.clone(); // appear in place
			} else {
				float k = 1f - (float) Math.exp(-dt * 18f); // quick smooth glide
				for (int i = 0; i < 6; i++) boBox[i] += (target[i] - boBox[i]) * k;
			}
			boLastPos = pos;
		}
		float goal = target != null ? 1f : 0f;
		boFade += (goal - boFade) * (1f - (float) Math.exp(-dt * 12f));
		if (boBox == null || boFade < 0.01f) return;

		float pulse = 0.5f + 0.5f * (float) Math.sin(now / 1_000_000_000.0 * 2.6);
		float grow = 0.003f + 0.004f * pulse;
		float x1 = (float) (boBox[0] - cam.x) - grow, y1 = (float) (boBox[1] - cam.y) - grow, z1 = (float) (boBox[2] - cam.z) - grow;
		float x2 = (float) (boBox[3] - cam.x) + grow, y2 = (float) (boBox[4] - cam.y) + grow, z2 = (float) (boBox[5] - cam.z) + grow;

		int fill = Math.round(255 * boFade * (0.20f + 0.10f * pulse));
		int edge = Math.round(255 * boFade * (0.70f + 0.25f * pulse));
		float cx = (x1 + x2) * 0.5f, cy = (y1 + y2) * 0.5f, cz = (z1 + z2) * 0.5f;
		float dist = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
		float t = 0.004f + dist * 0.0007f;

		BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		UiDraw.cuboid(bb, m, x1, y1, z1, x2, y2, z2, 235, 240, 255, fill);
		for (int iy = 0; iy < 2; iy++) {
			float y = iy == 0 ? y1 : y2;
			for (int iz = 0; iz < 2; iz++) {
				float z = iz == 0 ? z1 : z2;
				UiDraw.cuboid(bb, m, x1 - t, y - t, z - t, x2 + t, y + t, z + t, 245, 248, 255, edge);
			}
		}
		for (int ix = 0; ix < 2; ix++) {
			float x = ix == 0 ? x1 : x2;
			for (int iz = 0; iz < 2; iz++) {
				float z = iz == 0 ? z1 : z2;
				UiDraw.cuboid(bb, m, x - t, y1 - t, z - t, x + t, y2 + t, z + t, 245, 248, 255, edge);
			}
		}
		for (int ix = 0; ix < 2; ix++) {
			float x = ix == 0 ? x1 : x2;
			for (int iy = 0; iy < 2; iy++) {
				float y = iy == 0 ? y1 : y2;
				UiDraw.cuboid(bb, m, x - t, y - t, z1 - t, x + t, y + t, z2 + t, 245, 248, 255, edge);
			}
		}
		RenderSystem.disableDepthTest();
		BufferRenderer.drawWithGlobalProgram(bb.end());
		RenderSystem.enableDepthTest();
	}

	private static void renderWorld(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return;
		boolean doBox = enabled && espBox;
		boolean doHat = chinaHat;
		boolean doTraj = trajectories && heldThrowKind(mc.player) != 0;
		boolean doItem = itemEsp;
		boolean doBlock = blockOutline;
		boolean doTracers = tracers;
		boolean doTE = targetEsp;
		boolean doCyc = cyclone && cycRing;
		if (!doBox && !doHat && !doTraj && !doItem && !doBlock && !doTracers && !doTE && !doCyc) return;
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

		if (doItem) {
			BufferBuilder bb = null;
			for (Entity e : mc.world.getEntities()) {
				if (!(e instanceof net.minecraft.entity.ItemEntity) || e.isRemoved()) continue;
				if (e.squaredDistanceTo(mc.player) > 64.0 * 64.0) continue;
				if (bb == null) bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
				Vec3d p = e.getLerpedPos(td);
				Vec3d d = p.subtract(e.getPos());
				Box b = e.getBoundingBox().offset(d.x - cam.x, d.y - cam.y, d.z - cam.z).expand(0.04);
				float dist = (float) Math.sqrt(e.squaredDistanceTo(mc.player));
				float t = 0.006f + dist * 0.0012f;
				UiDraw.espBox(bb, m, (float) b.minX, (float) b.minY, (float) b.minZ,
						(float) b.maxX, (float) b.maxY, (float) b.maxZ, itemColor, itemAlpha, t);
			}
			if (bb != null) {
				RenderSystem.disableDepthTest();
				BufferRenderer.drawWithGlobalProgram(bb.end());
				RenderSystem.enableDepthTest();
			}
		}

		if (doCyc) {
			Vec3d pp = mc.player.getLerpedPos(td);
			float rx = (float) (pp.x - cam.x), ry = (float) (pp.y - cam.y) + 0.03f, rz = (float) (pp.z - cam.z);
			double tt = System.nanoTime() / 1_000_000_000.0;
			int cr = (cycColor >> 16) & 255, cg = (cycColor >> 8) & 255, cb = cycColor & 255;
			BufferBuilder rb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
			for (int ring = 0; ring < 2; ring++) {
				float rad = ring == 0 ? cycRange : cycRange * 0.62f;
				float hw = ring == 0 ? 0.05f : 0.03f;
				int segs = ring == 0 ? 40 : 28;
				double dir = ring == 0 ? 1.5 : -2.2;
				for (int i = 0; i < segs; i += 2) {
					double a0 = i * Math.PI * 2 / segs + tt * dir;
					double a1 = a0 + Math.PI * 2 / segs * 0.85;
					float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
					int al = Math.round(200 * (0.65f + 0.35f * (float) Math.sin(tt * 3 + i)));
					UiDraw.quad(rb, m,
							rx + c0 * (rad - hw), ry, rz + s0 * (rad - hw),
							rx + c0 * (rad + hw), ry, rz + s0 * (rad + hw),
							rx + c1 * (rad + hw), ry, rz + s1 * (rad + hw),
							rx + c1 * (rad - hw), ry, rz + s1 * (rad - hw),
							cr, cg, cb, al);
				}
			}
			RenderSystem.enableDepthTest();
			BufferRenderer.drawWithGlobalProgram(rb.end());
		}

		if (doTE) {
			drawTargetEsp(mc, ctx, cam, m, td);
		} else {
			teTarget = null;
			teFade = 0f;
		}

		if (doTracers) {
			BufferBuilder bb = null;
			Vec3d look = Vec3d.fromPolar(ctx.camera().getPitch(), ctx.camera().getYaw());
			Vec3d start = look.multiply(0.6);
			for (Entity e : mc.world.getEntities()) {
				if (!(e instanceof PlayerEntity) || e == mc.player || !((PlayerEntity) e).isAlive() || e.isSpectator()) continue;
				float dist = (float) Math.sqrt(e.squaredDistanceTo(mc.player));
				if (dist > 128f) continue;
				if (bb == null) bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
				Vec3d end = e.getLerpedPos(td).subtract(cam).add(0, e.getHeight() * 0.5, 0);
				int rgb = dist < 16f ? 0xFF5C7A : dist < 40f ? 0xF2C14E : 0x6FE3A0;
				float w = (float) Math.max(0.004, Math.min(0.04, end.length() * 0.0024));
				ribbon(bb, m, start, end, w, rgb, 210);
			}
			if (bb != null) {
				RenderSystem.disableDepthTest();
				BufferRenderer.drawWithGlobalProgram(bb.end());
				RenderSystem.enableDepthTest();
			}
		}

		if (doBlock) {
			drawBlockOutline(mc, cam, m);
		} else {
			boBox = null;
			boFade = 0f;
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
				int rgb = kind == 1 ? 0xCDBFFF : kind == 2 ? 0xF9A8D4 : kind == 3 ? 0x93C5FD : 0x67E8F9;
				BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
				int n = pts.size();
				for (int i = 0; i < n - 1; i++) {
					Vec3d a = pts.get(i).subtract(cam);
					Vec3d b = pts.get(i + 1).subtract(cam);
					// thin, constant on-screen width line
					float w = (float) Math.max(0.0025, Math.min(0.02, a.add(b).multiply(0.5).length() * 0.0011));
					ribbon(bb, m, a, b, w, rgb, 235);
				}
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
		if (s.isOf(Items.BOW)) return 3;
		if (s.isOf(Items.TRIDENT)) return 4;
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

		float speed;
		float roll = 0f;
		double gravity = 0.05;
		if (kind == 1) {
			speed = 1.5f;
			gravity = 0.03;
		} else if (kind == 2) {
			speed = 0.5f;
			roll = -20f;
		} else if (kind == 3) {
			float pull = (p.isUsingItem() && p.getActiveItem().isOf(Items.BOW))
					? BowItem.getPullProgress(p.getItemUseTime()) : 1f;
			speed = Math.max(0.3f, pull) * 3.0f;
		} else {
			speed = 2.5f;
		}
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
