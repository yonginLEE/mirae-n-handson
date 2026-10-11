# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 저장소 성격

Claude Code 심화 과정 실습 저장소. 더미 에듀테크 도메인(문항 은행 · 과제 배포 · 성적 집계)으로 **레거시 → 현행 이관**을 연습한다. 실제 서비스 · 실명 데이터는 없다.

- Claude Code 는 항상 저장소 루트에서 실행한다. `.claude/`, `hooks/`, `docs/` 도 루트에 만든다.
- README 의 "이 저장소에 넣지 않은 것" 표에 있는 파일(예: `/api/items/search` 엔드포인트, `characterization/tests/<모듈>.test.js`, `hooks/*.mjs`, `.mcp.json`)은 실습 중에 만든다. 요청받기 전에 미리 만들지 않는다.
- 도메인 용어: 문항 `item` · 단원 `unit`(코드 예: `M5-1`) · 난이도 `level`(1~5) · 태그 `tag` / 학급 `class` · 과제 `assignment` · 배포 `distribution` · 제출 `submission` / 학생 식별자 `STU-<숫자>`.

## 모듈과 실행 방식

| 모듈 | 위치 | 스택 | 주소 | 실행 |
|---|---|---|---|---|
| 문항 은행 (레거시) | `legacy/item-bank-php/` | PHP 7.4 + mysqli | 8081 | `docker compose --profile php up -d` |
| 과제 배포 (레거시) | `legacy/assignment-thymeleaf/` | Spring MVC + Thymeleaf + JDBC | 8082 | `--profile thymeleaf` |
| 성적 집계 (레거시) | `legacy/grade-mssql/` | MS-SQL 저장 프로시저(`sql/*.sql`) + 얇은 Java 호출부 | 8083 | `--profile mssql` (Apple Silicon 에서는 느리거나 안 뜸) |
| 현행 API | `modern/api/` | Spring Boot 3.3 · Java 21 · JPA | 8080 | 로컬 `./gradlew bootRun` (DB 는 `--profile modern`) |
| 현행 화면 | `modern/web/` | React 18 · TS · Vite | 5173 | 로컬 `npm run dev` |

- `php` · `thymeleaf` · `modern` 프로필은 MariaDB 서비스 하나(`itembank` DB, 3306)를 같이 쓴다. 스키마 · 시드는 `db/mariadb/init/*.sql`.
- DB 데이터는 tmpfs 에 있다. `docker compose --profile <p> down && ... up -d` 로 시드 상태(고정 ID · 고정 시각)로 되돌린다.
- `down` · `build` · `logs` 에도 반드시 `--profile` 을 붙인다. 붙이지 않으면 해당 서비스가 잡히지 않는다.
- 조회용 DB 계정은 읽기 전용이다. MariaDB `readonly`/`readonly-pass`, MS-SQL `readonly`/`Readonly-pass1`. 쓰기 계정(`app`, `sa`)은 compose 안에서만 쓴다.
- 환경 점검은 `bash scripts/check-env.sh`, 서비스가 떴는지는 `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081` 로 본다. 200 또는 302 면 떠 있고 000 이면 꺼져 있다.

## 명령

```bash
# modern/api — 테스트는 H2(MariaDB 모드) 인메모리라 DB 컨테이너 없이 통과해야 한다
cd modern/api && ./gradlew test
cd modern/api && ./gradlew test --tests com.example.item.ItemServiceTest            # 단일 클래스
cd modern/api && ./gradlew test --tests 'com.example.item.ItemServiceTest.getItem*' # 단일 메서드
cd modern/api && ./gradlew bootRun     # "Tomcat started on port 8080", modern 프로필 DB 필요

# modern/web
cd modern/web && npm ci                # npm install 이 아니라 npm ci (lock 파일을 바꾸지 않게)
cd modern/web && npm run lint && npm run typecheck && npm test
cd modern/web && npx vitest run src/components/ItemTable.test.tsx   # 단일 파일

# characterization (동작 보존 테스트)
cd characterization && npm ci
cd characterization && npm run baseline -- item-bank     # 모듈명: item-bank | assignment | grade
cd characterization && npm test                          # 대상: 레거시 기본 포트
cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test   # 대상: 새 API
cd characterization && npx vitest run tests/normalize.unit.test.js      # 서비스 없이 도는 단위 테스트

# mcp-skeleton
cd mcp-skeleton && npm ci && npx tsc   # build/index.js 생성
```

## 아키텍처 — 여러 파일을 봐야 보이는 것

### modern/api (팀 컨벤션의 기준)
- 패키지는 기능별로 나눈다(`item`, `assignment`, `common`, `config`). 한 패키지 안에 Entity · Repository · Service · Controller · `*Response` DTO(record)가 함께 있다.
- 흐름은 Controller → Service → Repository 이다. 컨트롤러는 서비스만 부른다. 예외는 try/catch 로 삼키지 않고 던진다. `common/GlobalExceptionHandler` 가 HTTP 상태로 바꾼다.
  - `NotFoundException` → 404, 검증 · 타입 오류와 `IllegalArgumentException` → 400, `IllegalStateException` → 409(예: 마감된 과제 재배포).
  - 응답 본문은 `ErrorResponse` 이다.
- `open-in-view: false` 이다. 서비스는 `@Transactional(readOnly = true)` 안에서 엔티티를 DTO 로 바꿔 돌려준다. 연관은 Repository 의 `@EntityGraph` 로 한 번에 가져온다. 지연 로딩에 기대지 않는다.
- Hikari 풀은 운영 값과 같게 맞춰 두었다(`maximum-pool-size: 5`, `connection-timeout: 3000`). 트랜잭션을 오래 잡는 코드가 있으면 풀이 금방 고갈된다. `incident-logs/a-connection-pool` 시나리오가 이 상황이다.
- 현재 시각은 `ClockConfig` 가 주입하는 `Clock` 에서 얻는다(`LocalDateTime.now(clock)`). 테스트에서는 시각을 고정한다.
- CORS 는 `WebConfig` 에서 `http://localhost:5173` 의 `/api/**` GET 만 허용한다.
- 문항 상태 코드는 문자열이다(`ItemStatus.ACTIVE = "A"` 등). "공개 문항"은 `status='A'` 이고, 기본 정렬은 `level desc, id asc` 이다.
- 테스트 구성:
  - 서비스는 Mockito 단위 테스트로, 저장소는 `@DataJpaTest` + `@ActiveProfiles("test")` 로 짠다.
  - 픽스처는 `ItemFixtures` 를 쓴다.
  - 테스트 이름은 `@DisplayName("메서드: 기대 동작")` 형식의 한국어로 쓴다.

### modern/web
- 모든 API 호출은 `src/api/client.ts` 의 `getJson()` 을 거친다. `VITE_API_BASE` 의 기본값은 `http://localhost:8080` 이다. 빈 문자열로 두면 상대 경로를 쓰고, Vite 프록시가 8080 으로 넘긴다.
- 조회 훅(`useUnits`, `useItem` 등)은 `hooks/useApiQuery.ts` 를 감싼다. 상태는 `idle/loading/success/error` 판별 유니온이고, key 가 바뀌거나 언마운트되면 AbortController 로 요청을 취소한다.
- 테스트는 `src/test/mockFetch.ts` 로 `fetch` 를 경로별로 가짜 응답으로 바꾼다. 픽스처는 `src/test/fixtures.ts` 에 있다.

### characterization — 이관 전후 동작 비교
- 레거시의 **현재 응답**을 스냅샷으로 찍어 기대값으로 삼는다. 이관한 뒤에는 `TARGET_BASE_URL` 만 바꿔 같은 스냅샷과 비교한다.
- `lib/normalize.mjs` 가 HTML(`<table id="items">`, `<p id="count">`, `<p id="message">`)과 JSON(`{items,count,message}`) 응답을 같은 `{status, rows, count, message}` 모양으로 바꾼다. 그래서 PHP 화면과 JSON API 를 같은 스냅샷으로 비교할 수 있다.
- `lib/target.mjs`:
  - 대상 주소는 이 파일의 `DEFAULT_BASE_URLS` 에만 있다. 테스트에는 `localhost:808x` 를 적지 않는다.
  - 경로가 바뀐 화면은 `PATH_ALIASES` 에 적는다(`/search.php` → `/api/items/search`). 레거시 경로가 404 를 돌려주면 여기 적힌 새 경로로 다시 요청한다.
- 규칙:
  - 스냅샷을 손으로 고치지 않는다. 실행마다 바뀌는 값은 정규화에서 뺀다.
  - 이관 후 비교가 실패하면 고칠 곳은 이관 코드다. 테스트 · 스냅샷을 고쳐서 통과시키지 않는다.
  - 조회 동작만 찍는다. 대상 하나에 케이스 10개 이상을 두고, 경계값 · 빈값 · 이상한 값을 포함한다.
  - 스냅샷은 `characterization/__snapshots__/` 에 모으고 커밋한다.

### 기타 실습 자료
- `incident-logs/`, `pipeline-samples/`: 더미 로그 · CSV 이다. 수만 줄짜리 파일이 있으므로 `grep`/`sed -n`/`wc -l` 로 필요한 부분만 본다. 원본은 고치지 않는다. RCA 는 `docs/rca/<폴더>.md` 에 쓴다. 로그에 섞인 IP · 이메일 · 학생 ID · 토큰 같은 값은 리포트로 옮길 때 가린다.
- `mcp-skeleton/`: MCP TypeScript SDK **v2**(`@modelcontextprotocol/server`, `zod/v4`) 기준이다. v1(`@modelcontextprotocol/sdk`) 예제는 그대로 쓰면 깨진다. ESM 이므로 import 에 `.js` 확장자를 붙인다. stdout 은 프로토콜 채널이라 로그는 `console.error` 로만 남긴다.
- `specs/`: 4회차 신규 개발 스펙이다. `specs/starters/java`(Spring Boot + H2, `./gradlew test`)와 `specs/starters/python`(FastAPI + sqlite, `pytest`, 8000)은 `/health` 만 있는 빈 골격이다.
- `templates/`: 프로젝트 유형별 `CLAUDE.*.md`, 검증 루프, 승인 · 시큐어코딩 체크리스트가 있다.
- `scripts/hook-node.sh`: Hook 실행기다. node 를 찾지 못하면 exit 2 로 막는다(fail-closed). `.claude/settings.json` 의 hook command 에서 `bash "$CLAUDE_PROJECT_DIR"/scripts/hook-node.sh hooks/<name>.mjs` 로 쓴다.

## 환경

기준 환경은 Windows + WSL2(Ubuntu 24.04) bash 이고 macOS zsh 에서도 같은 명령이 돈다. 저장소는 WSL 홈(`~/work`) 아래에 두고 `/mnt/c` 는 쓰지 않는다. 필요한 도구는 JDK 21, Node 22.5 이상, Python 3.12, Docker, `gh` 이다. 루트 `.env` 는 만들지 않는다. 권한 실습용 더미 파일은 `.env.perm-test` 이다.
