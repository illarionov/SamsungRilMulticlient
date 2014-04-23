package ru0xdc.samsung.ril.multiclient.app.rilexecutor;

public class DetectResult {

    public final boolean available;

    public final String error;

    static final DetectResult AVAILABLE = new  DetectResult(true, null);

    private DetectResult(boolean available, String error) {
        this.available = available;
        this.error = error;
    }

    static DetectResult Unavailable(String error) {
        return new DetectResult(false, error);
    }
}
