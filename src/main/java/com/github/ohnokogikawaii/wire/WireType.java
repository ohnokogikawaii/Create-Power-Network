package com.github.ohnokogikawaii.wire;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * A definition describing one physical/electrical wire type.
 *
 * WireType itself does not represent an installed wire.
 * It only contains the properties shared by all wires of this type.
 */
public final class WireType {

    private static final Gson GSON = new Gson();

    private final ResourceLocation id;

    private final Physical physical;
    private final Electrical electrical;
    private final Render render;
    private final Properties properties;

    private WireType(
            ResourceLocation id,
            Physical physical,
            Electrical electrical,
            Render render,
            Properties properties
    ) {
        this.id = id;
        this.physical = physical != null ? physical : new Physical();
        this.electrical = electrical != null ? electrical : new Electrical();
        this.render = render != null ? render : new Render();
        this.properties = properties != null ? properties : new Properties();
    }

    public static WireType fromJson(ResourceLocation id, String json) {
        Definition definition = GSON.fromJson(json, Definition.class);

        if (definition == null) {
            throw new JsonParseException(
                    "Wire type definition is empty: " + id
            );
        }

        return new WireType(
                id,
                definition.physical,
                definition.electrical,
                definition.render,
                definition.properties
        );
    }

    public ResourceLocation getId() {
        return id;
    }

    public double getDiameter() {
        return physical.diameter;
    }

    public double getMaximumLength() {
        return physical.maximumLength;
    }

    public double getMinimumLength() {
        return physical.minimumLength;
    }

    public double getResistance() {
        return electrical.resistance;
    }

    public double getMaximumCurrent() {
        return electrical.maximumCurrent;
    }

    public double getMaximumVoltage() {
        return electrical.maximumVoltage;
    }

    public ResourceLocation getTexture() {
        return ResourceLocation.parse(render.texture);
    }

    public double getRenderThickness() {
        return render.thickness;
    }

    public boolean isInsulated() {
        return properties.insulated;
    }

    public boolean canSag() {
        return properties.canSag;
    }

    @Override
    public String toString() {
        return "WireType{" +
                "id=" + id +
                '}';
    }

    private static final class Definition {
        private Physical physical;
        private Electrical electrical;
        private Render render;
        private Properties properties;
    }

    public static final class Physical {
        private double diameter = 0.0625;
        private double maximumLength = 64.0;
        private double minimumLength = 1.0;
    }

    public static final class Electrical {
        private double resistance = 0.0;
        private double maximumCurrent = Double.POSITIVE_INFINITY;
        private double maximumVoltage = Double.POSITIVE_INFINITY;
    }

    public static final class Render {
        private String texture = "minecraft:block/iron_block";
        private double thickness = 0.025;
    }

    public static final class Properties {
        private boolean insulated = false;
        private boolean canSag = true;
    }
}