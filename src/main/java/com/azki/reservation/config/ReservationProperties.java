package com.azki.reservation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "reservation")
public class ReservationProperties {

    /** How many days ahead of "now" we keep mirrored in the Redis ZSET. */
    private int slotWindowDays = 7;

    /** Redis key for the sorted set of available slot ids, scored by start_time epoch seconds. */
    private String zsetKey = "available_slots_zset";

    /** Max number of candidate slots to try before giving up on a reservation request. */
    private int maxPopRetries = 5;
}
