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

public class PlayerGlowScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFF8C8C8C;
	private static final int GRAY_HOVER = 0xFFD2D2D2;
	private static final int PANEL_BODY = 0xC8202020;
	private static final int PANEL_HEAD = 0xDC121212;
	private static final int BANNER = 0xDC121212;
	private static final int TRACK = 0xFF3C3C3C;
	private static final int TRACK_FILL = 0xFFE6E6E6;

	private static final int PAGE_MAIN = 0;
	private static final int PAGE_AIMBOT = 1;

	private static final int ROW_H = 15;
	private static final int HEAD_H = 16;
	private static final int SLIDER_H = 24;
	private static final int BANNER_H = 22;

	private static final boolean[][] GEAR = buildGear();

	private static final class Toggle {
		final String label;
		final BooleanSupplier get;
		final Consumer<Boolean> set;
		final boolean hasGear;
		int x, y, w, h;
		float on, hover, gearHover;

		Toggle(String label, boolean hasGear, BooleanSupplier get, Consumer<Boolean> set) {
			this.label = label;
			this.hasGear = hasGear;
			this.get = get;
			this.set = set;
			this.on = get.getAsBoolean() ? 1f : 0f;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		boolean gearContains(double mx, double my) {
			return hasGear && mx >= x + w - 16 && mx < x + w && my >= y && my < y + h;
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
	private long openStart = System.nanoTime();
	private long lastNanos = System.nanoTime();

	public PlayerGlowScreen() {
		super(Text.literal("Playboi Carti Client"));
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
		openStart = System.nanoTime();
		this.init();
	}

	@Override
	protected void init() {
		toggles.clear();
		sliders.clear();
		panels.clear();

		int gap = 8;
		if (page == PAGE_MAIN) {
			int pw = Math.min(120, (this.width - 8 - gap) / 2);
			int total = pw * 2 + gap;
			int visualsH = HEAD_H + ROW_H * 2 + 4;
			int combatH = HEAD_H + ROW_H + 4;
			int groupH = BANNER_H + 6 + visualsH;
			int top = Math.max(4, (this.height - groupH) / 2);

			bannerX = (this.width - total) / 2;
			bannerY = top;
			bannerW = total;

			int py = top + BANNER_H + 6;
			int vx = bannerX;
			int cx = bannerX + pw + gap;
			panels.add(new Panel("Visuals", vx, py, pw, visualsH));
			panels.add(new Panel("Combat", cx, py, pw, combatH));

			addToggle(vx, pw, py, 0, "Player Glow", false,
					() -> PlayerGlowClient.enabled, v -> PlayerGlowClient.enabled = v);
			addToggle(vx, pw, py, 1, "X-Ray", false,
					() -> PlayerGlowClient.xray,
					v -> {
						PlayerGlowClient.xray = v;
						MinecraftClient.getInstance().worldRenderer.reload();
					});
			addToggle(cx, pw, py, 0, "Aimbot", true,
					() -> PlayerGlowClient.aimbot, v -> PlayerGlowClient.aimbot = v);
		} else {
			int pw = Math.min(160, this.width - 8);
			int panelH = HEAD_H + ROW_H * 3 + 6 + SLIDER_H * 3 + 6;
			int groupH = BANNER_H + 6 + panelH;
			int top = Math.max(4, (this.height - groupH) / 2);

			bannerX = (this.width - pw) / 2;
			bannerY = top;
			bannerW = pw;

			int py = top + BANNER_H + 6;
			int px = bannerX;
			panels.add(new Panel("Aimbot", px, py, pw, panelH));
			backX = px + 3;
			backY = py + 1;

			addToggle(px, pw, py, 0, "Hold attack", false,
					() -> PlayerGlowClient.aimOnlyWhileAttacking, v -> PlayerGlowClient.aimOnlyWhileAttacking = v);
			addToggle(px, pw, py, 1, "Players", false,
					() -> PlayerGlowClient.aimPlayers, v -> PlayerGlowClient.aimPlayers = v);
			addToggle(px, pw, py, 2, "Mobs", false,
					() -> PlayerGlowClient.aimMobs, v -> PlayerGlowClient.aimMobs = v);

			int sy = py + HEAD_H + ROW_H * 3 + 6;
			addSlider(px, pw, sy, 0, new Slider("Range", 1.0, 8.0, 0.1,
					() -> PlayerGlowClient.aimRange, v -> PlayerGlowClient.aimRange = v, "%.1f"));
			addSlider(px, pw, sy, 1, new Slider("FOV", 30.0, 360.0, 5.0,
					() -> PlayerGlowClient.aimFov, v -> PlayerGlowClient.aimFov = (float) v, "%.0f deg"));
			addSlider(px, pw, sy, 2, new Slider("Smooth", 1.0, 20.0, 0.5,
					() -> PlayerGlowClient.aimSmooth, v -> PlayerGlowClient.aimSmooth = (float) v, "%.1f"));
		}

		closeX = bannerX + bannerW - 16;
		closeY = bannerY + 5;
	}

	private void addToggle(int px, int pw, int py, int row, String label, boolean gear,
			BooleanSupplier get, Consumer<Boolean> set) {
		Toggle t = new Toggle(label, gear, get, set);
		t.x = px;
		t.w = pw;
		t.y = py + HEAD_H + row * ROW_H;
		t.h = ROW_H;
		toggles.add(t);
	}

	private void addSlider(int px, int pw, int sy, int row, Slider s) {
		s.x = px + 6;
		s.w = pw - 12;
		s.y = sy + row * SLIDER_H;
		s.h = SLIDER_H;
		sliders.add(s);
	}

	// ---------- helpers ----------

	private static int lerpColor(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int ba = (b >> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		int oa = (int) (aa + (ba - aa) * t);
		int or = (int) (ar + (br - ar) * t);
		int og = (int) (ag + (bg - ag) * t);
		int ob = (int) (ab + (bb - ab) * t);
		return (oa << 24) | (or << 16) | (og << 8) | ob;
	}

	private void text(DrawContext c, String s, int x, int y, int color) {
		c.drawTextWithShadow(this.textRenderer, Text.literal(s), x, y, color);
	}

	private void centered(DrawContext c, String s, int cx, int y, int color) {
		c.drawCenteredTextWithShadow(this.textRenderer, Text.literal(s), cx, y, color);
	}

	private void scaledCentered(DrawContext c, String s, int cx, int y, float scale, int color) {
		int w = Math.round(this.textRenderer.getWidth(s) * scale);
		MatrixStack m = c.getMatrices();
		m.push();
		m.translate((float) (cx - w / 2), (float) y, 0f);
		m.scale(scale, scale, 1f);
		c.drawTextWithShadow(this.textRenderer, Text.literal(s), 0, 0, color);
		m.pop();
	}

	private static void drawGear(DrawContext c, int x, int y, int color) {
		for (int i = 0; i < 9; i++) {
			for (int j = 0; j < 9; j++) {
				if (GEAR[i][j]) {
					c.fill(x + i, y + j, x + i + 1, y + j + 1, color);
				}
			}
		}
	}

	// ---------- render ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		float open = Math.min(1f, (now - openStart) / 200_000_000f);
		float ease = 1f - (1f - open) * (1f - open) * (1f - open);
		float oy = (1f - ease) * -12f;
		lastOy = oy;
		float k = Math.min(1f, dt * 14f);
		int my = Math.round(mouseY - oy);

		ctx.fill(0, 0, this.width, this.height, ((int) (0x40 * ease) << 24));

		MatrixStack stack = ctx.getMatrices();
		stack.push();
		stack.translate(0f, oy, 0f);

		// banner
		ctx.fill(bannerX, bannerY, bannerX + bannerW, bannerY + BANNER_H, BANNER);
		scaledCentered(ctx, "Playboi Carti Client", bannerX + bannerW / 2, bannerY + 7, 1.25f, WHITE);

		boolean overClose = mouseX >= closeX && mouseX < closeX + 12 && my >= closeY && my < closeY + 12;
		closeHover += ((overClose ? 1f : 0f) - closeHover) * k;
		centered(ctx, "x", closeX + 6, closeY + 2, lerpColor(GRAY, WHITE, closeHover));

		// panels
		for (Panel p : panels) {
			ctx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), PANEL_BODY);
			ctx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + HEAD_H, PANEL_HEAD);
			centered(ctx, p.title(), p.x() + p.w() / 2, p.y() + 4, WHITE);
		}

		// back button on the aimbot page
		if (page == PAGE_AIMBOT) {
			boolean overBack = mouseX >= backX && mouseX < backX + 16 && my >= backY && my < backY + 14;
			backHover += ((overBack ? 1f : 0f) - backHover) * k;
			text(ctx, "<", backX + 3, backY + 3, lerpColor(GRAY, WHITE, backHover));
		}

		// toggles
		for (Toggle t : toggles) {
			boolean overGear = t.gearContains(mouseX, my);
			boolean over = t.contains(mouseX, my) && !overGear;
			t.hover += ((over ? 1f : 0f) - t.hover) * k;
			t.gearHover += ((overGear ? 1f : 0f) - t.gearHover) * k;
			t.on += ((t.get.getAsBoolean() ? 1f : 0f) - t.on) * k;

			if (t.hover > 0.02f) {
				ctx.fill(t.x, t.y, t.x + t.w, t.y + t.h, ((int) (0x28 * t.hover) << 24) | 0xFFFFFF);
			}
			int base = lerpColor(GRAY, WHITE, t.on);
			int col = lerpColor(base, WHITE, t.hover * 0.6f);
			text(ctx, ">", t.x + 5, t.y + 4, col);
			text(ctx, t.label, t.x + 14, t.y + 4, col);

			if (t.hasGear) {
				int gx = t.x + t.w - 14;
				int gy = t.y + (t.h - 9) / 2;
				drawGear(ctx, gx, gy, lerpColor(GRAY, WHITE, t.gearHover));
			}
		}

		// sliders
		for (Slider s : sliders) {
			float hoverTarget = (s.contains(mouseX, my) || dragging == s) ? 1f : 0f;
			s.hover += (hoverTarget - s.hover) * k;

			double v = s.get.getAsDouble();
			float frac = (float) Math.max(0.0, Math.min(1.0, (v - s.min) / (s.max - s.min)));

			text(ctx, s.label, s.x, s.y + 2, lerpColor(GRAY, WHITE, s.hover));
			String val = String.format(s.fmt, v);
			text(ctx, val, s.x + s.w - this.textRenderer.getWidth(val), s.y + 2, GRAY_HOVER);

			int ty = s.y + 15;
			ctx.fill(s.x, ty, s.x + s.w, ty + 3, TRACK);
			int fw = Math.round(s.w * frac);
			if (fw > 0) {
				ctx.fill(s.x, ty, s.x + fw, ty + 3, TRACK_FILL);
			}
			int kx = Math.max(s.x, Math.min(s.x + s.w - 6, s.x + fw - 3));
			ctx.fill(kx, ty - 2, kx + 6, ty + 5, WHITE);
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
		if (button == 0) {
			double my = mouseY - lastOy;

			if (mouseX >= closeX && mouseX < closeX + 12 && my >= closeY && my < closeY + 12) {
				this.close();
				return true;
			}
			if (page == PAGE_AIMBOT && mouseX >= backX && mouseX < backX + 16 && my >= backY && my < backY + 14) {
				setPage(PAGE_MAIN);
				return true;
			}
			for (Toggle t : toggles) {
				if (t.gearContains(mouseX, my)) {
					setPage(PAGE_AIMBOT);
					return true;
				}
				if (t.contains(mouseX, my)) {
					t.set.accept(!t.get.getAsBoolean());
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
