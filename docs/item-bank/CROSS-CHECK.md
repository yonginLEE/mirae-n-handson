# 문항 은행(item-bank-php) 비즈니스 규칙 — 코드 기준 추출

- 작성 기준: `legacy/item-bank-php` 의 PHP 소스만 읽음 (`search.php`, `register.php`, `units.php`, `index.php`, `inc/db.php`, `inc/layout.php`)
- 읽지 않은 것: `vendor/` (외부 라이브러리), `docs/` (독립 비교 목적), `db/` 등 이 폴더 밖의 파일
- 원칙: 코드에 있는 것만 규칙으로 적음. 주석과 코드가 다르면 코드를 따름.
- 줄번호는 2026-09-29 시점 파일 기준. 경로는 모두 `legacy/item-bank-php/` 기준.

## 한눈에 보기

| 구분 | 규칙 |
|---|---|
| 검색 조건 조합 | CX-01 ~ CX-08 |
| 검색 대상·제외 조건 | CX-09, CX-10 |
| 기본 정렬·정렬 옵션 | CX-11 ~ CX-13 |
| 등록 검증 | CX-14 ~ CX-19 |

**주석과 코드가 다른 곳: CX-04 (난이도 빈값).** 주석은 "전체 난이도(1~5 모두 포함)"라고 하지만 코드는 5를 제외한다.

---

## A. 검색 조건 조합

### CX-01 키워드는 앞뒤 공백을 제거하고 100자까지만 쓴다
- 근거: `search.php:85-89`
- 코드:
  ```php
  $q = trim($q);
  if (mb_strlen($q, 'UTF-8') > 100) {
      $q = mb_substr($q, 0, 100, 'UTF-8');
  ```
- 100자를 넘으면 잘라서 검색하고 경고만 표시한다(오류 아님). 공백만 남으면 키워드 조건은 적용하지 않는다(`search.php:90`).

### CX-02 키워드는 제목 또는 지문에 포함(LIKE 부분 일치)된 문항을 찾는다
- 근거: `search.php:97-98`
- 코드:
  ```php
  $like = '%' . $q . '%';
  $where .= " AND (title LIKE ? OR stem LIKE ?)";
  ```
- `%` · `_` 를 이스케이프하지 않으므로 와일드카드로 동작한다. 코드는 이를 경고로만 알린다(`search.php:94-96`).

### CX-03 단원은 코드와 정확히 일치하는 문항만 찾으며, 대소문자 변환이나 형식 보정을 하지 않는다
- 근거: `search.php:109-120`
- 코드:
  ```php
  $unit = trim($unit);
  if ($unit !== '') {
  ...
  $where .= " AND unit_code = ?";
  ```
- 형식(`/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/`)이 틀리거나 소문자여도, 등록되지 않은 코드여도 입력값 그대로 조회한다. 경고만 붙는다(`search.php:111-117`, `141-143`).

### CX-04 난이도를 비우면 전체가 아니라 난이도 5를 제외한 문항(level < 5)만 나온다
- 근거: `search.php:213-216`
- 코드:
  ```php
  // 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
  if ($level == '') {
      $where .= " AND level < 5";
  ```
- **주석과 코드가 다르다.** 주석은 1~5 모두 포함이라고 하나 코드는 5를 뺀다. 이 문서는 코드를 기준으로 쓴다.
- 화면 요약에도 이 조건은 나타나지 않는다. 난이도를 비우면 요약이 "조건 없이 검색했습니다."로 나온다(`search.php:355-356`). 이 요약 문구와 실제 조건이 어긋난다.
- 파일 머리 주석의 `level : 난이도 (1~5, 빈값 허용)` (`search.php:8`)만으로는 5가 빠진다는 것을 알 수 없다.

### CX-05 난이도 1~5 중 하나를 고르면 그 값과 정확히 같은 문항만 나온다
- 근거: `search.php:217-221`
- 코드:
  ```php
  else if (preg_match('/^[1-5]$/', $level)) {
      $where .= " AND level = ?";
  ```
- 난이도 5는 이렇게 명시해야만 조회된다.

### CX-06 난이도가 1~5 형식이 아니면 경고를 띄우되, 정수로 바꿔서 그 값으로 그대로 조회한다
- 근거: `search.php:223-229`
- 코드:
  ```php
  $warnings[] = '난이도는 1~5 사이여야 합니다.';
  $where .= " AND level = ?";
  $values[] = (int)$level;
  ```
- 오류로 막지 않는다. `abc` 는 0으로, `1.5` 는 1로 바뀐다(`(int)` 캐스팅 결과). 이때 `level < 5` 조건은 붙지 않는다.

### CX-07 태그는 이름이 정확히 일치하는 태그가 붙은 문항만 찾고, 이름은 50자까지만 쓴다
- 근거: `search.php:235-247`
- 코드:
  ```php
  $tag = trim($tag);
  ...
  $where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
          . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
  ```
- 부분 일치는 없다. 50자를 넘으면 잘라서 조회한다(`search.php:240-243`). 등록되지 않은 태그도 조회는 하며 경고만 붙는다(`search.php:258-260`).
- 태그는 하나만 지정할 수 있다. 여러 태그 조합 조건은 코드에 없다.

### CX-08 키워드·단원·난이도·태그 조건은 모두 AND 로 결합한다
- 근거: `search.php:44`, `98`, `118`, `215`/`219`, `244`
- 코드:
  ```php
  $where    = " WHERE 1=1";
  ...
  $where .= " AND unit_code = ?";
  ```
- 비어 있는 조건은 붙이지 않는다. 단, 난이도는 비어 있어도 CX-04 의 `level < 5` 가 항상 붙는다. 총 건수 SQL도 같은 `$where` 를 쓴다(`search.php:526`).

---

## B. 검색 대상과 제외 조건

### CX-09 검색은 `v_item_public` 뷰만 조회한다
- 근거: `search.php:521-526`
- 코드:
  ```php
  $select = "SELECT id, title, unit_code, level, tag_names, created_at";
  $from   = " FROM v_item_public";
  $countSql = "SELECT COUNT(*) AS cnt" . $from . $where;
  ```
- 이 뷰가 어떤 문항을 걸러 내는지는 이 폴더의 코드에 없다(뷰 정의는 범위 밖). 따라서 "어떤 문항이 검색에서 빠지는가"는 이 폴더만으로 확정할 수 없다. 확인된 사실은 검색 SQL 자체에 상태 조건이 없고 전적으로 뷰에 의존한다는 것뿐이다.
- 단원 목록(`unit`), 태그 목록(`tag`)은 이 뷰와 무관하게 전체를 읽는다(`search.php:156`, `178`).

### CX-10 단원 목록의 "공개 문항 수"는 상태가 `'A'` 인 문항만 센다
- 근거: `units.php:15-17`
- 코드:
  ```php
  // 단원별 공개(status='A') 문항 수. 삭제 · 검수중 문항은 세지 않는다.
  (SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
  ```
- 상태 `'A'` 이외는 모두 세지 않는다. 이 폴더 코드에서 확인되는 상태 값은 `'A'`(units.php)와 `'R'`(register.php:94) 두 가지다. 주석의 "삭제"에 해당하는 상태 값은 코드에 나오지 않는다.

---

## C. 기본 정렬과 정렬 옵션

### CX-11 정렬을 지정하지 않으면 난이도 높은 순(level DESC), 같으면 ID 오름차순이다
- 근거: `search.php:317-320`
- 코드:
  ```php
  case '':
      // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
      $orderBy = " ORDER BY level DESC, id ASC";
  ```
- 알 수 없는 `sort` 값도 같은 정렬을 쓰고 경고만 붙인다(`search.php:322-326`). 단 CX-04 때문에 기본 목록에는 난이도 5가 나오지 않는다.

### CX-12 지원하는 정렬 기준은 id · title · unit · level · created 이고, 방향을 안 주면 기준마다 기본 방향이 다르다
- 근거: `search.php:276-315`, `328-331`
- 코드:
  ```php
  case 'unit':
      ... " ORDER BY unit_code ASC, level DESC, id ASC";
  case 'level':
      if ($dir === 'asc') { ... " ORDER BY level ASC, id ASC"; } else { ... " ORDER BY level DESC, id ASC";
  ```
- 방향 미지정 시: `id`, `title`, `unit` 은 오름차순, `level`, `created` 는 내림차순.
- 동순위 처리: `title` · `unit` · `level` · `created` 는 마지막에 `id ASC` 를 붙인다. `unit` 은 방향과 관계없이 `level DESC` 가 고정이다. `id DESC` 는 동순위 기준이 없다.
- `level` · `created` 는 `dir` 이 `asc` 일 때만 오름차순이고 나머지(빈값 포함)는 내림차순이다. `id` · `title` · `unit` 은 `desc` 일 때만 내림차순이다.

### CX-13 정렬 방향은 asc/desc 만 인정하고, 그 밖의 값은 경고 후 무시한다
- 근거: `search.php:266-273`
- 코드:
  ```php
  if ($dir !== 'asc' && $dir !== 'desc') {
      if ($dir !== '') {
          $warnings[] = '정렬 방향은 asc 또는 desc 만 가능합니다.';
  ```
- `sort`, `dir` 은 소문자로 바꿔 비교한다(`search.php:266-267`).

---

## D. 등록 시 검증 (`register.php`)

### CX-14 제목은 앞뒤 공백을 뺀 뒤 5자 이상 200자 이하여야 한다
- 근거: `register.php:41`, `49-54`
- 코드:
  ```php
  if (mb_strlen($title, 'UTF-8') < 5) {
      $errors[] = '제목은 5자 이상 입력해야 합니다.';
  }
  if (mb_strlen($title, 'UTF-8') > 200) {
  ```
- 글자 수는 앞뒤 공백 제거 후 기준이며, 중간 공백은 글자 수에 포함된다.

### CX-15 지문은 앞뒤 공백을 뺀 뒤 비어 있으면 안 된다
- 근거: `register.php:42`, `56-58`
- 코드:
  ```php
  if ($stem === '') {
      $errors[] = '지문을 입력해야 합니다.';
  ```
- 지문의 최소·최대 길이 검증은 코드에 없다.

### CX-16 단원은 필수이며, 단원 목록에 있는 ID여야 한다
- 근거: `register.php:59-68`
- 코드:
  ```php
  if ((string)$u['id'] === $unitId) {
      $unitOk = true;
  ...
  $errors[] = '단원을 선택해야 합니다.';
  ```
- 검색은 단원 **코드**(`unit_code`)로, 등록은 단원 **ID**(`unit_id`)로 다룬다.

### CX-17 난이도는 1~5 정수 한 자리만 허용한다
- 근거: `register.php:69-72`
- 코드:
  ```php
  if (!preg_match('/^[1-5]$/', $level)) {
      $errors[] = '난이도는 1~5 사이여야 합니다.';
  ```
- 등록에서는 검색(CX-06)과 달리 범위를 벗어난 값을 오류로 막는다.

### CX-18 목록에 없는 태그 ID는 오류 없이 조용히 버리고, 유효한 태그만 저장한다
- 근거: `register.php:73-81`, `104-112`
- 코드:
  ```php
  $validTagIds = array();
  foreach ($tagIds as $tid) {
      foreach ($tags as $t) {
          if ((string)$t['id'] === (string)$tid) {
              $validTagIds[] = (int)$tid;
  ```
- 태그는 필수가 아니며(0개 가능), 개수 제한도 코드에 없다. 오류가 하나라도 있으면 저장하지 않는다(`register.php:83`).

### CX-19 등록된 문항은 상태 `'R'` 로 저장되고, 새 ID는 현재 최대 ID + 1 이다
- 근거: `register.php:87`, `92-95`
- 코드:
  ```php
  $res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
  ...
  VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())
  ```
- 등록 화면 안내문은 "검수 완료 후 검색에 노출됩니다"(`register.php:116`)이다. 그러나 노출 여부를 결정하는 것은 `v_item_public` 뷰(CX-09)이며 이 폴더에서는 확인할 수 없다. 코드로 확인되는 사실은 `'R'` 로 저장된다는 것, 그리고 `'A'` 만 공개 문항 수에 잡힌다(CX-10)는 것이다.
- 문항 저장과 태그 저장은 한 트랜잭션이며, 실패하면 롤백한다(`register.php:85`, `114`, `120`).

---

## E. 범위 참고 (규칙 아님)

- **수정 기능이 없다.** 요청 범위의 "수정 시 검증"에 해당하는 코드는 이 폴더에 없다. 파일은 `index.php`, `search.php`, `register.php`, `units.php` 뿐이며 `UPDATE` 문이 없다. 따라서 수정 규칙은 작성하지 않았다.
- 검색 SQL이 읽는 `v_item_public` 의 정의(어떤 상태를 제외하는지)는 이 폴더 밖이라 읽지 않았다. 다른 문서와 비교할 때 CX-09, CX-19 는 "뷰 정의 확인 필요"로 남는다.
- 페이지 크기(20건)와 페이지 번호 상한(999)은 요청 범위 밖이라 규칙에서 뺐다(`search.php:334-349`).
