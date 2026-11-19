package com.example.playerglow.mixin;

import com.example.playerglow.PlayerGlowClient;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Block.class)
public abstract class BlockMixin {
	private static final Map<Block, Boolean> playerglow$oreCache = new ConcurrentHashMap<>();

	@Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
	private static void playerglow$xray(BlockState state, BlockState otherState, Direction side,
			CallbackInfoReturnable<Boolean> cir) {
		if (!PlayerGlowClient.xray) return;
		boolean ore = playerglow$oreCache.computeIfAbsent(state.getBlock(), b -> {
			String path = Registries.BLOCK.getId(b).getPath();
			return path.endsWith("_ore") || path.equals("ancient_debris");
		});
		cir.setReturnValue(ore);
	}
}
