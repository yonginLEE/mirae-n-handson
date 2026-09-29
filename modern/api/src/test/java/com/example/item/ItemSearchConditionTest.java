package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** 검색 조건 다듬기 — 레거시 search.php 의 입력 처리와 같은지 본다. DB 없이 돈다. */
class ItemSearchConditionTest {

    /** 시드처럼 단원 M5-1(대소문자 무시) · 태그 계산 · 개념만 등록돼 있다고 본다. */
    private static final Predicate<String> UNIT_EXISTS = code -> code.equalsIgnoreCase("M5-1");
    private static final Predicate<String> TAG_EXISTS = name -> List.of("계산", "개념").contains(name);

    private static ItemSearchCondition from(Map<String, List<String>> params) {
        return ItemSearchCondition.from(params, UNIT_EXISTS, TAG_EXISTS);
    }

    private static ItemSearchCondition from(String... keyValues) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.add(keyValues[i], keyValues[i + 1]);
        }
        return from(params);
    }

    @Test
    @DisplayName("from: 파라미터가 없으면 난이도 5 제외 · 기본 정렬 · 1페이지")
    void fromEmptyUsesDefaults() {
        ItemSearchCondition c = from(Map.<String, List<String>>of());

        assertThat(c.keyword()).isNull();
        assertThat(c.unitCode()).isNull();
        assertThat(c.level()).isNull();
        assertThat(c.tag()).isNull();
        assertThat(c.orderBy()).isEqualTo("level DESC, id ASC");
        assertThat(c.page()).isEqualTo(1);
        assertThat(c.offset()).isZero();
        assertThat(c.sort()).isNull();
        assertThat(c.dir()).isNull();
        assertThat(c.warnings()).isEmpty();
    }

    @Test
    @DisplayName("from: 값이 전부 빈 문자열이면 파라미터가 없을 때와 같다")
    void fromBlankValuesUsesDefaults() {
        ItemSearchCondition c = from("q", "", "unit", "", "level", "", "tag", "", "sort", "", "dir", "", "page", "");

        assertThat(c).isEqualTo(from(Map.<String, List<String>>of()));
    }

    @Test
    @DisplayName("from: 같은 이름이 반복되면 마지막 값, q[] 배열 형식이면 첫 값")
    void fromDuplicateParams() {
        assertThat(from("level", "3", "level", "4").level()).isEqualTo(4L);
        assertThat(from(Map.of("level[]", List.of("3", "4"))).level()).isEqualTo(3L);
    }

    @Test
    @DisplayName("keyword: 앞뒤 공백을 지우고, 공백뿐이면 조건 없음")
    void keywordTrimsAndDropsBlank() {
        assertThat(ItemSearchCondition.keyword("  분수 ")).isEqualTo("분수");
        assertThat(ItemSearchCondition.keyword("  ")).isNull();
    }

    @Test
    @DisplayName("keyword: 100자는 그대로, 101자는 100자로 자른다(코드포인트 기준)")
    void keywordTruncatesAt100CodePoints() {
        assertThat(ItemSearchCondition.keyword("가".repeat(100))).hasSize(100);
        assertThat(ItemSearchCondition.keyword("가".repeat(101))).isEqualTo("가".repeat(100));
        String emoji = "😀".repeat(101);
        assertThat(LegacyPhp.mbLength(ItemSearchCondition.keyword(emoji))).isEqualTo(100);
    }

    @Test
    @DisplayName("keyword: 전각 공백은 PHP trim 처럼 지우지 않는다")
    void keywordKeepsIdeographicSpace() {
        assertThat(ItemSearchCondition.keyword("　분수")).isEqualTo("　분수");
    }

    @Test
    @DisplayName("unitCode: trim 만 하고 소문자 · 형식 오류도 입력값 그대로")
    void unitCodeKeepsInput() {
        assertThat(ItemSearchCondition.unitCode(" m5-1 ")).isEqualTo("m5-1");
        assertThat(ItemSearchCondition.unitCode("X9-9")).isEqualTo("X9-9");
        assertThat(ItemSearchCondition.unitCode(" ")).isNull();
    }

    @Test
    @DisplayName("level: 비면 null(level < 5), 그 밖은 PHP (int) 변환 값")
    void levelFollowsPhpIntCast() {
        assertThat(ItemSearchCondition.level("")).isNull();
        assertThat(ItemSearchCondition.level("3")).isEqualTo(3L);
        assertThat(ItemSearchCondition.level("5")).isEqualTo(5L);
        assertThat(ItemSearchCondition.level("-1")).isEqualTo(-1L);
        assertThat(ItemSearchCondition.level("5abc")).isEqualTo(5L);
        assertThat(ItemSearchCondition.level("3.9")).isEqualTo(3L);
        assertThat(ItemSearchCondition.level("1e1")).isEqualTo(10L);
        assertThat(ItemSearchCondition.level("abc")).isEqualTo(0L);
        assertThat(ItemSearchCondition.level("99999999999999999999")).isEqualTo(Long.MAX_VALUE);
        assertThat(ItemSearchCondition.level("1e1000")).isEqualTo(0L);
    }

    @Test
    @DisplayName("tag: trim 후 50자로 자른다")
    void tagTruncatesAt50() {
        assertThat(ItemSearchCondition.tag(" 계산 ")).isEqualTo("계산");
        assertThat(ItemSearchCondition.tag("가".repeat(51))).isEqualTo("가".repeat(50));
        assertThat(ItemSearchCondition.tag("")).isNull();
    }

    @Test
    @DisplayName("orderBy: 레거시 switch 와 같은 ORDER BY 를 만든다")
    void orderByMatchesLegacy() {
        assertThat(ItemSearchCondition.orderBy("", "")).isEqualTo("level DESC, id ASC");
        assertThat(ItemSearchCondition.orderBy("nope", "asc")).isEqualTo("level DESC, id ASC");
        assertThat(ItemSearchCondition.orderBy("id", "desc")).isEqualTo("id DESC");
        assertThat(ItemSearchCondition.orderBy("ID", "x")).isEqualTo("id ASC");
        assertThat(ItemSearchCondition.orderBy("title", "desc")).isEqualTo("title DESC, id ASC");
        assertThat(ItemSearchCondition.orderBy("unit", "desc")).isEqualTo("unit_code DESC, level DESC, id ASC");
        assertThat(ItemSearchCondition.orderBy("level", "")).isEqualTo("level DESC, id ASC");
        assertThat(ItemSearchCondition.orderBy("level", "asc")).isEqualTo("level ASC, id ASC");
        assertThat(ItemSearchCondition.orderBy("created", "")).isEqualTo("created_at DESC, id ASC");
    }

    @Test
    @DisplayName("page: 숫자 아니면 1, 0 이하면 1, 999 초과면 999, 끝의 줄바꿈 하나는 허용")
    void pageClamps() {
        assertThat(ItemSearchCondition.page("abc")).isEqualTo(1);
        assertThat(ItemSearchCondition.page("0")).isEqualTo(1);
        assertThat(ItemSearchCondition.page("999")).isEqualTo(999);
        assertThat(ItemSearchCondition.page("1000")).isEqualTo(999);
        assertThat(ItemSearchCondition.page("99999999999999999999")).isEqualTo(999);
        assertThat(ItemSearchCondition.page("5\n")).isEqualTo(5);
        assertThat(ItemSearchCondition.page(" 5")).isEqualTo(1);
        assertThat(ItemSearchCondition.page("-3")).isEqualTo(1);
    }

    @Test
    @DisplayName("sort · dir: 표시용 값 — 알 수 없는 기준은 null, 방향이 비면 기준별 기본 방향")
    void displaySortAndDir() {
        assertThat(from("sort", " LEVEL ")).extracting("sort", "dir").containsExactly("level", "desc");
        assertThat(from("sort", "created")).extracting("sort", "dir").containsExactly("created", "desc");
        assertThat(from("sort", "title", "dir", "x")).extracting("sort", "dir").containsExactly("title", "asc");
        assertThat(from("sort", "unit", "dir", "DESC")).extracting("sort", "dir").containsExactly("unit", "desc");
        assertThat(from("sort", "nope", "dir", "desc")).extracting("sort", "dir").containsExactly(null, "desc");
        assertThat(from("dir", "asc")).extracting("sort", "dir").containsExactly(null, "asc");
    }

    @Test
    @DisplayName("warnings: 키워드 — 100자 초과 · 한 글자 · 와일드카드, 공백뿐이면 없음")
    void keywordWarnings() {
        assertThat(from("q", "가".repeat(101)).warnings()).containsExactly("키워드가 너무 길어 100자까지만 사용했습니다.");
        assertThat(from("q", "%").warnings())
            .containsExactly("키워드가 한 글자라 결과가 많을 수 있습니다.", "키워드의 % 와 _ 는 와일드카드로 처리됩니다.");
        assertThat(from("q", "분_수").warnings()).containsExactly("키워드의 % 와 _ 는 와일드카드로 처리됩니다.");
        assertThat(from("q", "  ").warnings()).isEmpty();
        assertThat(from("q", "분수").warnings()).isEmpty();
    }

    @Test
    @DisplayName("warnings: 단원 — 형식 · 소문자 · 미등록 순서, 소문자는 등록된 코드로 친다")
    void unitWarnings() {
        assertThat(from("unit", "abc").warnings()).containsExactly(
            "단원 코드 형식이 올바르지 않습니다. (예: M5-1)",
            "단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)",
            "등록되지 않은 단원 코드입니다: abc");
        assertThat(from("unit", "m5-1").warnings()).containsExactly("단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)");
        assertThat(from("unit", "X9-9").warnings()).containsExactly("등록되지 않은 단원 코드입니다: X9-9");
        assertThat(from("unit", "M5-1").warnings()).isEmpty();
    }

    @Test
    @DisplayName("warnings: 난이도 — 1~5 한 자리가 아니면 경고, 비면 없음")
    void levelWarnings() {
        assertThat(from("level", "5abc").warnings()).containsExactly("난이도는 1~5 사이여야 합니다.");
        assertThat(from("level", "0").warnings()).containsExactly("난이도는 1~5 사이여야 합니다.");
        assertThat(from("level", " 3 ").warnings()).isEmpty();
        assertThat(from("level", "").warnings()).isEmpty();
    }

    @Test
    @DisplayName("warnings: 태그 — 부분 일치 · 50자 초과 · 미등록(대소문자 구분, 자른 뒤 이름으로)")
    void tagWarnings() {
        assertThat(from("tag", "계%").warnings())
            .containsExactly("태그는 부분 일치를 지원하지 않습니다.", "등록되지 않은 태그입니다: 계%");
        assertThat(from("tag", "가".repeat(51)).warnings())
            .containsExactly("태그 이름이 너무 길어 50자까지만 사용했습니다.", "등록되지 않은 태그입니다: " + "가".repeat(50));
        assertThat(from("tag", " 계산 ").warnings()).isEmpty();
    }

    @Test
    @DisplayName("warnings: 정렬 — 방향 경고가 기준 경고보다 먼저")
    void sortWarnings() {
        assertThat(from("sort", "nope", "dir", "up").warnings()).containsExactly(
            "정렬 방향은 asc 또는 desc 만 가능합니다.", "알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.");
        assertThat(from("sort", "ID", "dir", "DESC").warnings()).isEmpty();
    }

    @Test
    @DisplayName("warnings: 페이지 — 숫자 아님(빈 값 · 1 제외) · 999 초과")
    void pageWarnings() {
        assertThat(from("page", "abc").warnings()).containsExactly("페이지 번호가 올바르지 않아 1페이지를 표시합니다.");
        assertThat(from("page", "1000").warnings()).containsExactly("페이지 번호는 999 를 넘을 수 없습니다.");
        assertThat(from("page", "").warnings()).isEmpty();
        assertThat(from("page", "0").warnings()).isEmpty();
        assertThat(from("page", "999").warnings()).isEmpty();
    }

    @Test
    @DisplayName("warnings: 여러 조건이 한꺼번에 틀리면 레거시 순서(키워드 → 단원 → 난이도 → 태그 → 정렬 → 페이지)")
    void warningsKeepLegacyOrder() {
        assertThat(from("page", "z", "dir", "y", "sort", "x", "tag", "%", "level", "9", "unit", "X9-9", "q", "수")
            .warnings()).containsExactly(
                "키워드가 한 글자라 결과가 많을 수 있습니다.",
                "등록되지 않은 단원 코드입니다: X9-9",
                "난이도는 1~5 사이여야 합니다.",
                "태그는 부분 일치를 지원하지 않습니다.",
                "등록되지 않은 태그입니다: %",
                "정렬 방향은 asc 또는 desc 만 가능합니다.",
                "알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.",
                "페이지 번호가 올바르지 않아 1페이지를 표시합니다.");
    }
}
