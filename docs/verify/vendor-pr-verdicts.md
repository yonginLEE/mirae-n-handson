# 외주사 PR 판정 모음

검증 기준: `templates/approval-checklist.md` v1 (네 기준 모두 충족해야 승인, 미충족은 항목 번호로 표기). 요청 범위는 각 patch 머리말 `Subject:` 의 한 문장이다.

| PR | 판정 | 미충족 기준 | 근거(파일:줄번호) | 외주사에 돌려보낼 수정 요청 |
|---|---|---|---|---|
| pr-1 (`vendor-prs/pr-1-missing-tests.patch`, 요청: 단원별 문항 수 통계 API 추가, 리뷰 커밋 a374f70) | 반려 | 1-4 새 public 메서드·엔드포인트에 테스트 없음 | 새 로직 `UnitService.java:36-41` (`countActiveItemsByUnit`) 과 새 엔드포인트 `UnitController.java:26` 를 검증하는 테스트가 patch 에 없다. `UnitServiceTest.java` 의 `@Test` 는 25·38줄 둘뿐이고 둘 다 `listUnits` 용이다. 커밋 메시지는 "기존 테스트 전부 통과"만 주장한다. | `countActiveItemsByUnit()` 단위 테스트를 추가해 주세요: ① 단원 순서가 `listUnits()` 와 같음(학년, 코드 순) ② 문항 0개 단원이 0 으로 포함됨 ③ `ItemStatus.ACTIVE` 로만 센다(삭제 문항 제외) ④ 단원이 없으면 빈 목록. 가능하면 `GET /api/units/item-counts` 컨트롤러 테스트도 추가해 주세요. |
| pr-2 (`vendor-prs/pr-2-hardcoded-secret.patch`, 요청: 외부 채점 서버 연동 클라이언트 추가, 리뷰 커밋 80d1916) | 반려 | 2-1 자격 증명 리터럴(치명), 1-4 `requestGrading` 을 실행하는 테스트 없음, 1-5 오류 경로 테스트 없음 | **2-1:** `GradingServerClient.java:28-29` 에 채점 서버 계정(`username`)과 비밀번호(`password`)가 `private final String` 리터럴로 들어 있고, `:65-67` `basicAuth()` 가 이를 Basic 인증 헤더로 만든다. 저장소·빌드 산출물에 비밀번호가 남는다. 값은 이 문서에 옮기지 않는다. **1-4:** `GradingServerClientTest.java:16` 은 `buildRequest` 만 호출하고 public `requestGrading`(`GradingServerClient.java:38`)은 실행하지 않는다. **1-5:** 200 외 응답(`:42-44`) · `IOException`(`:47`) · `InterruptedException`(`:49`) 분기를 다루는 테스트가 없다. | 계정·비밀번호 리터럴을 코드에서 제거하고, `grading.base-url` 처럼 설정(`@Value("${grading.username}")` · `${grading.password}`) 또는 환경변수/시크릿 저장소에서 읽도록 바꿔 주세요. 기본값도 두지 마세요. 이미 이 값이 쓰이고 있다면 **노출된 것으로 보고 채점 서버 쪽 비밀번호를 교체**해 주세요(git 이력에 남음). 테스트는 주입한 가짜 값으로 `Authorization` 헤더를 검증하게 바꿔 주세요. 또 `requestGrading` 테스트를 추가해 주세요(`@DisplayName("메서드: 기대 동작")` 한국어 형식): 200 이면 본문 반환, 200 외 상태면 `IllegalStateException`, 연결 실패 시 `IllegalStateException`. |

## pr-1 확인 내용

- `./gradlew cleanTest test` 통과(실패 0건). 단, 새 코드를 실행하는 테스트는 없다.
- 새 API 를 18080 포트에서 직접 호출: `GET /api/units/item-counts` → 200. 단원 5개가 `GET /api/units` 와 같은 순서이고, 건수(6·5·5·5·5)가 DB 의 `status='A'` 집계와 일치한다. 삭제 문항은 제외된다.
- 나머지 기준은 충족: 요청 범위 이탈 없음(변경 3개 파일 모두 요청 문장으로 설명됨), 비밀값·SQL 이어붙임·임시 출력 없음, 컨트롤러는 서비스만 호출.
- 제안(승인·반려에 영향 없음): `UnitService.java:38-39` 가 단원마다 `count` 쿼리를 한 번씩 실행한다(단원 수만큼 쿼리). 단원이 늘면 `GROUP BY` 집계 쿼리 한 번으로 바꾸는 것을 검토.
- 참고: `POST /api/units/item-counts` 는 500 을 돌려주는데, 기존 `POST /api/units` 도 같은 500 이라 이 PR 이 만든 문제가 아니다(허용되지 않는 메서드 처리 방식이 원래 그렇다).

## pr-2 확인 내용

- `./gradlew cleanTest test` 통과(실패 0건). 새 테스트 `GradingServerClientTest` 1건도 통과했다. 다만 이 테스트는 요청 모양(POST · URI · 헤더 접두사 `Basic `)만 보고 비밀번호 처리는 검증하지 않는다.
- 런타임 표면 없음: `GradingServerClient` 를 호출하는 곳이 `modern/api/src/main` 에 없고 외부 채점 서버도 없어 앱에서 직접 실행해 보지 못했다. 근거는 소스 판독이다.
- 나머지 기준은 충족: 요청 범위 이탈 없음(변경 2개 파일 모두 요청 문장으로 설명됨), 의존성·스키마·설정 파일 변경 없음, 임시 출력 없음.
- 경고(반려 사유는 아님): `GradingServerClient.java:56` 이 `studentId` 를 JSON 문자열에 그대로 이어 붙인다. 따옴표·역슬래시가 든 값이면 본문이 깨지거나 필드가 주입된다. 지금은 `STU-<숫자>` 형식만 들어온다는 전제에 기대고 있으니 JSON 직렬화(Jackson)로 바꿀 것을 권한다.
- 제안: `:41` 응답 본문(점수)을 문자열로 그대로 돌려준다(`:18` 주석에 의도로 적힘). 호출부가 생길 때 숫자 타입으로 파싱·검증하는 것을 검토.
