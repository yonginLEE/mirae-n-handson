package com.example.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 문항 검색 조건. 레거시 {@code search.php} 의 {@code buildSearchQuery()} 가 파라미터를 다듬는 방식을 그대로 옮겼다.
 * 규칙 ID 는 docs/item-bank/BUSINESS-RULES.md 를 따른다. 버그로 보이는 동작도 이관 중에는 고치지 않는다.
 *
 * @param keyword   제목 · 지문 LIKE 키워드. 없으면 null (BR-03, BR-04, BR-40)
 * @param unitCode  단원 코드. 없으면 null (BR-07)
 * @param level     {@code level = ?} 로 비교할 값. null 이면 {@code level < 5} (BR-09 ~ BR-11)
 * @param tag       태그 이름. 없으면 null (BR-12, BR-13)
 * @param orderBy   ORDER BY 절 (BR-29 ~ BR-32)
 * @param page      1 ~ 999 (BR-35)
 * @param sort      화면 표시용 정렬 기준. 비었거나 알 수 없는 값이면 null (BR-30, BR-31)
 * @param dir       화면 표시용 정렬 방향. 정렬 기준이 있고 방향이 비면 기준별 기본 방향, 그 밖에 비면 null (BR-32)
 * @param warnings  입력값 경고. 레거시 {@code search.php} 와 같은 문구 · 같은 순서
 */
public record ItemSearchCondition(
    String keyword,
    String unitCode,
    Long level,
    String tag,
    String orderBy,
    int page,
    String sort,
    String dir,
    List<String> warnings) {

    /** 한 페이지 건수 (BR-34). */
    public static final int PAGE_SIZE = 20;

    static final int KEYWORD_MAX_LENGTH = 100;
    static final int TAG_MAX_LENGTH = 50;
    static final int MAX_PAGE = 999;
    static final String DEFAULT_ORDER_BY = "level DESC, id ASC";

    private static final Pattern PAGE_PATTERN = LegacyPhp.pattern("^[0-9]+$");
    private static final Pattern UNIT_CODE_PATTERN = LegacyPhp.pattern("^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$");
    private static final Pattern LEVEL_PATTERN = LegacyPhp.pattern("^[1-5]$");
    private static final List<String> SORT_KEYS = List.of("id", "title", "unit", "level", "created");

    /** OFFSET 값. */
    public int offset() {
        return (page - 1) * PAGE_SIZE;
    }

    /**
     * 요청 파라미터 → 검색 조건.
     * 같은 이름이 여러 번 오면 PHP 처럼 마지막 값을 쓰고, {@code q[]} 같은 배열 형식만 오면 첫 값을 쓴다 (BR-02).
     *
     * @param unitExists 단원 코드가 {@code unit} 테이블에 있는지 (SQL 비교 — 대소문자 무시, BR-08)
     * @param tagExists  태그 이름이 등록돼 있는지 (문자열 그대로 비교 — 대소문자 구분, BR-14)
     */
    public static ItemSearchCondition from(
            Map<String, List<String>> params, Predicate<String> unitExists, Predicate<String> tagExists) {
        String q = param(params, "q", "");
        String unit = param(params, "unit", "");
        String level = param(params, "level", "");
        String tag = param(params, "tag", "");
        String sort = param(params, "sort", "");
        String dir = param(params, "dir", "");
        String pageRaw = param(params, "page", "1");

        return new ItemSearchCondition(
            keyword(q), unitCode(unit), level(level), tag(tag), orderBy(sort, dir), page(pageRaw),
            displaySort(sort), displayDir(sort, dir),
            warnings(q, unit, level, tag, sort, dir, pageRaw, unitExists, tagExists));
    }

    private static String param(Map<String, List<String>> params, String name, String absent) {
        List<String> plain = params.get(name);
        if (plain != null && !plain.isEmpty()) {
            return nullToEmpty(plain.get(plain.size() - 1));
        }
        List<String> array = params.get(name + "[]");
        if (array != null && !array.isEmpty()) {
            return nullToEmpty(array.get(0));
        }
        return absent;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** BR-03 · BR-04 · BR-40: trim → 100자 자르기 → 비었으면 조건 없음. 한 글자 · % _ 는 경고만이라 그대로 (BR-05, BR-06). */
    static String keyword(String raw) {
        String q = LegacyPhp.trim(raw);
        if (LegacyPhp.mbLength(q) > KEYWORD_MAX_LENGTH) {
            q = LegacyPhp.mbHead(q, KEYWORD_MAX_LENGTH);
        }
        return q.isEmpty() ? null : q;
    }

    /** BR-07 · BR-08: trim 만 한다. 형식 · 대소문자 · 없는 코드는 경고만이라 입력값 그대로 조회한다. */
    static String unitCode(String raw) {
        String unit = LegacyPhp.trim(raw);
        return unit.isEmpty() ? null : unit;
    }

    /**
     * BR-09 ~ BR-11.
     * 비었으면 null({@code level < 5}), 그 밖은 PHP {@code (int)} 로 바꾼 값(범위 밖 분기에는 {@code < 5} 가 없다).
     */
    static Long level(String raw) {
        String level = LegacyPhp.trim(raw);
        if (level.isEmpty()) {
            return null;
        }
        // 1~5 분기(:217)와 범위 밖 분기(:223)는 경고 유무만 다르고 SQL 은 둘 다 level = (int)$level 이다.
        return LegacyPhp.intval(level);
    }

    /** BR-12 · BR-13: trim → 50자 자르기. % _ · 등록 안 된 태그는 경고만이라 그대로 (BR-14). */
    static String tag(String raw) {
        String tag = LegacyPhp.trim(raw);
        if (tag.isEmpty()) {
            return null;
        }
        if (LegacyPhp.mbLength(tag) > TAG_MAX_LENGTH) {
            tag = LegacyPhp.mbHead(tag, TAG_MAX_LENGTH);
        }
        return tag;
    }

    /** BR-29 ~ BR-32: {@code search.php:266-327} 의 switch 를 그대로 옮겼다. */
    static String orderBy(String rawSort, String rawDir) {
        String sort = LegacyPhp.asciiLower(LegacyPhp.trim(rawSort));
        String dir = LegacyPhp.asciiLower(LegacyPhp.trim(rawDir));
        if (!dir.equals("asc") && !dir.equals("desc")) {
            dir = "";
        }
        return switch (sort) {
            case "id" -> dir.equals("desc") ? "id DESC" : "id ASC";
            case "title" -> dir.equals("desc") ? "title DESC, id ASC" : "title ASC, id ASC";
            case "unit" -> dir.equals("desc") ? "unit_code DESC, level DESC, id ASC" : "unit_code ASC, level DESC, id ASC";
            case "level" -> dir.equals("asc") ? "level ASC, id ASC" : "level DESC, id ASC";
            case "created" -> dir.equals("asc") ? "created_at ASC, id ASC" : "created_at DESC, id ASC";
            default -> DEFAULT_ORDER_BY;
        };
    }

    /** BR-35: 숫자가 아니면 1, 1 미만이면 1, 999 초과면 999. page 는 trim 하지 않는다. */
    static int page(String raw) {
        long page = 1;
        if (LegacyPhp.pregMatch(PAGE_PATTERN, raw)) {
            page = LegacyPhp.intval(raw);
        }
        if (page < 1) {
            page = 1;
        }
        if (page > MAX_PAGE) {
            page = MAX_PAGE;
        }
        return (int) page;
    }

    /** BR-30 · BR-31: 소문자로 바꾼 정렬 기준. 비었거나 알 수 없으면 null ({@code search.php:266}, {@code :324}). */
    static String displaySort(String rawSort) {
        String sort = LegacyPhp.asciiLower(LegacyPhp.trim(rawSort));
        return SORT_KEYS.contains(sort) ? sort : null;
    }

    /** BR-32: 화면 표시용 방향 ({@code search.php:267-273}, {@code :328-331}). */
    static String displayDir(String rawSort, String rawDir) {
        String sort = displaySort(rawSort);
        String dir = LegacyPhp.asciiLower(LegacyPhp.trim(rawDir));
        if (!dir.equals("asc") && !dir.equals("desc")) {
            dir = "";
        }
        if (sort != null && dir.isEmpty()) {
            dir = (sort.equals("level") || sort.equals("created")) ? "desc" : "asc";
        }
        return dir.isEmpty() ? null : dir;
    }

    /**
     * 입력값 경고 — {@code search.php} 의 {@code $warnings[]} 를 같은 문구 · 같은 순서로 옮겼다.
     * 경고는 조회에 영향을 주지 않는다 (BR-04 ~ BR-08, BR-11, BR-13, BR-14, BR-30, BR-32, BR-35).
     */
    static List<String> warnings(
            String rawQ, String rawUnit, String rawLevel, String rawTag, String rawSort, String rawDir,
            String pageRaw, Predicate<String> unitExists, Predicate<String> tagExists) {
        List<String> warnings = new ArrayList<>();

        // 키워드 (:85-96)
        String q = LegacyPhp.trim(rawQ);
        if (LegacyPhp.mbLength(q) > KEYWORD_MAX_LENGTH) {
            q = LegacyPhp.mbHead(q, KEYWORD_MAX_LENGTH);
            warnings.add("키워드가 너무 길어 100자까지만 사용했습니다.");
        }
        if (!q.isEmpty()) {
            if (LegacyPhp.mbLength(q) == 1) {
                warnings.add("키워드가 한 글자라 결과가 많을 수 있습니다.");
            }
            if (q.contains("%") || q.contains("_")) {
                warnings.add("키워드의 % 와 _ 는 와일드카드로 처리됩니다.");
            }
        }

        // 단원 (:109-146)
        String unit = LegacyPhp.trim(rawUnit);
        if (!unit.isEmpty()) {
            if (!LegacyPhp.pregMatch(UNIT_CODE_PATTERN, unit)) {
                warnings.add("단원 코드 형식이 올바르지 않습니다. (예: M5-1)");
            }
            if (!unit.equals(LegacyPhp.asciiUpper(unit))) {
                warnings.add("단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)");
            }
            if (!unitExists.test(unit)) {
                warnings.add("등록되지 않은 단원 코드입니다: " + unit);
            }
        }

        // 난이도 (:211-230)
        String level = LegacyPhp.trim(rawLevel);
        if (!level.isEmpty() && !LegacyPhp.pregMatch(LEVEL_PATTERN, level)) {
            warnings.add("난이도는 1~5 사이여야 합니다.");
        }

        // 태그 (:235-261)
        String tag = LegacyPhp.trim(rawTag);
        if (!tag.isEmpty()) {
            if (tag.contains("%") || tag.contains("_")) {
                warnings.add("태그는 부분 일치를 지원하지 않습니다.");
            }
            if (LegacyPhp.mbLength(tag) > TAG_MAX_LENGTH) {
                tag = LegacyPhp.mbHead(tag, TAG_MAX_LENGTH);
                warnings.add("태그 이름이 너무 길어 50자까지만 사용했습니다.");
            }
            if (!tagExists.test(tag)) {
                warnings.add("등록되지 않은 태그입니다: " + tag);
            }
        }

        // 정렬 (:266-327)
        String sort = LegacyPhp.asciiLower(LegacyPhp.trim(rawSort));
        String dir = LegacyPhp.asciiLower(LegacyPhp.trim(rawDir));
        if (!dir.equals("asc") && !dir.equals("desc") && !dir.isEmpty()) {
            warnings.add("정렬 방향은 asc 또는 desc 만 가능합니다.");
        }
        if (!sort.isEmpty() && !SORT_KEYS.contains(sort)) {
            warnings.add("알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.");
        }

        // 페이지 (:336-348)
        long page = 1;
        if (LegacyPhp.pregMatch(PAGE_PATTERN, pageRaw)) {
            page = LegacyPhp.intval(pageRaw);
        } else if (!pageRaw.isEmpty() && !pageRaw.equals("1")) {
            warnings.add("페이지 번호가 올바르지 않아 1페이지를 표시합니다.");
        }
        if (page > MAX_PAGE) {
            warnings.add("페이지 번호는 999 를 넘을 수 없습니다.");
        }

        return List.copyOf(warnings);
    }
}
