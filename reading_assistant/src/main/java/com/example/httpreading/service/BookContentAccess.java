package com.example.httpreading.service;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.httpreading.api.ErrorCode;
import com.example.httpreading.domain.entity.Books;
import com.example.httpreading.repository.BooksRepository;

@Service
public class BookContentAccess {
    private final BooksRepository books;

    public BookContentAccess(BooksRepository books) {
        this.books = books;
    }

    public void requireReadable(Long bookId) {
        if (books.findById(bookId).filter(book -> !readable(book, currentUserId())).isPresent()) {
            throw ErrorCode.BOOK_NOT_FOUND.toException();
        }
    }

    public Set<Long> readableIds(Collection<Long> bookIds) {
        Long userId = currentUserId();
        return books.findAllById(bookIds.stream().filter(Objects::nonNull).distinct().toList())
                .stream().filter(book -> readable(book, userId)).map(Books::getId)
                .collect(Collectors.toSet());
    }

    private static boolean readable(Books book, Long userId) {
        return book.getCloudOwnerId() == null || book.getCloudOwnerId().equals(userId);
    }

    private static Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof Long id ? id : null;
    }
}
