// 동작 보존 테스트 — 문항 은행(item-bank)의 문항 검색 화면 search.php (조회 전용)
//
// - 대상 주소는 lib/target.mjs 가 정한다(TARGET_BASE_URL, 없으면 레거시 기본 주소). 여기에는 주소를 적지 않는다.
// - 기대값을 손으로 적지 않는다. 지금 시스템의 실제 응답이 기대값이다(npm run baseline -- item-bank).
// - 케이스 이름 뒤 [BR-xx] 는 겨냥한 규칙(docs/item-bank/BUSINESS-RULES.md)이다.
// - 새 API 에도 같은 테스트를 돌리므로 legacyOnly 같은 건너뛰기를 두지 않는다.
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';
const PATH = '/search.php';

const search = (params) => fetchNormalized(MODULE, PATH, params);

describe('item-bank · 문항 검색(search.php)', () => {
  // ── 정상 입력 ──────────────────────────────────────────────
  it('조건 없음 — 공개 문항 중 난이도 5 제외, 기본 정렬 [BR-09 BR-27 BR-28 BR-29 BR-41]', async () => {
    expect(await search({})).toMatchSnapshot();
  });

  it('단원 + 난이도 + 태그 AND 조합 [BR-01 BR-07 BR-10 BR-12]', async () => {
    expect(await search({ unit: 'M5-1', level: 3, tag: '계산' })).toMatchSnapshot();
  });

  it('키워드 정상 — 제목 또는 지문에 포함 [BR-03]', async () => {
    expect(await search({ q: '분수' })).toMatchSnapshot();
  });

  // ── 경계값 ────────────────────────────────────────────────
  it('키워드 100자 — 잘리지 않는 마지막 길이 [BR-04]', async () => {
    expect(await search({ q: '가'.repeat(100) })).toMatchSnapshot();
  });

  it('키워드 101자 — 100자로 잘리는 첫 길이 [BR-04]', async () => {
    expect(await search({ q: '가'.repeat(101) })).toMatchSnapshot();
  });

  it('키워드 한 글자 — 경고만 띄우고 그대로 검색 [BR-05]', async () => {
    expect(await search({ q: '수' })).toMatchSnapshot();
  });

  it('난이도 5 직접 지정 — 조건 없을 때 빠지는 5 가 나오는지 [BR-10 BR-09]', async () => {
    expect(await search({ level: 5 })).toMatchSnapshot();
  });

  it('페이지 999 — 경고 없이 통과하는 마지막 번호 [BR-35 BR-36]', async () => {
    expect(await search({ page: 999 })).toMatchSnapshot();
  });

  it('페이지 1000 — 999 로 잘리는 첫 번호 [BR-35]', async () => {
    expect(await search({ page: 1000 })).toMatchSnapshot();
  });

  // ── 빈 값 · 누락 ──────────────────────────────────────────
  it('키워드가 공백뿐 — 조건을 붙이지 않음 [BR-40 BR-05 BR-06]', async () => {
    expect(await search({ q: '  ' })).toMatchSnapshot();
  });

  it('파라미터는 있지만 값이 전부 빈 문자열 [BR-09 BR-29 BR-32 BR-35]', async () => {
    expect(
      await search({ q: '', unit: '', level: '', tag: '', sort: '', dir: '', page: '' }),
    ).toMatchSnapshot();
  });

  // ── 이상한 값 ─────────────────────────────────────────────
  it('음수 난이도 [BR-11]', async () => {
    expect(await search({ level: -1 })).toMatchSnapshot();
  });

  it('숫자 뒤에 문자가 붙은 난이도 5abc — (int) 변환으로 5 가 되는지 [BR-11 BR-09]', async () => {
    expect(await search({ level: '5abc' })).toMatchSnapshot();
  });

  it('형식은 맞지만 없는 단원 코드 [BR-08]', async () => {
    expect(await search({ unit: 'X9-9' })).toMatchSnapshot();
  });
});
