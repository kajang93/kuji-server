package com.kuji.backend.domain.shipping.dto;

import com.kuji.backend.domain.shipping.entity.Shipping;
import com.kuji.backend.domain.shipping.enums.ShippingStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ShippingResponse(
    Long id,
    String recipientName,
    String phone,
    String address,
    String detailAddress,
    String trackingNumber,
    String courierName,
    String deliveryMessage,
    ShippingStatus status,
    LocalDateTime createdAt,
    List<ShippedItemResponse> items
) {
    public static ShippingResponse from(Shipping shipping, List<ShippedItemResponse> items) {
        return new ShippingResponse(
            shipping.getId(),
            shipping.getRecipientName(),
            shipping.getPhone(),
            shipping.getAddress(),
            shipping.getDetailAddress(),
            shipping.getTrackingNumber(),
            shipping.getCourierName(),
            shipping.getDeliveryMessage(),
            shipping.getStatus(),
            shipping.getCreatedAt(),
            items
        );
    }

    public record ShippedItemResponse(
        Long drawHistoryId,
        String kujiName,
        String grade,
        String itemName,
        String itemImage
    ) {}
}
