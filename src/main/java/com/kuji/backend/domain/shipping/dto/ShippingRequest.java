package com.kuji.backend.domain.shipping.dto;

import java.util.List;

public record ShippingRequest(
    List<Long> drawHistoryIds,
    String recipientName,
    String phone,
    String zipcode,
    String address,
    String detailAddress,
    String deliveryMessage
) {
}
