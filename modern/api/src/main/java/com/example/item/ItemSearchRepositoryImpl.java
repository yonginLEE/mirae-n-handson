package com.example.item;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 문항 검색 SQL. 레거시 {@code search.php:44-526} 과 같은 SQL 을 공개 문항 뷰 {@code v_item_public} 에 네이티브로 실행한다.
 * 대소문자 무시 비교(utf8mb4_unicode_ci), LIKE 의 와일드카드 · 역슬래시 처리, 태그 순서가 레거시와 같아지도록
 * JPQL 로 바꾸지 않고 MariaDB 에서 그대로 비교한다.
 */
class ItemSearchRepositoryImpl implements ItemSearchRepository {

    private static final String FROM = " FROM v_item_public";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public long countBySearch(ItemSearchCondition condition) {
        // BR-41: 목록과 같은 FROM + WHERE 로 센다.
        Query query = entityManager.createNativeQuery("SELECT COUNT(*) AS cnt" + FROM + where(condition));
        bind(query, condition);
        return ((Number) query.getSingleResult()).longValue();
    }

    @Override
    public List<ItemSearchRow> findPageBySearch(ItemSearchCondition condition) {
        String sql = "SELECT id, title, unit_code, level, tag_names" + FROM + where(condition)
            + " ORDER BY " + condition.orderBy()
            + " LIMIT " + ItemSearchCondition.PAGE_SIZE + " OFFSET " + condition.offset();
        Query query = entityManager.createNativeQuery(sql);
        bind(query, condition);
        @SuppressWarnings("unchecked")
        List<Object[]> result = query.getResultList();
        return result.stream().map(ItemSearchRepositoryImpl::toRow).toList();
    }

    @Override
    public boolean existsUnitCode(String code) {
        // search.php:125 와 같은 비교. 경고(BR-08)에만 쓴다.
        Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM unit WHERE code = :code");
        query.setParameter("code", code);
        return ((Number) query.getSingleResult()).longValue() > 0;
    }

    @Override
    public List<String> findAllTagNames() {
        // search.php:178 과 같은 조회. 경고(BR-14)에만 쓴다.
        @SuppressWarnings("unchecked")
        List<Object> result = entityManager.createNativeQuery("SELECT name FROM tag ORDER BY id ASC").getResultList();
        return result.stream().map(ItemSearchRepositoryImpl::asString).toList();
    }

    /** BR-01: 조건은 모두 AND. */
    private static String where(ItemSearchCondition c) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (c.keyword() != null) {
            // BR-03 · BR-06: % _ 는 이스케이프하지 않는다.
            where.append(" AND (title LIKE :likeKeyword OR stem LIKE :likeKeyword)");
        }
        if (c.unitCode() != null) {
            where.append(" AND unit_code = :unitCode");
        }
        if (c.level() == null) {
            // BR-09: 난이도가 비면 5 를 뺀다.
            where.append(" AND level < 5");
        } else {
            where.append(" AND level = :level");
        }
        if (c.tag() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id")
                .append(" WHERE it.item_id = v_item_public.id AND t.name = :tag)");
        }
        return where.toString();
    }

    private static void bind(Query query, ItemSearchCondition c) {
        if (c.keyword() != null) {
            query.setParameter("likeKeyword", "%" + c.keyword() + "%");
        }
        if (c.unitCode() != null) {
            query.setParameter("unitCode", c.unitCode());
        }
        if (c.level() != null) {
            query.setParameter("level", c.level());
        }
        if (c.tag() != null) {
            query.setParameter("tag", c.tag());
        }
    }

    private static ItemSearchRow toRow(Object[] r) {
        return new ItemSearchRow(
            ((Number) r[0]).intValue(),
            asString(r[1]),
            asString(r[2]),
            ((Number) r[3]).intValue(),
            splitTags(asString(r[4])));
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value.toString();
    }

    /** 뷰의 tag_names(쉼표 구분, 태그 id 순 — BR-39) → 목록. 비었거나 NULL 이면 빈 목록. */
    static List<String> splitTags(String tagNames) {
        if (tagNames == null) {
            return List.of();
        }
        return Arrays.stream(tagNames.split(","))
            .map(String::trim)
            .filter(t -> !t.isEmpty())
            .toList();
    }
}
