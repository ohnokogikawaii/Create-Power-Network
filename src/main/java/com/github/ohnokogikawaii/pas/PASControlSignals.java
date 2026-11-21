
package com.github.ohnokogikawaii.pas;

/**
 * PASからSOGへ渡す制御情報。
 *
 * 現段階では実際の電流・電圧値ではなく、
 * 検出状態をBooleanで管理する。
 */
public record PASControlSignals(
        boolean controlPowerAvailable,
        boolean zeroSequenceCurrentDetected,
        boolean zeroSequenceVoltageDetected,
        boolean overcurrentDetected,
        boolean pasOpen
) {
    public static final PASControlSignals NONE =
            new PASControlSignals(
                    false,
                    false,
                    false,
                    false,
                    false
            );
}
