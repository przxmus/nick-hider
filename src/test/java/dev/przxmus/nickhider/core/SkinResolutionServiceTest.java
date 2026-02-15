package dev.przxmus.nickhider.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkinResolutionServiceTest {
    private static final UUID TARGET_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    Path tempDir;

    @Test
    void noCacheReturnsDefaultThenLastGoodAfterAsyncFetch() {
        QueueExecutor executor = new QueueExecutor();
        MutableClock clock = new MutableClock(1_000L);
        AtomicInteger lookupCalls = new AtomicInteger(0);

        SkinResolutionService.ProfileIdentity identity = new SkinResolutionService.ProfileIdentity(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "sourceuser"
        );

        SkinResolutionService service = new SkinResolutionService(
                tempDir.resolve("skins"),
                executor,
                clock,
                millis -> {},
                bound -> 0L,
                normalized -> {
                    lookupCalls.incrementAndGet();
                    return identity;
                },
                (profile, fallbackUuid) -> loadedSkin("skin-loaded"),
                fallbackUuid -> defaultSkin()
        );

        ResolvedSkin first = service.resolveOrFallback("SourceUser", TARGET_UUID);
        assertEquals("default-skin", first.textureLocation());
        assertEquals(1, executor.pendingTasks());

        executor.runAll();
        ResolvedSkin second = service.resolveOrFallback("SourceUser", TARGET_UUID);
        assertEquals("skin-loaded", second.textureLocation());
        assertEquals(1, lookupCalls.get());
    }

    @Test
    void staleCacheReturnsLastGoodAndTriggersBackgroundRefresh() {
        QueueExecutor executor = new QueueExecutor();
        MutableClock clock = new MutableClock(2_000L);

        SkinResolutionService.ProfileIdentity identity = new SkinResolutionService.ProfileIdentity(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "refreshuser"
        );

        SkinResolutionService service = new SkinResolutionService(
                tempDir.resolve("skins"),
                executor,
                clock,
                millis -> {},
                bound -> 0L,
                normalized -> identity,
                (profile, fallbackUuid) -> loadedSkin("skin-stable"),
                fallbackUuid -> defaultSkin()
        );

        service.resolveOrFallback("refreshuser", TARGET_UUID);
        executor.runAll();

        clock.advance(601_000L);
        ResolvedSkin staleResult = service.resolveOrFallback("refreshuser", TARGET_UUID);

        assertEquals("skin-stable", staleResult.textureLocation());
        assertEquals(1, executor.pendingTasks());
    }

    @Test
    void rateLimitKeepsLastGoodAndCooldownPreventsImmediateRefetch() {
        QueueExecutor executor = new QueueExecutor();
        MutableClock clock = new MutableClock(3_000L);
        AtomicInteger lookupCalls = new AtomicInteger(0);

        SkinResolutionService.ProfileIdentity identity = new SkinResolutionService.ProfileIdentity(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "ratelimituser"
        );

        Queue<Object> outcomes = new ArrayDeque<>();
        outcomes.add(identity);
        outcomes.add(SkinResolutionService.FetchException.rateLimited("NH-SKIN-429", "rate limit", 120_000L, true));
        outcomes.add(SkinResolutionService.FetchException.rateLimited("NH-SKIN-429", "rate limit", 120_000L, true));
        outcomes.add(SkinResolutionService.FetchException.rateLimited("NH-SKIN-429", "rate limit", 120_000L, true));

        SkinResolutionService service = new SkinResolutionService(
                tempDir.resolve("skins"),
                executor,
                clock,
                millis -> {},
                bound -> 0L,
                normalized -> {
                    lookupCalls.incrementAndGet();
                    Object outcome = outcomes.poll();
                    if (outcome instanceof SkinResolutionService.FetchException exception) {
                        throw exception;
                    }
                    return (SkinResolutionService.ProfileIdentity) outcome;
                },
                (profile, fallbackUuid) -> loadedSkin("skin-rate-limited"),
                fallbackUuid -> defaultSkin()
        );

        service.resolveOrFallback("ratelimituser", TARGET_UUID);
        executor.runAll();
        assertEquals("skin-rate-limited", service.resolveOrFallback("ratelimituser", TARGET_UUID).textureLocation());

        clock.advance(601_000L);
        ResolvedSkin beforeFailure = service.resolveOrFallback("ratelimituser", TARGET_UUID);
        assertEquals("skin-rate-limited", beforeFailure.textureLocation());
        executor.runAll();

        ResolvedSkin afterFailure = service.resolveOrFallback("ratelimituser", TARGET_UUID);
        assertEquals("skin-rate-limited", afterFailure.textureLocation());
        assertTrue(service.statusSummary().startsWith("Rate limited"));

        int callsAfterFailure = lookupCalls.get();
        service.resolveOrFallback("ratelimituser", TARGET_UUID);
        assertEquals(callsAfterFailure, lookupCalls.get());
        assertEquals(0, executor.pendingTasks());
    }

    @Test
    void notFoundUsesCooldownAndAvoidsRetryStorm() {
        QueueExecutor executor = new QueueExecutor();
        MutableClock clock = new MutableClock(4_000L);
        AtomicInteger lookupCalls = new AtomicInteger(0);

        SkinResolutionService service = new SkinResolutionService(
                tempDir.resolve("skins"),
                executor,
                clock,
                millis -> {},
                bound -> 0L,
                normalized -> {
                    lookupCalls.incrementAndGet();
                    throw SkinResolutionService.FetchException.notFound("NH-SKIN-404", "not found");
                },
                (profile, fallbackUuid) -> loadedSkin("unused"),
                fallbackUuid -> defaultSkin()
        );

        ResolvedSkin first = service.resolveOrFallback("missinguser", TARGET_UUID);
        assertEquals("default-skin", first.textureLocation());
        executor.runAll();
        assertEquals(1, lookupCalls.get());

        service.resolveOrFallback("missinguser", TARGET_UUID);
        assertEquals(1, lookupCalls.get());
        assertEquals(0, executor.pendingTasks());
    }

    private static ResolvedSkin defaultSkin() {
        return new ResolvedSkin("default-skin", ResolvedSkin.MODEL_DEFAULT, null, null);
    }

    private static ResolvedSkin loadedSkin(String textureId) {
        return new ResolvedSkin(textureId, ResolvedSkin.MODEL_DEFAULT, null, null);
    }

    private static final class QueueExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                Runnable task = tasks.poll();
                if (task != null) {
                    task.run();
                }
            }
        }

        int pendingTasks() {
            return tasks.size();
        }
    }

    private static final class MutableClock implements SkinResolutionService.Clock {
        private long nowMs;

        private MutableClock(long nowMs) {
            this.nowMs = nowMs;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }

        void advance(long deltaMs) {
            nowMs += deltaMs;
        }
    }
}
