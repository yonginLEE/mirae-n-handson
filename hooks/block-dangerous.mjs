#!/usr/bin/env node
// hooks/block-dangerous.mjs
// PreToolUse(Bash) 훅: 위험한 셸 명령을 실행 전에 차단한다.
// 차단 = exit 2 + stderr 메시지(Claude에게 거부 사유로 전달됨)
//
// 종료 코드 규약
//   exit 0 : 통과 (명령 실행 허용)
//   exit 2 : 차단 (stderr 내용이 Claude에게 거부 사유로 전달됨)
//
// settings.json 의 permissions.deny 와는 별개의 2차 방어선이다.
// deny 는 명령 앞부분 패턴 위주라 `rm -fr`, `rm -r -f` 같은 변형을 놓칠 수 있어
// 여기서 정규식으로 한 번 더 막는다.
import { readFileSync } from "node:fs";

// 1) Claude Code 가 stdin 으로 넘기는 JSON 을 읽는다.
//    예: { "tool_name": "Bash", "tool_input": { "command": "ls -al" }, ... }
let input;
try {
  input = JSON.parse(readFileSync(0, "utf8")); // fd 0 = stdin
} catch {
  // stdin JSON을 읽지 못하면 판단하지 않고 통과(fail-open)시킨다.
  // 훅 자체의 오류로 정상 작업이 막히지 않게 하려는 선택이다.
  process.exit(0);
}

// 2) Bash 도구가 아니면 검사 대상이 아니므로 통과.
if (input.tool_name !== "Bash") process.exit(0);

// 3) 실행하려는 셸 명령 문자열을 꺼낸다. 없으면 빈 문자열.
const command = String(input.tool_input?.command ?? "");

// 4) 따옴표 · 백슬래시를 제거한 사본도 함께 검사한다.
//    셸은 r""m, r\m, 'rm' 을 모두 rm 으로 해석하므로, 문자열을 쪼개 정규식을 피하는 우회를 막는다.
//    (변수 치환 $CMD, 명령 치환 $(...) 같은 동적 우회는 정적 검사로 막을 수 없다)
const normalized = command.replace(/["'\\]/g, "");

// 5) 차단 규칙 목록. 각 항목: { pattern: 매칭 정규식, reason: 거부 사유 }
const RULES = [
  {
    // rm 에 "재귀(-r/-R/--recursive)" 와 "강제(-f/--force)" 옵션이 함께 있으면 차단.
    //  - \brm\b                : 단어 경계로 rm 만 매칭
    //  - (?=[^;&|]*\s(...))    : lookahead. [^;&|]* 는 ; & | 에서 멈추므로
    //                            `rm a; ls -f` 처럼 다른 명령의 -f 는 오탐하지 않는다.
    //  - 두 lookahead 를 따로 두어 -rf / -fr / -r -f 등 옵션 순서와 분리 여부에 상관없다.
    pattern: /\brm\b(?=[^;&|]*\s(-[a-zA-Z]*[rR]|--recursive))(?=[^;&|]*\s(-[a-zA-Z]*f|--force))/,
    reason: "rm -rf(-fr · -r -f · -Rf · --recursive --force 포함)는 금지되어 있습니다. 삭제 대상을 구체적으로 지정하거나 사람에게 요청하세요.",
  },
  {
    // SQL 의 DROP TABLE / DROP DATABASE. 대소문자 무시(i), 공백 개수 무관(\s+).
    pattern: /DROP\s+(TABLE|DATABASE)/i,
    reason: "DROP TABLE / DROP DATABASE 는 금지되어 있습니다.",
  },
  {
    // 운영 DB 호스트명(prod-db 또는 prod.db). [-.] 는 하이픈 또는 점.
    // 호스트명은 대소문자를 가리지 않으므로 PROD-DB 도 막는다(i).
    pattern: /prod[-.]db/i,
    reason: "운영 DB 호스트(prod-db / prod.db)에 대한 접근은 금지되어 있습니다.",
  },
];

// 6) 원본과 정규화본 중 하나라도 규칙에 맞으면 즉시 차단한다.
for (const { pattern, reason } of RULES) {
  if (pattern.test(command) || pattern.test(normalized)) {
    // stderr 로 내보낸 메시지가 Claude 에게 전달되어, 왜 막혔는지 알고 방향을 바꾼다.
    console.error(`[block-dangerous] 차단됨: ${reason}\n실행하려던 명령: ${command}`);
    process.exit(2);
  }
}

// 7) 어떤 규칙에도 걸리지 않으면 통과.
process.exit(0);
