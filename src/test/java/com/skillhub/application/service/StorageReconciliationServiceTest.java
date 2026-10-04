package com.skillhub.application.service;

import com.skillhub.domain.model.StorageObjectInfo;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.ElementVersionRepositoryPort;
import com.skillhub.domain.port.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

class StorageReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    private final StoragePort storage = mock(StoragePort.class);
    private final ElementVersionRepositoryPort versions = mock(ElementVersionRepositoryPort.class);
    private final ClockPort clock = mock(ClockPort.class);
    private final StorageReconciliationService service =
        new StorageReconciliationService(storage, versions, clock, Duration.ofHours(24));

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(NOW);
    }

    @Test
    void deletesOrphansOlderThanGrace() {
        when(versions.findAllS3Keys()).thenReturn(Set.of("team/a/1.0.0.zip"));
        when(storage.list()).thenReturn(List.of(
            new StorageObjectInfo("team/a/1.0.0.zip", NOW.minus(Duration.ofHours(48))),
            new StorageObjectInfo("team/orphan/2.0.0.zip", NOW.minus(Duration.ofHours(25)))
        ));

        service.reconcile();

        verify(storage).delete("team/orphan/2.0.0.zip");
        verify(storage, never()).delete("team/a/1.0.0.zip");
    }

    @Test
    void keepsOrphansWithinGrace() {
        when(versions.findAllS3Keys()).thenReturn(Set.of());
        when(storage.list()).thenReturn(List.of(
            new StorageObjectInfo("team/fresh/1.0.0.zip", NOW.minus(Duration.ofHours(1)))
        ));

        service.reconcile();

        verify(storage, never()).delete(anyString());
    }

    @Test
    void keepsKnownKeysRegardlessOfAge() {
        when(versions.findAllS3Keys()).thenReturn(Set.of("team/known/1.0.0.zip"));
        when(storage.list()).thenReturn(List.of(
            new StorageObjectInfo("team/known/1.0.0.zip", NOW.minus(Duration.ofHours(72)))
        ));

        service.reconcile();

        verify(storage, never()).delete(anyString());
    }

    @Test
    void continuesAfterFailedDelete() {
        when(versions.findAllS3Keys()).thenReturn(Set.of());
        when(storage.list()).thenReturn(List.of(
            new StorageObjectInfo("team/broken/1.0.0.zip", NOW.minus(Duration.ofHours(48))),
            new StorageObjectInfo("team/other/1.0.0.zip", NOW.minus(Duration.ofHours(48)))
        ));
        doThrow(new RuntimeException("s3 down")).when(storage).delete("team/broken/1.0.0.zip");

        service.reconcile();

        verify(storage).delete("team/other/1.0.0.zip");
    }

    @Test
    void emptyBucketIsNoop() {
        when(versions.findAllS3Keys()).thenReturn(Set.of());
        when(storage.list()).thenReturn(List.of());

        service.reconcile();

        verify(storage, never()).delete(anyString());
    }

    @Test
    void listFailureDoesNotPropagate() {
        when(storage.list()).thenThrow(new RuntimeException("s3 down"));

        service.reconcile();

        verify(storage, never()).delete(anyString());
    }
}
