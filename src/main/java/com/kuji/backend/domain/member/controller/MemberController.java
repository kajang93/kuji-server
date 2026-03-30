package com.kuji.backend.domain.member.controller;

import com.kuji.backend.domain.member.dto.SignUpRequest;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController // 💡 "나는 화면(HTML) 말고 데이터(JSON)만 반환하는 전용 웨이터야!" 라는 뜻
@RequestMapping("/api/members") // 💡 식당 주소: "/api/members" 로 들어오는 손님은 내가 다 받겠다!
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService; // 주방장(Service) 호출 준비 완료

    /**
     * 일반(LOCAL) 회원 가입 API
     */
    @PostMapping("/signup")
    public ResponseEntity<Long> signUp(@RequestBody SignUpRequest request) {

        // 1. @RequestBody가 프론트에서 보낸 JSON을 우리가 만든 DTO 상자에 예쁘게 담아줍니다.
        // 2. 주방장(Service)에게 주문서(DTO)를 넘겨서 요리(DB 저장)를 시킵니다.
        Long memberId = memberService.signUp(request, SocialType.LOCAL, null);

        // 3. 요리가 다 되면 생성된 회원번호(ID)를 손님(프론트엔드)에게 전달! 200 OK!
        return ResponseEntity.ok(memberId);
    }
}   