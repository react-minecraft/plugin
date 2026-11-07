package com.g4vrk.react.api;

import com.g4vrk.schedula.api.SchedulaAPI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.AvailableSince("2.0")
public interface ReactAPI {

    @NotNull SchedulaAPI schedulaAPI();

}
