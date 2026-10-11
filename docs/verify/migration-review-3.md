# 검증: 문항 검색 이관 (`upstream/main...HEAD -- modern characterization`, 브랜치 review/pr-1-day2 @ 7232e15)

요청: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관. (엔드포인트는 명시되지 않았다. 커밋 21ca10c 와 `migration-review-1.md` 기준으로 `search.php` → `GET /api/items/search` 로 판단했다.)

**판정: 반려 (재판정은 아래 "재판정" 절 참고)** — 1-4 미충족, 1-6 · 1-7 미충족, characterization 은 실행했으나 레거시 서버가 꺼져 있어 비교하지 못했다. 3-2 · 3-3 도 미충족.

범위 밖 확인: `-- modern characterization` 으로 비교 범위를 한정했으므로 `.mcp.json` · `dbhub.toml` · `.claude/*` 등 범위 밖 파일은 이 판정에서 보지 않았다(3-4 는 이 범위 안에서만 판정).

## 기준별 결과

| 기준 | 결과 | 미충족 · 확인 불가 항목 |
|---|---|---|
| 테스트 통과 | **미충족** | 1-4, 1-6, 1-7 미충족. characterization 은 아래 "테스트 실행" 참고(통과 확인 못함). |
| 치명 이슈 0건 | 충족 | 2-1 · 2-2 · 2-3 해당 없음 |
| 요청 범위 이탈 없음 | **미충족** | 3-2, 3-3 |
| 컨벤션 준수 | 충족 (확인 필요 1건) | 4-8 이름 형식 확인 필요 |

## 테스트 실행

| 대상 | 명령 | 결과 |
|---|---|---|
| modern/api | `./gradlew cleanTest test` | BUILD SUCCESSFUL. 51건, 실패 0 · 오류 0 · skip 0 |
| characterization (레거시 대상) | `npm test` | **실패 39 / 통과 13 (52건)**. 실패 39건 전부 `TypeError: fetch failed` ← `ECONNREFUSED 127.0.0.1:8081`. 레거시 item-bank 서버(8081)가 꺼져 있다. 스냅샷 차이가 아니라 접속 실패다. 통과한 13건은 서버가 필요 없는 `normalize.unit.test.js` 로 보인다. |
| characterization (새 API 대상) | `TARGET_BASE_URL=http://localhost:8080 npm test` | **실행하지 않음.** 새 API(8080)도 꺼져 있다(`curl` 응답 없음). verify 절차에 따라 사람에게 기동을 요청한다. |

실행 후 `git status` 는 깨끗하다. 스냅샷과 테스트 파일은 건드리지 않았다.

→ characterization 은 "통과"라고 쓸 수 없다. 레거시(8081)와 새 API(8080)를 기동한 뒤 두 번 다시 돌려야 한다.

## 이슈 목록

| 심각도 | 파일:줄 | 근거 | 수정 방향 |
|---|---|---|---|
| 경고 (1-4) | `UnitController.java:26`, `UnitService.java:32` | 새 public 메서드 `countActiveItemsByUnit` 와 엔드포인트 `GET /api/units/item-counts` 를 실행하는 테스트가 없다(`src/test` 에서 `item-counts` · `countActiveItemsByUnit` 검색 결과 0건). `UnitServiceTest` 는 `listUnits` 만 다룬다. | `countActiveItemsByUnit()` 단위 테스트와 컨트롤러 테스트 추가. 테스트 파일은 tester 단계에서 추가. |
| 경고 (3-2 · 3-3) | `UnitController.java:25-29`, `UnitService.java:16-43`, `UnitItemCountResponse.java:1-9`, `ItemRepository.java:10` | `item-counts` 는 `search.php` 와 대응하지 않는 신규 엔드포인트다(요청은 엔드포인트 1개). `UnitService` 생성자와 `ItemRepository extends` 도 이 때문에 바뀌었다. 이 코드는 `7232e15 review: vendor pr-1 적용` 에서 들어온 외주 PR 이다. | 이관 판정에서 분리하거나(별도 브랜치 · revert) 이관 대상이라면 대응하는 레거시 위치를 밝힌다. 사람의 결정 필요. |
| 경고 | `UnitService.java:38-39` | 단원마다 `countByUnitIdAndStatus` 를 한 번씩 보낸다(반복문 안 쿼리, N+1). | `GROUP BY unit_id` 집계 쿼리 1회로 교체. 위 분리와 함께 처리. |
| 경고 (1-6 · 1-7) | `GET /api/items/search` | 앱을 띄워 직접 호출하지 않았고(8080 꺼짐), 호출하지 못한 사유를 이 문서에 적는 것 외에 별도 기록이 없다. 사유: 새 API · 레거시 서버가 기동되어 있지 않음. | 사람이 두 서버를 기동한 뒤 직접 호출 결과를 추가한다. |
| 확인 필요 | `characterization/README.md:48`, `tests/item-bank-warnings.test.js`, `__snapshots__/item-bank-warnings.test.js.snap` | `upstream/main` 대비 README 1줄 추가와 테스트 · 스냅샷 신규 추가가 있다(`item-bank.test.js` 와 스냅샷도 이 범위에서는 신규). 새 스냅샷이 레거시 실행 결과로 만들어졌는지 diff 만으로는 알 수 없다. CLAUDE.md 완료 기준 3번에 따라 판단하지 않고 묻는다. | 사람이 허용 여부 결정. 레거시 환경에서 재생성해 비교하는 방법이 있다(기존 파일은 수정하지 않음). |
| 확인 필요 (4-8) | `ItemControllerTest.java:70, 84, 113` | `@DisplayName` 이 "GET /api/items/search → 200, …" 형식이다. 기존 테스트(70줄)도 같은 형식이라 규칙 출처가 불분명하다. 위반으로 판정하지 않는다. | 팀 규칙 확인 후 결정. |
| 제안 | `ItemSearchService.java:37-39` | 태그 검증 때마다 `tag` 전체를 읽는다. `search.php:252-257` 과 같은 동작이라 이관 중에는 유지. | 이관 후 `EXISTS` 쿼리로 교체. |

## 치명 이슈 확인

- 비밀값 리터럴: `modern/api/src/main` 에서 찾지 못했다(`password` · `secret` · `token` · `apiKey` 검색).
- SQL: `ItemSearchRepositoryImpl.java:33-34` 의 `ORDER BY` · `LIMIT` · `OFFSET` 만 문자열 결합이다. `ORDER BY` 는 `ItemSearchCondition.java:49-57` 의 `ALLOWED_ORDER_BY` 허용 목록 밖이면 생성자에서 거부한다. `LIMIT` · `OFFSET` 은 정수다. 나머지는 파라미터 바인딩.
- 데이터 삭제 · 덮어쓰기: 없음. 조회는 `v_item_public` 뷰를 쓴다.

## 컨벤션 확인

4-1 컨트롤러는 서비스만 호출. 4-2 삼키는 `try/catch` 없음. 4-3 `ItemSearchService` · `UnitService` 에 `@Transactional(readOnly = true)`. 4-4 응답 DTO 는 `record`. 4-5 `LocalDateTime.now()` 없음. 4-6 `"A"` 리터럴 없음(`ItemStatus` 사용, 검색은 공개 뷰). 4-7 임시 출력 없음. 4-9 추가한 저장소 테스트 없음 → 예.

## 의심 동작 (고치지 않음)

없음. 이관 코드가 레거시 quirk(`%` `_` 비이스케이프, 빈 태그명, 잘못된 값도 400 대신 200)를 의도적으로 보존한다.

## 반려 해제 조건

1. `item-counts` 처리 방침 결정(분리 또는 이관 대상 확정) 후 테스트 보강.
2. 레거시(8081) · 새 API(8080) 기동 후 characterization `npm test` 두 대상 모두 전체 통과.
3. 새 엔드포인트 직접 호출 결과 기록(1-6).
4. `characterization/` 변경 허용 여부를 사람이 결정.

참고: `migration-review-1.md` 의 "승인" 은 21ca10c 시점(`item-counts` 추가 이전)의 판정이며 현재 HEAD 에는 적용되지 않는다.

---

# 재판정 (서버 기동 후, 이관 파일로 범위 한정)

레거시(8081, `docker compose --profile php up -d`)와 새 API(8080, `./gradlew bootRun`)를 기동하고 다시 실행했다. 사람 결정: `item-counts`(`Unit*`)는 이 브랜치에 두되 이관 판정에서 제외하고, 외주 PR(pr-1) 판정으로 따로 본다.

## 실행 결과

| 대상 | 명령 | 결과 |
|---|---|---|
| characterization (레거시) | `npm test` | **52/52 통과** (4 파일) |
| characterization (새 API) | `TARGET_BASE_URL=http://localhost:8080 npm test` | **51 통과 · 1 skip** (3 파일 통과 · 1 파일 skip). skip 은 `example-units.test.js:21` `it.skipIf(legacyOnly)` — 레거시 전용으로 설계된 케이스 |
| 직접 호출 (1-6) | `GET /api/items/search?q=분수&page=1` | 200, `{status, rows, ...}` 반환 |
| 직접 호출 | `GET /api/items/search` (조건 없음) / `?level=abc&page=xyz` | 둘 다 200 (레거시처럼 400 이 아님) |

실행 후 `git status` 에 `characterization/` 변경 없음(스냅샷 · 테스트 미수정).

## characterization 변경 건 (확인 필요 → 근거 확보)

레거시 서버에 대해 커밋된 스냅샷(`item-bank-warnings` 포함)이 52/52 통과한다. 즉 새 스냅샷은 레거시 실제 출력과 일치한다. 재생성은 필요 없었다. README 1줄은 설명 추가다.

## 범위별 판정

| 범위 | 판정 | 근거 |
|---|---|---|
| 검색 이관 (`ItemController` · `ItemSearch*` · `LegacyPhp` · characterization) | **승인** | 1-1~1-3 충족(51건 · 실패 0 · skip 0), 1-4 해당 파일 테스트 있음, 1-6 직접 호출 확인, 치명 0건, 레거시 52/52 · 새 API 51 통과, 컨벤션 충족 |
| `item-counts` (`Unit*` · `UnitItemCountResponse` · `ItemRepository` extends) | **반려 유지 (이관 판정 제외)** | 1-4 테스트 없음, N+1, 범위 이탈. 외주 PR 판정으로 별도 처리 |

남은 제안: 4-8 `@DisplayName` 형식 확인, `ItemSearchService.java:37-39` 태그 검증 `EXISTS` 전환(이관 후).
의심 동작: 없음.

주의: 이 브랜치를 한 PR 로 올리면 `Unit*` 때문에 전체가 반려된다. 이관 PR 에서는 `Unit*` 를 분리해야 한다.
