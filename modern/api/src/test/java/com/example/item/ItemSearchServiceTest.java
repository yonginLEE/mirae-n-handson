package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemSearchService itemSearchService;

    @Test
    @DisplayName("search: 건수와 행을 담고, 결과가 있으면 message 는 null")
    void searchReturnsRowsAndCount() {
        ItemSearchRow row = new ItemSearchRow(12, "대분수의 덧셈", "M5-1", 3, List.of("계산", "오답률높음"));
        when(itemRepository.countBySearch(any())).thenReturn(1L);
        when(itemRepository.findPageBySearch(any())).thenReturn(List.of(row));

        ItemSearchResponse response = itemSearchService.search(Map.of("unit", List.of("M5-1")));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.rows()).containsExactly(row);
        assertThat(response.message()).isNull();
    }

    @Test
    @DisplayName("search: 0건이면 오류가 아니라 \"검색 결과가 없습니다\"")
    void searchWithNoResultReturnsMessage() {
        when(itemRepository.countBySearch(any())).thenReturn(0L);
        when(itemRepository.findPageBySearch(any())).thenReturn(List.of());

        ItemSearchResponse response = itemSearchService.search(Map.of("unit", List.of("X9-9")));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.count()).isZero();
        assertThat(response.rows()).isEmpty();
        assertThat(response.message()).isEqualTo("검색 결과가 없습니다");
    }

    @Test
    @DisplayName("search: 건수는 있고 페이지가 넘치면 빈 행에 message 는 null")
    void searchBeyondLastPageKeepsCount() {
        when(itemRepository.countBySearch(any())).thenReturn(20L);
        when(itemRepository.findPageBySearch(any())).thenReturn(List.of());

        ItemSearchResponse response = itemSearchService.search(Map.of("page", List.of("999")));

        assertThat(response.count()).isEqualTo(20);
        assertThat(response.rows()).isEmpty();
        assertThat(response.message()).isNull();
        assertThat(response.page()).isEqualTo(999);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("search: 페이지 메타 · 정렬 표시 · 경고를 응답에 담는다")
    void searchReturnsMetaAndWarnings() {
        when(itemRepository.countBySearch(any())).thenReturn(41L);
        when(itemRepository.findPageBySearch(any())).thenReturn(List.of());
        when(itemRepository.existsUnitCode("m5-1")).thenReturn(true);
        when(itemRepository.findAllTagNames()).thenReturn(List.of("계산", "개념"));

        ItemSearchResponse response = itemSearchService.search(Map.of(
            "unit", List.of("m5-1"), "tag", List.of("계산"), "sort", List.of("level"), "page", List.of("2")));

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.sort()).isEqualTo("level");
        assertThat(response.dir()).isEqualTo("desc");
        assertThat(response.warnings()).containsExactly("단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)");
    }

    @Test
    @DisplayName("search: 0건이면 totalPages 0, 미등록 단원 · 태그는 경고")
    void searchWithUnknownUnitAndTagWarns() {
        when(itemRepository.countBySearch(any())).thenReturn(0L);
        when(itemRepository.findPageBySearch(any())).thenReturn(List.of());
        when(itemRepository.existsUnitCode("X9-9")).thenReturn(false);
        when(itemRepository.findAllTagNames()).thenReturn(List.of("계산"));

        ItemSearchResponse response = itemSearchService.search(Map.of("unit", List.of("X9-9"), "tag", List.of("없음")));

        assertThat(response.totalPages()).isZero();
        assertThat(response.warnings())
            .containsExactly("등록되지 않은 단원 코드입니다: X9-9", "등록되지 않은 태그입니다: 없음");
    }

    @Test
    @DisplayName("splitTags: 쉼표로 나누고 NULL 은 빈 목록")
    void splitTags() {
        assertThat(ItemSearchRepositoryImpl.splitTags("계산,오답률높음")).containsExactly("계산", "오답률높음");
        assertThat(ItemSearchRepositoryImpl.splitTags(null)).isEmpty();
    }
}
