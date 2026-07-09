package com.parking.backend.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.parking.backend.model.ParkingTariff;
import com.parking.backend.service.ParkingTariffService;

import jakarta.validation.Valid;

@RestController
//@CrossOrigin("*")
@RequestMapping("/api/tariffs")
public class ParkingTariffController {

    private final ParkingTariffService parkingTariffService;

    public ParkingTariffController(
            ParkingTariffService parkingTariffService) {

        this.parkingTariffService = parkingTariffService;
    }

    @PostMapping
    public ParkingTariff addTariff(
            @Valid @RequestBody ParkingTariff tariff,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized");
        }

        return parkingTariffService.addTariff(tariff);
    }

    @GetMapping("/{parkingId}")
    public List<ParkingTariff> getTariffs(
            @PathVariable String parkingId) {

        return parkingTariffService.getTariffsByParking(parkingId);
    }

    @PutMapping("/{id}")
    public ParkingTariff updateTariff(
            @PathVariable String id,
            @Valid @RequestBody ParkingTariff tariff,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized");
        }

        return parkingTariffService.updateTariff(id, tariff);
    }

    @DeleteMapping("/{id}")
    public String deleteTariff(
            @PathVariable String id,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized");
        }

        parkingTariffService.deleteTariff(id);

        return "Tariff deleted successfully";
    }

    @PostMapping("/bulk")
public List<ParkingTariff> saveTariffs(
        @Valid @RequestBody List<ParkingTariff> tariffs,
        Authentication auth) {

    if (!auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
        throw new RuntimeException("Unauthorized");
    }

    return parkingTariffService.saveTariffs(tariffs);
}
}