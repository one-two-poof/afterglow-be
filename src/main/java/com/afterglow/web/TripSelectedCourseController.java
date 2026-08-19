package com.afterglow.web;

import com.afterglow.service.TripSelectedCourseService;
import com.afterglow.web.dto.TripSelectedCourseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "TripSelectedCourse", description = "사용자가 고른 추천 코스 조회. 로그인(JWT) 필요")
public class TripSelectedCourseController {

    private final TripSelectedCourseService tripSelectedCourseService;

    public TripSelectedCourseController(TripSelectedCourseService tripSelectedCourseService) {
        this.tripSelectedCourseService = tripSelectedCourseService;
    }

    @Operation(
            summary = "내가 고른 추천 코스 조회",
            description = "로그인한 사용자가 trip_recommended_courses 후보들 중 실제로 선택한 코스를 "
                    + "일자별 일정/장소/시술 정보까지 전부 조인해서 최신순으로 내려준다. JWT 필요.")
    @GetMapping
    public ResponseEntity<List<TripSelectedCourseResponse>> list(@AuthenticationPrincipal String userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(tripSelectedCourseService.listByUser(Long.parseLong(userId)));
    }
}
