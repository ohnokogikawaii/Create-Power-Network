package com.github.ohnokogikawaii.registry;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.wire.WireItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(
                    PowerNetwork.MODID
            );

    public static final DeferredItem<BlockItem> TERMINAL =
            ITEMS.register(
                    "terminal",
                    () -> new BlockItem(
                            ModBlocks.TERMINAL.get(),
                            new Item.Properties()
                    )
            );

    /**
     * Copper wire.
     *
     * The physical/electrical properties are defined by:
     * data/powernetwork/wire_types/copper.json
     */
    public static final DeferredItem<WireItem> COPPER_WIRE =
            registerWire(
                    "copper_wire",
                    "powernetwork:copper"
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
                        new Item.Properties()
                                .stacksTo(64),
                        ResourceLocation.parse(wireTypeId)
                )
        );
    }



    public static void register(
            IEventBus modEventBus
    ) {
        ITEMS.register(modEventBus);
    }
}