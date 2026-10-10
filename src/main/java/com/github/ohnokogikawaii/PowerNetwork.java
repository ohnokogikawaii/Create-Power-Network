
package com.github.ohnokogikawaii;

import com.github.ohnokogikawaii.registry.ModBlocks;
import com.github.ohnokogikawaii.registry.ModEntities;
import com.github.ohnokogikawaii.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(PowerNetwork.MODID)
public class PowerNetwork {

    public static final String MODID = "powernetwork";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> POWER_NETWORK_TAB =
            CREATIVE_MODE_TABS.register(
                    "power_network",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.powernetwork"))
                            .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                            .icon(() -> ModItems.COPPER_WIRE.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(ModItems.TERMINAL.get());
                                output.accept(ModItems.HUB_CONNECTOR.get());
                                output.accept(ModItems.COPPER_WIRE.get());
                                output.accept(ModItems.WIRE_BRANCH_CONNECTOR_DOWN.get());
                                output.accept(ModItems.WIRE_BRANCH_CONNECTOR_ANGLED.get());
                            })
                            .build()
            );

    public PowerNetwork(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}