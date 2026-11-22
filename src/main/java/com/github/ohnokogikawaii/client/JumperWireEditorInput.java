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

        // 制御点配置中は右クリックを配置操作に使用する。
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