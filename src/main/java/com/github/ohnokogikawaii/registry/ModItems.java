package com.github.ohnokogikawaii.registry;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.wire.WireBranchConnectorItem;
import com.github.ohnokogikawaii.wire.WireItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(PowerNetwork.MODID);

    public static final DeferredItem<BlockItem> TERMINAL =
            ITEMS.register(
                    "terminal",
                    () -> new BlockItem(
                            ModBlocks.TERMINAL.get(),
                            new Item.Properties()
                    )
            );

    public static final DeferredItem<BlockItem> HUB_CONNECTOR =
            ITEMS.register(
                    "hub_connector",
                    () -> new BlockItem(
                            ModBlocks.HUB_CONNECTOR.get(),
                            new Item.Properties()
                    )
            );

    public static final DeferredItem<WireItem> COPPER_WIRE =
            registerWire(
                    "copper_wire",
                    "powernetwork:copper"
            );

    public static final DeferredItem<WireBranchConnectorItem>
            WIRE_BRANCH_CONNECTOR_DOWN =
            ITEMS.register(
                    "wire_branch_connector_down",
                    () -> new WireBranchConnectorItem(
                            new Item.Properties(),
                            true
                    )
            );

    public static final DeferredItem<WireBranchConnectorItem>
            WIRE_BRANCH_CONNECTOR_ANGLED =
            ITEMS.register(
                    "wire_branch_connector_angled",
                    () -> new WireBranchConnectorItem(
                            new Item.Properties(),
                            false
                    )
            );

    private ModItems() {
    }

    private static DeferredItem<WireItem> registerWire(
            String itemId,
            String wireTypeId
    ) {
        return ITEMS.register(
                itemId,
                () -> new WireItem(
                        new Item.Properties().stacksTo(64),
                        ResourceLocation.parse(wireTypeId)
                )
        );
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

