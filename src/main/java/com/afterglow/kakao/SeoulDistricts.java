package com.afterglow.kakao;

import java.util.List;

/**
 * 서울 25개 자치구 대략적인 중심 좌표 + TourAPI sigunguCode. 카카오 카테고리 스윕(반경 검색)의
 * 시작점으로만 쓰이며, 정확한 행정구역 경계가 필요한 용도가 아니라 인접 구끼리 약간 겹쳐도 문제없다
 * (동일 place_id는 자동으로 중복 제거됨).
 *
 * <p>sigunguCode는 TourAPI 4.0 {@code areaCode2}(areaCode=1, 서울) 조회로 실측 확인한 값이다 —
 * 추측하지 않고 직접 API를 호출해서 확인했다(2026-08-29).
 */
public final class SeoulDistricts {

    public record Center(String name, double lat, double lng, String tourApiSigunguCode) {
    }

    public static final List<Center> ALL = List.of(
            new Center("강남구", 37.5172, 127.0473, "1"),
            new Center("강동구", 37.5301, 127.1238, "2"),
            new Center("강북구", 37.6396, 127.0257, "3"),
            new Center("강서구", 37.5509, 126.8495, "4"),
            new Center("관악구", 37.4784, 126.9516, "5"),
            new Center("광진구", 37.5384, 127.0822, "6"),
            new Center("구로구", 37.4954, 126.8874, "7"),
            new Center("금천구", 37.4519, 126.9020, "8"),
            new Center("노원구", 37.6542, 127.0568, "9"),
            new Center("도봉구", 37.6688, 127.0471, "10"),
            new Center("동대문구", 37.5744, 127.0396, "11"),
            new Center("동작구", 37.5124, 126.9393, "12"),
            new Center("마포구", 37.5663, 126.9019, "13"),
            new Center("서대문구", 37.5791, 126.9368, "14"),
            new Center("서초구", 37.4837, 127.0324, "15"),
            new Center("성동구", 37.5633, 127.0371, "16"),
            new Center("성북구", 37.5894, 127.0167, "17"),
            new Center("송파구", 37.5145, 127.1059, "18"),
            new Center("양천구", 37.5169, 126.8664, "19"),
            new Center("영등포구", 37.5264, 126.8963, "20"),
            new Center("용산구", 37.5326, 126.9905, "21"),
            new Center("은평구", 37.6027, 126.9291, "22"),
            new Center("종로구", 37.5730, 126.9794, "23"),
            new Center("중구", 37.5641, 126.9979, "24"),
            new Center("중랑구", 37.6066, 127.0927, "25"));

    private SeoulDistricts() {
    }
}
