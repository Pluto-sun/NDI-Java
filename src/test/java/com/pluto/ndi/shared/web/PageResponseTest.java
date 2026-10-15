package com.pluto.ndi.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    void mapsSpringDataPageWithoutLeakingItsType() {
        var page = new PageImpl<>(List.of("first", "second"), PageRequest.of(2, 10), 22);

        var response = PageResponse.from(page);

        assertThat(response.items()).containsExactly("first", "second");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(22);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThatThrownBy(() -> response.items().add("third"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
