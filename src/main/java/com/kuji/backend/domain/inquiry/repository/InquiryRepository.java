package com.kuji.backend.domain.inquiry.repository;

import com.kuji.backend.domain.inquiry.entity.Inquiry;
import com.kuji.backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    List<Inquiry> findAllByMemberOrderByCreatedAtDesc(Member member);
}
