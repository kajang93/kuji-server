# 나이스페이먼츠 결제 연동 안내

오시쿠지는 카드 결제를 **나이스페이먼츠 결제창 (Server 승인 모델)** 로 처리합니다.
쿠지 구매와 포인트 충전이 같은 흐름을 씁니다.

## 1. 결제 흐름

```
[앱] 구매하기
  │ ① POST /api/kuji/{boardId}/payment/prepare  (포인트 충전: /api/points/charge/prepare)
  │    → 서버가 주문번호(orderId)·금액을 만들어 결제 세션에 저장
  ▼
[앱] AUTHNICE.requestPay()  ─ 나이스 결제창 (카드사 인증)
  │    returnUrl = https://kujishop.shop/api/payments/nice/return
  │    mallReserved = kuji | points  (돌아갈 화면 구분)
  ▼
[나이스] → POST /api/payments/nice/return  (authResultCode, tid, orderId, amount, authToken, signature)
  │ ② 서버가 인증 성공(0000)과 위변조 서명을 확인
  │    signature = hex(sha256(authToken + clientId + amount + secretKey))
  │    이 단계에서는 아직 돈이 빠져나가지 않습니다 (승인 전)
  ▼  303 리다이렉트
[앱] /payment/return?payment=success&paymentKey={tid}&orderId=…&amount=…
     (포인트 충전은 /?pointCharge=success&…)
  │ ③ 로그인한 사용자의 확인 요청
  │    쿠지: POST /api/kuji/{boardId}/draw  { paymentType: "PG", paymentKey, orderId, amount }
  │    충전: POST /api/points/charge/confirm { paymentKey, orderId, amount }
  ▼
[서버] 준비된 주문과 회원·금액·수량 대조 → 판매 가능·재고 사전 확인
  │ ④ 나이스 승인 API  POST /v1/payments/{tid} { amount }
  │    응답의 주문번호·금액·KRW·status=paid·서명(hex(sha256(tid + amount + ediDate + secretKey))) 확인
  │ ⑤ 쿠지 판 잠금 → 재검증 → 추첨 → 보관함 저장 (충전은 포인트 지급)
  ▼
[앱] 개봉 화면 / 충전 완료
```

- `paymentKey` 라는 이름은 기존 필드를 그대로 쓰며, 값은 **나이스 거래 키(tid)** 입니다. DB 변경은 없습니다.
- 결제 수단은 신용·체크카드(`method: card`)만 엽니다.

## 2. 실패·취소 처리

| 상황 | 처리 |
|---|---|
| 사용자가 결제창을 닫음 / 카드 인증 실패 | `payment=fail` 로 돌아와 결제 없이 종료 |
| 서명이 맞지 않는 인증 결과 | `payment=fail` 처리, 서버 로그에 주문번호만 경고로 남김 |
| 승인 전에 판매 중지·재고 부족 | 승인 API를 호출하지 않으므로 결제되지 않음. 화면에 이유 표시 |
| 승인 후 재검증 실패 (그 사이 매진·판매 중지·가격 변경) | 나이스 취소 API `POST /v1/payments/{tid}/cancel` 로 **자동 전액 취소** |
| 승인 응답 유실 (타임아웃) | 거래 조회 `GET /v1/payments/{tid}` 로 확정. 조회도 안 되면 망취소 `POST /v1/payments/netcancel` |
| 같은 결제 재요청 (새로고침·뒤로가기) | 저장된 결과를 그대로 돌려주고 다시 승인하지 않음 |

자동 취소는 `PaymentRecoveryService` → `PaymentCompensationService` 가 담당하며, 실패하면 스케줄러가 재시도합니다.

## 3. 설정 값

| 위치 | 이름 | 값 |
|---|---|---|
| 서버 저장소 비밀값 | `NICE_CLIENT_ID` | 나이스 가맹점관리자 → 개발정보 → 클라이언트 키 (Server 승인) |
| 서버 저장소 비밀값 | `NICE_SECRET_KEY` | 개발정보 → 시크릿 키 (**Basic 인증** 방식으로 발급) |
| 클라이언트 저장소 비밀값 | `VITE_NICE_CLIENT_ID` | 서버와 같은 클라이언트 키 |
| (선택) 서버 환경변수 | `NICE_API_URL` | 비워 두면 키 종류로 자동 선택 |

- 클라이언트 키가 `R1_`/`R2_` 로 시작하면 운영(`api.nicepay.co.kr`), `S1_`/`S2_` 면 샌드박스(`sandbox-api.nicepay.co.kr`) API로 자동 연결됩니다.
- 판매자 정산 기록도 같은 기준으로 운영 매출(LIVE)과 테스트 매출(TEST)을 구분합니다.
- 시크릿 키는 서버에만 둡니다. 앱·로그·커밋에 넣지 않습니다. 유출되면 개발정보에서 즉시 재발급합니다.

## 4. 나이스 가맹점관리자에서 할 일

1. **개발정보 → KEY 정보**: 결제창 승인 방식 **Server 승인**, API 인가 방식 **Basic 인증** 으로 키를 발급합니다.
2. **IP 보안 (선택)**: 승인·취소 API는 EC2 서버에서만 호출하므로 EC2 공인 IP를 등록하면 더 안전합니다.
   등록 후 반드시 IP TEST 를 눌러 확인하세요. 잘못 등록하면 결제가 막힙니다.
3. **웹훅**: 카드 결제만 쓰므로 필수는 아닙니다 (가상계좌를 열 때 필요).

## 5. 테스트 방법

1. 샌드박스 키(`S2_…`)를 세 비밀값에 넣고 배포합니다.
2. 앱에서 쿠지 1장 구매 → 결제창 → 샌드박스 카드 인증 → 개봉 화면까지 확인합니다.
3. 포인트 1만 원 충전도 같은 방식으로 확인합니다.
4. 나이스 가맹점관리자 → 개발정보 → 로그에서 승인 요청과 응답을 확인할 수 있습니다.
5. 확인이 끝나면 운영 키(`R2_…`)로 세 비밀값을 바꾸고 다시 배포합니다.

## 6. 앱(안드로이드·iOS) 출시 시 추가 작업

앱 웹뷰에서 카드사 앱으로 넘어갔다 돌아오려면 `AUTHNICE.requestPay` 에 `appScheme`(예: `oshikuji://`)을 넣고,
안드로이드는 `intent://` 처리, iOS는 `LSApplicationQueriesSchemes` 에 카드사 앱 스킴을 등록해야 합니다.
(나이스 매뉴얼 → APP → Android / iOS 웹뷰 가이드)

## 참고

- 나이스페이먼츠 개발 매뉴얼: https://github.com/nicepayments/nicepay-manual
- 관련 코드: `global/infra/nice/NicePaymentClient.java`, `domain/payment/controller/NicePaymentReturnController.java`,
  클라이언트 `src/utils/nicePay.ts`
