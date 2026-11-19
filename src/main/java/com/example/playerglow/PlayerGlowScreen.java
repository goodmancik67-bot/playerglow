package com.example.playerglow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PlayerGlowScreen extends Screen {
	private static final int RUBY = 0xFFE0115F;
	private static final int RUBY_HOVER = 0xFFFF2D6F;
	private static final int RUBY_DARK = 0xFF8B0A2B;

	private record Btn(int x, int y, int w, int h, Supplier<Text> label, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Btn> buttons = new ArrayList<>();
	private int panelX, panelY, panelW, panelH;

	public PlayerGlowScreen() {
		super(Text.literal("Playboi Carti Client"));
	}

	@Override
	protected void init() {
		buttons.clear();
		panelW = 220;
		panelH = 7 * 24 + 50;
		panelX = this.width / 2 - panelW / 2;
		panelY = this.height / 2 - panelH / 2;

		int bw = panelW - 24, bh = 20;
		int bx = panelX + 12;
		int by = panelY + 38;
		int step = 24;

		addToggle(bx, by, bw, bh, "Player Glow",
				() -> PlayerGlowClient.enabled, v -> PlayerGlowClient.enabled = v);
		addToggle(bx, by + step, bw, bh, "X-Ray",
				() -> PlayerGlowClient.xray,
				v -> {
					PlayerGlowClient.xray = v;
					MinecraftClient.getInstance().worldRenderer.reload();
				});
		addToggle(bx, by + step * 2, bw, bh, "Aimbot",
				() -> PlayerGlowClient.aimbot, v -> PlayerGlowClient.aimbot = v);
		addToggle(bx, by + step * 3, bw, bh, "Aim only while attacking",
				() -> PlayerGlowClient.aimOnlyWhileAttacking, v -> PlayerGlowClient.aimOnlyWhileAttacking = v);
		addToggle(bx, by + step * 4, bw, bh, "Target players",
				() -> PlayerGlowClient.aimPlayers, v -> PlayerGlowClient.aimPlayers = v);
		addToggle(bx, by + step * 5, bw, bh, "Target mobs",
				() -> PlayerGlowClient.aimMobs, v -> PlayerGlowClient.aimMobs = v);
		buttons.add(new Btn(bx, by + step * 6, bw, bh, () -> Text.literal("Close"), this::close));
	}

	private void addToggle(int x, int y, int w, int h, String name,
			Supplier<Boolean> get, Consumer<Boolean> set) {
		buttons.add(new Btn(x, y, w, h,
				() -> Text.literal(name + ": " + (get.get() ? "ON" : "OFF")),
				() -> set.accept(!get.get())));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		context.fill(0, 0, this.width, this.height, 0x55200008);
		context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0200008);
		context.drawBorder(panelX, panelY, panelW, panelH, RUBY);
		context.fill(panelX, panelY + 26, panelX + panelW, panelY + 27, RUBY);

		context.drawCenteredTextWithShadow(this.textRenderer, this.title,
				this.width / 2, panelY + 9, 0xFFFF4D7D);

		for (Btn b : buttons) {
			boolean hover = b.contains(mouseX, mouseY);
			context.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), hover ? RUBY_HOVER : RUBY_DARK);
			context.drawBorder(b.x(), b.y(), b.w(), b.h(), RUBY);
			context.drawCenteredTextWithShadow(this.textRenderer, b.label().get(),
					b.x() + b.w() / 2, b.y() + (b.h() - 8) / 2, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (Btn b : buttons) {
				if (b.contains(mouseX, mouseY)) {
					b.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
