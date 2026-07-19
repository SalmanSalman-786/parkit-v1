package com.parking.backend.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.stereotype.Service;

import com.parking.backend.model.AuditAction;
import com.parking.backend.model.AuditActorRole;
import com.parking.backend.model.ParkingTariff;
import com.parking.backend.model.User;
import com.parking.backend.repository.ParkingTariffRepository;
import com.parking.backend.repository.UserRepository;

@Service
public class ParkingTariffService {

    public static final String BOOKING = "BOOKING";
    public static final String WALKIN = "WALKIN";

    public static final String TWO_WHEELER = "TWO_WHEELER";
    public static final String FOUR_WHEELER = "FOUR_WHEELER";

    public static final String HOURLY = "HOURLY";
    public static final String DAILY = "DAILY";

    private final ParkingTariffRepository parkingTariffRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public ParkingTariffService(
            ParkingTariffRepository parkingTariffRepository,
            UserRepository userRepository,
            AuditLogService auditLogService) {

        this.parkingTariffRepository = parkingTariffRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    public List<ParkingTariff> getTariffs(
            String parkingId,
            String bookingType,
            String vehicleType) {

        List<ParkingTariff> tariffs = parkingTariffRepository.findByParkingIdAndBookingTypeAndVehicleType(
                parkingId,
                bookingType,
                vehicleType);

        tariffs.sort(
                Comparator.comparingInt(ParkingTariff::getMaxValue));

        return tariffs;
    }

    public double calculatePrice(
            String parkingId,
            String bookingType,
            String vehicleType,
            long durationMinutes) {

        List<ParkingTariff> tariffs = getTariffs(
                parkingId,
                bookingType,
                vehicleType);

        if (tariffs.isEmpty()) {
            throw new RuntimeException("Tariff not configured");
        }

        // -------------------------
        // HOURLY TARIFF (Up to 24 Hours)
        // -------------------------
        if (durationMinutes <= 1440) {

            ParkingTariff lastHourly = null;

            for (ParkingTariff tariff : tariffs) {

                if (!HOURLY.equals(tariff.getSlabType())) {
                    continue;
                }

                lastHourly = tariff;

                if (durationMinutes <= tariff.getMaxValue()) {
                    return tariff.getPrice();
                }
            }

            // Use highest configured hourly slab
            if (lastHourly != null) {
                return lastHourly.getPrice();
            }
        }

        // -------------------------
        // DAILY TARIFF (More than 24 Hours)
        // -------------------------

        long days = (long) Math.ceil(durationMinutes / 1440.0);

        ParkingTariff lastDaily = null;

        for (ParkingTariff tariff : tariffs) {

            if (!DAILY.equals(tariff.getSlabType())) {
                continue;
            }

            lastDaily = tariff;

            if (days <= tariff.getMaxValue()) {
                return tariff.getPrice();
            }
        }

        // Use highest configured daily slab
        if (lastDaily != null) {
            return lastDaily.getPrice();
        }

        throw new RuntimeException("Tariff not configured");
    }

    @Transactional
    public ParkingTariff addTariff(ParkingTariff tariff) {

        return parkingTariffRepository.save(tariff);
    }

    public List<ParkingTariff> getTariffsByParking(String parkingId) {

        return parkingTariffRepository
                .findByParkingIdOrderByBookingTypeAscVehicleTypeAscSlabTypeAscMaxValueAsc(
                        parkingId);
    }

    @Transactional
    public ParkingTariff updateTariff(
            String id,
            ParkingTariff updated) {

        ParkingTariff tariff = parkingTariffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tariff not found"));

        tariff.setBookingType(updated.getBookingType());
        tariff.setVehicleType(updated.getVehicleType());
        tariff.setSlabType(updated.getSlabType());
        tariff.setMaxValue(updated.getMaxValue());
        tariff.setPrice(updated.getPrice());

        return parkingTariffRepository.save(tariff);
    }

    @Transactional
    
    public void deleteTariff(String id) {

        parkingTariffRepository.deleteById(id);
    }

    @Transactional
    public List<ParkingTariff> saveTariffs(
            List<ParkingTariff> tariffs,
            String adminId,
            String ipAddress) {

        if (tariffs.isEmpty()) {
            throw new RuntimeException("No tariffs to save");
        }

        String parkingId = tariffs.get(0).getParkingId();

        parkingTariffRepository.deleteByParkingId(parkingId);

        List<ParkingTariff> saved = parkingTariffRepository.saveAll(tariffs);
        User admin = userRepository.findById(adminId)
                .orElse(null);

        auditLogService.log(
                adminId,
                admin != null ? admin.getUsername() : null,
                admin != null ? admin.getName() : null,
                AuditActorRole.ADMIN,
                AuditAction.TARIFF_CHANGED,
                "PARKING",
                parkingId,
                "Updated " + saved.size() + " tariff(s) for parking",
                ipAddress,
                true);

        return saved;
    }
}