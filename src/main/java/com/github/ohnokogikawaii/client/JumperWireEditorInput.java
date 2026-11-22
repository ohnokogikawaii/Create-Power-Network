
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.registry.ModItems;
import com.github.ohnokogikawaii.wire.WireCutterItem;
import com.github.ohnokogikawaii.wire.WireEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(
        modid = PowerNetwork.MODID,
        bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT
)
public final class JumperWireEditorInput {

    private JumperWireEditorInput() {
    }

    @SubscribeEvent
    public static void onMouseButton(
            InputEvent.MouseButton.Pre event
    ) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null
                || minecraft.level == null) {
            return;
        }

        // 制御点の配置・移動中は既存の操作を優先する。
        if (JumperControlPointPlacement.isActive()) {
            return;
        }

        Player player = minecraft.player;

        boolean holdingEditor =
                player.getMainHandItem().is(
                        ModItems.JUMPER_WIRE_EDITOR.get()
                )
                        || player.getOffhandItem().is(
                        ModItems.JUMPER_WIRE_EDITOR.get()
                );

        if (!holdingEditor) {
            return;
        }

        /*
         * 最優先：中継点の箱を検索する。
         */
        JumperControlPointPlacement.ControlPointAimTarget pointTarget =
                JumperControlPointPlacement.findAimTarget(
                        minecraft.level,
                        player
                );

        if (pointTarget != null) {

            boolean started =
                    JumperControlPointPlacement.startEditing(
                            pointTarget.wire(),
                            pointTarget.index()
                    );

            if (started) {
                event.setCanceled(true);
            }

            return;
        }

        /*
         * 次に配線を検索する。
         */
        WireCutterItem.WireAimTarget target =
                WireCutterItem.findJumperAimTarget(
                        minecraft.level,
                        player
                );

        if (target == null) {
            return;
        }

        WireEntity wire = target.wire();

        if (wire == null
                || !wire.isAlive()
                || !wire.isJumperWire()) {
            return;
        }

        event.setCanceled(true);

        minecraft.setScreen(
                new JumperWireEditorScreen(wire)
        );
    }
}
