package com.parking.backend.repository;

import com.parking.backend.model.Parking;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ParkingRepository extends JpaRepository<Parking, String> {

    @Query("""
        SELECT p
        FROM Parking p
        WHERE p.latitude BETWEEN ?1 AND ?2
        AND p.longitude BETWEEN ?3 AND ?4
    """)
    List<Parking> findNearby(
            double minLat,
            double maxLat,
            double minLng,
            double maxLng);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Parking p WHERE p.id = :id")
    Optional<Parking> findByIdForUpdate(String id);
}