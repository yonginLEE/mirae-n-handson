package com.example.item;

import java.util.List;

/** 문항 검색 전용 저장소 조각. 구현은 {@link ItemSearchRepositoryImpl}. */
public interface ItemSearchRepository {

    /** 조건에 맞는 전체 건수. */
    long countBySearch(ItemSearchCondition condition);

    /** 조건에 맞는 현재 페이지 행. */
    List<ItemSearchRow> findPageBySearch(ItemSearchCondition condition);

    /** 단원 코드가 {@code unit} 테이블에 있는지. 비교는 DB 콜레이션을 따른다(대소문자 무시). */
    boolean existsUnitCode(String code);

    /** 등록된 태그 이름 전체(id 순). */
    List<String> findAllTagNames();
}
