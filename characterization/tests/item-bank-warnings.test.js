// 동작 보존 테스트 — 문항 검색(search.php)의 경고 · 정렬 표시 · 페이저
//
// - item-bank.test.js 의 정규화 결과 {status, rows, count, message} 에는 경고 · 정렬 · 페이지 정보가 없다.
//   그 스냅샷은 그대로 두고, 이 파일이 그 부분만 따로 스냅샷으로 비교한다.
// - 레거시(HTML)와 새 API(JSON)를 아래 한 모양으로 바꾼다:
//     { status, warnings: [...], sort, dir, pager: {current, pages} | null }
//   · warnings : 레거시 <ul id="warnings"> 의 <li> 문구(가공하지 않음) / JSON warnings
//   · sort, dir: 레거시 검색 폼의 hidden 입력값(없으면 null) / JSON sort, dir
//   · pager    : 레거시 <p class="pager"> — 전체 페이지가 2 이상일 때만 그린다(search.php:686).
//                current 는 <strong> 번호(현재 페이지가 전체 페이지 안에 있을 때만), pages 는 번호 개수.
//                JSON 은 page · totalPages 로 같은 규칙을 적용한다.
// - 대상 주소 · 경로 대응은 lib/target.mjs 를 그대로 쓴다.
import * as cheerio from 'cheerio';
import { describe, expect, it } from 'vitest';
import { fetchRaw } from '../lib/target.mjs';

const MODULE = 'item-bank';
const PATH = '/search.php';

function fromHtml(html, status) {
  const $ = cheerio.load(html);
  const warnings = $('#warnings li').toArray().map((li) => $(li).text());
  const hidden = (name) => {
    const el = $(`form#cond input[type="hidden"][name="${name}"]`);
    return el.length > 0 ? el.attr('value') : null;
  };
  let pager = null;
  const pagerEl = $('p.pager');
  if (pagerEl.length > 0) {
    const numbers = pagerEl
      .children('a, strong')
      .toArray()
      .map((el) => $(el).text().trim())
      .filter((t) => /^[0-9]+$/.test(t));
    const strong = pagerEl.children('strong').first();
    pager = { current: strong.length > 0 ? Number(strong.text().trim()) : null, pages: numbers.length };
  }
  return { status, warnings, sort: hidden('sort'), dir: hidden('dir'), pager };
}

function fromJson(data, status) {
  const pages = Number(data.totalPages ?? 0);
  const page = Number(data.page);
  return {
    status,
    warnings: Array.isArray(data.warnings) ? data.warnings : null,
    sort: data.sort ?? null,
    dir: data.dir ?? null,
    pager: pages > 1 ? { current: page <= pages ? page : null, pages } : null,
  };
}

async function search(params) {
  const response = await fetchRaw(MODULE, PATH, params);
  const status = response.status;
  const contentType = (response.headers.get('content-type') || '').toLowerCase();
  const body = await response.text();
  return contentType.includes('json') ? fromJson(JSON.parse(body), status) : fromHtml(body, status);
}

describe('item-bank · 문항 검색(search.php) 경고 · 정렬 표시 · 페이저', () => {
  // ── 경고 없음 ─────────────────────────────────────────────
  it('조건 없음 — 경고 없음, 정렬 표시 없음, 20건이라 페이저 없음 [BR-28 BR-34]', async () => {
    expect(await search({})).toMatchSnapshot();
  });

  it('값이 전부 빈 문자열 — 경고 없음 [BR-35]', async () => {
    expect(await search({ q: '', unit: '', level: '', tag: '', sort: '', dir: '', page: '' })).toMatchSnapshot();
  });

  // ── 키워드 ────────────────────────────────────────────────
  it('키워드 101자 — 100자로 자른다는 경고 [BR-04]', async () => {
    expect(await search({ q: '가'.repeat(101) })).toMatchSnapshot();
  });

  it('키워드 한 글자 [BR-05]', async () => {
    expect(await search({ q: '수' })).toMatchSnapshot();
  });

  it('키워드 % 한 글자 — 한 글자 경고 다음 와일드카드 경고 [BR-05 BR-06]', async () => {
    expect(await search({ q: '%' })).toMatchSnapshot();
  });

  it('키워드에 _ [BR-06]', async () => {
    expect(await search({ q: '분_' })).toMatchSnapshot();
  });

  it('키워드가 공백뿐 — 경고 없음 [BR-40]', async () => {
    expect(await search({ q: '  ' })).toMatchSnapshot();
  });

  // ── 단원 ──────────────────────────────────────────────────
  it('단원 소문자 m5-1 — 대문자 경고만, 등록된 코드로 친다 [BR-08]', async () => {
    expect(await search({ unit: 'm5-1' })).toMatchSnapshot();
  });

  it('형식은 맞지만 없는 단원 X9-9 [BR-08]', async () => {
    expect(await search({ unit: 'X9-9' })).toMatchSnapshot();
  });

  it('단원 abc — 형식 · 소문자 · 미등록 세 경고의 순서 [BR-08]', async () => {
    expect(await search({ unit: 'abc' })).toMatchSnapshot();
  });

  // ── 난이도 ────────────────────────────────────────────────
  it('난이도 5abc [BR-11]', async () => {
    expect(await search({ level: '5abc' })).toMatchSnapshot();
  });

  it('난이도 0 [BR-11]', async () => {
    expect(await search({ level: '0' })).toMatchSnapshot();
  });

  // ── 태그 ──────────────────────────────────────────────────
  it('태그 계% — 부분 일치 경고 다음 미등록 경고 [BR-14]', async () => {
    expect(await search({ tag: '계%' })).toMatchSnapshot();
  });

  it('태그 51자 — 자른 뒤의 이름으로 미등록 경고 [BR-13 BR-14]', async () => {
    expect(await search({ tag: '가'.repeat(51) })).toMatchSnapshot();
  });

  it('등록된 태그 계산 — 경고 없음 [BR-12]', async () => {
    expect(await search({ tag: '계산' })).toMatchSnapshot();
  });

  // ── 정렬 ──────────────────────────────────────────────────
  it('알 수 없는 정렬 기준 — 경고, 정렬 표시 없음 [BR-30]', async () => {
    expect(await search({ sort: 'nope' })).toMatchSnapshot();
  });

  it('정렬 LEVEL, 방향 없음 — level desc 로 표시 [BR-31 BR-32]', async () => {
    expect(await search({ sort: 'LEVEL' })).toMatchSnapshot();
  });

  it('정렬 title, 방향 DESC [BR-31 BR-32]', async () => {
    expect(await search({ sort: 'title', dir: 'DESC' })).toMatchSnapshot();
  });

  it('정렬 unit, 잘못된 방향 up — 방향 경고, unit asc 로 표시 [BR-32]', async () => {
    expect(await search({ sort: 'unit', dir: 'up' })).toMatchSnapshot();
  });

  it('정렬 기준 없이 방향 asc 만 [BR-32]', async () => {
    expect(await search({ dir: 'asc' })).toMatchSnapshot();
  });

  // ── 페이지 ────────────────────────────────────────────────
  it('페이지 abc — 1페이지 경고 [BR-35]', async () => {
    expect(await search({ page: 'abc' })).toMatchSnapshot();
  });

  it('페이지 1000 — 999 경고 [BR-35]', async () => {
    expect(await search({ page: 1000 })).toMatchSnapshot();
  });

  it('페이지 0 — 경고 없이 1페이지 [BR-35]', async () => {
    expect(await search({ page: 0 })).toMatchSnapshot();
  });

  // ── 조합 ──────────────────────────────────────────────────
  it('모든 파라미터가 틀림 — 경고 순서 키워드 → 단원 → 난이도 → 태그 → 정렬 → 페이지', async () => {
    expect(
      await search({ q: '수', unit: 'abc', level: '9', tag: '%', sort: 'x', dir: 'y', page: 'z' }),
    ).toMatchSnapshot();
  });
});
