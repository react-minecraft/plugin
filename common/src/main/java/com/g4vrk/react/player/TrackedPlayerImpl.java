package com.g4vrk.react.player;

import com.g4vrk.react.PermissionKey;
import com.g4vrk.react.React;
import com.g4vrk.react.api.event.player.PlayerBypassEvent;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.rcp.verdict.InferenceResult;
import com.g4vrk.react.check.Check;
import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.check.manager.CheckManager;
import com.g4vrk.react.history.InferenceHistory;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.player.rotation.RotationData;
import com.g4vrk.react.statistic.InferenceStatistic;
import com.g4vrk.react.storage.constant.StorageConstant;
import com.g4vrk.react.storage.model.PlayerStorageData;
import com.g4vrk.react.storage.model.StoredInference;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds React's checks, history and bypasses for a connected player.
 */
public final class TrackedPlayerImpl implements TrackedPlayer {

    private static final int MAX_ROTATION_WINDOW = 40;
    private static final long REMOVED_BYPASS = -1L;

    private final UUID uniqueId;
    private final String name;

    public final PlatformPlayer platformPlayer;

    public final CombatActivity combatActivity;
    public final RotationData rotationData = new RotationData(MAX_ROTATION_WINDOW);

    public final CheckManager checkManager;

    public final InferenceHistory inferenceHistory;
    public final InferenceStatistic inferenceStatistic;

    private final Map<String, Long> bypasses = new ConcurrentHashMap<>();
    private final Object bypassLock = new Object();
    private final Object inferenceLock = new Object();
    private volatile long inferenceGeneration;
    private volatile int inferenceResets;
    private volatile @Nullable HistorySnapshot historySnapshot;
    private volatile boolean bypassesLoaded;
    private volatile boolean dataReady;
    private volatile boolean rotationPermissionBypass = true;

    public TrackedPlayerImpl(
            final @NotNull UUID uniqueId,
            final @NotNull String name,
            final @NotNull PlatformPlayer platformPlayer
    ) {

        this.uniqueId = uniqueId;
        this.name = name;
        this.platformPlayer = platformPlayer;
        this.checkManager = new CheckManager(this);
        this.inferenceHistory = new InferenceHistory(
                React.INSTANCE.storageManager().historyRetention(),
                entry -> React.INSTANCE.storageManager().saveInference(
                        uniqueId,
                        new StoredInference(
                                entry.getTimestamp(),
                                entry.getCheck().id(),
                                entry.getProbability(),
                                entry.getModelId(),
                                entry.getRunId(),
                                entry.getThreshold(),
                                entry.getEnforcement(),
                                entry.getViolationsBefore(),
                                entry.getViolationsAfter()
                        )
                )
        );
        this.inferenceStatistic = new InferenceStatistic(this.inferenceHistory);

        this.combatActivity = new CombatActivity();

    }

    @Override
    public @NotNull UUID uniqueId() {

        return uniqueId;

    }

    @Override
    public @NotNull String name() {

        return name;

    }

    @Override
    public @NotNull PlatformPlayer platform() {

        return platformPlayer;

    }

    @Override
    public boolean inCombat() {

        return combatActivity.isActive();

    }

    @Override
    public void enterCombat() {

        React.INSTANCE.listeners().activateCombat(this);

    }

    @Override
    public boolean checked() {

        final AimAI aim = checkManager.getCheck(AimAI.class);
        return aim != null && aim.active();

    }

    @Override
    public @NotNull List<Check> checks() {

        return checkManager.checks();

    }

    @Override
    public @Nullable Check check(final @NotNull String id) {

        return checkManager.getCheck(id);

    }

    @Override
    public float @NotNull [] rotations() {

        return rotationData.history();

    }

    public void clearTransientState() {

        synchronized (inferenceLock) {
            inferenceResets++;
            inferenceGeneration++;
        }
        try {
            rotationData.clear();
            combatActivity.reset();
            if (React.INSTANCE.rcpHost() != null) {
                React.INSTANCE.rcpHost().reset(uniqueId);
            }
        } finally {
            synchronized (inferenceLock) {
                inferenceResets--;
            }
        }

    }

    public long inferenceGeneration() {

        return inferenceGeneration;

    }

    public boolean inferenceResetting() {

        return inferenceResets != 0;

    }

    public void applyInference(final long generation, final @NotNull InferenceResult result) {

        synchronized (inferenceLock) {
            if (inferenceResets != 0 || inferenceGeneration != generation) {
                return;
            }
            final AimAI check = checkManager.getCheck(AimAI.class);
            if (check != null) {
                check.onResult(result);
            }
        }

    }

    public boolean dataReady() {

        return dataReady;

    }

    public void markDataReady() {

        dataReady = true;

    }

    @Override
    public boolean bypassed(final @NotNull String check) {

        final String id = check.toLowerCase(Locale.ROOT);
        return bypassActive(StorageConstant.ALL_CHECKS)
                || bypassActive(id)
                || PermissionKey.hasBypassForCheck(platformPlayer, id);

    }

    /**
     * Refreshes the aim permission on the player's owning server thread.
     */
    public void refreshRotationPermission() {

        rotationPermissionBypass = PermissionKey.hasBypassForCheck(platformPlayer, "aim-ai");

    }

    /**
     * Checks the cached permission and temporary bypasses without accessing Bukkit.
     *
     * @param check the check id
     * @return whether rotation processing is bypassed
     */
    public boolean rotationBypassed(final @NotNull String check) {

        return rotationPermissionBypass || bypassActive(StorageConstant.ALL_CHECKS) || bypassActive(check);

    }

    @Override
    public @NotNull List<HistoryEntry> history() {

        final long revision = inferenceHistory.revision();
        final HistorySnapshot snapshot = this.historySnapshot;
        if (snapshot != null && snapshot.revision() == revision) {
            return snapshot.entries();
        }
        final InferenceHistoryEntry[] entries = inferenceHistory.entries();
        final HistoryEntry[] out = new HistoryEntry[entries.length];
        for (int i = 0; i < entries.length; i++) {
            final InferenceHistoryEntry entry = entries[i];
            out[i] = new HistoryEntry(entry.getTimestamp(), entry.getCheck().id(), entry.getProbability(),
                    entry.getModelId(), entry.getRunId(), entry.getThreshold(), entry.getEnforcement(),
                    entry.getViolationsBefore(), entry.getViolationsAfter());
        }
        final List<HistoryEntry> list = List.of(out);
        this.historySnapshot = new HistorySnapshot(revision, list);
        return list;

    }

    /**
     * The last {@link #history()} answer, reused until the history changes.
     */
    private record HistorySnapshot(long revision, @NotNull List<HistoryEntry> entries) {
    }

    @Override
    public @NotNull Map<String, Instant> bypasses() {

        final Map<String, Instant> active = new Object2ObjectOpenHashMap<>();
        final long now = System.currentTimeMillis();
        for (final Map.Entry<String, Long> entry : bypasses.entrySet()) {
            final long until = entry.getValue();
            if (until > now) {
                active.put(entry.getKey(), until == Long.MAX_VALUE ? null : Instant.ofEpochMilli(until));
            }
        }
        return Collections.unmodifiableMap(active);

    }

    @Override
    public boolean bypass(final @Nullable String check, final @Nullable Instant until) {

        final String key = bypassKey(check);
        if (React.INSTANCE.events().post(new PlayerBypassEvent(uniqueId, check, until, true)).cancelled()) {
            return false;
        }
        final long millis = until == null ? Long.MAX_VALUE : until.toEpochMilli();
        synchronized (bypassLock) {
            bypasses.put(key, millis);
            React.INSTANCE.storageManager().saveBypass(uniqueId, key, millis);
        }
        return true;

    }

    @Override
    public boolean removeBypass(final @Nullable String check) {

        final String key = bypassKey(check);
        if (!bypassActive(key)
                || React.INSTANCE.events().post(new PlayerBypassEvent(uniqueId, check, null, false)).cancelled()) {
            return false;
        }
        synchronized (bypassLock) {
            if (bypassesLoaded) {
                bypasses.remove(key);
            } else {
                bypasses.put(key, REMOVED_BYPASS);
            }
            React.INSTANCE.storageManager().saveBypass(uniqueId, key, REMOVED_BYPASS);
        }
        return true;

    }

    private boolean bypassActive(final @NotNull String key) {

        final boolean loaded = bypassesLoaded;
        final Long until = bypasses.get(key);
        if (until == null) {
            return false;
        }
        if (until > System.currentTimeMillis()) {
            return true;
        }
        if (loaded) {
            bypasses.remove(key, until);
        }
        return false;

    }

    private static @NotNull String bypassKey(final @Nullable String check) {

        return check == null ? StorageConstant.ALL_CHECKS : check.toLowerCase(Locale.ROOT);

    }

    public void applyStorageData(final @NotNull PlayerStorageData data) {

        loadBypasses(data);
        inferenceHistory.merge(mapHistory(data));

    }

    private void loadBypasses(final @NotNull PlayerStorageData data) {

        synchronized (bypassLock) {
            /*
             * Local grants and removals may still be queued for storage.
             * An older database snapshot must not overwrite them.
             */
            data.bypasses().forEach(bypasses::putIfAbsent);
            final long now = System.currentTimeMillis();
            bypasses.values().removeIf(until -> until <= now);
            bypassesLoaded = true;
        }

    }

    private @NotNull List<InferenceHistoryEntry> mapHistory(final @NotNull PlayerStorageData data) {

        final List<InferenceHistoryEntry> loadedHistory = new ArrayList<>(data.inferenceHistory().size());
        for (final StoredInference stored : data.inferenceHistory()) {
            final Check check = checkManager.getCheck(stored.check());
            if (check != null) {
                loadedHistory.add(new InferenceHistoryEntry(
                        stored.timestamp(),
                        check,
                        stored.probability(),
                        stored.modelId(),
                        stored.runId(),
                        stored.threshold(),
                        stored.enforcement(),
                        stored.violationsBefore(),
                        stored.violationsAfter()
                ));
            }
        }
        return loadedHistory;

    }

}
