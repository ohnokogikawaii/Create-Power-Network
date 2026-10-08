package com.github.ohnokogikawaii.registry;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.wire.WireItem;
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

    public static final DeferredItem<WireItem> WIRE =
            ITEMS.register(
                    "wire",
                    () -> new WireItem(
                            new Item.Properties()
                                    .stacksTo(64)
                    )
            );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}