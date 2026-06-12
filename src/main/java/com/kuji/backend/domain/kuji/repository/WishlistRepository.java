package com.kuji.backend.domain.kuji.repository;

import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.Wishlist;
import com.kuji.backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    Optional<Wishlist> findByMemberAndKujiBoard(Member member, KujiBoard kujiBoard);
    List<Wishlist> findAllByMemberOrderByCreatedAtDesc(Member member);
    List<Wishlist> findAllByKujiBoard(KujiBoard kujiBoard);
    boolean existsByMemberAndKujiBoard(Member member, KujiBoard kujiBoard);
    void deleteAllByKujiBoard(KujiBoard kujiBoard);
}
