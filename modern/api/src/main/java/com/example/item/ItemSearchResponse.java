package com.example.item;

import java.util.List;

/**
 * 문항 검색 응답. 앞의 네 필드는 동작 보존 테스트의 정규화 결과 {@code {status, rows, count, message}} 와 같은 모양이다.
 * 나머지는 레거시 화면이 그리던 페이저 · 정렬 표시 · 경고를 화면(프론트)이 그릴 수 있게 내려준다.
 *
 * @param status     HTTP 상태 코드와 같은 값
 * @param rows       현재 페이지 행
 * @param count      전체 건수 (BR-41)
 * @param message    0건이면 "검색 결과가 없습니다", 아니면 null (BR-36)
 * @param page       보정한 뒤의 현재 페이지 1 ~ 999 (BR-35)
 * @param pageSize   한 페이지 건수 (BR-34)
 * @param totalPages 전체 페이지 수 — {@code ceil(count / pageSize)}, 0건이면 0 (search.php:685)
 * @param sort       정렬 기준. 기본 정렬이면 null (BR-30, BR-31)
 * @param dir        정렬 방향. 없으면 null (BR-32)
 * @param warnings   입력값 경고. 없으면 빈 목록
 */
public record ItemSearchResponse(
    int status,
    List<ItemSearchRow> rows,
    long count,
    String message,
    int page,
    int pageSize,
    long totalPages,
    String sort,
    String dir,
    List<String> warnings) {

    static final String NO_RESULT_MESSAGE = "검색 결과가 없습니다";

    static ItemSearchResponse of(ItemSearchCondition condition, long count, List<ItemSearchRow> rows) {
        int pageSize = ItemSearchCondition.PAGE_SIZE;
        return new ItemSearchResponse(
            200, rows, count, count == 0 ? NO_RESULT_MESSAGE : null,
            condition.page(), pageSize, (count + pageSize - 1) / pageSize,
            condition.sort(), condition.dir(), condition.warnings());
    }
}
