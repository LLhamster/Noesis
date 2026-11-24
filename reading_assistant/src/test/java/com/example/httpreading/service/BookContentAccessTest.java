package com.example.httpreading.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.httpreading.common.web.BusinessException;
import com.example.httpreading.domain.entity.Books;
import com.example.httpreading.repository.BooksRepository;

class BookContentAccessTest {
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cloudTextIsVisibleOnlyToItsOwner() {
        BooksRepository books = mock(BooksRepository.class);
        Books privateBook = new Books();
        privateBook.setId(1L);
        privateBook.setCloudOwnerId(10L);
        Books publicBook = new Books();
        publicBook.setId(2L);
        when(books.findById(1L)).thenReturn(Optional.of(privateBook));
        when(books.findAllById(List.of(1L, 2L))).thenReturn(List.of(privateBook, publicBook));
        BookContentAccess access = new BookContentAccess(books);

        assertThrows(BusinessException.class, () -> access.requireReadable(1L));
        assertEquals(Set.of(2L), access.readableIds(List.of(1L, 2L)));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(10L, null, List.of()));
        assertDoesNotThrow(() -> access.requireReadable(1L));
        assertEquals(Set.of(1L, 2L), access.readableIds(List.of(1L, 2L)));
    }
}
