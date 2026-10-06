package com.skillhub.domain.port;

import java.time.Instant;

public interface ClockPort {
    Instant now();
}
