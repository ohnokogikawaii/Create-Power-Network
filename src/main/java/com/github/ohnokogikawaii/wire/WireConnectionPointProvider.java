package com.github.ohnokogikawaii.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 電線を接続できるブロックが実装する共通インターフェース。
 *
 * 接続座標とハイライト表示を分離し、
 * 通常端子・碍子・複数端子・極性付き端子を共通の仕組みで扱う。
 */
public interface WireConnectionPointProvider {

    /**
     * ハイライトの表示方式。
     *
     * BLOCK:
     *   ブロックの形状全体をハイライトする。
     *
     * CONNECTION_POINT:
     *   選択された接続点だけをハイライトする。
     */
    enum HighlightMode {
        BLOCK,
        CONNECTION_POINT
    }

    /**
     * RGB形式のハイライト色。
     *
     * 例:
     *   0xA6A6A6 = 灰色
     *   0xFF3333 = 赤色
     *   0x3399FF = 青色
     */
    int DEFAULT_HIGHLIGHT_COLOR = 0xA6A6A6;

    /**
     * ブロック上の電線接続点。
     *
     * position:
     *   ブロック左下手前を原点としたローカル座標。
     *
     * highlightHalfSize:
     *   CONNECTION_POINT モードで使用するハイライト箱の半サイズ。
     *
     * highlightColor:
     *   この接続点のハイライト色。RGB形式。
     */
    record ConnectionPoint(
            String id,
            Vec3 position,
            Vec3 highlightHalfSize,
            int highlightColor
    ) {
        public ConnectionPoint(
                String id,
                Vec3 position,
                Vec3 highlightHalfSize
        ) {
            this(
                    id,
                    position,
                    highlightHalfSize,
                    DEFAULT_HIGHLIGHT_COLOR
            );
        }

        public ConnectionPoint {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException(
                        "Connection point id cannot be blank"
                );
            }

            if (position == null || highlightHalfSize == null) {
                throw new IllegalArgumentException(
                        "Connection point position and highlight size are required"
                );
            }

            if (highlightHalfSize.x < 0.0
                    || highlightHalfSize.y < 0.0
                    || highlightHalfSize.z < 0.0) {
                throw new IllegalArgumentException(
                        "Connection point highlight size cannot be negative"
                );
            }

            // RGB の下位24ビットだけを使用する。
            highlightColor &= 0xFFFFFF;
        }

        /**
         * 接続点のワールド座標を取得する。
         */
        public Vec3 worldPosition(BlockPos blockPos) {
            return position.add(
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ()
            );
        }

        /**
         * 接続点を囲むワールド座標のハイライト箱を作成する。
         */
        public AABB createWorldHighlightBox(BlockPos blockPos) {
            Vec3 center = worldPosition(blockPos);

            return new AABB(
                    center.x - highlightHalfSize.x,
                    center.y - highlightHalfSize.y,
                    center.z - highlightHalfSize.z,
                    center.x + highlightHalfSize.x,
                    center.y + highlightHalfSize.y,
                    center.z + highlightHalfSize.z
            );
        }
    }

    /**
     * 標準サイズの接続点を作成する。
     */
    static ConnectionPoint connectionPoint(
            String id,
            double x,
            double y,
            double z
    ) {
        return connectionPoint(
                id,
                x,
                y,
                z,
                DEFAULT_HIGHLIGHT_COLOR
        );
    }

    /**
     * ハイライト色を指定して接続点を作成する。
     *
     * @param color RGB形式。例: 0xFF3333
     */
    static ConnectionPoint connectionPoint(
            String id,
            double x,
            double y,
            double z,
            int color
    ) {
        return new ConnectionPoint(
                id,
                new Vec3(x, y, z),
                new Vec3(
                        3.5 / 16.0,
                        1.5 / 16.0,
                        3.5 / 16.0
                ),
                color
        );
    }

    /**
     * このブロックが持つ接続点一覧を返す。
     *
     * ブロックの向きなどに依存しない場合に実装する。
     */
    List<ConnectionPoint> getConnectionPoints();

    /**
     * BlockState に応じた接続点一覧。
     *
     * 向きによって座標が変わるブロックでは、
     * このメソッドをオーバーライドする。
     */
    default List<ConnectionPoint> getConnectionPoints(
            BlockState state
    ) {
        return getConnectionPoints();
    }

    /**
     * ハイライト表示方式。
     *
     * 通常の端子・碍子はブロック全体を表示する。
     * 複数の端子を持つコネクタは必要に応じて
     * CONNECTION_POINT に変更する。
     */
    default HighlightMode getHighlightMode(BlockState state) {
        return HighlightMode.BLOCK;
    }

    /**
     * ブロック全体をハイライトするときの色。
     */
    default int getBlockHighlightColor(BlockState state) {
        return DEFAULT_HIGHLIGHT_COLOR;
    }

    /**
     * 選択された接続点のハイライト色。
     *
     * 極性付きコネクタでは、接続点に設定した色を使用する。
     */
    default int getConnectionPointHighlightColor(
            BlockState state,
            ConnectionPoint point
    ) {
        return point == null
                ? DEFAULT_HIGHLIGHT_COLOR
                : point.highlightColor();
    }

    /**
     * ブロック全体をハイライトする箱を取得する。
     *
     * デフォルトでは、ブロックの選択形状の外接箱を使う。
     * 必要なブロックではオーバーライドしてもよい。
     */
    default AABB getBlockHighlightBox(
            BlockState state,
            BlockGetter level,
            BlockPos pos
    ) {
        return state.getShape(level, pos)
                .bounds()
                .move(
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()
                );
    }

    /**
     * BlockState に依存しない接続点検索。
     * 既存コードとの互換性を維持する。
     */
    default ConnectionPoint getNearestConnectionPoint(
            BlockPos pos,
            Vec3 hitLocation
    ) {
        return ConnectionPointManager.findNearest(
                this,
                null,
                pos,
                hitLocation
        );
    }

    /**
     * BlockState に依存する接続点検索。
     */
    default ConnectionPoint getNearestConnectionPoint(
            BlockPos pos,
            Vec3 hitLocation,
            BlockState state
    ) {
        return ConnectionPointManager.findNearest(
                this,
                state,
                pos,
                hitLocation
        );
    }

    /**
     * 電線の接続先として有効かどうか。
     */
    default boolean isWireAnchorValid(
            BlockGetter level,
            BlockPos pos
    ) {
        return ConnectionPointManager.isConnectionPointProvider(
                level,
                pos
        );
    }
}