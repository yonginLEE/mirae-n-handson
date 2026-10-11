package com.example.item;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 검색 서비스 — 레거시 {@code search.php} 이관.
 * 없는 단원 · 범위 밖 난이도도 오류가 아니라 0건으로 응답한다(레거시와 같음).
 */
@Service
@Transactional(readOnly = true)
public class ItemSearchService {

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    private final ItemRepository itemRepository;

    public ItemSearchService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /** 요청 파라미터로 검색한다. 파라미터 이름은 레거시와 같다(q, unit, level, tag, sort, dir, page). */
    public ItemSearchResponse search(Map<String, List<String>> params) {
        ItemSearchCondition condition = ItemSearchCondition.from(
            params, itemRepository::existsUnitCode, this::tagExists);
        long count = itemRepository.countBySearch(condition);
        List<ItemSearchRow> rows = itemRepository.findPageBySearch(condition);
        log.debug("item search {} → {} rows of {}", condition, rows.size(), count);
        return ItemSearchResponse.of(condition, count, rows);
    }

    /** search.php:252-257 — 등록된 이름과 문자열 그대로(===) 비교한다. 빈 이름은 등록된 것으로 치지 않는다. */
    private boolean tagExists(String tag) {
        return itemRepository.findAllTagNames().stream().anyMatch(name -> !name.isEmpty() && name.equals(tag));
    }
}
