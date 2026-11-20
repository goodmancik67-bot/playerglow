package com.example.playerglow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import static com.example.playerglow.UiDraw.ACCENT;
import static com.example.playerglow.UiDraw.GRAY;
import static com.example.playerglow.UiDraw.WHITE;
import static com.example.playerglow.UiDraw.alpha;
import static com.example.playerglow.UiDraw.lerp;

public class PlayerGlowScreen extends Screen {
	private static final int PANEL_BODY = 0xC8181818;
	private static final int PANEL_HEAD = 0xE00E0E0E;
	private static final int TRACK = 0xFF343434;

	private static final int PAGE_MAIN = 0;
	private static final int PAGE_AIMBOT = 1;
	private static final int PAGE_CRYSTAL = 2;
	private static final int PAGE_ESP = 3;

	private static final int ROW_H = 15;
	private static final int HEAD_H = 16;
	private static final int SLIDER_H = 24;
	private static final int BANNER_H = 22;
	private static final int RAD = 6;

	private static final float OPEN_SEC = 0.45f;
	private static final float CLOSE_SEC = 0.35f;
	private static final float PAGE_SEC = 0.30f;

	private static final boolean[][] GEAR = buildGear();

	private static final class Toggle {
		final String label;
		final int gearPage;
		final BooleanSupplier get;
		final Consumer<Boolean> set;
		int x, y, w, h;
		float on, hover, gearHover, pulse;

		Toggle(String label, int gearPage, BooleanSupplier get, Consumer<Boolean> set) {
			this.label = label;
			this.gearPage = gearPage;
			this.get = get;
			this.set = set;
			this.on = get.getAsBoolean() ? 1f : 0f;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		boolean gearContains(double mx, double my) {
			return gearPage >= 0 && mx >= x + w - 18 && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final class Slider {
		final String label;
		final double min, max, step;
		final DoubleSupplier get;
		final DoubleConsumer set;
		final String fmt;
		int x, y, w, h;
		float hover;

		Slider(String label, double min, double max, double step, DoubleSupplier get, DoubleConsumer set, String fmt) {
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.fmt = fmt;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record Panel(String title, int x, int y, int w, int h) {}

	private final List<Toggle> toggles = new ArrayList<>();
	private final List<Slider> sliders = new ArrayList<>();
	private final List<Panel> panels = new ArrayList<>();

	private int page = PAGE_MAIN;
	private int bannerX, bannerY, bannerW;
	private int closeX, closeY;
	private int backX, backY;

	private Slider dragging = null;
	private float lastOy = 0f;
	private float backHover = 0f, closeHover = 0f;
	private float fade = 1f;
	private boolean closing = false;
	private long openStart = System.nanoTime();
	private long closeStart = 0L;
	private long pageStart = 0L;
	private long lastNanos = System.nanoTime();

	public PlayerGlowScreen() {
		super(Text.literal("Playboi Carti Client"));
		pageStart = openStart - 1_000_000_000L;
	}

	private static boolean[][] buildGear() {
		boolean[][] g = new boolean[9][9];
		for (int i = 0; i < 9; i++) {
			for (int j = 0; j < 9; j++) {
				double d = Math.hypot(i - 4, j - 4);
				g[i][j] = d <= 3.4 && d >= 1.6;
			}
		}
		int[][] teeth = {
				{4, 0}, {3, 0}, {5, 0}, {4, 8}, {3, 8}, {5, 8},
				{0, 4}, {0, 3}, {0, 5}, {8, 4}, {8, 3}, {8, 5},
				{1, 1}, {7, 1}, {1, 7}, {7, 7}
		};
		for (int[] t : teeth) {
			g[t[0]][t[1]] = true;
		}
		return g;
	}

	private void setPage(int p) {
		page = p;
		dragging = null;
		pageStart = System.nanoTime();
		this.init();
	}

	@Override
	public void close() {
		if (!closing) {
			closing = true;
			closeStart = System.nanoTime();
			dragging = null;
		}
	}

	// ---------- layout ----------

	@Override
	protected void init() {
		toggles.clear();
		sliders.clear();
		panels.clear();

		if (page == PAGE_MAIN) {
			buildMain();
		} else if (page == PAGE_AIMBOT) {
			List<Toggle> ts = new ArrayList<>();
			ts.add(new Toggle("Hold attack", -1,
					() -> PlayerGlowClient.aimOnlyWhileAttacking, v -> PlayerGlowClient.aimOnlyWhileAttacking = v));
			ts.add(new Toggle("Players", -1,
					() -> PlayerGlowClient.aimPlayers, v -> PlayerGlowClient.aimPlayers = v));
			ts.add(new Toggle("Mobs", -1,
					() -> PlayerGlowClient.aimMobs, v -> PlayerGlowClient.aimMobs = v));
			List<Slider> ss = new ArrayList<>();
			ss.add(new Slider("Range", 1.0, 8.0, 0.1,
					() -> PlayerGlowClient.aimRange, v -> PlayerGlowClient.aimRange = v, "%.1f"));
			ss.add(new Slider("FOV", 30.0, 360.0, 5.0,
					() -> PlayerGlowClient.aimFov, v -> PlayerGlowClient.aimFov = (float) v, "%.0f deg"));
			ss.add(new Slider("Smooth", 1.0, 20.0, 0.5,
					() -> PlayerGlowClient.aimSmooth, v -> PlayerGlowClient.aimSmooth = (float) v, "%.1f"));
			buildSettings("Aimbot", ts, ss);
		} else if (page == PAGE_CRYSTAL) {
			List<Toggle> ts = new ArrayList<>();
			ts.add(new Toggle("Auto place", -1,
					() -> PlayerGlowClient.crystalAutoPlace, v -> PlayerGlowClient.crystalAutoPlace = v));
			ts.add(new Toggle("Auto break", -1,
					() -> PlayerGlowClient.crystalAutoBreak, v -> PlayerGlowClient.crystalAutoBreak = v));
			List<Slider> ss = new ArrayList<>();
			ss.add(new Slider("Range", 1.0, 6.0, 0.5,
					() -> PlayerGlowClient.crystalRange, v -> PlayerGlowClient.crystalRange = v, "%.1f"));
			ss.add(new Slider("Delay", 0.0, 10.0, 1.0,
					() -> PlayerGlowClient.crystalDelayTicks, v -> PlayerGlowClient.crystalDelayTicks = (float) v, "%.0f tick"));
			buildSettings("CrystalAura", ts, ss);
		} else {
			List<Toggle> ts = new ArrayList<>();
			ts.add(new Toggle("Players", -1,
					() -> PlayerGlowClient.espPlayers, v -> PlayerGlowClient.espPlayers = v));
			ts.add(new Toggle("Mobs", -1,
					() -> PlayerGlowClient.espMobs, v -> PlayerGlowClient.espMobs = v));
			ts.add(new Toggle("China hat", -1,
					() -> PlayerGlowClient.chinaHat, v -> PlayerGlowClient.chinaHat = v));
			buildSettings("Player Glow", ts, new ArrayList<>());
		}

		closeX = bannerX + bannerW - 16;
		closeY = bannerY + 5;
	}

	private void buildMain() {
		int gap = 8;
		int pw = Math.min(126, (this.width - 8 - gap) / 2);
		int total = pw * 2 + gap;
		int visualsH = HEAD_H + ROW_H * 3 + 5;
		int combatH = HEAD_H + ROW_H * 4 + 5;
		int groupH = BANNER_H + 8 + Math.max(visualsH, combatH);
		int top = Math.max(6, (this.height - groupH) / 2);

		bannerX = (this.width - total) / 2;
		bannerY = top;
		bannerW = total;

		int py = top + BANNER_H + 8;
		int vx = bannerX;
		int cx = bannerX + pw + gap;
		panels.add(new Panel("Visuals", vx, py, pw, visualsH));
		panels.add(new Panel("Combat", cx, py, pw, combatH));

		addRow(vx, pw, py, 0, new Toggle("Player Glow", PAGE_ESP,
				() -> PlayerGlowClient.enabled, v -> PlayerGlowClient.enabled = v));
		addRow(vx, pw, py, 1, new Toggle("X-Ray", -1,
				() -> PlayerGlowClient.xray,
				v -> {
					PlayerGlowClient.xray = v;
					MinecraftClient.getInstance().worldRenderer.reload();
				}));
		addRow(vx, pw, py, 2, new Toggle("Target HUD", -1,
				() -> PlayerGlowClient.targetHud, v -> PlayerGlowClient.targetHud = v));

		addRow(cx, pw, py, 0, new Toggle("Aimbot", PAGE_AIMBOT,
				() -> PlayerGlowClient.aimbot, v -> PlayerGlowClient.aimbot = v));
		addRow(cx, pw, py, 1, new Toggle("CrystalAura", PAGE_CRYSTAL,
				() -> PlayerGlowClient.crystalAura, v -> PlayerGlowClient.crystalAura = v));
		addRow(cx, pw, py, 2, new Toggle("TriggerBot", -1,
				() -> PlayerGlowClient.triggerbot, v -> PlayerGlowClient.triggerbot = v));
		addRow(cx, pw, py, 3, new Toggle("Criticals", -1,
				() -> PlayerGlowClient.criticals, v -> PlayerGlowClient.criticals = v));
	}

	private void buildSettings(String title, List<Toggle> ts, List<Slider> ss) {
		int pw = Math.min(172, this.width - 8);
		int panelH = HEAD_H + ROW_H * ts.size() + (ss.isEmpty() ? 0 : 6 + SLIDER_H * ss.size()) + 6;
		int groupH = BANNER_H + 8 + panelH;
		int top = Math.max(6, (this.height - groupH) / 2);

		bannerX = (this.width - pw) / 2;
		bannerY = top;
		bannerW = pw;

		int py = top + BANNER_H + 8;
		int px = bannerX;
		panels.add(new Panel(title, px, py, pw, panelH));
		backX = px + 4;
		backY = py + 1;

		for (int i = 0; i < ts.size(); i++) {
			addRow(px, pw, py, i, ts.get(i));
		}
		int sy = py + HEAD_H + ROW_H * ts.size() + 6;
		for (int i = 0; i < ss.size(); i++) {
			Slider s = ss.get(i);
			s.x = px + 9;
			s.w = pw - 18;
			s.y = sy + i * SLIDER_H;
			s.h = SLIDER_H;
			sliders.add(s);
		}
	}

	private void addRow(int px, int pw, int py, int row, Toggle t) {
		t.x = px;
		t.w = pw;
		t.y = py + HEAD_H + row * ROW_H;
		t.h = ROW_H;
		toggles.add(t);
	}

	// ---------- helpers ----------

	private int fa(int color) {
		return alpha(color, fade);
	}

	private void txt(DrawContext c, String s, int x, int y, int color) {
		UiDraw.text(c, this.textRenderer, s, x, y, fa(color), true);
	}

	private void ctxt(DrawContext c, String s, int cx, int y, int color) {
		int w = this.textRenderer.getWidth(UiDraw.fnt(s));
		txt(c, s, cx - w / 2, y, color);
	}

	private void gtxt(DrawContext c, String s, int x, int y, int color, float glow) {
		UiDraw.glowText(c, this.textRenderer, s, x, y, fa(color), glow * fade);
	}

	private void drawGear(DrawContext c, int x, int y, int color) {
		for (int i = 0; i < 9; i++) {
			for (int j = 0; j < 9; j++) {
				if (GEAR[i][j]) {
					c.fill(x + i, y + j, x + i + 1, y + j + 1, color);
				}
			}
		}
	}

	private static float smooth(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3f - 2f * t);
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
			p = 1f - (now - closeStart) / 1_000_000_000f / CLOSE_SEC;
			if (p <= 0f) {
				MinecraftClient.getInstance().setScreen(null);
				return;
			}
		} else {
			p = (now - openStart) / 1_000_000_000f / OPEN_SEC;
		}
		float ease = smooth(p);
		float pageEase = 0.3f + 0.7f * smooth((now - pageStart) / 1_000_000_000f / PAGE_SEC);
		fade = ease * pageEase;

		float oy = (1f - ease) * -22f;
		lastOy = oy;
		float k = Math.min(1f, dt * 6f);
		int my = Math.round(mouseY - oy);
		float glow = 0.75f + 0.25f * (float) Math.sin(time * 1.6);

		ctx.fill(0, 0, this.width, this.height, ((int) (0x48 * ease) << 24));

		MatrixStack stack = ctx.getMatrices();
		stack.push();
		stack.translate(0f, oy, 0f);

		// banner
		UiDraw.fillRound(ctx, bannerX, bannerY, bannerW, BANNER_H, RAD, fa(PANEL_HEAD));
		UiDraw.glowStroke(ctx, bannerX, bannerY, bannerW, BANNER_H, RAD, fa(alpha(ACCENT, 0.9f)), glow * fade);
		{
			String title = "Playboi Carti Client";
			int tw = Math.round(this.textRenderer.getWidth(UiDraw.fnt(title)) * 1.2f);
			MatrixStack m = ctx.getMatrices();
			m.push();
			m.translate((float) (bannerX + bannerW / 2 - tw / 2), (float) (bannerY + 7), 0f);
			m.scale(1.2f, 1.2f, 1f);
			UiDraw.text(ctx, this.textRenderer, title, 0, 0, fa(WHITE), true);
			m.pop();
		}

		boolean overClose = !closing && mouseX >= closeX && mouseX < closeX + 12 && my >= closeY && my < closeY + 12;
		closeHover += ((overClose ? 1f : 0f) - closeHover) * k;
		ctxt(ctx, "x", closeX + 6, closeY + 2, lerp(GRAY, ACCENT, closeHover));

		// panels
		for (Panel pn : panels) {
			UiDraw.fillRound(ctx, pn.x(), pn.y(), pn.w(), pn.h(), RAD, fa(PANEL_BODY));
			UiDraw.fillRoundTop(ctx, pn.x(), pn.y(), pn.w(), HEAD_H, RAD, fa(PANEL_HEAD));
			ctx.fill(pn.x() + 4, pn.y() + HEAD_H, pn.x() + pn.w() - 4, pn.y() + HEAD_H + 1, fa(alpha(ACCENT, 0.35f)));
			UiDraw.glowStroke(ctx, pn.x(), pn.y(), pn.w(), pn.h(), RAD, fa(alpha(ACCENT, 0.85f)), glow * fade);
			ctxt(ctx, pn.title(), pn.x() + pn.w() / 2, pn.y() + 4, WHITE);
		}

		// back button
		if (page != PAGE_MAIN) {
			boolean overBack = !closing && mouseX >= backX && mouseX < backX + 16 && my >= backY && my < backY + 14;
			backHover += ((overBack ? 1f : 0f) - backHover) * k;
			txt(ctx, "<", backX + 3, backY + 3, lerp(GRAY, ACCENT, backHover));
		}

		// toggles
		for (Toggle t : toggles) {
			boolean overGear = !closing && t.gearContains(mouseX, my);
			boolean over = !closing && t.contains(mouseX, my) && !overGear;
			t.hover += ((over ? 1f : 0f) - t.hover) * k;
			t.gearHover += ((overGear ? 1f : 0f) - t.gearHover) * k;
			t.on += ((t.get.getAsBoolean() ? 1f : 0f) - t.on) * Math.min(1f, dt * 4f);
			t.pulse = Math.max(0f, t.pulse - dt * 0.9f);

			if (t.hover > 0.02f) {
				int hc = ((int) (0x26 * t.hover) << 24) | 0xFFFFFF;
				UiDraw.fillRound(ctx, t.x + 3, t.y, t.w - 6, t.h, 3, fa(hc));
			}
			if (t.on > 0.02f) {
				ctx.fill(t.x + 3, t.y + 3, t.x + 4, t.y + t.h - 3, fa(alpha(ACCENT, t.on)));
			}
			int base = lerp(GRAY, ACCENT, t.on);
			int col = lerp(base, WHITE, t.hover * (1f - t.on) * 0.5f);
			float g = Math.min(1f, t.on + t.pulse * 0.8f);
			gtxt(ctx, ">", t.x + 8, t.y + 4, col, g);
			gtxt(ctx, t.label, t.x + 17, t.y + 4, col, g);

			if (t.gearPage >= 0) {
				int gx = t.x + t.w - 15;
				int gy = t.y + (t.h - 9) / 2;
				drawGear(ctx, gx, gy, fa(lerp(GRAY, ACCENT, t.gearHover)));
			}
		}

		// sliders
		for (Slider s : sliders) {
			float hoverTarget = (!closing && (s.contains(mouseX, my) || dragging == s)) ? 1f : 0f;
			s.hover += (hoverTarget - s.hover) * k;

			double v = s.get.getAsDouble();
			float frac = (float) Math.max(0.0, Math.min(1.0, (v - s.min) / (s.max - s.min)));

			txt(ctx, s.label, s.x, s.y + 2, lerp(GRAY, WHITE, s.hover));
			String val = String.format(s.fmt, v);
			txt(ctx, val, s.x + s.w - this.textRenderer.getWidth(UiDraw.fnt(val)), s.y + 2, ACCENT);

			int ty = s.y + 15;
			UiDraw.fillRound(ctx, s.x, ty, s.w, 3, 1, fa(TRACK));
			int fw = Math.round(s.w * frac);
			if (fw > 1) {
				UiDraw.fillRound(ctx, s.x, ty - 1, fw, 5, 2, fa(alpha(ACCENT, 0.18f)));
				UiDraw.fillRound(ctx, s.x, ty, fw, 3, 1, fa(ACCENT));
			}
			int kx = Math.max(s.x, Math.min(s.x + s.w - 6, s.x + fw - 3));
			UiDraw.fillRound(ctx, kx, ty - 2, 6, 7, 2, fa(WHITE));
		}

		stack.pop();
	}

	// ---------- input ----------

	private void updateSlider(Slider s, double mouseX) {
		double t = (mouseX - s.x) / (double) s.w;
		t = Math.max(0.0, Math.min(1.0, t));
		double v = s.min + t * (s.max - s.min);
		v = Math.round(v / s.step) * s.step;
		v = Math.max(s.min, Math.min(s.max, v));
		s.set.accept(v);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (closing) return true;
		if (button == 0) {
			double my = mouseY - lastOy;

			if (mouseX >= closeX && mouseX < closeX + 12 && my >= closeY && my < closeY + 12) {
				this.close();
				return true;
			}
			if (page != PAGE_MAIN && mouseX >= backX && mouseX < backX + 16 && my >= backY && my < backY + 14) {
				setPage(PAGE_MAIN);
				return true;
			}
			for (Toggle t : toggles) {
				if (t.gearContains(mouseX, my)) {
					setPage(t.gearPage);
					return true;
				}
				if (t.contains(mouseX, my)) {
					t.set.accept(!t.get.getAsBoolean());
					t.pulse = 1f;
					return true;
				}
			}
			for (Slider s : sliders) {
				if (s.contains(mouseX, my)) {
					dragging = s;
					updateSlider(s, mouseX);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging != null && button == 0) {
			updateSlider(dragging, mouseX);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) {
			dragging = null;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
