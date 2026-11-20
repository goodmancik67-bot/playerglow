package com.example.playerglow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;

public final class UiDraw {
	private UiDraw() {}

	public static final int ACCENT = 0xFF86E3A8;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFF8C8C8C;

	public static final Style FONT = Style.EMPTY.withFont(Identifier.of("playerglow", "ui"));

	public static Text fnt(String s) {
		return Text.literal(s).setStyle(FONT);
	}

	public static int alpha(int color, float mul) {
		int a = (color >> 24) & 255;
		a = Math.max(0, Math.min(255, Math.round(a * mul)));
		return (a << 24) | (color & 0xFFFFFF);
	}

	public static int lerp(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int ba = (b >> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		int oa = (int) (aa + (ba - aa) * t);
		int or = (int) (ar + (br - ar) * t);
		int og = (int) (ag + (bg - ag) * t);
		int ob = (int) (ab + (bb - ab) * t);
		return (oa << 24) | (or << 16) | (og << 8) | ob;
	}

	private static int inset(int r, int edge) {
		if (edge >= r) return 0;
		double dy = r - edge - 0.5;
		double dx = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
		return (int) Math.round(r - dx);
	}

	public static void fillRound(DrawContext c, int x, int y, int w, int h, int r, int color) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		for (int row = 0; row < h; row++) {
			int edge = Math.min(row, h - 1 - row);
			int ins = inset(r, edge);
			c.fill(x + ins, y + row, x + w - ins, y + row + 1, color);
		}
	}

	public static void fillRoundTop(DrawContext c, int x, int y, int w, int h, int r, int color) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		for (int row = 0; row < h; row++) {
			int ins = row < r ? inset(r, row) : 0;
			c.fill(x + ins, y + row, x + w - ins, y + row + 1, color);
		}
	}

	public static void strokeRound(DrawContext c, int x, int y, int w, int h, int r, int color) {
		r = Math.max(1, Math.min(r, Math.min(w, h) / 2));
		for (int row = 0; row < h; row++) {
			int edge = Math.min(row, h - 1 - row);
			int o = inset(r, edge);
			if (row == 0 || row == h - 1) {
				c.fill(x + o, y + row, x + w - o, y + row + 1, color);
				continue;
			}
			int innerRow = row - 1;
			int ih = h - 2;
			int ie = Math.min(innerRow, ih - 1 - innerRow);
			int ii = 1 + (ie < r - 1 ? inset(r - 1, ie) : 0);
			ii = Math.max(ii, o + 1);
			c.fill(x + o, y + row, x + ii, y + row + 1, color);
			c.fill(x + w - ii, y + row, x + w - o, y + row + 1, color);
		}
	}

	/** Rounded green outline with a soft glow around it. */
	public static void glowStroke(DrawContext c, int x, int y, int w, int h, int r, int color, float glow) {
		for (int k = 4; k >= 1; k--) {
			float a = glow * 0.16f / k;
			strokeRound(c, x - k, y - k, w + 2 * k, h + 2 * k, r + k, alpha(color, a));
		}
		strokeRound(c, x, y, w, h, r, color);
	}

	public static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
		if (((color >> 24) & 255) < 10) return;
		c.drawText(tr, fnt(s), x, y, color, shadow);
	}

	/** Text that glows (used for enabled features). */
	public static void glowText(DrawContext c, TextRenderer tr, String s, int x, int y, int color, float glow) {
		if (glow > 0.03f) {
			int g = alpha(ACCENT, 0.16f * glow);
			text(c, tr, s, x - 1, y, g, false);
			text(c, tr, s, x + 1, y, g, false);
			text(c, tr, s, x, y - 1, g, false);
			text(c, tr, s, x, y + 1, g, false);
		}
		text(c, tr, s, x, y, color, true);
	}

	// ---------- Target HUD ----------

	private static float shownHealth = 0f;
	private static LivingEntity lastTarget = null;

	public static void drawTargetHud(DrawContext c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!PlayerGlowClient.targetHud || mc.player == null || mc.world == null) return;

		LivingEntity target = null;
		if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le) {
			target = le;
		}
		if (target == null) {
			double best = 10.0 * 10.0;
			for (Entity e : mc.world.getEntities()) {
				if (e == mc.player || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
				if (e instanceof ArmorStandEntity || e.isSpectator()) continue;
				double d = e.squaredDistanceTo(mc.player);
				if (d < best) {
					best = d;
					target = le;
				}
			}
		}
		if (target == null || !target.isAlive() || target instanceof ArmorStandEntity) return;

		if (target != lastTarget) {
			lastTarget = target;
			shownHealth = target.getHealth();
		}
		shownHealth += (target.getHealth() - shownHealth) * 0.08f;

		TextRenderer tr = mc.textRenderer;
		int w = 112, h = 32;
		int x = c.getScaledWindowWidth() / 2 + 24;
		int y = c.getScaledWindowHeight() / 2 + 20;

		fillRound(c, x, y, w, h, 5, 0xC8181818);
		glowStroke(c, x, y, w, h, 5, alpha(ACCENT, 0.9f), 0.8f);

		String name = target.getName().getString();
		if (name.length() > 16) name = name.substring(0, 16);
		text(c, tr, name, x + 6, y + 5, WHITE, true);

		float max = Math.max(1f, target.getMaxHealth());
		float frac = Math.max(0f, Math.min(1f, shownHealth / max));
		int bx = x + 6, by = y + 20, bw = w - 12;
		fillRound(c, bx, by, bw, 5, 2, 0xFF2E2E2E);
		int fw = Math.round(bw * frac);
		if (fw > 1) fillRound(c, bx, by, fw, 5, 2, ACCENT);

		String hp = String.format("%.1f", target.getHealth());
		text(c, tr, hp, x + w - 6 - tr.getWidth(fnt(hp)), y + 5, ACCENT, true);
	}

	public static boolean isPlayer(Entity e) {
		return e instanceof PlayerEntity;
	}
}
