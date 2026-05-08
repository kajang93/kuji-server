package com.kuji.backend.domain.shipping.dto;

public record TrackingRequest(
    String courierName,
    String trackingNumber
) {
}
