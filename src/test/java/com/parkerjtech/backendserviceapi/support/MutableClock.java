package com.parkerjtech.backendserviceapi.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {
    private final AtomicReference<Instant> now;
    public MutableClock(Instant initial) { now = new AtomicReference<>(initial); }
    public void setInstant(Instant instant) { now.set(instant); }
    @Override public Instant instant() { return now.get(); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
}
