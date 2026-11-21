package com.example.playerglow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static com.example.playerglow.UiDraw.BORDER;
import static com.example.playerglow.UiDraw.C1;
import static com.example.playerglow.UiDraw.C2;
import static com.example.playerglow.UiDraw.CARD_OFF;
import static com.example.playerglow.UiDraw.MUTED;
import static com.example.playerglow.UiDraw.TEXT;
import static com.example.playerglow.UiDraw.TRACK;
import static com.example.playerglow.UiDraw.WHITE;
import static com.example.playerglow.UiDraw.WIN_BG;
import static com.example.playerglow.UiDraw.alpha;
import static com.example.playerglow.UiDraw.lerp;

public class PlayerGlowScreen extends Screen {
	// design size of the window (GUI units); everything is laid out in this space
	private static final int W = 372, H = 284, SB = 98, WRAD = 14;
	private static final int CX = SB + 12, CW = W - SB - 24;

	private static final int CAT_COMBAT = 0, CAT_RENDER = 1, CAT_SETTINGS = 2, CAT_UTILS = 3;
	private static final int[] CAT_ORDER = { CAT_COMBAT, CAT_RENDER, CAT_UTILS, CAT_SETTINGS };
	private static final String[] CAT_NAMES = { "Combat", "Render", "Settings", "Utilities" };
	private static final int VIEW_LIST = -1, VIEW_AIMBOT = 0, VIEW_CRYSTAL = 1, VIEW_ESP = 2, VIEW_HAT = 3, VIEW_ITEM = 4, VIEW_FASTPLACE = 5;

	// window position offset (kept while the game is running)
	static float offX = 0f, offY = 0f;
	// menu settings (kept while the game is running)
	static float menuScale = 1.0f, pendingScale = 1.0f, menuOpacity = 0.88f;

	private static final class Module {
		final String name, desc;
		final int cat, gearView;
		final BooleanSupplier get;
		final Consumer<Boolean> set;
		float x, y, w, h;
		float on, hover, gearHover, rip = -1f;
		boolean visible;

		Module(String name, String desc, int cat, int gearView, BooleanSupplier get, Consumer<Boolean> set) {
			this.name = name;
			this.desc = desc;
			this.cat = cat;
			this.gearView = gearView;
			this.get = get;
			this.set = set;
			this.on = get.getAsBoolean() ? 1f : 0f;
		}

		boolean contains(double mx, double my) {
			return visible && mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final class Toggle {
		final String label;
		final BooleanSupplier get;
		final Consumer<Boolean> set;
		float x, y, w, h, on, hover;

		Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
			this.label = label;
			this.get = get;
			this.set = set;
			this.on = get.getAsBoolean() ? 1f : 0f;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final class Slider {
		final String label;
		final double min, max, step;
		final DoubleSupplier get;
		final DoubleConsumer set;
		final String fmt;
		final boolean rainbow;
		float x, y, w, h, hover;

		Slider(String label, double min, double max, double step, DoubleSupplier get, DoubleConsumer set,
				String fmt, boolean rainbow) {
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.fmt = fmt;
			this.rainbow = rainbow;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		float trackX() { return x + 12; }
		float trackW() { return w - 24; }
	}

	private static final class Swatches {
		final IntSupplier get;
		final IntConsumer set;
		float x, y, w, h;

		Swatches(IntSupplier get, IntConsumer set) {
			this.get = get;
			this.set = set;
		}

		float cellX(int i) {
			int n = UiDraw.PALETTE.length;
			float inner = w - 28;
			return x + 14 + inner * (i + 0.5f) / n;
		}
	}

	private static final class Flake {
		float x, y, speed, size, phase, rot, rotSpeed, alpha;
	}

	private static final class Spark {
		float x, y, vx, vy, age, life, size;
	}

	private final List<Module> modules = new ArrayList<>();
	private final List<Toggle> toggles = new ArrayList<>();
	private final List<Slider> sliders = new ArrayList<>();
	private Swatches swatches = null;
	private final List<Flake> flakes = new ArrayList<>();
	private final List<Spark> sparks = new ArrayList<>();
	private final Random rnd = new Random();

	private int cat = CAT_COMBAT;
	private int view = VIEW_LIST;
	private boolean compact = false;
	private String viewTitle = "";
	private String query = "";
	private boolean searchFocus = false;

	private final float[] catOn = {1f, 0f, 0f, 0f};
	private float backHover = 0f, exitHover = 0f;
	private Slider dragSlider = null;
	private boolean movingWindow = false;
	private boolean closing = false;

	private long openStart = System.nanoTime();
	private long closeStart = 0L;
	private long contentStart = 0L;
	private long lastNanos = System.nanoTime();

	// last frame transform, used for mouse mapping
	private float tx = 0f, ty = 0f, ts = 1f;

	public PlayerGlowScreen() {
		super(Text.literal("Playboi Carti Client"));
		contentStart = openStart - 1_000_000_000L;

		modules.add(new Module("Aimbot", "Наводит прицел на\nигроков и мобов", CAT_COMBAT, VIEW_AIMBOT,
				() -> PlayerGlowClient.aimbot, v -> PlayerGlowClient.aimbot = v));
		modules.add(new Module("CrystalAura", "Ставит кристалл на обсидиан\nи сам его взрывает", CAT_COMBAT, VIEW_CRYSTAL,
				() -> PlayerGlowClient.crystalAura, v -> PlayerGlowClient.crystalAura = v));
		modules.add(new Module("TriggerBot", "Бьёт цель в прицеле\nровно в тайминг", CAT_COMBAT, -1,
				() -> PlayerGlowClient.triggerbot, v -> PlayerGlowClient.triggerbot = v));
		modules.add(new Module("Auto Totem", "Сам кладёт тотем\nбессмертия в левую руку", CAT_COMBAT, -1,
				() -> PlayerGlowClient.autoTotem, v -> PlayerGlowClient.autoTotem = v));
		modules.add(new Module("Criticals", "Каждый удар\nстановится критом", CAT_COMBAT, -1,
				() -> PlayerGlowClient.criticals, v -> PlayerGlowClient.criticals = v));
		modules.add(new Module("Auto Shift", "Приседает после удара\nпо мобу или игроку", CAT_COMBAT, -1,
				() -> PlayerGlowClient.autoShift, v -> PlayerGlowClient.autoShift = v));

		modules.add(new Module("Player Glow", "3D-бокс вокруг игроков\nи мобов сквозь стены", CAT_RENDER, VIEW_ESP,
				() -> PlayerGlowClient.enabled, v -> PlayerGlowClient.enabled = v));
		modules.add(new Module("China Hat", "Шляпа над головой\nигроков и мобов", CAT_RENDER, VIEW_HAT,
				() -> PlayerGlowClient.chinaHat, v -> PlayerGlowClient.chinaHat = v));
		modules.add(new Module("X-Ray", "Показывает только руды\nи включает ночное зрение", CAT_RENDER, -1,
				() -> PlayerGlowClient.xray,
				v -> {
					PlayerGlowClient.xray = v;
					MinecraftClient.getInstance().worldRenderer.reload();
				}));
		modules.add(new Module("Target HUD", "Здоровье цели на 2 секунды\nпосле вашего удара", CAT_RENDER, -1,
				() -> PlayerGlowClient.targetHud, v -> PlayerGlowClient.targetHud = v));
		modules.add(new Module("Hit Marker", "Маркер в прицеле\nпри попадании", CAT_RENDER, -1,
				() -> PlayerGlowClient.hitMarker, v -> PlayerGlowClient.hitMarker = v));
		modules.add(new Module("Info HUD", "FPS, координаты\nи направление взгляда", CAT_RENDER, -1,
				() -> PlayerGlowClient.infoHud, v -> PlayerGlowClient.infoHud = v));
		modules.add(new Module("Glow Hand", "Меч в руке светится\nфиолетовой заливкой", CAT_RENDER, -1,
				() -> PlayerGlowClient.swordShimmer, v -> PlayerGlowClient.swordShimmer = v));
		modules.add(new Module("Item ESP", "3D-бокс вокруг предметов\nна земле", CAT_RENDER, VIEW_ITEM,
				() -> PlayerGlowClient.itemEsp, v -> PlayerGlowClient.itemEsp = v));
		modules.add(new Module("Block Outline", "Белая подсветка блока\nна который смотришь", CAT_RENDER, -1,
				() -> PlayerGlowClient.blockOutline, v -> PlayerGlowClient.blockOutline = v));
		modules.add(new Module("Tracers", "Линии от прицела\nк игрокам", CAT_RENDER, -1,
				() -> PlayerGlowClient.tracers, v -> PlayerGlowClient.tracers = v));
		modules.add(new Module("Fast Place", "Очень быстро ставит\nблоки, скорость в настройках", CAT_UTILS, VIEW_FASTPLACE,
				() -> PlayerGlowClient.fastPlace, v -> PlayerGlowClient.fastPlace = v));
		modules.add(new Module("Auto Sprint", "Всегда бежишь,\nпока идёшь вперёд", CAT_UTILS, -1,
				() -> PlayerGlowClient.autoSprint, v -> PlayerGlowClient.autoSprint = v));
		modules.add(new Module("Trajectories", "Линия полёта перла,\nзелий, лука и трезубца", CAT_RENDER, -1,
				() -> PlayerGlowClient.trajectories, v -> PlayerGlowClient.trajectories = v));
	}

	// ---------- layout ----------

	@Override
	protected void init() {
		layout();
	}

	private void layout() {
		toggles.clear();
		sliders.clear();
		swatches = null;

		if (view == VIEW_LIST) {
			layoutList();
		} else {
			layoutSettings();
		}
	}

	private boolean matches(Module m) {
		if (!query.isEmpty()) {
			return m.name.toLowerCase().contains(query.toLowerCase());
		}
		return m.cat == cat;
	}

	/** Two fluffy snow drifts sitting on the bottom edge of the window. */
	private void drawSnowDrifts() {
		float[][] d = { { 124f, 100f, 24f }, { 314f, 88f, 20f } };
		int white = fa(0xF2FFFFFF), shade = fa(0xFFD3E2F5);
		for (float[] m : d) {
			float cx = m[0], w = m[1], h = m[2];
			float by = H - 2f;
			UiDraw.Gfx.shadow(cx - w / 2f, by - h, w, h, h / 2f, 6f, fa(0x30AFCBFF));
			UiDraw.Gfx.rr(cx - w / 2f, by - h * 0.55f, w, h * 0.55f, h * 0.27f, shade);
			UiDraw.Gfx.circle(cx - w * 0.30f, by - h * 0.50f, h * 0.38f, white);
			UiDraw.Gfx.circle(cx - w * 0.08f, by - h * 0.62f, h * 0.50f, white);
			UiDraw.Gfx.circle(cx + w * 0.16f, by - h * 0.55f, h * 0.42f, white);
			UiDraw.Gfx.circle(cx + w * 0.34f, by - h * 0.42f, h * 0.32f, white);
			UiDraw.Gfx.rr(cx - w * 0.46f, by - h * 0.40f, w * 0.92f, h * 0.38f, h * 0.19f, white);
		}
	}

	/** Santa hat perched on the top-right corner of the window. */
	private void drawSantaHat(float time) {
		float sway = (float) Math.sin(time * 1.4f) * 1.6f;
		int red = fa(0xFFD9252E), redDark = fa(0xFFA81820), white = fa(0xFFFFFFFF), fur = fa(0xFFEAF0F8);
		// body: chain of circles from the brim (lower right) bending up-left to the tip
		float[][] body = { { 358f, 8f, 17f }, { 352f, -2f, 16.5f }, { 345f, -11f, 15f }, { 337f, -19f, 13f },
				{ 328f, -24f, 10.5f }, { 319f + sway * 0.4f, -25f, 8f }, { 311f + sway * 0.8f, -22f, 6f } };
		for (float[] c : body) UiDraw.Gfx.circle(c[0] + 1.5f, c[1] + 2f, c[2], redDark);
		for (float[] c : body) UiDraw.Gfx.circle(c[0], c[1], c[2], red);
		// highlight
		UiDraw.Gfx.circle(346f, -8f, 5f, fa(0x35FFFFFF));
		// fur brim (rounded bar + puffs)
		UiDraw.Gfx.line(338f, 12f, 378f, 26f, 13f, fur);
		for (int i = 0; i < 6; i++) {
			float t = i / 5f;
			UiDraw.Gfx.circle(338f + 40f * t, 12f + 14f * t, 7f, white);
		}
		// pompom
		UiDraw.Gfx.circle(306f + sway, -19f, 7.5f, white);
		UiDraw.Gfx.circle(304.5f + sway, -20.5f, 2.4f, fa(0x66C7D9F0));
	}

	private void layoutList() {
		float cw = (CW - 8) / 2f;
		int count = 0;
		for (Module m : modules) if (matches(m)) count++;
		compact = count > 8;
		float ch = compact ? 34f : 44f;
		float gap = compact ? 4f : 8f;
		float top = compact ? 46f : 50f;
		int i = 0;
		for (Module m : modules) {
			m.visible = matches(m);
			if (!m.visible) continue;
			m.x = CX + (i % 2) * (cw + 8);
			m.y = top + (i / 2) * (ch + gap);
			m.w = cw;
			m.h = ch;
			i++;
		}
		if (query.isEmpty() && cat == CAT_SETTINGS) {
			pendingScale = menuScale;
			Slider a = new Slider("Menu size", 50.0, 140.0, 5.0,
					() -> pendingScale * 100.0, v -> pendingScale = (float) (v / 100.0), "%.0f%%", false);
			a.x = CX; a.y = 50; a.w = CW; a.h = 36;
			Slider b = new Slider("Menu opacity", 30.0, 100.0, 5.0,
					() -> menuOpacity * 100.0, v -> menuOpacity = (float) (v / 100.0), "%.0f%%", false);
			b.x = CX; b.y = 90; b.w = CW; b.h = 36;
			sliders.add(a);
			sliders.add(b);
		}
	}

	private void layoutSettings() {
		for (Module m : modules) m.visible = false;

		List<Toggle> ts = new ArrayList<>();
		List<Slider> ss = new ArrayList<>();
		Swatches sw = null;

		if (view == VIEW_AIMBOT) {
			viewTitle = "Aimbot";
			ts.add(new Toggle("Hold attack", () -> PlayerGlowClient.aimOnlyWhileAttacking, v -> PlayerGlowClient.aimOnlyWhileAttacking = v));
			ts.add(new Toggle("Players", () -> PlayerGlowClient.aimPlayers, v -> PlayerGlowClient.aimPlayers = v));
			ts.add(new Toggle("Mobs", () -> PlayerGlowClient.aimMobs, v -> PlayerGlowClient.aimMobs = v));
			ss.add(new Slider("Range", 1.0, 8.0, 0.1, () -> PlayerGlowClient.aimRange, v -> PlayerGlowClient.aimRange = v, "%.1f", false));
			ss.add(new Slider("FOV", 30.0, 360.0, 5.0, () -> PlayerGlowClient.aimFov, v -> PlayerGlowClient.aimFov = (float) v, "%.0f deg", false));
			ss.add(new Slider("Smooth", 1.0, 20.0, 0.5, () -> PlayerGlowClient.aimSmooth, v -> PlayerGlowClient.aimSmooth = (float) v, "%.1f", false));
		} else if (view == VIEW_CRYSTAL) {
			viewTitle = "CrystalAura";
			ts.add(new Toggle("Auto place", () -> PlayerGlowClient.crystalAutoPlace, v -> PlayerGlowClient.crystalAutoPlace = v));
			ts.add(new Toggle("Auto break", () -> PlayerGlowClient.crystalAutoBreak, v -> PlayerGlowClient.crystalAutoBreak = v));
			ss.add(new Slider("Range", 1.0, 6.0, 0.5, () -> PlayerGlowClient.crystalRange, v -> PlayerGlowClient.crystalRange = v, "%.1f", false));
			ss.add(new Slider("Delay", 0.0, 10.0, 1.0, () -> PlayerGlowClient.crystalDelayTicks, v -> PlayerGlowClient.crystalDelayTicks = (float) v, "%.0f tick", false));
		} else if (view == VIEW_ESP) {
			viewTitle = "Player Glow";
			ts.add(new Toggle("Players", () -> PlayerGlowClient.espPlayers, v -> PlayerGlowClient.espPlayers = v));
			ts.add(new Toggle("Mobs", () -> PlayerGlowClient.espMobs, v -> PlayerGlowClient.espMobs = v));
			ts.add(new Toggle("3D Box", () -> PlayerGlowClient.espBox, v -> PlayerGlowClient.espBox = v));
			sw = new Swatches(() -> PlayerGlowClient.espColor, v -> PlayerGlowClient.espColor = v);
			ss.add(new Slider("Hue", 0.0, 360.0, 1.0,
					() -> UiDraw.hueOf(PlayerGlowClient.espColor),
					v -> PlayerGlowClient.espColor = UiDraw.hsv((float) v, 0.75f, 1f), "%.0f", true));
			ss.add(new Slider("Opacity", 10.0, 100.0, 5.0,
					() -> PlayerGlowClient.espAlpha * 100.0, v -> PlayerGlowClient.espAlpha = (float) (v / 100.0), "%.0f%%", false));
		} else if (view == VIEW_FASTPLACE) {
			viewTitle = "Fast Place";
			ss.add(new Slider("Delay", 0.0, 4.0, 1.0, () -> PlayerGlowClient.fastPlaceDelay,
					v -> PlayerGlowClient.fastPlaceDelay = (float) v, "%.0f tick", false));
		} else if (view == VIEW_ITEM) {
			viewTitle = "Item ESP";
			sw = new Swatches(() -> PlayerGlowClient.itemColor, v -> PlayerGlowClient.itemColor = v);
			ss.add(new Slider("Hue", 0.0, 360.0, 1.0,
					() -> UiDraw.hueOf(PlayerGlowClient.itemColor),
					v -> PlayerGlowClient.itemColor = UiDraw.hsv((float) v, 0.75f, 1f), "%.0f", true));
			ss.add(new Slider("Opacity", 10.0, 100.0, 5.0,
					() -> PlayerGlowClient.itemAlpha * 100.0, v -> PlayerGlowClient.itemAlpha = (float) (v / 100.0), "%.0f%%", false));
		} else {
			viewTitle = "China Hat";
			ts.add(new Toggle("Players", () -> PlayerGlowClient.hatPlayers, v -> PlayerGlowClient.hatPlayers = v));
			ts.add(new Toggle("Mobs", () -> PlayerGlowClient.hatMobs, v -> PlayerGlowClient.hatMobs = v));
			sw = new Swatches(() -> PlayerGlowClient.hatColor, v -> PlayerGlowClient.hatColor = v);
			ss.add(new Slider("Hue", 0.0, 360.0, 1.0,
					() -> UiDraw.hueOf(PlayerGlowClient.hatColor),
					v -> PlayerGlowClient.hatColor = UiDraw.hsv((float) v, 0.75f, 1f), "%.0f", true));
			ss.add(new Slider("Opacity", 10.0, 100.0, 5.0,
					() -> PlayerGlowClient.hatAlpha * 100.0, v -> PlayerGlowClient.hatAlpha = (float) (v / 100.0), "%.0f%%", false));
		}

		float y = 50;
		for (Toggle t : ts) {
			t.x = CX; t.y = y; t.w = CW; t.h = 26;
			toggles.add(t);
			y += 30;
		}
		if (sw != null) {
			sw.x = CX; sw.y = y; sw.w = CW; sw.h = 30;
			swatches = sw;
			y += 34;
		}
		for (Slider s : ss) {
			s.x = CX; s.y = y; s.w = CW; s.h = 36;
			sliders.add(s);
			y += 40;
		}
	}

	private void setView(int v) {
		view = v;
		dragSlider = null;
		searchFocus = false;
		contentStart = System.nanoTime();
		layout();
	}

	private void setCat(int c) {
		cat = c;
		query = "";
		contentStart = System.nanoTime();
		layout();
	}

	@Override
	public void close() {
		if (!closing) {
			closing = true;
			closeStart = System.nanoTime();
			dragSlider = null;
		}
	}

	// ---------- helpers ----------

	private static float smooth(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3f - 2f * t);
	}

	private float fade = 1f;

	private int fa(int c) {
		return alpha(c, fade);
	}

	private void txt(DrawContext c, String s, float x, float y, float scale, int color) {
		UiDraw.txt(c, this.textRenderer, s, x, y, scale, fa(color));
	}

	private float tw(String s, float scale) {
		return UiDraw.width(this.textRenderer, s, scale);
	}

	private void initFlakes() {
		if (!flakes.isEmpty()) return;
		for (int i = 0; i < 26; i++) {
			Flake f = new Flake();
			resetFlake(f, true);
			flakes.add(f);
		}
	}

	private void resetFlake(Flake f, boolean anywhere) {
		f.x = rnd.nextFloat() * W;
		f.y = anywhere ? rnd.nextFloat() * H : -8f;
		f.speed = 5f + rnd.nextFloat() * 9f;
		f.size = 2.2f + rnd.nextFloat() * 3.6f;
		f.phase = rnd.nextFloat() * 6.28f;
		f.rot = rnd.nextFloat() * 3.14f;
		f.rotSpeed = (rnd.nextFloat() - 0.5f) * 0.5f;
		f.alpha = 0.22f + rnd.nextFloat() * 0.45f;
	}

	// ---------- render ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		float time = (now - openStart) / 1_000_000_000f;

		float p;
		if (closing) {
			p = 1f - (now - closeStart) / 1_000_000_000f / 0.35f;
			if (p <= 0f) {
				MinecraftClient.getInstance().setScreen(null);
				return;
			}
		} else {
			p = (now - openStart) / 1_000_000_000f / 0.5f;
		}
		float ease = smooth(p);
		float cf = 0.25f + 0.75f * smooth((now - contentStart) / 1_000_000_000f / 0.3f);
		fade = ease;

		this.renderBackground(ctx, mouseX, mouseY, delta);

		float screenFit = Math.min((this.width - 10f) / W, (this.height - 10f) / H);
		float base = Math.max(0.3f, Math.min(screenFit, menuScale));
		float sa = 0.94f + 0.06f * ease;
		ts = base * sa;
		tx = (this.width - W * ts) / 2f + offX;
		ty = (this.height - H * ts) / 2f + offY + (1f - ease) * 14f;

		double lmx = (mouseX - tx) / ts, lmy = (mouseY - ty) / ts;
		boolean live = !closing;
		float k = Math.min(1f, dt * 8f);
		float kSlow = Math.min(1f, dt * 6f);

		// ---- animation state ----
		catOn[0] += ((cat == CAT_COMBAT && view == VIEW_LIST && query.isEmpty() ? 1f : 0f) - catOn[0]) * k;
		catOn[1] += ((cat == CAT_RENDER && view == VIEW_LIST && query.isEmpty() ? 1f : 0f) - catOn[1]) * k;
		catOn[2] += ((cat == CAT_SETTINGS && view == VIEW_LIST && query.isEmpty() ? 1f : 0f) - catOn[2]) * k;
		catOn[3] += ((cat == CAT_UTILS && view == VIEW_LIST && query.isEmpty() ? 1f : 0f) - catOn[3]) * k;
		boolean overExit = live && inRect(lmx, lmy, 8, H - 36, SB - 16, 26);
		exitHover += ((overExit ? 1f : 0f) - exitHover) * k;
		boolean overBack = live && view != VIEW_LIST && Math.hypot(lmx - (CX + 11), lmy - 25) <= 12;
		backHover += ((overBack ? 1f : 0f) - backHover) * k;

		for (Module m : modules) {
			if (!m.visible) continue;
			boolean gOver = live && m.gearView >= 0 && Math.hypot(lmx - gearX(m), lmy - gearY(m)) <= 9;
			boolean over = live && m.contains(lmx, lmy) && !gOver;
			m.hover += ((over ? 1f : 0f) - m.hover) * k;
			m.gearHover += ((gOver ? 1f : 0f) - m.gearHover) * k;
			m.on += ((m.get.getAsBoolean() ? 1f : 0f) - m.on) * kSlow;
			if (m.rip >= 0f) {
				m.rip += dt / 0.7f;
				if (m.rip > 1f) m.rip = -1f;
			}
		}
		for (Toggle t : toggles) {
			t.hover += (((live && t.contains(lmx, lmy)) ? 1f : 0f) - t.hover) * k;
			t.on += ((t.get.getAsBoolean() ? 1f : 0f) - t.on) * kSlow;
		}
		for (Slider s : sliders) {
			s.hover += (((live && (s.contains(lmx, lmy) || dragSlider == s)) ? 1f : 0f) - s.hover) * k;
		}
		for (int i = sparks.size() - 1; i >= 0; i--) {
			Spark sp = sparks.get(i);
			sp.age += dt;
			sp.x += sp.vx * dt;
			sp.y += sp.vy * dt;
			sp.vx *= 0.97f;
			sp.vy *= 0.97f;
			if (sp.age >= sp.life) sparks.remove(i);
		}
		initFlakes();
		for (Flake f : flakes) {
			f.y += f.speed * dt;
			f.x += (float) Math.sin(time * 0.7f + f.phase) * 5f * dt;
			f.rot += f.rotSpeed * dt;
			if (f.y > H + 8f) resetFlake(f, false);
		}

		// ---- shapes ----
		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate(tx, ty, 0f);
		ms.scale(ts, ts, 1f);

		UiDraw.Gfx.begin(ctx, ts);
		UiDraw.Gfx.shadow(0, 10, W, H, WRAD, 30, fa(0x99000000));
		UiDraw.Gfx.rr(0, 0, W, H, WRAD, fa(alpha(0xFF0D0F13, menuOpacity)));
		UiDraw.Gfx.stroke(0, 0, W, H, WRAD, 1f, fa(0x26FFFFFF));

		for (Flake f : flakes) {
			float tw2 = 0.75f + 0.25f * (float) Math.sin(time * 1.6f + f.phase);
			int col = fa(alpha(0xFFCFE6FF, f.alpha * tw2));
			if (f.size > 4f) {
				UiDraw.Gfx.shadow(f.x - 1, f.y - 1, 2, 2, 1, f.size * 1.6f, fa(alpha(0xFF5C9BFF, 0.10f * f.alpha * 2f)));
			}
			UiDraw.Gfx.snowflake(f.x, f.y, f.size, f.rot, col);
		}

		UiDraw.Gfx.rr(SB, 14, 1, H - 28, 0.5f, fa(0x14FFFFFF));

		// sidebar
		UiDraw.Gfx.rrGrad(12, 12, 28, 28, 9, fa(C1), fa(C2), 3);
		UiDraw.Gfx.snowflake(26, 26, 7f, 0.2f, fa(WHITE));
		for (int slot = 0; slot < 4; slot++) {
			int i = CAT_ORDER[slot];
			float cy = 64 + slot * 26;
			if (catOn[i] > 0.01f) {
				UiDraw.Gfx.rrGrad(8, cy, SB - 16, 22, 9, fa(alpha(C1, catOn[i])), fa(alpha(C2, catOn[i])), 1);
			}
			int ic = fa(lerp(MUTED, WHITE, catOn[i]));
			if (i == 0) {
				UiDraw.Gfx.ringCircle(22, cy + 11, 5.2f, 1.2f, ic);
				for (int a = 0; a < 4; a++) {
					float ca = (float) Math.cos(a * Math.PI / 2), sa2 = (float) Math.sin(a * Math.PI / 2);
					UiDraw.Gfx.line(22 + ca * 2.5f, cy + 11 + sa2 * 2.5f, 22 + ca * 7.2f, cy + 11 + sa2 * 7.2f, 1.1f, ic);
				}
			} else if (i == 1) {
				UiDraw.Gfx.stroke(15, cy + 7, 14, 8, 4, 1.2f, ic);
				UiDraw.Gfx.circle(22, cy + 11, 1.9f, ic);
			} else if (i == CAT_UTILS) {
				for (int r = 0; r < 3; r++) {
					UiDraw.Gfx.line(15, cy + 6.5f + r * 4.5f, 29, cy + 6.5f + r * 4.5f, 1.2f, ic);
					UiDraw.Gfx.circle(17.5f + ((r * 5) % 9), cy + 6.5f + r * 4.5f, 1.9f, ic);
				}
			} else {
				UiDraw.Gfx.ringCircle(22, cy + 11, 3.2f, 1.5f, ic);
				for (int a = 0; a < 8; a++) {
					float ca = (float) Math.cos(a * Math.PI / 4), sa2 = (float) Math.sin(a * Math.PI / 4);
					UiDraw.Gfx.line(22 + ca * 3.8f, cy + 11 + sa2 * 3.8f, 22 + ca * 5.8f, cy + 11 + sa2 * 5.8f, 1.7f, ic);
				}
			}
		}
		if (exitHover > 0.02f) {
			UiDraw.Gfx.rr(8, H - 36, SB - 16, 26, 9, fa(alpha(0x14FFFFFF, exitHover * 2f)));
		}
		{
			int ec = fa(lerp(MUTED, WHITE, exitHover));
			UiDraw.Gfx.ringCircle(22, H - 23, 5f, 1.2f, ec);
			UiDraw.Gfx.line(22, H - 29, 22, H - 23, 1.4f, ec);
		}

		drawSnowDrifts();

		if (view == VIEW_LIST) {
			drawSearchShapes(ctx, time);
			for (Module m : modules) {
				if (m.visible) drawModuleShapes(m, cf, time);
			}
			if (!sliders.isEmpty()) drawSettingsShapes(cf, lmx, lmy);
		} else {
			drawSettingsShapes(cf, lmx, lmy);
		}

		drawSantaHat(time);

		for (Spark sp : sparks) {
			float a = 1f - sp.age / sp.life;
			UiDraw.Gfx.snowflake(sp.x, sp.y, sp.size * (0.6f + 0.4f * a), sp.age * 2f, fa(alpha(0xFFFFFFFF, a)));
		}
		UiDraw.Gfx.end();

		// ---- text ----
		txt(ctx, "Playboi Carti", 46, 14, 0.85f, TEXT);
		txt(ctx, "Client", 46, 26, 0.7f, MUTED);
		txt(ctx, "Основные", 14, 50, 0.68f, MUTED);
		for (int slot = 0; slot < 4; slot++) {
			int ci = CAT_ORDER[slot];
			txt(ctx, ci == CAT_UTILS ? "Utilities" : CAT_NAMES[ci], 36, 64 + slot * 26 + 6.5f, 0.9f, lerp(MUTED, WHITE, catOn[ci]));
		}
		txt(ctx, "Выйти", 36, H - 36 + 8.5f, 0.9f, lerp(MUTED, WHITE, exitHover));

		if (view == VIEW_LIST) {
			String shown = query.isEmpty() ? "Поиск" : query;
			int sc = query.isEmpty() ? MUTED : TEXT;
			txt(ctx, shown, CX + 28, 12 + 8.5f, 0.9f, sc);
			if (searchFocus && ((int) (time * 2f) % 2 == 0)) {
				txt(ctx, "|", CX + 28 + (query.isEmpty() ? 0 : tw(query, 0.9f) + 1f), 12 + 7.5f, 0.95f, TEXT);
			}
			for (Module m : modules) {
				if (m.visible) drawModuleText(ctx, m, cf);
			}
			if (!sliders.isEmpty()) drawSettingsText(ctx, cf);
		} else {
			drawSettingsText(ctx, cf);
		}

		ms.pop();
	}

	private static boolean inRect(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private float gearX(Module m) {
		return m.x + 8 + tw(m.name, 0.95f) + 11;
	}

	private float gearY(Module m) {
		return m.y + 13;
	}

	private void drawSearchShapes(DrawContext ctx, float time) {
		UiDraw.Gfx.rr(CX, 12, CW, 26, 9, fa(CARD_OFF));
		UiDraw.Gfx.stroke(CX, 12, CW, 26, 9, 1f, fa(searchFocus ? 0x55FFFFFF : BORDER));
		UiDraw.Gfx.ringCircle(CX + 15, 24.5f, 4.2f, 1.2f, fa(MUTED));
		UiDraw.Gfx.line(CX + 18.2f, 27.7f, CX + 21.5f, 31f, 1.3f, fa(MUTED));
	}

	private void drawModuleShapes(Module m, float cf, float time) {
		float pop = (m.rip >= 0f) ? (float) Math.sin(m.rip * Math.PI) * 1.6f : 0f;
		float x = m.x - pop, y = m.y - pop, w = m.w + pop * 2, h = m.h + pop * 2;
		int oldBorder = fa(alpha(BORDER, cf));

		UiDraw.Gfx.rr(x, y, w, h, 10, fa(alpha(CARD_OFF, cf)));
		UiDraw.Gfx.stroke(x, y, w, h, 10, 1f, oldBorder);

		if (m.on > 0.01f) {
			float wipe = h + (w - h) * (1f - (float) Math.pow(1f - Math.min(1f, m.on), 3));
			UiDraw.Gfx.shadow(x, y + 3, w, h, 10, 12, fa(alpha(0xFF7A5CF0, 0.34f * m.on * cf)));
			UiDraw.Gfx.fill(x, y, wipe, h, 10, fa(C1), fa(C2), 1, x, y, w, h);
		}
		if (m.hover > 0.02f) {
			UiDraw.Gfx.rr(x, y, w, h, 10, fa(alpha(0x14FFFFFF, m.hover)));
		}
		if (m.gearView >= 0) {
			float gx = gearX(m), gy = gearY(m);
			int gc = fa(lerp(lerp(MUTED, 0xCCFFFFFF, m.on), WHITE, m.gearHover));
			UiDraw.Gfx.ringCircle(gx, gy, 3.1f, 1.5f, gc);
			for (int a = 0; a < 8; a++) {
				float ca = (float) Math.cos(a * Math.PI / 4), sa = (float) Math.sin(a * Math.PI / 4);
				UiDraw.Gfx.line(gx + ca * 3.6f, gy + sa * 3.6f, gx + ca * 5.4f, gy + sa * 5.4f, 1.7f, gc);
			}
		}
	}

	private void drawModuleText(DrawContext ctx, Module m, float cf) {
		int nameCol = lerp(TEXT, WHITE, m.on);
		txt(ctx, m.name, m.x + 9, m.y + (compact ? 5f : 8f), 0.95f, alpha(nameCol, cf));
		int dcol = lerp(MUTED, 0xDDFFFFFF, m.on);
		String[] lines = m.desc.split("\n");
		for (int i = 0; i < lines.length; i++) {
			txt(ctx, lines[i], m.x + 9, m.y + (compact ? 15.5f : 22f) + i * (compact ? 8.5f : 9f), 0.72f, alpha(dcol, cf));
		}
	}

	private void drawSettingsShapes(float cf, double lmx, double lmy) {
		if (view != VIEW_LIST) {
			UiDraw.Gfx.circle(CX + 11, 25, 11, fa(alpha(CARD_OFF, 1f + backHover)));
			UiDraw.Gfx.ringCircle(CX + 11, 25, 11, 1f, fa(BORDER));
			int bc = fa(lerp(MUTED, WHITE, backHover));
			UiDraw.Gfx.line(CX + 12.5f, 20.5f, CX + 8.5f, 25f, 1.5f, bc);
			UiDraw.Gfx.line(CX + 8.5f, 25f, CX + 12.5f, 29.5f, 1.5f, bc);
		}

		for (Toggle t : toggles) {
			UiDraw.Gfx.rr(t.x, t.y, t.w, t.h, 9, fa(alpha(CARD_OFF, cf)));
			UiDraw.Gfx.stroke(t.x, t.y, t.w, t.h, 9, 1f, fa(alpha(BORDER, cf)));
			if (t.hover > 0.02f) UiDraw.Gfx.rr(t.x, t.y, t.w, t.h, 9, fa(alpha(0x10FFFFFF, t.hover)));
			float sx = t.x + t.w - 38, sy = t.y + (t.h - 14) / 2f;
			UiDraw.Gfx.rr(sx, sy, 28, 14, 7, fa(alpha(TRACK, cf)));
			if (t.on > 0.01f) {
				UiDraw.Gfx.fill(sx, sy, 28, 14, 7, fa(alpha(C1, t.on * cf)), fa(alpha(C2, t.on * cf)), 1, sx, sy, 28, 14);
			}
			float kx = sx + 7 + 14 * t.on;
			UiDraw.Gfx.shadow(kx - 4.5f, sy + 3, 9, 9, 4.5f, 4, fa(alpha(0xFF000000, 0.35f * cf)));
			UiDraw.Gfx.circle(kx, sy + 7, 5, fa(alpha(WHITE, cf)));
		}
		if (swatches != null) {
			Swatches sw = swatches;
			UiDraw.Gfx.rr(sw.x, sw.y, sw.w, sw.h, 9, fa(alpha(CARD_OFF, cf)));
			UiDraw.Gfx.stroke(sw.x, sw.y, sw.w, sw.h, 9, 1f, fa(alpha(BORDER, cf)));
			int cur = sw.get.getAsInt();
			for (int i = 0; i < UiDraw.PALETTE.length; i++) {
				float cx = sw.cellX(i), cy = sw.y + sw.h / 2f;
				int rgb = UiDraw.PALETTE[i];
				UiDraw.Gfx.circle(cx, cy, 7, fa(alpha(0xFF000000 | rgb, cf)));
				if (rgb == cur) UiDraw.Gfx.ringCircle(cx, cy, 9.2f, 1.4f, fa(alpha(WHITE, cf)));
			}
		}
		for (Slider s : sliders) {
			UiDraw.Gfx.rr(s.x, s.y, s.w, s.h, 9, fa(alpha(CARD_OFF, cf)));
			UiDraw.Gfx.stroke(s.x, s.y, s.w, s.h, 9, 1f, fa(alpha(BORDER, cf)));
			if (s.hover > 0.02f) UiDraw.Gfx.rr(s.x, s.y, s.w, s.h, 9, fa(alpha(0x0CFFFFFF, s.hover)));
			double v = s.get.getAsDouble();
			float frac = (float) Math.max(0.0, Math.min(1.0, (v - s.min) / (s.max - s.min)));
			float tx0 = s.trackX(), tw0 = s.trackW(), ty0 = s.y + 24;
			if (s.rainbow) {
				UiDraw.Gfx.rainbow(tx0, ty0, tw0, 4);
			} else {
				UiDraw.Gfx.rr(tx0, ty0, tw0, 4, 2, fa(alpha(TRACK, cf)));
				if (tw0 * frac > 3f) {
					UiDraw.Gfx.fill(tx0, ty0, tw0 * frac, 4, 2, fa(C1), fa(C2), 1, tx0, ty0, tw0, 4);
				}
			}
			float kx = tx0 + tw0 * frac;
			UiDraw.Gfx.shadow(kx - 5, ty0 + 1, 10, 10, 5, 5, fa(alpha(0xFF000000, 0.4f * cf)));
			UiDraw.Gfx.circle(kx, ty0 + 2, 5.6f, fa(alpha(WHITE, cf)));
		}
	}

	private void drawSettingsText(DrawContext ctx, float cf) {
		if (view != VIEW_LIST) txt(ctx, viewTitle, CX + 30, 19.5f, 1.1f, TEXT);
		for (Toggle t : toggles) {
			txt(ctx, t.label, t.x + 11, t.y + 8.5f, 0.9f, alpha(lerp(MUTED, WHITE, 0.4f + 0.6f * t.on), cf));
		}
		for (Slider s : sliders) {
			txt(ctx, s.label, s.x + 12, s.y + 8, 0.9f, alpha(TEXT, cf));
			String val = String.format(s.fmt, s.get.getAsDouble());
			txt(ctx, val, s.x + s.w - 12 - tw(val, 0.9f), s.y + 8, 0.9f, alpha(MUTED, cf));
		}
	}

	// ---------- input ----------

	private void updateSlider(Slider s, double lmx) {
		double t = (lmx - s.trackX()) / s.trackW();
		t = Math.max(0.0, Math.min(1.0, t));
		double v = s.min + t * (s.max - s.min);
		v = Math.round(v / s.step) * s.step;
		v = Math.max(s.min, Math.min(s.max, v));
		s.set.accept(v);
	}

	private void spawnSparks(float cx, float cy) {
		for (int i = 0; i < 9; i++) {
			Spark sp = new Spark();
			double a = rnd.nextDouble() * Math.PI * 2;
			float sp0 = 18f + rnd.nextFloat() * 30f;
			sp.x = cx;
			sp.y = cy;
			sp.vx = (float) Math.cos(a) * sp0;
			sp.vy = (float) Math.sin(a) * sp0;
			sp.age = 0f;
			sp.life = 0.6f + rnd.nextFloat() * 0.4f;
			sp.size = 2f + rnd.nextFloat() * 2f;
			sparks.add(sp);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (closing) return true;
		double lmx = (mouseX - tx) / ts, lmy = (mouseY - ty) / ts;

		if (lmx < 0 || lmy < 0 || lmx >= W || lmy >= H) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		if (button != 0 && button != 1) return true;

		if (inRect(lmx, lmy, 8, H - 36, SB - 16, 26)) {
			this.close();
			return true;
		}
		for (int slot = 0; slot < 4; slot++) {
			if (button == 0 && inRect(lmx, lmy, 8, 64 + slot * 26, SB - 16, 22)) {
				view = VIEW_LIST;
				setCat(CAT_ORDER[slot]);
				return true;
			}
		}

		if (view == VIEW_LIST) {
			if (inRect(lmx, lmy, CX, 12, CW, 26)) {
				searchFocus = true;
				return true;
			}
			searchFocus = false;
			if (button == 0) {
				for (Slider sl : sliders) {
					if (sl.contains(lmx, lmy)) {
						dragSlider = sl;
						updateSlider(sl, lmx);
						return true;
					}
				}
			}
			for (Module m : modules) {
				if (!m.visible || !m.contains(lmx, lmy)) continue;
				boolean onGear = m.gearView >= 0 && Math.hypot(lmx - gearX(m), lmy - gearY(m)) <= 9;
				if ((button == 0 && onGear) || (button == 1 && m.gearView >= 0)) {
					setView(m.gearView);
					return true;
				}
				if (button == 0) {
					boolean now = !m.get.getAsBoolean();
					m.set.accept(now);
					if (now) {
						m.rip = 0f;
						spawnSparks(m.x + m.w / 2f, m.y + m.h / 2f);
					}
					return true;
				}
			}
		} else {
			if (button == 0 && Math.hypot(lmx - (CX + 11), lmy - 25) <= 12) {
				setView(VIEW_LIST);
				return true;
			}
			if (button == 0) {
				for (Toggle t : toggles) {
					if (t.contains(lmx, lmy)) {
						t.set.accept(!t.get.getAsBoolean());
						return true;
					}
				}
				if (swatches != null && inRect(lmx, lmy, swatches.x, swatches.y, swatches.w, swatches.h)) {
					for (int i = 0; i < UiDraw.PALETTE.length; i++) {
						if (Math.abs(lmx - swatches.cellX(i)) <= 9 && Math.abs(lmy - (swatches.y + swatches.h / 2f)) <= 10) {
							swatches.set.accept(UiDraw.PALETTE[i]);
							return true;
						}
					}
				}
				for (Slider s : sliders) {
					if (s.contains(lmx, lmy)) {
						dragSlider = s;
						updateSlider(s, lmx);
						return true;
					}
				}
			}
		}

		if (button == 0) {
			movingWindow = true;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0) {
			if (dragSlider != null) {
				updateSlider(dragSlider, (mouseX - tx) / ts);
				return true;
			}
			if (movingWindow) {
				offX = Math.max(-this.width * 0.6f, Math.min(this.width * 0.6f, offX + (float) deltaX));
				offY = Math.max(-this.height * 0.6f, Math.min(this.height * 0.6f, offY + (float) deltaY));
				return true;
			}
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) {
			dragSlider = null;
			movingWindow = false;
			menuScale = pendingScale;
			PlayerGlowClient.saveConfig();
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (searchFocus && view == VIEW_LIST && chr >= 32 && query.length() < 18) {
			query += chr;
			contentStart = System.nanoTime();
			layout();
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchFocus && view == VIEW_LIST) {
			if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
				if (!query.isEmpty()) {
					query = query.substring(0, query.length() - 1);
					layout();
				}
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				searchFocus = false;
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void removed() {
		PlayerGlowClient.saveConfig();
		super.removed();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
