// 구매 식별자와 영수증의 중복을 방지하며 실제판매 원장을 저장합니다.
package com.kuji.backend.domain.settlement.repository;

import com.kuji.backend.domain.settlement.entity.SellerSale;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerSaleRepository extends JpaRepository<SellerSale, Long> {}
