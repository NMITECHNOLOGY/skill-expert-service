package com.nmi.platform.skillexpert.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nmi.platform.skillexpert.model.entity.ExpertBooking;

public interface ExpertBookingRepository extends JpaRepository<ExpertBooking, Long> {

    List<ExpertBooking> findByCustomerUserIdOrderByScheduledAtDesc(String customerUserId);

    List<ExpertBooking> findByProfile_UserIdOrderByScheduledAtAsc(String expertUserId);
}
