package com.example.playerglow;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public final class UiDraw {
	private UiDraw() {}

	// ---------- palette ----------
	public static final int C1 = 0xFF4A78F0;      // blue
	public static final int C2 = 0xFFE4465F;      // red / pink
	public static final int WHITE = 0xFFFFFFFF;
	public static final int TEXT = 0xFFF2F4F8;
	public static final int MUTED = 0xFF8C919C;
	public static final int WIN_BG = 0xE00D0F13;
	public static final int CARD_OFF = 0x14FFFFFF;
	public static final int BORDER = 0x1CFFFFFF;
	public static final int TRACK = 0xFF2A2E38;

	public static final int[] PALETTE = {
			0x3A9BFF, 0x2EE6D6, 0x4ADE80, 0xF2C14E, 0xFB923C, 0xF43F5E, 0xA78BFA, 0xFFFFFF
	};

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

	/** h in degrees 0..360, s and v in 0..1. Returns 0xRRGGBB. */
	public static int hsv(float h, float s, float v) {
		h = ((h % 360f) + 360f) % 360f;
		float c = v * s;
		float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
		float m = v - c;
		float r, g, b;
		if (h < 60) { r = c; g = x; b = 0; }
		else if (h < 120) { r = x; g = c; b = 0; }
		else if (h < 180) { r = 0; g = c; b = x; }
		else if (h < 240) { r = 0; g = x; b = c; }
		else if (h < 300) { r = x; g = 0; b = c; }
		else { r = c; g = 0; b = x; }
		int ri = Math.round((r + m) * 255), gi = Math.round((g + m) * 255), bi = Math.round((b + m) * 255);
		return (ri << 16) | (gi << 8) | bi;
	}

	public static float hueOf(int rgb) {
		float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float d = max - min;
		if (d < 0.001f) return 0f;
		float h;
		if (max == r) h = ((g - b) / d) % 6f;
		else if (max == g) h = (b - r) / d + 2f;
		else h = (r - g) / d + 4f;
		h *= 60f;
		return h < 0 ? h + 360f : h;
	}

	// ---------- text ----------

	/** Smooth text at any scale (uses the matrix stack, so it stays sharp). */
	public static void txt(DrawContext c, TextRenderer tr, String s, float x, float y, float scale, int color) {
		if (((color >> 24) & 255) < 8) return;
		MatrixStack ms = c.getMatrices();
		ms.push();
		ms.translate(x, y, 0f);
		ms.scale(scale, scale, 1f);
		c.drawText(tr, fnt(s), 0, 0, color, false);
		ms.pop();
	}

	public static float width(TextRenderer tr, String s, float scale) {
		return tr.getWidth(fnt(s)) * scale;
	}

	// ---------- smooth anti-aliased 2D shapes (real triangles, not pixel fills) ----------

	public static final class Gfx {
		private Gfx() {}

		private static BufferBuilder bb;
		private static Matrix4f mat;
		private static float feather = 0.5f;

		public static float feather() {
			return feather;
		}

		public static void begin(DrawContext c, float scale) {
			c.draw();
			mat = new Matrix4f(c.getMatrices().peek().getPositionMatrix());
			double gs = MinecraftClient.getInstance().getWindow().getScaleFactor();
			feather = (float) (1.0 / (gs * Math.max(0.1f, scale)));
			bb = null;
		}

		public static void end() {
			if (bb == null) return;
			RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableDepthTest();
			RenderSystem.disableCull();
			BufferRenderer.drawWithGlobalProgram(bb.end());
			RenderSystem.enableCull();
			RenderSystem.enableDepthTest();
			bb = null;
		}

		private static void v(float x, float y, int argb) {
			if (bb == null) {
				bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
			}
			bb.vertex(mat, x, y, 0f).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
		}

		private static void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
			v(x1, y1, c1);
			v(x2, y2, c2);
			v(x3, y3, c3);
		}

		private static void quadStrip(float ax, float ay, int ac, float bx, float by, int bc,
				float cx, float cy, int cc, float dx, float dy, int dc) {
			tri(ax, ay, ac, bx, by, bc, cx, cy, cc);
			tri(ax, ay, ac, cx, cy, cc, dx, dy, dc);
		}

		private static int segs(float r) {
			return r > 10f ? 10 : r > 4f ? 7 : 5;
		}

		/** Points of a rounded rect grown by off (clockwise, y down). */
		private static float[] ring(float x, float y, float w, float h, float r, float off, int n) {
			r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
			float rr = Math.max(0.01f, r + off);
			float[] p = new float[(n + 1) * 8];
			float[][] ctr = {{x + r, y + r}, {x + w - r, y + r}, {x + w - r, y + h - r}, {x + r, y + h - r}};
			float[] start = {(float) Math.PI, (float) (Math.PI * 1.5), 0f, (float) (Math.PI * 0.5)};
			int k = 0;
			for (int c = 0; c < 4; c++) {
				for (int i = 0; i <= n; i++) {
					float a = start[c] + (float) (Math.PI / 2 * i / n);
					p[k++] = ctr[c][0] + (float) Math.cos(a) * rr;
					p[k++] = ctr[c][1] + (float) Math.sin(a) * rr;
				}
			}
			return p;
		}

		/** gradient: mode 0 solid(c0), 1 horizontal, 2 vertical, 3 diagonal over the box g*. */
		private static int colorAt(float px, float py, int c0, int c1, int mode, float gx, float gy, float gw, float gh) {
			if (mode == 0) return c0;
			float t;
			if (mode == 1) t = (px - gx) / gw;
			else if (mode == 2) t = (py - gy) / gh;
			else t = ((px - gx) / gw + (py - gy) / gh) * 0.5f;
			return lerp(c0, c1, t);
		}

		public static void fill(float x, float y, float w, float h, float r, int c0, int c1, int mode,
				float gx, float gy, float gw, float gh) {
			if (w <= 0 || h <= 0) return;
			int n = segs(r);
			float[] in = ring(x, y, w, h, r, -feather * 0.5f, n);
			float[] out = ring(x, y, w, h, r, feather * 0.5f, n);
			float cx = x + w / 2f, cy = y + h / 2f;
			int cc = colorAt(cx, cy, c0, c1, mode, gx, gy, gw, gh);
			int cnt = in.length / 2;
			for (int i = 0; i < cnt; i++) {
				int j = (i + 1) % cnt;
				int ci = colorAt(in[i * 2], in[i * 2 + 1], c0, c1, mode, gx, gy, gw, gh);
				int cj = colorAt(in[j * 2], in[j * 2 + 1], c0, c1, mode, gx, gy, gw, gh);
				tri(cx, cy, cc, in[i * 2], in[i * 2 + 1], ci, in[j * 2], in[j * 2 + 1], cj);
				quadStrip(in[i * 2], in[i * 2 + 1], ci, out[i * 2], out[i * 2 + 1], alpha(ci, 0f),
						out[j * 2], out[j * 2 + 1], alpha(cj, 0f), in[j * 2], in[j * 2 + 1], cj);
			}
		}

		public static void rr(float x, float y, float w, float h, float r, int color) {
			fill(x, y, w, h, r, color, color, 0, x, y, w, h);
		}

		public static void rrGrad(float x, float y, float w, float h, float r, int c0, int c1, int mode) {
			fill(x, y, w, h, r, c0, c1, mode, x, y, w, h);
		}

		public static void circle(float cx, float cy, float rad, int color) {
			rr(cx - rad, cy - rad, rad * 2, rad * 2, rad, color);
		}

		public static void stroke(float x, float y, float w, float h, float r, float lw, int color) {
			int n = segs(r);
			float[][] rg = {
					ring(x, y, w, h, r, -lw / 2f - feather, n),
					ring(x, y, w, h, r, -lw / 2f, n),
					ring(x, y, w, h, r, lw / 2f, n),
					ring(x, y, w, h, r, lw / 2f + feather, n)};
			int[] col = {alpha(color, 0f), color, color, alpha(color, 0f)};
			int cnt = rg[0].length / 2;
			for (int s = 0; s < 3; s++) {
				for (int i = 0; i < cnt; i++) {
					int j = (i + 1) % cnt;
					quadStrip(rg[s][i * 2], rg[s][i * 2 + 1], col[s],
							rg[s + 1][i * 2], rg[s + 1][i * 2 + 1], col[s + 1],
							rg[s + 1][j * 2], rg[s + 1][j * 2 + 1], col[s + 1],
							rg[s][j * 2], rg[s][j * 2 + 1], col[s]);
				}
			}
		}

		public static void ringCircle(float cx, float cy, float rad, float lw, int color) {
			stroke(cx - rad, cy - rad, rad * 2, rad * 2, rad, lw, color);
		}

		/** Soft shadow / glow under a rounded rect. */
		public static void shadow(float x, float y, float w, float h, float r, float blur, int color) {
			int n = segs(r + blur);
			float[][] rg = {
					ring(x, y, w, h, r, -blur * 0.45f, n),
					ring(x, y, w, h, r, 0f, n),
					ring(x, y, w, h, r, blur, n)};
			int[] col = {color, alpha(color, 0.42f), alpha(color, 0f)};
			float cx = x + w / 2f, cy = y + h / 2f;
			int cnt = rg[0].length / 2;
			for (int i = 0; i < cnt; i++) {
				int j = (i + 1) % cnt;
				tri(cx, cy, col[0], rg[0][i * 2], rg[0][i * 2 + 1], col[0], rg[0][j * 2], rg[0][j * 2 + 1], col[0]);
				for (int s = 0; s < 2; s++) {
					quadStrip(rg[s][i * 2], rg[s][i * 2 + 1], col[s],
							rg[s + 1][i * 2], rg[s + 1][i * 2 + 1], col[s + 1],
							rg[s + 1][j * 2], rg[s + 1][j * 2 + 1], col[s + 1],
							rg[s][j * 2], rg[s][j * 2 + 1], col[s]);
				}
			}
		}

		public static void line(float x1, float y1, float x2, float y2, float wd, int color) {
			float dx = x2 - x1, dy = y2 - y1;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			if (len < 0.001f) return;
			float nx = -dy / len, ny = dx / len;
			float h = wd / 2f, f = feather;
			int tr = alpha(color, 0f);
			quadStrip(x1 + nx * (h + f), y1 + ny * (h + f), tr, x1 + nx * h, y1 + ny * h, color,
					x2 + nx * h, y2 + ny * h, color, x2 + nx * (h + f), y2 + ny * (h + f), tr);
			quadStrip(x1 + nx * h, y1 + ny * h, color, x1 - nx * h, y1 - ny * h, color,
					x2 - nx * h, y2 - ny * h, color, x2 + nx * h, y2 + ny * h, color);
			quadStrip(x1 - nx * h, y1 - ny * h, color, x1 - nx * (h + f), y1 - ny * (h + f), tr,
					x2 - nx * (h + f), y2 - ny * (h + f), tr, x2 - nx * h, y2 - ny * h, color);
		}

		/** Horizontal rainbow bar (hue slider). */
		public static void rainbow(float x, float y, float w, float h) {
			int n = 36;
			for (int i = 0; i < n; i++) {
				float x0 = x + w * i / n, x1 = x + w * (i + 1) / n;
				int c0 = 0xFF000000 | hsv(360f * i / n, 0.75f, 1f);
				int c1 = 0xFF000000 | hsv(360f * (i + 1) / n, 0.75f, 1f);
				quadStrip(x0, y, c0, x1, y, c1, x1, y + h, c1, x0, y + h, c0);
			}
		}

		public static void snowflake(float cx, float cy, float size, float rot, int color) {
			float lw = Math.max(0.5f, size * 0.16f);
			for (int k = 0; k < 3; k++) {
				float a = rot + (float) (k * Math.PI / 3);
				float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
				line(cx - ca * size, cy - sa * size, cx + ca * size, cy + sa * size, lw, color);
			}
			for (int k = 0; k < 6; k++) {
				float a = rot + (float) (k * Math.PI / 3);
				float bx = cx + (float) Math.cos(a) * size * 0.58f, by = cy + (float) Math.sin(a) * size * 0.58f;
				for (int sgn = -1; sgn <= 1; sgn += 2) {
					float b = a + sgn * 0.95f;
					line(bx, by, bx + (float) Math.cos(b) * size * 0.34f, by + (float) Math.sin(b) * size * 0.34f,
							lw * 0.85f, color);
				}
			}
		}
	}

	// ---------- HUD: target / hit marker / info ----------

	private static LivingEntity lastHit = null;
	private static long lastHitNanos = 0L;
	private static float shownHealth = 0f;

	public static void noteHit(LivingEntity e) {
		if (e != lastHit) {
			lastHit = e;
			shownHealth = e.getHealth();
		}
		lastHitNanos = System.nanoTime();
	}

	public static void drawTargetHud(DrawContext c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!PlayerGlowClient.targetHud || lastHit == null || mc.player == null) return;
		float age = (System.nanoTime() - lastHitNanos) / 1_000_000_000f;
		if (age > 2.0f) return;
		float a = Math.min(1f, age / 0.15f) * Math.min(1f, (2.0f - age) / 0.35f);

		TextRenderer tr = mc.textRenderer;
		shownHealth += (lastHit.getHealth() - shownHealth) * 0.12f;
		float w = 124f, h = 36f;
		float x = c.getScaledWindowWidth() / 2f + 24f;
		float y = c.getScaledWindowHeight() / 2f + 18f + (1f - a) * 6f;

		Gfx.begin(c, 1f);
		Gfx.shadow(x, y + 2, w, h, 9, 10, alpha(0xFF000000, 0.45f * a));
		Gfx.rr(x, y, w, h, 9, alpha(WIN_BG, a));
		Gfx.stroke(x, y, w, h, 9, 1f, alpha(BORDER, a));
		float max = Math.max(1f, lastHit.getMaxHealth());
		float frac = Math.max(0f, Math.min(1f, shownHealth / max));
		float bx = x + 10, by = y + 24, bw = w - 20;
		Gfx.rr(bx, by, bw, 5, 2.5f, alpha(TRACK, a));
		if (bw * frac > 3f) Gfx.fill(bx, by, bw * frac, 5, 2.5f, alpha(C1, a), alpha(C2, a), 1, bx, by, bw, 5);
		Gfx.end();

		String name = lastHit.getName().getString();
		if (name.length() > 14) name = name.substring(0, 14);
		txt(c, tr, name, x + 10, y + 8, 0.95f, alpha(TEXT, a));
		String hp = String.format("%.1f", Math.max(0f, lastHit.getHealth()));
		txt(c, tr, hp, x + w - 10 - width(tr, hp, 0.95f), y + 8, 0.95f, alpha(WHITE, a));
	}

	public static void drawHitMarker(DrawContext c) {
		if (!PlayerGlowClient.hitMarker || lastHitNanos == 0L) return;
		float t = (System.nanoTime() - lastHitNanos) / 1_000_000_000f / 0.4f;
		if (t >= 1f) return;
		float cx = c.getScaledWindowWidth() / 2f;
		float cy = c.getScaledWindowHeight() / 2f;
		float gap = 3.5f + t * 4f, len = 4.5f;
		int col = alpha(lerp(WHITE, C2, t), 1f - t);
		Gfx.begin(c, 1f);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				Gfx.line(cx + sx * gap, cy + sy * gap, cx + sx * (gap + len), cy + sy * (gap + len), 1.3f, col);
			}
		}
		Gfx.end();
	}

	public static void drawInfoHud(DrawContext c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!PlayerGlowClient.infoHud || mc.player == null) return;
		TextRenderer tr = mc.textRenderer;
		String l1 = "FPS  " + mc.getCurrentFps();
		String l2 = String.format("XYZ  %.0f  %.0f  %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
		String l3 = "Facing  " + mc.player.getHorizontalFacing().asString();
		float tw = Math.max(width(tr, l1, 0.95f), Math.max(width(tr, l2, 0.95f), width(tr, l3, 0.95f)));
		float w = tw + 20f, h = 48f, x = 8f, y = 8f;
		Gfx.begin(c, 1f);
		Gfx.shadow(x, y + 2, w, h, 9, 10, alpha(0xFF000000, 0.4f));
		Gfx.rr(x, y, w, h, 9, WIN_BG);
		Gfx.stroke(x, y, w, h, 9, 1f, BORDER);
		Gfx.rrGrad(x + 6, y + 9, 2.2f, h - 18, 1.1f, C1, C2, 2);
		Gfx.end();
		txt(c, tr, l1, x + 14, y + 8, 0.95f, TEXT);
		txt(c, tr, l2, x + 14, y + 21, 0.95f, MUTED);
		txt(c, tr, l3, x + 14, y + 34, 0.95f, MUTED);
	}

	// ---------- 3D helpers ----------

	public static void vtx(BufferBuilder bb, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
		bb.vertex(m, x, y, z).color(r, g, b, a);
	}

	public static void quad(BufferBuilder bb, Matrix4f m,
			float x1, float y1, float z1, float x2, float y2, float z2,
			float x3, float y3, float z3, float x4, float y4, float z4,
			int r, int g, int b, int a) {
		vtx(bb, m, x1, y1, z1, r, g, b, a);
		vtx(bb, m, x2, y2, z2, r, g, b, a);
		vtx(bb, m, x3, y3, z3, r, g, b, a);
		vtx(bb, m, x4, y4, z4, r, g, b, a);
	}

	public static void cuboid(BufferBuilder bb, Matrix4f m,
			float x1, float y1, float z1, float x2, float y2, float z2,
			int r, int g, int b, int a) {
		quad(bb, m, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, r, g, b, a);
		quad(bb, m, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
		quad(bb, m, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
		quad(bb, m, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
		quad(bb, m, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
		quad(bb, m, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, r, g, b, a);
	}

	/** One glowing elongated crystal (flat bipyramid), spinning around its axis and tilted. */
	public static void crystal(BufferBuilder bb, Matrix4f m, float cx, float cy, float cz,
			float len, float wid, float spin, float tilt, float tiltDir, int rgb, int a, boolean shaded) {
		float wz = wid * 0.55f;
		float[][] v = {
				{ 0f, len, 0f }, { 0f, -len, 0f },
				{ wid, 0f, 0f }, { 0f, 0f, wz }, { -wid, 0f, 0f }, { 0f, 0f, -wz } };
		float cs = (float) Math.cos(spin), sn = (float) Math.sin(spin);
		float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
		float cd = (float) Math.cos(tiltDir), sd = (float) Math.sin(tiltDir);
		float[][] p = new float[6][3];
		for (int i = 0; i < 6; i++) {
			float x = v[i][0], y = v[i][1], z = v[i][2];
			float x1 = x * cs + z * sn, z1 = -x * sn + z * cs; // spin around Y
			float y2 = y * ct - z1 * st, z2 = y * st + z1 * ct; // tilt around X
			float x3 = x1 * cd + z2 * sd, z3 = -x1 * sd + z2 * cd; // turn around Y
			p[i][0] = cx + x3;
			p[i][1] = cy + y2;
			p[i][2] = cz + z3;
		}
		int r0 = (rgb >> 16) & 255, g0 = (rgb >> 8) & 255, b0 = rgb & 255;
		float[] shade = { 1.0f, 0.88f, 0.78f, 0.92f, 0.82f, 0.7f, 0.76f, 0.86f };
		for (int f = 0; f < 8; f++) {
			int apex = f < 4 ? 0 : 1;
			int e0 = 2 + (f % 4), e1 = 2 + ((f + 1) % 4);
			float sh = shaded ? shade[f] : 1f;
			int r = Math.min(255, Math.round(r0 * sh + (shaded ? 22 : 0)));
			int g = Math.min(255, Math.round(g0 * sh + (shaded ? 22 : 0)));
			int b = Math.min(255, Math.round(b0 * sh + (shaded ? 22 : 0)));
			quad(bb, m, p[apex][0], p[apex][1], p[apex][2], p[e0][0], p[e0][1], p[e0][2],
					p[e1][0], p[e1][1], p[e1][2], p[e1][0], p[e1][1], p[e1][2], r, g, b, a);
		}
	}

	/** Glowing petals orbiting the crosshair (Target ESP). */
	public static void drawCrosshairOrbit(DrawContext c) {
		if (!PlayerGlowClient.targetEsp || !PlayerGlowClient.targetCross) return;
		float cx = c.getScaledWindowWidth() / 2f, cy = c.getScaledWindowHeight() / 2f;
		double t = System.nanoTime() / 1_000_000_000.0;
		float spd = PlayerGlowClient.targetSpeed;
		int rgb = PlayerGlowClient.targetColor & 0xFFFFFF;
		float al = PlayerGlowClient.targetAlpha;
		int n = 7;
		Gfx.begin(c, 1f);
		for (int i = 0; i < n; i++) {
			float ang = (float) (t * spd * 1.1 + i * Math.PI * 2 / n);
			float rad = 13f + 3.2f * (float) Math.sin(t * 1.8 + i * 1.9);
			float px = cx + (float) Math.cos(ang) * rad, py = cy + (float) Math.sin(ang) * rad;
			float tw = 0.7f + 0.3f * (float) Math.sin(t * 4 + i * 2.3);
			float dx = (float) Math.cos(ang + Math.PI / 2), dy = (float) Math.sin(ang + Math.PI / 2);
			Gfx.circle(px, py, 6.5f, alpha(0xFF000000 | rgb, 0.10f * al * tw));
			Gfx.circle(px, py, 4.2f, alpha(0xFF000000 | rgb, 0.18f * al * tw));
			Gfx.line(px - dx * 2.4f, py - dy * 2.4f, px + dx * 2.4f, py + dy * 2.4f, 3.0f, alpha(0xFF000000 | rgb, 0.95f * al * tw));
			Gfx.circle(px, py, 1.3f, alpha(0xFFFFFFFF, 0.9f * al * tw));
		}
		Gfx.end();
	}

	/** Translucent 3D box with solid edges. */
	public static void espBox(BufferBuilder bb, Matrix4f m,
			float x1, float y1, float z1, float x2, float y2, float z2,
			int rgb, float alpha, float t) {
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		int fill = Math.round(255 * alpha * 0.18f);
		int edge = Math.round(255 * alpha);
		cuboid(bb, m, x1, y1, z1, x2, y2, z2, r, g, b, fill);
		for (int iy = 0; iy < 2; iy++) {
			float y = iy == 0 ? y1 : y2;
			for (int iz = 0; iz < 2; iz++) {
				float z = iz == 0 ? z1 : z2;
				cuboid(bb, m, x1 - t, y - t, z - t, x2 + t, y + t, z + t, r, g, b, edge);
			}
		}
		for (int ix = 0; ix < 2; ix++) {
			float x = ix == 0 ? x1 : x2;
			for (int iz = 0; iz < 2; iz++) {
				float z = iz == 0 ? z1 : z2;
				cuboid(bb, m, x - t, y1 - t, z - t, x + t, y2 + t, z + t, r, g, b, edge);
			}
		}
		for (int ix = 0; ix < 2; ix++) {
			float x = ix == 0 ? x1 : x2;
			for (int iy = 0; iy < 2; iy++) {
				float y = iy == 0 ? y1 : y2;
				cuboid(bb, m, x - t, y - t, z1 - t, x + t, y + t, z2 + t, r, g, b, edge);
			}
		}
	}

	/** Conical straw hat: curved cone, woven shading, dark rim band and a small top knob. */
	public static void chinaHat(BufferBuilder bb, Matrix4f m,
			float cx, float by, float cz, float radius, float spin, int rgb, float alpha) {
		final int rings = 7, seg = 30;
		float height = radius * 0.62f;
		int a = Math.round(255 * alpha);
		int br = (rgb >> 16) & 255, bg = (rgb >> 8) & 255, bbl = rgb & 255;

		for (int i = 0; i < rings; i++) {
			float t0 = i / (float) rings, t1 = (i + 1) / (float) rings;
			float r0 = radius * t0, r1 = radius * t1;
			float y0 = by + height * (float) Math.pow(1.0 - t0, 1.35);
			float y1 = by + height * (float) Math.pow(1.0 - t1, 1.35);
			for (int j = 0; j < seg; j++) {
				float a0 = spin + (float) (j * Math.PI * 2 / seg);
				float a1 = spin + (float) ((j + 1) * Math.PI * 2 / seg);
				float mid = (a0 + a1) * 0.5f;
				float light = 0.5f + 0.5f * (float) Math.cos(mid - 0.9f);
				float shade = 0.60f + 0.40f * light;
				if ((i & 1) == 0) shade *= 0.93f;
				if ((j & 1) == 0) shade *= 0.96f;
				int r = Math.min(255, Math.round(br * shade));
				int g = Math.min(255, Math.round(bg * shade));
				int b = Math.min(255, Math.round(bbl * shade));
				quad(bb, m,
						cx + (float) Math.cos(a0) * r0, y0, cz + (float) Math.sin(a0) * r0,
						cx + (float) Math.cos(a1) * r0, y0, cz + (float) Math.sin(a1) * r0,
						cx + (float) Math.cos(a1) * r1, y1, cz + (float) Math.sin(a1) * r1,
						cx + (float) Math.cos(a0) * r1, y1, cz + (float) Math.sin(a0) * r1,
						r, g, b, a);
			}
		}

		// dark rim band
		float bandH = 0.05f;
		int dr = Math.round(br * 0.55f), dg = Math.round(bg * 0.55f), db = Math.round(bbl * 0.55f);
		for (int j = 0; j < seg; j++) {
			float a0 = spin + (float) (j * Math.PI * 2 / seg);
			float a1 = spin + (float) ((j + 1) * Math.PI * 2 / seg);
			quad(bb, m,
					cx + (float) Math.cos(a0) * radius, by, cz + (float) Math.sin(a0) * radius,
					cx + (float) Math.cos(a1) * radius, by, cz + (float) Math.sin(a1) * radius,
					cx + (float) Math.cos(a1) * radius, by - bandH, cz + (float) Math.sin(a1) * radius,
					cx + (float) Math.cos(a0) * radius, by - bandH, cz + (float) Math.sin(a0) * radius,
					dr, dg, db, a);
		}

		// top knob
		float kr = radius * 0.10f, kh = radius * 0.16f;
		float ty = by + height;
		int kr8 = Math.min(255, br + 40), kg8 = Math.min(255, bg + 40), kb8 = Math.min(255, bbl + 40);
		for (int j = 0; j < 10; j++) {
			float a0 = spin + (float) (j * Math.PI * 2 / 10);
			float a1 = spin + (float) ((j + 1) * Math.PI * 2 / 10);
			quad(bb, m,
					cx, ty + kh, cz,
					cx, ty + kh, cz,
					cx + (float) Math.cos(a1) * kr, ty, cz + (float) Math.sin(a1) * kr,
					cx + (float) Math.cos(a0) * kr, ty, cz + (float) Math.sin(a0) * kr,
					kr8, kg8, kb8, a);
		}
	}
}
