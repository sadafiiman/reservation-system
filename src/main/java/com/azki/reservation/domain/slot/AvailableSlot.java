package com.azki.reservation.domain.slot;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "available_slots")
@Getter
@Setter
@NoArgsConstructor
public class AvailableSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "is_reserved", nullable = false)
    private boolean reserved = false;

    /**
     * Optimistic-lock safety net. In practice we take a pessimistic row lock
     * before mutating this entity (see AvailableSlotRepository#lockById), so
     * this mostly guards against any code path that bypasses that lock.
     */
    @Version
    private Long version;
}
