#!/usr/bin/env node
// hooks/sql-injection-scan.mjs
// PreToolUse(Bash) 훅: `git commit` 직전에 스테이징된 변경(git diff --cached)에서
// 문자열로 SQL 을 조립하는 패턴을 찾으면 커밋을 차단한다. 차단 = exit 2 + stderr 메시지
// 근거: templates/secure-coding-checklist.md 16번 (바인딩이 안 되는 자리는 허용 목록으로만 받는다)
//
// 정적 패턴 검사라서 오탐 · 미탐이 있다. 한 줄에 쓴 경우만 잡고, 여러 줄로 나눠 이어 붙인 쿼리는 놓친다.
// 정렬 컬럼처럼 허용 목록으로 검증한 뒤 쓰는 정당한 `${}` 는 사람이 확인하고 직접 커밋한다.
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

let input;
try {
  input = JSON.parse(readFileSync(0, "utf8")); // fd 0 = stdin
} catch {
  process.exit(0); // 입력을 읽지 못하면 통과(fail-open)
}

if (input.tool_name !== "Bash") process.exit(0);

const command = String(input.tool_input?.command ?? "").trim();
if (!command.startsWith("git commit")) process.exit(0);

let diff = "";
try {
  diff = execFileSync("git", ["diff", "--cached", "--no-color", "--no-ext-diff", "-U0"], {
    cwd: input.cwd || process.cwd(),
    encoding: "utf8",
    maxBuffer: 50 * 1024 * 1024,
  });
} catch {
  process.exit(0); // git 저장소가 아니거나 git 실행 실패: 통과
}

const SQL_WORD = "(?:select|insert|update|delete|where|order\\s+by|group\\s+by|from|join)";

// 각 규칙: ext(대상 확장자), re(추가된 줄에 대한 패턴), name(보고용), pathRe(선택: 경로 조건)
const RULES = [
  // MyBatis XML: ${} 는 바인딩이 아니라 문자열 치환이다. (pom.xml 의 ${} 는 SQL 단어가 없어 제외된다)
  { ext: /\.xml$/i, name: "MyBatis ${} 문자열 치환", re: new RegExp("\\$\\{.*", "i"), when: (path, line) =>
      /mapper|mybatis|sqlmap/i.test(path) || new RegExp(`\\b${SQL_WORD}\\b`, "i").test(line) },
  // MyBatis 애노테이션: @Select("... ${x}")
  { ext: /\.(java|kt)$/i, name: "MyBatis 애노테이션 ${}", re: /@(?:Select|Insert|Update|Delete)\b.*\$\{/ },
  // Java / Kotlin: SQL 문자열 리터럴 뒤에 + 로 이어 붙임
  { ext: /\.(java|kt)$/i, name: "SQL 문자열 연결(+)", re: new RegExp(`"[^"]*\\b${SQL_WORD}\\b[^"]*"\\s*\\+`, "i") },
  { ext: /\.(java|kt)$/i, name: "SQL 문자열 연결(+)", re: new RegExp(`\\+\\s*"[^"]*\\b${SQL_WORD}\\b[^"]*"`, "i") },
  // Java / Kotlin: String.format 으로 SQL 조립
  { ext: /\.(java|kt)$/i, name: "SQL String.format 조립", re: new RegExp(`String\\.format\\(\\s*"[^"]*\\b${SQL_WORD}\\b`, "i") },
  // Python: text(f"..."), execute(f"..."), SQL f-string
  { ext: /\.py$/i, name: "text()/execute() 에 f-string", re: /\b(?:text|execute|executemany)\(\s*[rb]?f["']/i },
  { ext: /\.py$/i, name: "SQL f-string 조립", re: new RegExp(`\\bf["'][^"']*\\b${SQL_WORD}\\b[^"']*\\{`, "i") },
  // Python: execute("..." % x), execute("...".format()), text("..." + x)
  { ext: /\.py$/i, name: "SQL %, .format, + 조립", re: /\b(?:text|execute|executemany)\(\s*["'][^"']*["']\s*(?:%|\+|\.format\()/i },
];

// 추가된 줄만 파일별로 검사한다. -U0 이라 헤더 "+++ b/경로" 로 현재 파일을 추적한다.
const counts = new Map();
let file = "";
for (const line of diff.split("\n")) {
  if (line.startsWith("+++ ")) {
    file = line.slice(4).replace(/^b\//, "");
    continue;
  }
  if (!line.startsWith("+")) continue;
  const added = line.slice(1);
  for (const rule of RULES) {
    if (!rule.ext.test(file)) continue;
    if (!rule.re.test(added)) continue;
    if (rule.when && !rule.when(file, added)) continue;
    const key = `${rule.name} — ${file}`;
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
}

// 줄 내용은 출력하지 않는다(메시지는 Claude 컨텍스트로 들어간다). 패턴 이름 · 파일 · 건수만 보고한다.
if (counts.size > 0) {
  const findings = [...counts].map(([k, n]) => `- ${k}: ${n}건`);
  console.error(
    `[sql-injection-scan] 커밋 차단: 스테이징된 변경에서 SQL 을 문자열로 조립하는 패턴이 발견되었습니다.\n` +
      `${findings.join("\n")}\n` +
      `파라미터 바인딩(#{}, :name, ?)으로 바꾸세요. ORDER BY · 컬럼명처럼 바인딩이 안 되는 자리는 허용 목록으로 검증한 값만 쓰고, 해당 코드는 사람이 확인한 뒤 직접 커밋합니다. (체크리스트 16번)`
  );
  process.exit(2);
}

process.exit(0);
