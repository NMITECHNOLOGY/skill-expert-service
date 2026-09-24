package com.nmi.platform.skillexpert.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nmi.platform.skillexpert.model.entity.ExpertProfile;
import com.nmi.platform.skillexpert.model.enums.ExpertProfileStatus;

public interface ExpertProfileRepository extends JpaRepository<ExpertProfile, Long> {

    Optional<ExpertProfile> findByUserId(String userId);

    Page<ExpertProfile> findByStatus(ExpertProfileStatus status, Pageable pageable);

    @Query("""
            SELECT p FROM ExpertProfile p
            LEFT JOIN p.revision r
            WHERE (p.status IN :visible OR r.status IN :revisionVisible)
              AND (:status IS NULL OR p.status = :status OR r.status = :status)
              AND (
                :search IS NULL OR :search = ''
                OR LOWER(COALESCE(p.displayName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(p.jobTitle, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(r.displayName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(r.jobTitle, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.userId) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<ExpertProfile> searchAdmin(
            @Param("visible") Collection<ExpertProfileStatus> visible,
            @Param("revisionVisible") Collection<ExpertProfileStatus> revisionVisible,
            @Param("status") ExpertProfileStatus status,
            @Param("search") String search,
            Pageable pageable);
}
