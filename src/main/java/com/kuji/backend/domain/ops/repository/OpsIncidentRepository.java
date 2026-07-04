package com.kuji.backend.domain.ops.repository;

import com.kuji.backend.domain.ops.entity.OpsIncident;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsIncidentRepository extends JpaRepository<OpsIncident, String> {
}
