package com.example.playerglow;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class PlayerGlowScreen extends Screen {
	private ButtonWidget toggleButton;

	public PlayerGlowScreen() {
		super(Text.literal("Player Glow"));
	}

	@Override
	protected void init() {
		int w = 160, h = 20;
		toggleButton = ButtonWidget.builder(label(), b -> {
			PlayerGlowClient.enabled = !PlayerGlowClient.enabled;
			b.setMessage(label());
		}).dimensions(this.width / 2 - w / 2, this.height / 2 - h / 2, w, h).build();
		this.addDrawableChild(toggleButton);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
				.dimensions(this.width / 2 - w / 2, this.height / 2 + 28, w, h).build());
	}

	private static Text label() {
		return Text.literal("Player Glow: " + (PlayerGlowClient.enabled ? "ON" : "OFF"));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
