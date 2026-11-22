package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.wire.WireEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class JumperWireEditorScreen extends Screen {

    private final WireEntity wire;

    public JumperWireEditorScreen(WireEntity wire) {
        super(Component.literal("ジャンパ線編集"));
        this.wire = wire;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        addRenderableWidget(
                net.minecraft.client.gui.components.Button.builder(
                        Component.literal("閉じる"),
                        button -> onClose()
                ).bounds(
                        centerX - 50,
                        centerY + 45,
                        100,
                        20
                ).build()
        );
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.drawCenteredString(
                this.font,
                this.title,
                centerX,
                centerY - 55,
                0xFFFFFF
        );

        if (wire.isRemoved()) {
            graphics.drawCenteredString(
                    this.font,
                    "対象のジャンパ線が存在しません",
                    centerX,
                    centerY - 15,
                    0xFF5555
            );
            return;
        }

        graphics.drawCenteredString(
                this.font,
                "制御点数: " + wire.getJumperControlPoints().size(),
                centerX,
                centerY - 25,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                this.font,
                "最大制御点数: 16",
                centerX,
                centerY - 10,
                0xAAAAAA
        );

        graphics.drawCenteredString(
                this.font,
                "制御点の追加・編集機能は次の段階で実装します",
                centerX,
                centerY + 15,
                0xAAAAAA
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}