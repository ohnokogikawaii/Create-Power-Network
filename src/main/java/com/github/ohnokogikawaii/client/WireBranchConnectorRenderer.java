package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.wire.WireBranchConnectorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class WireBranchConnectorRenderer
        extends EntityRenderer<WireBranchConnectorEntity> {

    private static final double BODY_HALF_SIZE = 0.075;
    private static final double ARM_RADIUS = 0.025;

    public WireBranchConnectorRenderer(
            EntityRendererProvider.Context context
    ) {
        super(context);
    }

    @Override
    public void render(
            WireBranchConnectorEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        Vec3 origin = entity.position();

        VertexConsumer buffer =
                bufferSource.getBuffer(RenderType.lines());

        PoseStack.Pose pose = poseStack.last();

        double s = BODY_HALF_SIZE;

        Vec3 p000 = new Vec3(-s, -s, -s);
        Vec3 p001 = new Vec3(-s, -s,  s);
        Vec3 p010 = new Vec3(-s,  s, -s);
        Vec3 p011 = new Vec3(-s,  s,  s);
        Vec3 p100 = new Vec3( s, -s, -s);
        Vec3 p101 = new Vec3( s, -s,  s);
        Vec3 p110 = new Vec3( s,  s, -s);
        Vec3 p111 = new Vec3( s,  s,  s);

        // 本体の12辺
        line(buffer, pose, p000, p001, 190, 190, 195);
        line(buffer, pose, p000, p010, 190, 190, 195);
        line(buffer, pose, p000, p100, 190, 190, 195);

        line(buffer, pose, p001, p011, 190, 190, 195);
        line(buffer, pose, p001, p101, 190, 190, 195);

        line(buffer, pose, p010, p011, 190, 190, 195);
        line(buffer, pose, p010, p110, 190, 190, 195);

        line(buffer, pose, p011, p111, 190, 190, 195);
        line(buffer, pose, p100, p101, 190, 190, 195);
        line(buffer, pose, p100, p110, 190, 190, 195);

        line(buffer, pose, p101, p111, 190, 190, 195);
        line(buffer, pose, p110, p111, 190, 190, 195);

        // 分岐端子へのアーム
        Vec3 port = entity
                .getBranchPortWorldPosition()
                .subtract(origin);

        line(
                buffer,
                pose,
                Vec3.ZERO,
                port,
                220, 180, 90
        );

        // 接続位置を示す十字
        Vec3 x = new Vec3(ARM_RADIUS, 0, 0);
        Vec3 y = new Vec3(0, ARM_RADIUS, 0);
        Vec3 z = new Vec3(0, 0, ARM_RADIUS);

        line(buffer, pose, port.subtract(x), port.add(x),
                220, 180, 90);
        line(buffer, pose, port.subtract(y), port.add(y),
                220, 180, 90);
        line(buffer, pose, port.subtract(z), port.add(z),
                220, 180, 90);

        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                bufferSource,
                packedLight
        );
    }

    private static void line(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            int red,
            int green,
            int blue
    ) {
        Vec3 direction = end.subtract(start);

        if (direction.lengthSqr() < 1.0E-10) {
            return;
        }

        Vec3 normal = direction.normalize();

        buffer.addVertex(
                        pose,
                        (float) start.x,
                        (float) start.y,
                        (float) start.z
                )
                .setColor(red, green, blue, 255)
                .setNormal(
                        pose,
                        (float) normal.x,
                        (float) normal.y,
                        (float) normal.z
                );

        buffer.addVertex(
                        pose,
                        (float) end.x,
                        (float) end.y,
                        (float) end.z
                )
                .setColor(red, green, blue, 255)
                .setNormal(
                        pose,
                        (float) normal.x,
                        (float) normal.y,
                        (float) normal.z
                );
    }

    @Override
    public ResourceLocation getTextureLocation(
            WireBranchConnectorEntity entity
    ) {
        return MissingTextureAtlasSprite.getLocation();
    }
}

