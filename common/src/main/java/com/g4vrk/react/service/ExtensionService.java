package com.g4vrk.react.service;

import com.g4vrk.react.api.addon.internal.manager.AddonManager;
import com.g4vrk.react.api.rcp.RCPLifecycle;
import com.g4vrk.react.api.rcp.internal.manager.RCPManager;
import com.g4vrk.react.rcp.RCPHost;
import com.g4vrk.react.rcp.update.RCPUpdater;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.File;

/**
 * Addons and RCP providers: loading their jars, keeping the React RCP up to date, starting,
 * reloading and closing them.
 */
public final class ExtensionService {

    private final File dataFolder;
    private final Logger logger;

    private AddonManager addons;
    private RCPManager providers;
    private RCPUpdater updater;

    public ExtensionService(final @NotNull File dataFolder, final @NotNull Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void loadAddons() {
        this.addons = new AddonManager(dataFolder, logger);
        addons.load();
    }

    public void loadProviders(final @NotNull RCPHost host, final @NotNull String language) {
        this.providers = new RCPManager(dataFolder, logger, host);
        this.updater = new RCPUpdater(
                providers.directory().toPath(),
                providers.providersDirectory().toPath(),
                language,
                logger
        );
    }

    public @NotNull RCPUpdater updater() {
        return updater;
    }

    public void startProviders(final @NotNull Scheduler scheduler) {
        final RCPManager manager = providers;
        final RCPUpdater rcpUpdater = updater;
        if (manager == null || rcpUpdater == null) {
            return;
        }
        scheduler.scheduleAsync(() -> {
            rcpUpdater.update();
            try {
                manager.start();
            } catch (final IllegalStateException ex) {
                logger.debug("RCP manager closed before it started", ex);
            }
        }, TickSchedule.instant());
    }

    public void startAddons() {
        if (addons != null) {
            addons.start();
        }
    }

    public void reload() {
        if (providers == null) {
            return;
        }
        for (final RCPLifecycle lifecycle : providers.components()) {
            try {
                lifecycle.onReload();
            } catch (final Throwable ex) {
                logger.error("RCP provider {} could not reload", lifecycle.getClass().getName(), ex);
            }
        }
    }

    public void close() {
        try {
            if (addons != null) {
                logger.info("Stopping all addons...");
                addons.close();
            }
        } catch (final Exception ex) {
            logger.error("Could not disable addons", ex);
        } finally {
            addons = null;
        }

        try {
            if (providers != null) {
                logger.info("Stopping all RCP providers...");
                providers.close();
            }
        } catch (final Exception ex) {
            logger.error("Could not disable RCP providers", ex);
        } finally {
            providers = null;
        }
    }

}
