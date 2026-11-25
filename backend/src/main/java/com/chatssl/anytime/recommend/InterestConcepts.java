package com.chatssl.anytime.recommend;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 관심사 유사도. 프론트 src/lib/ai.ts의 concepts(), sharedConcepts(), similarity()를 그대로 옮겼다.
 * 관심사 문장에 키워드가 들어 있으면 그 '개념'으로 묶고, 개념 집합의 자카드 유사도(교집합/합집합)를 쓴다.
 * 나중에 AI 임베딩으로 바꾸면 similarity()만 교체하면 된다.
 *
 * CONCEPTS 순서가 결과 순서다(예: '보드게임'은 보드게임, 게임 순으로 잡힌다). 프론트와 같은 순서로 둔다.
 */
final class InterestConcepts {

    private static final Map<String, List<String>> CONCEPTS = new LinkedHashMap<>();

    static {
        CONCEPTS.put("보드게임", List.of("보드게임", "카탄", "뱅", "할리갈리", "보드"));
        CONCEPTS.put("게임", List.of("게임", "롤", "스팀"));
        CONCEPTS.put("카페공부", List.of("카페", "공부", "스터디", "독서", "책"));
        CONCEPTS.put("코딩", List.of("코딩", "개발", "프로그래밍"));
        CONCEPTS.put("산책", List.of("산책", "러닝", "운동", "걷기", "조깅"));
        CONCEPTS.put("식사", List.of("점심", "밥", "식사", "맛집"));
        CONCEPTS.put("영화", List.of("영화", "드라마", "시네마"));
        CONCEPTS.put("전시", List.of("전시", "미술", "사진", "스케치"));
        CONCEPTS.put("음악", List.of("음악", "노래", "밴드"));
        CONCEPTS.put("대화", List.of("수다", "언어교환", "대화", "토크"));
    }

    private InterestConcepts() {
    }

    /** 관심사 목록 → 개념 집합 (들어간 순서 유지) */
    static Set<String> concepts(Collection<String> interests) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : interests) {
            CONCEPTS.forEach((concept, keys) -> {
                if (keys.stream().anyMatch(s::contains)) {
                    out.add(concept);
                }
            });
        }
        return out;
    }

    /** 둘 다 가진 개념. a의 순서대로 */
    static List<String> shared(Collection<String> a, Collection<String> b) {
        Set<String> bs = concepts(b);
        return concepts(a).stream().filter(bs::contains).toList();
    }

    /** 자카드 유사도 0~1. 둘 다 개념이 없으면 0 */
    static double similarity(Collection<String> a, Collection<String> b) {
        Set<String> as = concepts(a);
        Set<String> bs = concepts(b);
        long inter = as.stream().filter(bs::contains).count();
        Set<String> union = new HashSet<>(as);
        union.addAll(bs);
        return union.isEmpty() ? 0 : (double) inter / union.size();
    }
}
