// 송금 없이 적격 원장을 재검증하고 관리자가 확인한 실제 지급만 멱등하게 기록한다.
package com.kuji.backend.domain.settlement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.settlement.dto.SettlementDtos;
import com.kuji.backend.domain.settlement.repository.SellerPayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerPayoutService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final MemberRepository members;
    private final SellerPayoutRepository payouts;
    private final ObjectMapper mapper;

    public SettlementDtos.PageResponse<SettlementDtos.Seller> sellers(Long adminId, int page, int size) {
        requireAdmin(adminId); validatePage(page, size);
        return page(payouts.sellers(page, size), page, size);
    }
    public SettlementDtos.PageResponse<SettlementDtos.Sale> sales(Long adminId, Long sellerId, int page, int size) {
        requireAdmin(adminId); requireSellerExists(sellerId); validatePage(page, size);
        return page(payouts.sales(sellerId, page, size), page, size);
    }
    public SettlementDtos.Preview preview(Long adminId, Long sellerId, SettlementDtos.Selection selection) {
        requireAdmin(adminId); requireSellerExists(sellerId);
        List<Long> ids = selectionIds(selection == null ? null : selection.saleIds());
        return previewOf(sellerId, ids, eligible(sellerId, ids));
    }

    @Transactional
    public SettlementDtos.Payout record(Long adminId, Long sellerId, SettlementDtos.CreatePayout request) {
        requireAdmin(adminId); requireSellerExists(sellerId);
        SettlementDtos.CreatePayout input = normalized(request);
        String fingerprint = fingerprint(sellerId, input);
        try {
            // 같은 요청 UUID는 판매자·관리자가 달라도 한 번만 처리합니다.
            payouts.lockRequest(input.requestId());
            var existing = payouts.byRequest(input.requestId());
            if (existing.isPresent()) {
                if (!existing.get().fingerprint().equals(fingerprint)) throw conflict("같은 요청 번호로 다른 지급 내용을 기록할 수 없습니다.");
                return existing.get().value();
            }
            List<Long> buyers = payouts.buyerIds(sellerId, input.saleIds());
            if (buyers.size() != input.saleIds().size()) throw conflict("선택한 판매자의 지급 대상 원장을 다시 확인해주세요.");
            // 환불 접수도 구매자 회원을 잠그므로 함께 정렬해 잠근 뒤 환불 여부를 다시 읽습니다.
            SortedSet<Long> memberIds = new TreeSet<>(buyers); memberIds.add(sellerId);
            payouts.lockMembers(memberIds);
            payouts.lockSalesAndDraws(sellerId, input.saleIds());
            List<SettlementDtos.Sale> sales = eligible(sellerId, input.saleIds());
            var calculated = previewOf(sellerId, input.saleIds(), sales);
            if (input.expectedAmount() != calculated.netAmount()) throw conflict("지급 예정액이 현재 원장 합계와 다릅니다. 다시 미리보기 후 확인해주세요.");
            if (sales.stream().anyMatch(sale -> input.paidDate().isBefore(sale.completedAt().toLocalDate()))) {
                throw badRequest("지급일은 선택한 거래일보다 빠를 수 없습니다.");
            }
            long id = payouts.insert(sellerId, adminId, input, fingerprint);
            sales.forEach(sale -> payouts.allocate(id, sale));
            return payouts.byId(id, sellerId).orElseThrow().value();
        } catch (PessimisticLockingFailureException e) {
            throw conflict("다른 정산 또는 환불 처리가 진행 중입니다. 잠시 후 같은 요청 번호로 다시 확인해주세요.");
        }
    }

    public SettlementDtos.PageResponse<SettlementDtos.Payout> adminPayouts(Long adminId, Long sellerId, int page, int size) {
        requireAdmin(adminId); requireSellerExists(sellerId); validatePage(page, size);
        return page(payouts.payouts(sellerId, page, size), page, size);
    }
    public SettlementDtos.Payout adminPayout(Long adminId, Long id) {
        requireAdmin(adminId); requireId(id);
        return payouts.byId(id, null).orElseThrow(() -> notFound("지급 기록을 찾을 수 없습니다.")).value();
    }
    public SettlementDtos.PageResponse<SettlementDtos.Payout> myPayouts(Long sellerId, int page, int size) {
        requireSeller(sellerId); validatePage(page, size);
        return page(payouts.payouts(sellerId, page, size), page, size);
    }
    public SettlementDtos.Payout myPayout(Long sellerId, Long id) {
        requireSeller(sellerId); requireId(id);
        return payouts.byId(id, sellerId).orElseThrow(() -> notFound("지급 기록을 찾을 수 없습니다.")).value();
    }
    private List<SettlementDtos.Sale> eligible(Long sellerId, List<Long> ids) {
        var sales = payouts.selected(sellerId, ids);
        if (sales.size() != ids.size() || sales.stream().anyMatch(sale -> !sale.eligible())) {
            throw conflict("선택한 원장에 기지급·배송 미완료·환불 검토·실제 결제 미확인 거래가 있습니다. 대상을 다시 확인해주세요.");
        }
        return sales;
    }
    private SettlementDtos.Preview previewOf(Long sellerId, List<Long> ids, List<SettlementDtos.Sale> sales) {
        long gross = 0, fee = 0, net = 0;
        try {
            for (var sale : sales) {
                gross = Math.addExact(gross, sale.grossAmount());
                fee = Math.addExact(fee, sale.feeAmount());
                net = Math.addExact(net, sale.netAmount());
            }
        } catch (ArithmeticException e) { throw conflict("원장 합계를 처리할 수 없습니다. 선택 범위를 나눠 확인해주세요."); }
        if (net <= 0) throw conflict("지급 대상 금액이 없습니다.");
        return new SettlementDtos.Preview(sellerId, ids, gross, fee, net, sales.size(), true,
                "미리보기는 송금이나 지급 예약이 아닙니다. 실제 송금 후 증빙을 확인해 기록하며, 기록 시 배송·환불·기지급 여부와 금액을 다시 검증합니다.");
    }
    private SettlementDtos.CreatePayout normalized(SettlementDtos.CreatePayout input) {
        if (input == null || input.requestId() == null || input.expectedAmount() == null || input.expectedAmount() < 1
                || input.paidDate() == null || input.paidDate().isAfter(LocalDate.now(KOREA)) || !Boolean.TRUE.equals(input.transferConfirmed())) {
            throw badRequest("실제 송금 확인, 요청 번호, 지급액과 오늘 이전의 지급일을 확인해주세요.");
        }
        return new SettlementDtos.CreatePayout(input.requestId(), selectionIds(input.saleIds()), input.expectedAmount(), input.paidDate(),
                reference(input.transferReference(), 200), reference(input.evidenceReference(), 500), true);
    }
    private String reference(String input, int max) {
        String value = input == null ? "" : input.strip();
        if (value.isEmpty() || value.length() > max || value.codePoints().anyMatch(Character::isISOControl)) {
            throw badRequest("송금 참조와 비공개 증빙 참조를 길이와 줄바꿈 없이 입력해주세요.");
        }
        return value;
    }
    private List<Long> selectionIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id < 1)
                || new HashSet<>(ids).size() != ids.size()) throw badRequest("중복 없이 1~100개의 원장을 선택해주세요.");
        return ids.stream().sorted().toList();
    }
    private String fingerprint(Long sellerId, SettlementDtos.CreatePayout input) {
        try {
            byte[] value = mapper.writeValueAsBytes(List.of(sellerId, input.saleIds(), input.expectedAmount(), input.paidDate().toString(),
                    input.transferReference(), input.evidenceReference()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) { throw new IllegalStateException("지급 요청을 확인하지 못했습니다."); }
    }
    private Member requireMember(Long id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        return members.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
    private void requireAdmin(Long id) {
        if (requireMember(id).getRole() != RoleType.ADMIN) throw new AccessDeniedException("지급 기록을 관리할 권한이 없습니다.");
    }
    private void requireSeller(Long id) {
        RoleType role = requireMember(id).getRole();
        if (role != RoleType.BIZ && role != RoleType.ADMIN) throw new AccessDeniedException("본인 지급 내역을 조회할 권한이 없습니다.");
    }
    private void requireSellerExists(Long id) {
        requireId(id); if (!members.existsById(id)) throw notFound("판매자를 찾을 수 없습니다.");
    }
    private void requireId(Long id) { if (id == null || id < 1) throw badRequest("조회 번호를 확인해주세요."); }
    private void validatePage(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100) throw badRequest("조회 범위를 확인해주세요.");
    }
    private <T> SettlementDtos.PageResponse<T> page(List<T> content, int page, int size) {
        return new SettlementDtos.PageResponse<>(content.stream().limit(size).toList(), page, size, content.size() > size);
    }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
