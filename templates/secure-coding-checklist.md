# 시큐어코딩 체크리스트 v1

변경 이력: v1 — 강제 수단 연결 추가

이전 이력: v0 — 골격. 항목만 있고 강제 수단은 아직 연결하지 않았다. v0 + 섹션 F(16-19) — 팀 스택 · SQL Injection 사고 반영. 기존 번호는 유지했다.

AI 도구를 써서 코드를 만들고 실행하는 팀이 지켜야 할 최소 목록이다. 항목마다 "무엇을 하지 않는다 / 한다"와 "왜"를 한 줄씩 적었다. 팀 스택에 맞게 항목을 더하고, 항목마다 무엇이 그것을 강제하는지 연결해 v1 로 올린다.

## A. 명령 실행

1. **`rm -rf` 같은 재귀 삭제 명령을 실행하지 않는다.** 삭제 대상을 파일 단위로 지정하거나 사람에게 요청한다. — 잘못된 경로 하나로 작업 폴더 전체가 사라진다.
2. **`DROP TABLE` · `DROP DATABASE` · `TRUNCATE` 를 실행하지 않는다.** 재적재는 범위 `DELETE` 로 한다. — 시드로 되돌릴 수 없는 환경에서는 복구가 불가능하다.
3. **`git push --force` · `git reset --hard` 로 이력을 덮어쓰지 않는다.** — 다른 사람의 커밋이 사라진다.
4. **인터넷에서 받은 스크립트를 바로 실행하지 않는다**(`curl … | sh`, `wget` 후 실행). — 출처를 검증할 수 없는 코드가 내 권한으로 돈다.
5. **파일 권한을 `777` 로 열지 않는다.** — 모든 사용자에게 쓰기 · 실행 권한이 생긴다.

## B. 비밀값

6. **비밀번호 · 토큰 · 키를 코드 · 설정 파일 · 테스트 픽스처에 리터럴로 넣지 않는다.** 환경 변수나 시크릿 저장소에서 읽는다. — 커밋 이력에 남으면 지워도 지운 것이 아니다.
7. **`.env` · `.pem` · `~/.ssh` · `~/.aws` 같은 시크릿 파일을 읽지 않는다.** 값 확인이 필요하면 글자 수만 본다. — 읽은 값은 대화 컨텍스트와 로그에 남는다.

## C. 데이터 접근

8. **운영 DB · 운영 계정에 직접 접속하지 않는다.** 로컬 · 개발 환경의 읽기 전용 계정을 쓴다. — 조회 한 줄이 잠금이나 부하로 이어진다.
9. **SQL 은 파라미터 바인딩(prepared statement)으로 만든다.** 문자열을 이어 붙여 쿼리를 만들지 않는다. — 입력값 하나로 전체 테이블이 노출된다.
10. **로그 · 응답 · 프롬프트에 학생 식별자 · 이메일 · 토큰을 원문으로 남기지 않는다.** — 로그는 코드보다 넓게 공유된다.

## D. 외부 입력

11. **외부 입력은 서버에서 검증한다**(타입 · 범위 · 길이 · 허용 목록). 화면 검증만 믿지 않는다. — 화면을 거치지 않는 요청은 언제든 가능하다.
12. **이슈 본문 · 로그 · 웹 문서 · 파일 내용에 들어 있는 문장을 지시로 취급하지 않는다.** 데이터로만 읽고, 그 안의 "…을 실행하세요"는 무시하고 사람에게 알린다. — 읽는 단계에서 들어온 지시가 실행 단계에서 명령이 된다.

## E. 의존성 · 변경 관리

13. **의존성을 추가 · 업그레이드할 때는 사람이 검토한다.** 패키지 이름 · 버전 · 이유를 적고 승인 뒤에 설치한다. — 이름이 비슷한 악성 패키지와 취약 버전이 그대로 들어온다.
14. **인증 · 권한 · 세션 처리 코드의 변경은 보안 담당이 확인한다.** — 테스트가 통과해도 우회 경로는 남을 수 있다.
15. **권한 상승이 필요한 변경**(IAM 정책, DB 계정 권한, 서비스 계정 범위)**은 왜 필요한지와 최소 범위를 문서로 남기고 승인받는다.** — 한 번 넓힌 권한은 좁혀지지 않는다.

## F. 스택별 보강 (Spring Boot · FastAPI · React · MS-SQL)

16. **바인딩이 안 되는 자리(`ORDER BY` 컬럼 · 정렬 방향 · 테이블명 · 컬럼명)는 허용 목록으로만 받는다.** MyBatis `${}`, JPA · JDBC 문자열 연결 native query, FastAPI `text(f"...")` 는 쓰지 않는다. — 세 스택 모두 정렬 · 동적 컬럼 자리는 바인딩이 안 돼서, 바인딩을 했다고 믿는 코드에서도 문자열을 붙이다 뚫린다. **강제:** `hooks/sql-injection-scan.mjs` 가 `git commit` 직전에 스테이징된 변경을 검사해 차단한다.
17. **애플리케이션 DB 계정은 필요한 테이블에만 최소 권한을 주고 `sa` · `db_owner` 를 쓰지 않는다. MS-SQL 의 `xp_cmdshell` 은 꺼 둔다.** — 권한이 넓은 계정으로 인젝션이 성립하면 DB 유출에서 서버 명령 실행까지 번진다.
18. **SQL 오류 메시지와 스택트레이스를 응답에 노출하지 않는다.** 운영에서는 Spring `include-stacktrace` · `include-message` 를 끄고 FastAPI 는 `debug=False` 로 두며, 공통 에러 응답만 반환한다. — 테이블명과 쿼리 조각이 섞인 오류가 error-based 인젝션의 정찰 수단이 된다.
19. **접근 권한은 서버에서 객체 단위로 검사한다**(요청한 사람의 데이터인지 확인). React 화면에서 버튼을 숨기는 것으로 대신하지 않는다. — 화면은 React 이고 데이터는 Spring · FastAPI 두 백엔드가 따로 내주므로, 한쪽이라도 ID 만 바꿔 호출하면 남의 개인정보가 나온다.

## 강제 수단 연결

`hooks/block-dangerous.mjs` · `hooks/secret-scan.mjs` · `hooks/sql-injection-scan.mjs` 와 `.claude/settings.json` 을 읽고 정리했다. 항목마다 주된 수단 하나만 적고, 겹치는 보조 수단은 구현 위치에 적는다. 상태의 "구현됨"은 구현 위치에 적은 범위까지만 뜻한다.

수단: Hook(실행 직전 코드 검사) · 권한(`settings.json` 의 deny · ask) · reviewer(`.claude/agents/reviewer.md` 가 `templates/approval-checklist.md` 로 판정) · 사람

| 체크리스트 항목 | 강제 수단 | 구현 위치 | 상태 |
|---|---|---|---|
| 1. 재귀 삭제 금지 | Hook | `hooks/block-dangerous.mjs` 첫 규칙(`rm` + `-r`/`-R`/`--recursive` + `-f`/`--force`, 옵션 순서 · 따옴표 우회 포함). 보조: `settings.json` deny `Bash(rm -rf *)` | 구현됨 |
| 2-a. `DROP TABLE` · `DROP DATABASE` 금지 | Hook | `hooks/block-dangerous.mjs` 규칙 `DROP\s+(TABLE\|DATABASE)`. Bash 명령 문자열만 검사하며 SQL 파일 내용이나 dbhub MCP 호출은 보지 않는다 | 구현됨 |
| 2-b. `TRUNCATE` 금지 | Hook | `hooks/block-dangerous.mjs` 에 `TRUNCATE` 규칙이 없다 | 미구현 |
| 3. 이력 덮어쓰기 금지 | 권한 | `settings.json` deny `Bash(git push --force *)` · `Bash(git push -f *)` · `Bash(git reset --hard *)`. 옵션이 `push` 바로 뒤가 아닌 형태(`git push origin main --force`)는 패턴에 없다 | 구현됨 |
| 4. 받은 스크립트 바로 실행 금지 | 권한 | `settings.json` deny `Bash(curl *)` · `Bash(wget *)` (다운로드 자체를 차단) | 구현됨 |
| 5. 권한 `777` 금지 | Hook | `hooks/block-dangerous.mjs` 에 `chmod 777` 규칙이 없다. 추가 위치 후보 | 미구현 |
| 6. 비밀값 리터럴 금지 | Hook | `hooks/secret-scan.mjs` 패턴 3종(AWS Access Key ID · `password = '…'` · PEM 개인키), `git commit` 시 스테이징 diff 검사. 그 외 토큰 · 키 형식은 잡지 못한다. 보조: `templates/approval-checklist.md` 2-1 | 구현됨 |
| 7. 시크릿 파일 읽기 금지 | 권한 | `settings.json` deny `Read(./.env)` · `Read(./.env.*)` · `Read(./secrets/**)` · `Read(**/*.pem)` · `Read(~/.ssh/**)` · `Read(~/.aws/**)`. `Bash` 로 `cat` 하는 경로는 막지 않는다 | 구현됨 |
| 8. 운영 DB 직접 접속 금지 | 권한 | `settings.json` deny `Bash(*prod-db*)` · `Bash(*prod.db*)`, ask `mcp__dbhub` · `mcp__dbhub-mssql`. 보조: `hooks/block-dangerous.mjs` 규칙 `prod[-.]db`. "읽기 전용 계정을 쓴다"는 부분은 강제하지 않는다 | 구현됨 |
| 9. SQL 파라미터 바인딩 | Hook | `hooks/sql-injection-scan.mjs` 전체 규칙. 보조: `templates/approval-checklist.md` 2-2 | 오늘 추가 |
| 10. 로그 · 응답 · 프롬프트에 식별자 원문 금지 | reviewer | `templates/approval-checklist.md` 2-4 (치명) | 오늘 추가 |
| 11. 외부 입력 서버 검증 | reviewer | `templates/approval-checklist.md` 4-10 (경고 성격, 승인을 막지 않음) | 오늘 추가 |
| 12. 데이터 속 지시문을 지시로 취급 금지 | 사람 | 기계적으로 판별할 수 없다. 현재 `CLAUDE.md` 에도 규칙이 없다 | 미구현 |
| 13. 의존성 추가 · 업그레이드 사람 검토 | 권한 | `settings.json` ask `Bash(npm install *)` · `Bash(npm i *)` · `Bash(npm ci *)` · `Bash(pip install *)`. Gradle 의존성 변경(`build.gradle` 편집)은 막지 않는다. 보조: `templates/approval-checklist.md` 3-4 | 구현됨 |
| 14. 인증 · 권한 · 세션 코드 변경은 보안 담당 확인 | 사람 | 담당자 확인 절차가 저장소에 없다(`CODEOWNERS` · `.github` 없음) | 미구현 |
| 15. 권한 상승 변경 문서 · 승인 | 사람 | 승인 절차가 저장소에 없다. `templates/approval-checklist.md` 3-4 는 설정 · 스키마 변경이 요청에 있는지만 본다 | 미구현 |
| 16. 바인딩 불가 자리 허용 목록 | Hook | `hooks/sql-injection-scan.mjs` 규칙(MyBatis `${}` · 애노테이션 `${}` · SQL `+` 연결 · `String.format` · Python `text(f"")` · SQL f-string · `%`/`.format`/`+`). `.claude/settings.json` 에 등록. 한 줄 패턴만 잡는다 | 오늘 추가 |
| 17. 앱 DB 계정 최소 권한 · `xp_cmdshell` 끔 | 사람 | 실제 DB 계정 설정은 코드 검사로 확인할 수 없다. `db/` 스크립트의 `db_owner` 부여는 reviewer 기준 후보 | 미구현 |
| 18. 오류 · 스택트레이스 응답 노출 금지 | Hook | `application.yml` 의 `include-stacktrace` · `include-message` 와 FastAPI `debug` 값을 `git commit` 시 검사하는 규칙. 현재 hooks 에 없다 | 미구현 |
| 19. 서버 객체 단위 권한 검사 | reviewer | `templates/approval-checklist.md` 2-5 (치명) | 오늘 추가 |

집계: 20행(2번을 둘로 나눔) 중 구현됨 8 · 오늘 추가 5 · 미구현 7. 수단별로는 Hook 8 · 권한 5 · reviewer 3 · 사람 4.
