package com.kuji.backend.domain.kuji.service;

import com.kuji.backend.domain.kuji.dto.KujiBoardResponse;
import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.Wishlist;
import com.kuji.backend.domain.kuji.repository.KujiBoardRepository;
import com.kuji.backend.domain.kuji.repository.WishlistRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final MemberRepository memberRepository;
    private final KujiBoardRepository kujiBoardRepository;

    /**
     * 찜 토글 (있으면 삭제, 없으면 추가)
     */
    @Transactional
    public boolean toggleWishlist(Long memberId, Long boardId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
        KujiBoard kujiBoard = kujiBoardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지판을 찾을 수 없습니다."));

        Optional<Wishlist> wishlistOpt = wishlistRepository.findByMemberAndKujiBoard(member, kujiBoard);

        if (wishlistOpt.isPresent()) {
            wishlistRepository.delete(wishlistOpt.get());
            return false; // 찜 해제됨
        } else {
            Wishlist wishlist = Wishlist.builder()
                    .member(member)
                    .kujiBoard(kujiBoard)
                    .build();
            wishlistRepository.save(wishlist);
            return true; // 찜 등록됨
        }
    }

    /**
     * 나의 찜 목록 조회
     */
    public List<KujiBoardResponse> getMyWishlist(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        return wishlistRepository.findAllByMemberOrderByCreatedAtDesc(member).stream()
                .map(wishlist -> KujiBoardResponse.from(wishlist.getKujiBoard()))
                .toList();
    }
}
