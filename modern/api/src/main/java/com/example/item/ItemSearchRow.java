package com.example.item;

import java.util.List;

/**
 * 문항 검색 결과 한 행. 동작 보존 테스트의 정규화 결과(레거시 표의 열 id, title, unit, level, tags)와 같은 필드만 둔다.
 * {@code unit} 은 단원 코드, {@code tags} 는 태그 이름 목록(id 순).
 */
public record ItemSearchRow(
    Integer id,
    String title,
    String unit,
    Integer level,
    List<String> tags) {
}
