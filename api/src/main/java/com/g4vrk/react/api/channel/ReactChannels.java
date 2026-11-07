package com.g4vrk.react.api.channel;

import org.jetbrains.annotations.ApiStatus;

import com.g4vrk.react.api.channel.impl.ReportChannel;

@ApiStatus.AvailableSince("2.0")
public final class ReactChannels {

    public static final ReportChannel ALERTS = new ReportChannel();
    public static final ReportChannel MONITOR = new ReportChannel();

    private ReactChannels() {
    }

}
