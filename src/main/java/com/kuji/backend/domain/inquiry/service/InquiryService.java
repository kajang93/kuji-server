package com.kuji.backend.domain.inquiry.service;

import com.kuji.backend.domain.inquiry.dto.InquiryCreateRequest;
import com.kuji.backend.domain.inquiry.dto.InquiryResponse;
import com.kuji.backend.domain.inquiry.entity.Inquiry;
import com.kuji.backend.domain.inquiry.repository.InquiryRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final MemberRepository memberRepository;

    /**
     * 문의 등록
     */
    @Transactional
    public Long createInquiry(Long memberId, InquiryCreateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        Inquiry inquiry = Inquiry.builder()
                .member(member)
                .title(request.title())
                .content(request.content())
                .inquiryType(request.inquiryType())
                .shippingId(request.shippingId())
                .build();

        return inquiryRepository.save(inquiry).getId();
    }

    /**
     * 전체 문의 내역 조회 (관리자용)
     */
    public List<InquiryResponse> getAllInquiries() {
        return inquiryRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(InquiryResponse::from)
                .toList();
    }

    /**
     * 나의 문의 내역 조회
     */
    public List<InquiryResponse> getMyInquiries(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        return inquiryRepository.findAllByMemberOrderByCreatedAtDesc(member).stream()
                .map(InquiryResponse::from)
                .toList();
    }

    /**
     * 문의 상세 조회
     */
    public InquiryResponse getInquiry(Long id, Long memberId) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문의입니다."));

        // 본인 문의인지 확인 (관리자가 아닐 경우)
        if (!inquiry.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("조회 권한이 없습니다.");
        }

        return InquiryResponse.from(inquiry);
    }

    /**
     * 답변 등록 (관리자용)
     */
    @Transactional
    public void answerInquiry(Long id, String answerContent) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문의입니다."));

        inquiry.answer(answerContent);
    }
}
