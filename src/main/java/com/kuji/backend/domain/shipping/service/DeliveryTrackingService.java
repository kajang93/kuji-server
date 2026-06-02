package com.kuji.backend.domain.shipping.service;

import com.kuji.backend.domain.shipping.dto.TrackingResponse;
import com.kuji.backend.domain.shipping.entity.Shipping;
import com.kuji.backend.domain.shipping.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryTrackingService {

    private final ShippingRepository shippingRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    // Delivery Tracker (tracker.delivery) 무료 오픈소스 API URL
    private static final String DELIVERY_TRACKER_API_URL = "https://apis.tracker.delivery/carriers/{carrier_id}/tracks/{track_id}";

    /**
     * 배송 추적 정보 조회 (Delivery Tracker 오픈소스 API 연동)
     */
    @Transactional(readOnly = true)
    public TrackingResponse getTrackingInfo(Long shippingId) {
        Shipping shipping = shippingRepository.findById(shippingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배송 정보입니다."));

        // 아직 송장번호가 없다면 (배송 준비 중)
        if (shipping.getTrackingNumber() == null || shipping.getTrackingNumber().isBlank()) {
            return TrackingResponse.builder()
                    .orderNumber(shipping.getId().toString())
                    .trackingNumber("-")
                    .courier(shipping.getCourierName() != null ? shipping.getCourierName() : "-")
                    .recipientAddress(shipping.getAddress())
                    .deliveryDriver("미배정")
                    .deliveryDriverPhone("-")
                    .history(List.of(
                            TrackingResponse.TrackingDetail.builder()
                                    .date(shipping.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                    .time(shipping.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")))
                                    .location("물류센터")
                                    .status("배송 준비 중")
                                    .isCompleted(true)
                                    .build()
                    ))
                    .build();
        }

        // Delivery Tracker API 연동
        String carrierId = getCarrierId(shipping.getCourierName());
        String trackingNumberToUse = shipping.getTrackingNumber();

        // [공식 테스트 모드] 택배사 코드가 dev.track.dummy 인 경우, 동적으로 더미 송장번호 생성
        if ("dev.track.dummy".equals(carrierId)) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            int hour = (now.getHour() / 3) * 3; // 0, 3, 6, 9, 12, 15, 18, 21
            trackingNumberToUse = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
            log.info("가상 택배사 테스트 호출. 동적 생성된 송장번호: {}", trackingNumberToUse);
        }
        
        try {
            // GET https://apis.tracker.delivery/carriers/{carrier_id}/tracks/{track_id}
            Map<String, Object> response = restTemplate.getForObject(
                    DELIVERY_TRACKER_API_URL, 
                    Map.class, 
                    carrierId, 
                    trackingNumberToUse
            );

            if (response == null || response.containsKey("message")) {
                log.error("Delivery Tracker API 오류: {}", response);
                throw new IllegalArgumentException("배송 조회 중 오류가 발생했습니다.");
            }

            List<Map<String, Object>> progresses = (List<Map<String, Object>>) response.get("progresses");
            List<TrackingResponse.TrackingDetail> history = new ArrayList<>();
            String driverName = "미배정";
            String driverPhone = "-";

            if (progresses != null && !progresses.isEmpty()) {
                for (Map<String, Object> progress : progresses) {
                    String timeString = (String) progress.get("time"); // "2020-01-01T10:00:00+09:00"
                    String date = timeString != null && timeString.length() >= 10 ? timeString.substring(0, 10) : "-";
                    String time = timeString != null && timeString.length() >= 16 ? timeString.substring(11, 16) : "-";

                    Map<String, String> statusMap = (Map<String, String>) progress.get("status");
                    String kind = statusMap != null ? statusMap.get("text") : "알수없음";

                    Map<String, String> locationMap = (Map<String, String>) progress.get("location");
                    String location = locationMap != null ? locationMap.get("name") : "-";

                    // 배송 기사 정보는 오픈소스 API 스펙상 명시적으로 제공되지 않고 description에 포함될 수 있음
                    String description = (String) progress.get("description");
                    if (description != null && kind.contains("배달출발")) {
                        // "담당 기사: 홍길동 010-1234-5678" 같은 텍스트가 있을 수 있음
                        if (description.contains("담당 기사")) {
                            driverName = "배송기사 확인필요";
                        }
                    }

                    history.add(TrackingResponse.TrackingDetail.builder()
                            .date(date)
                            .time(time)
                            .location(location)
                            .status(kind)
                            .isCompleted(true)
                            .build());
                }
            }

            return TrackingResponse.builder()
                    .orderNumber(shipping.getId().toString())
                    .trackingNumber(shipping.getTrackingNumber())
                    .courier(shipping.getCourierName())
                    .recipientAddress(shipping.getAddress())
                    .deliveryDriver(driverName)
                    .deliveryDriverPhone(driverPhone)
                    .history(history)
                    .build();

        } catch (Exception e) {
            log.error("택배 조회 실패", e);
            throw new IllegalArgumentException("올바르지 않은 송장번호이거나 택배사 서버에 문제가 있습니다.");
        }
    }

    /**
     * 택배사 이름을 Delivery Tracker (tracker.delivery) Carrier ID로 변환
     */
    private String getCarrierId(String courierName) {
        if (courierName == null) return "kr.cjlogistics";
        return switch (courierName) {
            case "CJ대한통운" -> "kr.cjlogistics";
            case "우체국택배", "우체국" -> "kr.epost";
            case "한진택배", "한진" -> "kr.hanjin";
            case "롯데택배", "롯데" -> "kr.lotte";
            case "로젠택배", "로젠" -> "kr.logen";
            case "CU편의점택배" -> "kr.cupost";
            case "GS25편의점택배" -> "kr.cvsnet";
            case "dev.track.dummy" -> "dev.track.dummy"; // 개발자 전용 명시적 더미 코드 (일반인은 절대 모름)
            default -> "kr.cjlogistics"; // 기본값
        };
    }
}
