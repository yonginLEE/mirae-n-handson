# 검증: 문항 검색 이관 (`upstream/main...HEAD`, 브랜치 day1 @ 21ca10c)

> 이 파일은 다른 세션이 yi-agent 저장소를 잘못 열어 `BLOCKED` 로 끝난 리뷰를 대체한다. mirae-n-handson 에서 verify Skill 절차대로 다시 했다.

요청: 레거시 `search.php` 를 동작 변경 없이 `GET /api/items/search` 로 이관(커밋 메시지 기준).

**판정: 승인** — 경고 1건 수정 후 단위 테스트 51건, characterization(레거시 52/52 · 새 API 51 통과 + 1 skip) 통과.

| 기준 | 결과 |
|---|---|
| 테스트 통과 | 충족 — `./gradlew cleanTest test` 51건(실패 0·skip 0). characterization 레거시 52/52, 새 API 51 통과·1 skip(`example-units.test.js:21` `skipIf(legacyOnly)` — 레거시 전용 케이스로 설계된 skip) |
| 치명 이슈 0건 | 충족 (0건) |
| 요청 범위 이탈 없음 | 대체로 충족 — 아래 제안 1 |
| 컨벤션 준수 | 충족 — 컨트롤러는 서비스만 호출, 임시 출력 없음(`log.debug` 만) |

## 이슈 목록

| 심각도 | 파일:줄 | 근거 | 수정 방향 |
|---|---|---|---|
| 경고(수정함) | `ItemSearchRepositoryImpl.java:33` / `ItemSearchCondition.java:23-32` | `ORDER BY` 를 `condition.orderBy()` 문자열 그대로 SQL 에 이어 붙인다. 지금은 `orderBy()`(:132-146)가 고정 문자열만 돌려줘 안전하지만, `ItemSearchCondition` 은 public record 라 누구나 임의의 `orderBy` 로 만들 수 있다. 다음 변경에서 SQL 주입 경로가 된다. | → 적용: `ItemSearchCondition` 의 compact constructor 가 허용 정렬 절 10개(`ALLOWED_ORDER_BY`) 밖의 값을 거부한다. |
| 제안 | 브랜치 전체 | `CLAUDE_off.md`(97줄, 비활성 사본)와 `.claude/skills/document-module/` 은 검색 이관과 직접 관련이 없다. | 이관 PR 에서 분리하는 것을 검토. |
| 제안 | `ItemSearchService.java:37-38` | 태그가 있을 때마다 `tag` 전체를 읽어 비교한다(`search.php:252-257` 과 같은 동작이라 이관 중에는 유지). | 이관 후 `EXISTS` 쿼리로 교체. |

확인 필요(줄번호 근거 없음): 없음.

## 남은 일

없음. 제안 2건은 목록으로만 남긴다.
