package ru0xdc.samsung.ril.multiclient.app.rilexecutor;

public class RawResult {
    public final byte result[];
    public final Throwable exception;

    public RawResult(byte r[], Throwable ex)
    {
        result = r;
        exception = ex;
    }

}
