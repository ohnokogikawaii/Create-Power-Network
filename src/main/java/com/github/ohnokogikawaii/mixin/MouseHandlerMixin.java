package com.github.ohnokogikawaii.mixin;

import com.github.ohnokogikawaii.client.JumperControlPointPlacement;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    /**
     * ジャンパ制御点の移動中はカメラ回転を止め、
     * マウス上下の移動量を制御点の高さへ渡す。
     */
    @Inject(
            method = "handleAccumulatedMovement",
            at = @At("HEAD"),
            cancellable = true
    )
    private void powernetwork$handleJumperControlPointMouse(
            CallbackInfo callback
    ) {
        if (!JumperControlPointPlacement.isMoving()) {
            return;
        }

        JumperControlPointPlacement.applyMouseVerticalDelta(
                this.accumulatedDY
        );

        // 視点回転に使われる移動量を消費する。
        this.accumulatedDX = 0.0;
        this.accumulatedDY = 0.0;

        // Minecraft標準の視点回転処理を実行しない。
        callback.cancel();
    }
}