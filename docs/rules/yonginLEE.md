# 실 공통 규칙 후보 — yonginLEE (주 스택: 데이터 사이언티스트)

- 출처: `.claude/settings.json`(permissions · hooks), `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs`, `hooks/sql-injection-scan.mjs`
- 기존 파일은 수정하지 않았고, 이 문서만 새로 만들었습니다.
- 스택 코드: `JAVA`(gradlew · MyBatis), `NODE`(npm), `PY`(pip · Python). 뜻이 스택과 무관하면 `공통`.

## 규칙 표

| 규칙 | 종류 | 적용 범위 | 막는 사고 또는 허용 이유 |
|---|---|---|---|
| `Bash(rm -rf *)` | deny | 공통 | 재귀 강제 삭제로 작업 디렉터리 · 홈이 통째로 사라지는 사고 |
| `Bash(sudo *)` | deny | 공통 | 관리자 권한으로 시스템 파일 · 패키지를 바꾸는 사고 |
| `Bash(git push --force *)`, `Bash(git push -f *)` | deny | 공통 | 원격 이력을 덮어써 팀원 커밋이 사라지는 사고 |
| `Bash(git reset --hard *)` | deny | 공통 | 커밋하지 않은 변경이 복구 불가능하게 날아가는 사고 |
| `Bash(*prod-db*)`, `Bash(*prod.db*)` | deny | 공통 | 운영 DB 에 직접 접속 · 변경하는 사고 |
| `Bash(curl *)`, `Bash(wget *)` | deny | 공통 | 사내 코드 · 데이터가 외부로 전송되거나 출처 불명 스크립트를 내려받는 사고 |
| `Read(./.env)`, `Read(./.env.*)` | deny | 공통 | 환경 변수의 비밀값이 AI 컨텍스트로 들어가는 사고 |
| `Read(./secrets/**)`, `Read(**/*.pem)`, `Read(~/.ssh/**)`, `Read(~/.aws/**)` | deny | 공통 | 개인키 · 클라우드 자격 증명 노출 |
| `Edit(./.env)`, `Edit(./.env.*)`, `Edit(./secrets/**)` | deny | 공통 | AI 가 비밀 설정 파일을 임의로 바꾸거나 값을 써 넣는 사고 |
| `mcp__github__delete_file`, `mcp__github__merge_pull_request` | deny | 공통 | 리뷰 없는 파일 삭제 · PR 자동 병합 |
| `Bash(git push *)` | ask | 공통 | 원격 반영은 사람이 확인한 뒤에만 (강제 push 는 deny 가 먼저 막음) |
| `Bash(npm install *)`, `npm i *`, `npm ci *` | ask | NODE | 의존성 추가로 인한 공급망 · 라이선스 문제 확인 |
| `Bash(pip install *)` | ask | PY | 출처 불명 패키지 설치, 환경 오염 · 버전 충돌 확인 |
| `Bash(docker compose down *)`, `docker compose --profile *` | ask | 공통 | 컨테이너 · 볼륨 삭제로 로컬 DB 데이터가 사라지는 사고 (Docker 는 빌드 도구가 아니라 공통으로 분류) |
| `mcp__github__create_pull_request`, `mcp__github__push_files` | ask | 공통 | 원격 저장소에 쓰는 동작은 사람이 확인 |
| `mcp__dbhub`, `mcp__dbhub-mssql` | ask | 공통 | DB 질의는 실행 전 사람이 대상 · 쿼리를 확인 |
| `npm test` · `run test` · `run lint` · `run typecheck` · `run build` | allow | NODE | 읽기 성격의 검증 명령이라 매번 승인할 이유가 없음 |
| `./gradlew test` · `check` · `build` | allow | JAVA | 위와 같은 이유 (검증 명령) |
| `git status` · `diff` · `log` · `add` · `commit` | allow | 공통 | 로컬 이력 작업이고, 커밋은 secret-scan · sql-injection-scan Hook 이 2차로 검사 |
| `WebFetch(domain:docs.spring.io)`, `docs.gradle.org` | allow | JAVA | 공식 문서 조회만 허용 |
| `WebFetch(domain:developer.mozilla.org)` | allow | NODE | 웹 · 프론트 공식 문서 조회만 허용 |
| `mcp__github__get_file_contents`, `list_pull_requests`, `pull_request_read`, `list_issues` | allow | 공통 | 읽기 전용 조회 |
| `mcp__item-bank__search_items` | allow | 공통 | 읽기 전용 검색 |
| block-dangerous: `rm` 의 재귀 + 강제 옵션 조합 (`-fr`, `-r -f`, `--recursive --force`, 따옴표 분할) | Hook | 공통 | deny 패턴이 놓치는 `rm` 변형 우회를 정규식으로 한 번 더 차단 |
| block-dangerous: `DROP TABLE` / `DROP DATABASE` | Hook | 공통 | 테이블 · DB 삭제 사고 |
| block-dangerous: `prod-db` / `prod.db` (대소문자 무시) | Hook | 공통 | deny 의 운영 DB 규칙 2차 방어 (대문자 호스트명 우회) |
| secret-scan: `git commit` 직전 staged diff 에서 AWS 키 · 하드코딩 password · PEM 개인키 탐지 | Hook | 공통 | 시크릿이 이력에 커밋되는 사고 (값은 출력하지 않고 건수만 보고) |
| sql-injection-scan: MyBatis `${}`, Java/Kotlin 문자열 연결 · `String.format` 의 SQL 조립 | Hook | JAVA | SQL 인젝션 (체크리스트 16번) |
| sql-injection-scan: Python `execute(f"...")`, SQL f-string, `%` · `.format` 조립 | Hook | PY | SQL 인젝션 (체크리스트 16번) |

## 추가 제안 (데이터 사이언티스트 스택에 필요한데 지금 없는 규칙)

| 제안 규칙 | 종류 | 범위 | 이유 |
|---|---|---|---|
| `Read`/`Edit` 로 `data/raw/**`, 개인정보 포함 CSV · 엑셀 접근 | ask 또는 deny | PY | 원본 · 개인정보 데이터가 AI 컨텍스트로 들어가는 사고. 샘플 추출본만 허용하는 방식을 권장 |
| `Read(~/.kaggle/**)`, `~/.config/gcloud/**`, `~/.databrickscfg`, `~/.netrc`, `credentials.json` | deny | 공통 | 현재 deny 는 `.env` · `.ssh` · `.aws` · `.pem` 만 있어 DS 가 자주 쓰는 자격 증명 파일이 비어 있음 |
| `Bash(cat .env*)`, `Bash(type .env*)` 등 Bash 경유 읽기 | deny | 공통 | `Read` deny 는 Bash 의 `cat` 을 막지 못함 |
| 대용량 · 데이터 · 모델 파일 커밋 차단 (`*.parquet`, `*.pkl`, `*.h5`, `*.pt`, `*.csv` 일정 크기 이상) | Hook | PY | 데이터 · 모델 파일이 저장소에 올라가는 사고. Git LFS · DVC 안내를 사유 메시지에 포함 |
| 노트북 출력 셀 점검 (`.ipynb` 의 `outputs` 에 데이터 · 시크릿이 남았는지) | Hook | PY | 노트북 출력으로 개인정보 · 결과 데이터가 커밋됨. 현재 sql-injection-scan 은 `.py` 만 보고 `.ipynb` 는 놓침 |
| 개인정보 패턴 탐지 (주민등록번호 · 전화번호 · 이메일) | Hook | 공통 | secret-scan 은 시크릿만 탐지하고 개인정보는 보지 않음 |
| secret-scan 패턴 확장 (GitHub · OpenAI 류 토큰, `postgres://user:pw@` 접속 문자열, GCP 서비스 계정 JSON) | Hook | 공통 | 현재는 AWS 키 · password · PEM 3종뿐이라 DS 환경의 API 토큰 · DB 접속 문자열을 놓침 |
| DB 데이터 변경 쿼리 (`DELETE FROM`, `TRUNCATE`, `UPDATE` 에 WHERE 없음) | Hook 또는 ask | 공통 | Hook 은 `DROP` 만 막고 `TRUNCATE` · 전체 `DELETE` 는 통과 |
| `pytest`, `ruff check`, `mypy`, `python -m pytest` | allow | PY | npm · gradlew 검증 명령은 allow 인데 PY 쪽 검증 명령이 없어 매번 승인 필요 |
| `WebFetch` 허용 도메인 (`pandas.pydata.org`, `scikit-learn.org`, `numpy.org`, `docs.python.org`, `pytorch.org`) | allow | PY | curl 은 막으면서 공식 문서만 읽기 전용으로 열어 주는 방식 |
| 클라우드 비용 · 삭제 명령 (`aws s3 rm`, 학습 job 제출, 대규모 쿼리 실행) | ask | 공통 | 비용 폭주 · 데이터 삭제 사고 |
| `pickle.load` · `joblib.load` 출처 불명 파일 로딩 | ask | PY | 역직렬화 시 임의 코드 실행 |
| Hook 우회 보완: `git commit` 판별을 `startsWith` 대신 명령 전체에서 검사 | Hook | 공통 | `git add . && git commit`, `git -C dir commit` 은 secret-scan · sql-injection-scan 을 통과함 |

## 주의 (다른 스택에 그대로 걸면 일을 막을 수 있는 규칙)

- **`curl` · `wget` 전체 deny**: 헬스체크(`curl localhost`), 설치 스크립트, API 수동 테스트, 데이터 수집을 모두 막습니다. 데이터 수집이 잦은 DS · 인프라 스택에서 특히 부담이 큽니다. 도메인 단위 allow 로 풀 수 있는지 먼저 확인하세요.
- **`rm -rf` deny + Hook**: `node_modules`, `dist`, `target`, `.venv`, `__pycache__` 정리처럼 정상적인 빌드 산출물 삭제도 막습니다.
- **`sudo` 전체 deny**: 패키지 설치가 필요한 인프라 · 컨테이너 작업을 막습니다.
- **`./.env.*` Read · Edit deny**: `.env.example` · `.env.sample` 같은 템플릿 파일까지 걸립니다. 신규 개발자 온보딩 파일을 읽거나 고치지 못합니다. 예외 패턴이 필요합니다.
- **`Read(**/*.pem)` deny**: 인증서 · 키를 다루는 인프라나 PHP 서버 작업에서 정상 파일을 못 읽습니다.
- **`git reset --hard` deny**: 흔한 롤백 수단이라 작업이 막힐 수 있습니다. 대안(`git restore`, `git revert`)을 같이 안내해야 합니다.
- **`DROP TABLE` Hook**: 마이그레이션(Flyway · Alembic · Laravel 등), 테스트 픽스처의 `DROP TABLE IF EXISTS`, 임시 테이블 정리를 모두 막습니다.
- **`prod-db` / `prod.db` 정규식**: 명령 문자열 전체를 검사하므로 커밋 메시지 · 로그 · 문서에 `prod-db` 가 들어가도 차단됩니다. `prod.database` 같은 이름도 오탐됩니다.
- **sql-injection-scan 의 Python 규칙 (DS 에 영향이 큼)**: `from`, `select`, `where`, `update`, `delete` 가 SQL 키워드 목록에 있어, `f"loading from {path}"` 같은 일반 로그 · 메시지 f-string 도 SQL 조립으로 오탐될 수 있습니다. 차단되면 AI 가 커밋하지 못하고 사람이 직접 커밋해야 합니다.
- **sql-injection-scan 의 정당한 `${}`**: 허용 목록으로 검증한 ORDER BY 컬럼도 차단되어, 해당 코드는 사람이 직접 커밋해야 합니다.
- **secret-scan 의 password 패턴**: 테스트 코드의 `password = "test1234"` 같은 더미 값도 차단합니다.
- **`mcp__github__merge_pull_request` deny**: 자동 병합 흐름이 있는 팀에서는 막힙니다.
- **`git push` ask**: CI · 봇처럼 사람이 없는 실행 환경에서는 승인 대기로 멈춥니다.
- **스택 한정 allow · ask**: `npm`, `gradlew`, `pip` 규칙은 해당 스택에만 의미가 있습니다. `composer`, `terraform` 등 다른 도구는 규칙이 없어 매번 승인을 거치므로, 스택별 규칙은 스택 코드로 따로 둬야 합니다.

## 참고: 기존 파일에서 발견한 문제 (수정하지 않음)

- `hooks/secret-scan.mjs` 마지막 줄이 `process.exit(0);s` 로 끝납니다. `s` 는 오타로 보입니다. `process.exit(0)` 이 먼저 실행되어 지금은 동작에 영향이 없지만, 정리가 필요합니다.
