package com.g4vrk.react.api.channel;

import com.g4vrk.react.api.channel.impl.ReportChannel;

public final class ReactChannels {

    public static final ReportChannel ALERTS = new ReportChannel();
    public static final ReportChannel MONITOR = new ReportChannel();

    private ReactChannels() {
    }

}
