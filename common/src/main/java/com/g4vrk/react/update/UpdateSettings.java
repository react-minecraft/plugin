package com.g4vrk.react.update;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.time.Duration;

/**
 * {@code update/settings.yml}: how often updates are checked and what happens to them.
 *
 * @param interval   the time between checks
 * @param autoReboot whether a staged addon or RCP is rebooted at once
 * @param notifications whether alert recipients hear about downloads
 */
@ConfigSerializable
public record UpdateSettings(Duration interval, boolean autoReboot, @Setting("notify") boolean notifications) {
}
