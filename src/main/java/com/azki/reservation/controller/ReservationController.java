package com.azki.reservation.controller;

import com.azki.reservation.dto.response.ReservationResponse;
import com.azki.reservation.security.UserPrincipal;
import com.azki.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Reserve and cancel the nearest available slot")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @Operation(summary = "Reserve the nearest available slot for the authenticated user")
    public ResponseEntity<ReservationResponse> reserve(@AuthenticationPrincipal UserPrincipal principal) {
        ReservationResponse response = reservationService.reserveNearestSlot(principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a reservation owned by the authenticated user")
    public ResponseEntity<Void> cancel(@PathVariable Long id,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        reservationService.cancelReservation(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
