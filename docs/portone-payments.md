# 포트원(PortOne) V2 결제 연동 안내

오시쿠지는 카드 결제를 **포트원 V2 결제창**으로 처리합니다. 실제 결제대행사(PG)는 포트원 채널로 연결하며,
현재 테스트 채널은 **KG이니시스(inicis_v2)** 입니다. 쿠지 구매와 포인트 충전이 같은 흐름을 씁니다.

## 1. 결제 흐름

```
[앱] 구매하기
  │ ① POST /api/kuji/{boardId}/payment/prepare  (포인트 충전: /api/points/charge/prepare)
  │    → 서버가 주문번호(orderId)·금액을 만들어 결제 세션에 저장
  ▼
[앱] PortOne.requestPayment()  ─ 결제창 (카드 결제가 여기서 끝남)
  │    paymentId = 주문번호, totalAmount = 서버가 정한 금액, payMethod = CARD
  ▼
[앱] /payment/return?payment=success&orderId=…&paymentKey=…&amount=…
     (포인트 충전은 /?pointCharge=success&…)
     · 모바일: 포트원이 이 주소로 이동시키고, 실패·취소면 code·message 를 붙임
     · PC: 결제창 결과를 받은 앱이 같은 주소로 이동
  │ ② 로그인한 사용자의 확인 요청
  │    쿠지: POST /api/kuji/{boardId}/draw  { paymentType: "PG", paymentKey, orderId, amount }
  │    충전: POST /api/points/charge/confirm { paymentKey, orderId, amount }
  ▼
[서버] ③ 결제 복구 기록을 먼저 남기고 준비된 주문과 회원·금액·수량 대조
  │ ④ 포트원 결제 조회  GET https://api.portone.io/payments/{paymentId}
  │    상점 ID·결제 ID·금액·KRW·status=PAID 확인 (운영 모드면 LIVE 채널 결제인지도 확인)
  │ ⑤ 쿠지 판 잠금 → 재검증 → 추첨 → 보관함 저장 (충전은 포인트 지급)
  ▼
[앱] 개봉 화면 / 충전 완료
```

- 결제 ID는 서버가 만든 주문번호(`KUJI-…`, `CHARGE-…`)를 그대로 씁니다. `paymentKey` 필드에도 같은 값이 들어갑니다. DB 변경은 없습니다.
- 결제 수단은 신용·체크카드만 엽니다.

## 2. 실패·취소 처리

포트원 일반 결제는 **결제창에서 이미 결제가 끝난 뒤** 앱으로 돌아옵니다.
그래서 서버가 주문을 처리하지 못하면 어떤 이유든 결제를 되돌립니다.

| 상황 | 처리 |
|---|---|
| 사용자가 결제창을 닫음 / 카드 승인 실패 | 결제 없음. 앱이 실패로 처리 |
| 결제 후 판매 중지·매진·가격 변경·서버 오류 | 처리가 롤백되는 즉시 포트원 취소 API `POST /payments/{paymentId}/cancel` 로 **자동 전액 취소**. 화면에 "결제된 금액은 자동으로 취소됩니다" 안내 |
| 취소 요청이 실패하거나 서버가 중간에 멈춤 | 결제 복구 스케줄러가 1분마다 포트원 조회로 다시 확인하고 취소 |
| 같은 결제 재요청 (새로고침·뒤로가기) | 저장된 결과를 그대로 돌려주고 다시 처리하지 않음 |
| 금액·주문번호를 바꾼 요청 | 준비된 주문과 달라 거절 |
| 운영 중 테스트 채널로 결제한 요청 | 상품을 주지 않고 취소 (테스트 결제는 자동 환불되므로 악용 방지) |

## 3. 설정 값

| 위치 | 이름 | 값 | 비고 |
|---|---|---|---|
| 서버 저장소 비밀값 | `PORTONE_API_SECRET` | 포트원 콘솔 → 연동 정보 → **V2 API Secret** | **필수, 비공개** |
| 서버 저장소 비밀값 | `PORTONE_LIVE` | 테스트 채널이면 비워 두기, 실결제 채널이면 `true` | 정산 기록 운영/테스트 구분에도 사용 |
| 클라이언트 저장소 비밀값 | `VITE_PORTONE_CHANNEL_KEY` | 실결제 채널 키 | 비워 두면 코드의 테스트 채널 키 사용 |
| 코드 기본값 | 상점 ID | `store-5aa93057-fbd5-4899-85b8-79b660c2747a` | 공개 값 |
| 코드 기본값 | 테스트 채널 키 | `channel-key-563d8886-224b-42cf-ba32-6cfa24cd4f59` | 공개 값 (KG이니시스 테스트) |

- API Secret 은 서버에만 둡니다. 앱·로그·커밋·채팅에 넣지 않습니다. 유출되면 콘솔에서 즉시 재발급합니다.

## 4. 테스트 결제 (PG 계약 전)

1. `PORTONE_API_SECRET` 을 넣고 서버를 배포합니다. (`PORTONE_LIVE` 는 비워 둡니다)
2. 앱에서 쿠지 1장 구매 → 결제창 → 카드 결제 → 개봉 화면까지 확인합니다.
3. 포인트 1만 원 충전도 확인합니다.
4. 포트원 관리자콘솔 결제 내역에 결제 건이 보이면 성공입니다.

- 테스트 채널 결제는 30분~1시간 또는 당일 자정 전에 **자동 환불**됩니다. 콘솔에는 '결제완료'로 남아 있어도 정상입니다.
- KB국민·NH농협·카카오뱅크 카드는 테스트 결제가 막히는 경우가 많으니 다른 카드로 시도합니다.
- 테스트 결제로 받은 당첨품은 배송 신청하지 말고, 판매자 화면에서 재고를 맞춰 둡니다.

## 5. 실결제 전환 (PG 계약 완료 후)

1. 포트원 콘솔 → 채널 관리 → **실연동** 탭에서 계약한 PG(KG이니시스 실제 MID)로 채널을 추가합니다.
2. 클라이언트 비밀값 `VITE_PORTONE_CHANNEL_KEY` 에 실연동 채널 키를 넣습니다.
3. 서버 비밀값 `PORTONE_LIVE` 에 `true` 를 넣습니다.
4. 두 저장소를 다시 배포하고, 소액 실결제 1건 후 콘솔에서 취소해 확인합니다.

## 6. 앱(안드로이드·iOS) 출시 시

앱 웹뷰에서는 카드사 앱으로 넘어갔다 돌아와야 하므로 `requestPayment` 에 `appScheme`(예: `oshikuji://`)을 넣고,
안드로이드 `intent://` 처리와 iOS `LSApplicationQueriesSchemes` 등록이 필요합니다. 포트원 개발자센터의 모바일 웹뷰 가이드를 따릅니다.

## 참고

- 포트원 개발자센터: https://developers.portone.io
- 관련 코드: 서버 `global/infra/portone/PortOnePaymentClient.java`, `domain/payment/service/PaymentRecoveryService.java`,
  `PaymentCompensationService.java` / 클라이언트 `src/utils/portonePay.ts`
