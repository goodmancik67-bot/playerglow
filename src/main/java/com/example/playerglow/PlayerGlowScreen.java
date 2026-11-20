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
	private static final int RUBY = 0xFFE0115F;
	private static final int RUBY_LIGHT = 0xFFFF4D7D;
	private static final int RUBY_DARK = 0xFF8B0A2B;
	private static final int CARD_BG = 0xFF1C0710;
	private static final int CARD_ROW_HOVER = 0xFF2E0F1B;
	private static final int CARD_BORDER = 0xFF4A1224;
	private static final int OFF_TRACK = 0xFF3A1A22;
	private static final int TEXT_ON = 0xFFFFFFFF;
	private static final int TEXT_OFF = 0xFFB59AA2;
	private static final int TEXT_DIM = 0xFF8A6A74;

	private static final class Toggle {
		final String label;
		final BooleanSupplier get;
		final Consumer<Boolean> set;
		int x, y, w, h;
		float on, hover;

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

	private record Card(String title, int x, int y, int w, int h) {}

	private final List<Toggle> toggles = new ArrayList<>();
	private final List<Slider> sliders = new ArrayList<>();
	private final List<Card> cards = new ArrayList<>();

	private int winX, winY, winW, winH;
	private int closeX, closeY;
	private int cardY, cardW, cardH;
	private int cardX1, cardX2, cardX3;

	private Slider dragging = null;
	private float lastOy = 0f;
	private long openStart = System.nanoTime();
	private long lastNanos = System.nanoTime();

	public PlayerGlowScreen() {
		super(Text.literal("Playboi Carti Client"));
	}

	@Override
	protected void init() {
		toggles.clear();
		sliders.clear();
		cards.clear();

		int pad = 10, gap = 8;
		winW = Math.min(this.width - 16, 540);
		winH = 184;
		winX = (this.width - winW) / 2;
		winY = Math.max(4, (this.height - winH) / 2);

		cardW = (winW - 2 * pad - 2 * gap) / 3;
		cardH = 120;
		cardY = winY + 40;
		cardX1 = winX + pad;
		cardX2 = cardX1 + cardW + gap;
		cardX3 = cardX2 + cardW + gap;

		closeX = winX + winW - pad - 16;
		closeY = winY + 9;

		cards.add(new Card("VISUALS", cardX1, cardY, cardW, cardH));
		cards.add(new Card("AIMBOT", cardX2, cardY, cardW, cardH));
		cards.add(new Card("TUNING", cardX3, cardY, cardW, cardH));

		addToggle(cardX1, 0, "Player Glow",
				() -> PlayerGlowClient.enabled, v -> PlayerGlowClient.enabled = v);
		addToggle(cardX1, 1, "X-Ray",
				() -> PlayerGlowClient.xray,
				v -> {
					PlayerGlowClient.xray = v;
					MinecraftClient.getInstance().worldRenderer.reload();
				});

		addToggle(cardX2, 0, "Aimbot",
				() -> PlayerGlowClient.aimbot, v -> PlayerGlowClient.aimbot = v);
		addToggle(cardX2, 1, "Hold attack",
				() -> PlayerGlowClient.aimOnlyWhileAttacking, v -> PlayerGlowClient.aimOnlyWhileAttacking = v);
		addToggle(cardX2, 2, "Players",
				() -> PlayerGlowClient.aimPlayers, v -> PlayerGlowClient.aimPlayers = v);
		addToggle(cardX2, 3, "Mobs",
				() -> PlayerGlowClient.aimMobs, v -> PlayerGlowClient.aimMobs = v);

		addSlider(cardX3, 0, new Slider("Range", 1.0, 8.0, 0.1,
				() -> PlayerGlowClient.aimRange, v -> PlayerGlowClient.aimRange = v, "%.1f"));
		addSlider(cardX3, 1, new Slider("FOV", 30.0, 360.0, 5.0,
				() -> PlayerGlowClient.aimFov, v -> PlayerGlowClient.aimFov = (float) v, "%.0f deg"));
		addSlider(cardX3, 2, new Slider("Smooth", 1.0, 20.0, 0.5,
				() -> PlayerGlowClient.aimSmooth, v -> PlayerGlowClient.aimSmooth = (float) v, "%.1f"));
	}

	private void addToggle(int cardX, int row, String label, BooleanSupplier get, Consumer<Boolean> set) {
		Toggle t = new Toggle(label, get, set);
		t.x = cardX + 4;
		t.y = cardY + 24 + row * 22;
		t.w = cardW - 8;
		t.h = 20;
		toggles.add(t);
	}

	private void addSlider(int cardX, int row, Slider s) {
		s.x = cardX + 6;
		s.y = cardY + 24 + row * 30;
		s.w = cardW - 12;
		s.h = 28;
		sliders.add(s);
	}

	// ---------- drawing helpers ----------

	private static void roundRect(DrawContext c, int x, int y, int w, int h, int color) {
		if (w < 5 || h < 5) {
			c.fill(x, y, x + w, y + h, color);
			return;
		}
		c.fill(x + 2, y, x + w - 2, y + 1, color);
		c.fill(x + 1, y + 1, x + w - 1, y + 2, color);
		c.fill(x, y + 2, x + w, y + h - 2, color);
		c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
		c.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
	}

	private static int lerpColor(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int ar = (a >> 24) & 255, rr = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int br = (b >> 24) & 255, brr = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		int oa = (int) (ar + (br - ar) * t);
		int or = (int) (rr + (brr - rr) * t);
		int og = (int) (ag + (bg - ag) * t);
		int ob = (int) (ab + (bb - ab) * t);
		return (oa << 24) | (or << 16) | (og << 8) | ob;
	}

	private void text(DrawContext c, String s, int x, int y, int color) {
		c.drawTextWithShadow(this.textRenderer, Text.literal(s), x, y, color);
	}

	private void scaledText(DrawContext c, String s, int x, int y, float scale, int color) {
		MatrixStack m = c.getMatrices();
		m.push();
		m.translate((float) x, (float) y, 0f);
		m.scale(scale, scale, 1f);
		c.drawTextWithShadow(this.textRenderer, Text.literal(s), 0, 0, color);
		m.pop();
	}

	// ---------- render ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);

		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		float open = Math.min(1f, (now - openStart) / 220_000_000f);
		float ease = 1f - (1f - open) * (1f - open) * (1f - open);
		float oy = (1f - ease) * -14f;
		lastOy = oy;
		float k = Math.min(1f, dt * 14f);
		int my = Math.round(mouseY - oy);

		int dim = ((int) (0x66 * ease) << 24) | 0x100004;
		ctx.fill(0, 0, this.width, this.height, dim);

		MatrixStack stack = ctx.getMatrices();
		stack.push();
		stack.translate(0f, oy, 0f);

		// window: glow, frame, gradient body
		roundRect(ctx, winX - 2, winY - 2, winW + 4, winH + 4, 0x33E0115F);
		roundRect(ctx, winX, winY, winW, winH, RUBY);
		ctx.fillGradient(winX + 1, winY + 2, winX + winW - 1, winY + winH - 2, 0xFF2E0814, 0xFF10030A);

		// header
		scaledText(ctx, "PLAYBOI CARTI CLIENT", winX + 10, winY + 8, 1.4f, RUBY_LIGHT);
		text(ctx, "client menu  -  press M to open", winX + 10, winY + 23, TEXT_DIM);
		ctx.fill(winX + 10, winY + 34, winX + winW - 10, winY + 35, RUBY_DARK);

		// close button
		boolean closeHover = mouseX >= closeX && mouseX < closeX + 16 && my >= closeY && my < closeY + 16;
		roundRect(ctx, closeX, closeY, 16, 16, closeHover ? RUBY : RUBY_DARK);
		ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("x"), closeX + 8, closeY + 4, TEXT_ON);

		// cards
		for (Card cd : cards) {
			roundRect(ctx, cd.x(), cd.y(), cd.w(), cd.h(), CARD_BORDER);
			roundRect(ctx, cd.x() + 1, cd.y() + 1, cd.w() - 2, cd.h() - 2, CARD_BG);
			ctx.fill(cd.x() + 6, cd.y() + 7, cd.x() + 8, cd.y() + 15, RUBY);
			text(ctx, cd.title(), cd.x() + 12, cd.y() + 7, RUBY_LIGHT);
		}

		// hints in the visuals card
		if (cardW >= 140) {
			int hy = cardY + 72;
			text(ctx, "Glow outlines players", cardX1 + 8, hy, TEXT_DIM);
			text(ctx, "through walls.", cardX1 + 8, hy + 10, TEXT_DIM);
			text(ctx, "X-Ray shows only ores", cardX1 + 8, hy + 22, TEXT_DIM);
			text(ctx, "and adds night vision.", cardX1 + 8, hy + 32, TEXT_DIM);
		}

		// toggles
		for (Toggle t : toggles) {
			float hoverTarget = t.contains(mouseX, my) ? 1f : 0f;
			t.hover += (hoverTarget - t.hover) * k;
			float onTarget = t.get.getAsBoolean() ? 1f : 0f;
			t.on += (onTarget - t.on) * k;

			if (t.hover > 0.02f) {
				roundRect(ctx, t.x, t.y, t.w, t.h, lerpColor(CARD_BG, CARD_ROW_HOVER, t.hover));
			}
			text(ctx, t.label, t.x + 6, t.y + 6, lerpColor(TEXT_OFF, TEXT_ON, t.on));

			int sw = 26, sh = 12;
			int sx = t.x + t.w - sw - 6;
			int sy = t.y + 4;
			roundRect(ctx, sx, sy, sw, sh, lerpColor(OFF_TRACK, RUBY, t.on));
			int kx = sx + 2 + Math.round((sw - 12) * t.on);
			roundRect(ctx, kx, sy + 2, 8, 8, 0xFFFFFFFF);
		}

		// sliders
		for (Slider s : sliders) {
			float hoverTarget = (s.contains(mouseX, my) || dragging == s) ? 1f : 0f;
			s.hover += (hoverTarget - s.hover) * k;

			double v = s.get.getAsDouble();
			float frac = (float) Math.max(0.0, Math.min(1.0, (v - s.min) / (s.max - s.min)));

			text(ctx, s.label, s.x, s.y + 1, lerpColor(TEXT_OFF, TEXT_ON, s.hover));
			String val = String.format(s.fmt, v);
			text(ctx, val, s.x + s.w - this.textRenderer.getWidth(val), s.y + 1, RUBY_LIGHT);

			int ty = s.y + 17;
			roundRect(ctx, s.x, ty, s.w, 4, OFF_TRACK);
			int fw = Math.round(s.w * frac);
			if (fw > 0) {
				roundRect(ctx, s.x, ty, Math.max(fw, 1), 4, RUBY);
			}
			int kx = s.x + fw - 4;
			kx = Math.max(s.x - 1, Math.min(s.x + s.w - 7, kx));
			roundRect(ctx, kx - 1, ty - 3, 10, 10, lerpColor(RUBY_DARK, RUBY_LIGHT, s.hover));
			roundRect(ctx, kx, ty - 2, 8, 8, 0xFFFFFFFF);
		}

		// footer
		ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("ESC or X to close"),
				this.width / 2, cardY + cardH + 8, TEXT_DIM);

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
			if (mouseX >= closeX && mouseX < closeX + 16 && my >= closeY && my < closeY + 16) {
				this.close();
				return true;
			}
			for (Toggle t : toggles) {
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
