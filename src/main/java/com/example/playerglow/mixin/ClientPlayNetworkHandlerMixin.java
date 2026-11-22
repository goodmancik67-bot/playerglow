package com.example.playerglow.mixin;

import com.example.playerglow.PlayerGlowClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Velocity: keeps only a part of the knockback sent by the server. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
	/** .playboi typed in chat never reaches the server. */
	@Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true, require = 0)
	private void playerglow$chat(String content, CallbackInfo ci) {
		if (PlayerGlowClient.chatCommand(content)) ci.cancel();
	}

	@Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true, require = 0)
	private void playerglow$velocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
		if (!PlayerGlowClient.velocity) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!mc.isOnThread()) return; // let vanilla move the packet to the main thread first
		if (mc.player == null || packet.getEntityId() != mc.player.getId()) return;
		double h = PlayerGlowClient.velH / 100.0, v = PlayerGlowClient.velV / 100.0;
		Vec3d cur = mc.player.getVelocity();
		mc.player.setVelocity(
				cur.x + (packet.getVelocityX() - cur.x) * h,
				cur.y + (packet.getVelocityY() - cur.y) * v,
				cur.z + (packet.getVelocityZ() - cur.z) * h);
		ci.cancel();
	}
}
