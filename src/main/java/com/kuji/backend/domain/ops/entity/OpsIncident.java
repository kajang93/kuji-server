package com.kuji.backend.domain.ops.entity;

import com.kuji.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "ops_incident")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpsIncident extends BaseTimeEntity {

    @Id
    @Column(length = 50)
    private String id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String suggestedAction;

    @Column(length = 50, nullable = false)
    private String token;

    @Column(length = 20, nullable = false)
    private String status; // PENDING, APPROVED, REJECTED

    @Builder
    public OpsIncident(String description, String suggestedAction) {
        this.id = UUID.randomUUID().toString();
        this.description = description;
        this.suggestedAction = suggestedAction;
        this.token = UUID.randomUUID().toString().replace("-", "");
        this.status = "PENDING";
    }

    public void approve() {
        this.status = "APPROVED";
    }

    public void reject() {
        this.status = "REJECTED";
    }
}
