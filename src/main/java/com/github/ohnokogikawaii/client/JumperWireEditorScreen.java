
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.network.JumperControlPointNetwork;
import com.github.ohnokogikawaii.wire.WireEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class JumperWireEditorScreen extends Screen {

    private final WireEntity wire;

    private List<Vec3> displayedPoints = List.of();

    private static final int ROW_HEIGHT = 20;
    private static final int ROW_TOP = 48;

    public JumperWireEditorScreen(WireEntity wire) {
        super(Component.literal("ジャンパ線編集"));
        this.wire = wire;
    }

    @Override
    protected void init() {
        if (wire == null || wire.isRemoved()) {
            return;
        }

        List<Vec3> points = wire.getJumperControlPoints();
        displayedPoints = List.copyOf(points);

        int centerX = this.width / 2;

        for (int i = 0; i < points.size(); i++) {
            final int index = i;

            int column = i / 8;
            int row = i % 8;

            int columnX = centerX - 150 + column * 155;
            int rowY = ROW_TOP + row * ROW_HEIGHT;

            // 始点方向へ移動
            Button upButton = Button.builder(
                    Component.literal("↑始"),
                    button -> {
                        JumperControlPointNetwork.requestReorderControlPoint(
                                wire.getId(),
                                index,
                                index - 1
                        );
                    }
            ).bounds(
                    columnX + 86,
                    rowY,
                    32,
                    18
            ).build();

            upButton.active = index > 0;
            addRenderableWidget(upButton);

            // 終点方向へ移動
            Button downButton = Button.builder(
                    Component.literal("↓終"),
                    button -> {
                        JumperControlPointNetwork.requestReorderControlPoint(
                                wire.getId(),
                                index,
                                index + 1
                        );
                    }
            ).bounds(
                    columnX + 120,
                    rowY,
                    32,
                    18
            ).build();

            downButton.active = index < points.size() - 1;
            addRenderableWidget(downButton);
        }

        boolean canAdd = points.size() < 16;

        Button addButton = Button.builder(
                Component.literal("制御点を追加"),
                button -> {
                    JumperControlPointPlacement.start(wire);
                    onClose();
                }
        ).bounds(
                centerX - 105,
                this.height - 25,
                100,
                20
        ).build();

        addButton.active = canAdd;
        addRenderableWidget(addButton);

        addRenderableWidget(
                Button.builder(
                        Component.literal("閉じる"),
                        button -> onClose()
                ).bounds(
                        centerX + 5,
                        this.height - 25,
                        100,
                        20
                ).build()
        );
    }

    @Override
    public void tick() {
        super.tick();

        if (wire == null || wire.isRemoved()) {
            return;
        }

        List<Vec3> currentPoints = wire.getJumperControlPoints();

        if (!currentPoints.equals(displayedPoints)) {
            rebuildWidgets();
        }
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

        graphics.drawCenteredString(
                this.font,
                this.title,
                centerX,
                12,
                0xFFFFFF
        );

        if (wire == null || wire.isRemoved()) {
            graphics.drawCenteredString(
                    this.font,
                    "対象のジャンパ線が存在しません",
                    centerX,
                    40,
                    0xFF5555
            );
            return;
        }

        List<Vec3> points = wire.getJumperControlPoints();
        List<Integer> pointIds = wire.getJumperControlPointIds();

        graphics.drawCenteredString(
                this.font,
                "制御点数: " + points.size() + " / 16",
                centerX,
                30,
                0xFFFFFF
        );

        if (points.isEmpty()) {
            graphics.drawCenteredString(
                    this.font,
                    "制御点はありません",
                    centerX,
                    65,
                    0xAAAAAA
            );
        }

        for (int i = 0; i < points.size(); i++) {
            int column = i / 8;
            int row = i % 8;

            int columnX = centerX - 150 + column * 155;
            int rowY = ROW_TOP + row * ROW_HEIGHT;

            graphics.drawString(
                    this.font,
                    "制御点 " + pointIds.get(i),
                    columnX + 3,
                    rowY + 5,
                    0xFFFFFF
            );
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
