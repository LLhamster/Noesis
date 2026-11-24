package com.example.httpreading.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.httpreading.service.BookFileDownloadService;
import com.example.httpreading.service.BookFileDownloadService.BookFileDownload;

class ClientBooksControllerTest {

    @TempDir
    Path tempDir;

    private BookFileDownloadService downloadService;
    private MockMvc mockMvc;
    private Path source;

    @BeforeEach
    void setUp() throws Exception {
        downloadService = org.mockito.Mockito.mock(BookFileDownloadService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ClientBooksController(downloadService))
            .build();
        source = tempDir.resolve("original.epub");
        Files.writeString(source, "0123456789");
        when(downloadService.getDownload(1L)).thenReturn(new BookFileDownload(
            source,
            "application/epub+zip",
            "测试阅读.epub",
            10,
            "\"book-hash\""));
    }

    @Test
    void returnsFullSourceWithDownloadAndCacheHeaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/client/books/1/file"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/epub+zip"))
            .andExpect(content().string("0123456789"))
            .andExpect(header().string("ETag", "\"book-hash\""))
            .andExpect(header().string("Accept-Ranges", "bytes"))
            .andExpect(header().string("Content-Length", "10"))
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(header().string("Content-Disposition", containsString(".epub")))
            .andExpect(header().string("Cache-Control", containsString("private")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void returnsSingleByteRange() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/api/client/books/1/file").header("Range", "bytes=2-5"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isPartialContent())
            .andExpect(content().string("2345"))
            .andExpect(header().string("Content-Range", "bytes 2-5/10"))
            .andExpect(header().string("Content-Length", "4"));
    }

    @Test
    void rejectsUnsatisfiableOrMultipleRanges() throws Exception {
        mockMvc.perform(get("/api/client/books/1/file").header("Range", "bytes=20-30"))
            .andExpect(status().isRequestedRangeNotSatisfiable())
            .andExpect(header().string("Content-Range", "bytes */10"));

        mockMvc.perform(get("/api/client/books/1/file").header("Range", "bytes=0-1,4-5"))
            .andExpect(status().isRequestedRangeNotSatisfiable())
            .andExpect(header().string("Content-Range", "bytes */10"));
    }

    @Test
    void honorsConditionalAndIfRangeRequests() throws Exception {
        mockMvc.perform(get("/api/client/books/1/file")
                .header("If-None-Match", "\"book-hash\""))
            .andExpect(status().isNotModified())
            .andExpect(content().string(""));

        MvcResult result = mockMvc.perform(get("/api/client/books/1/file")
                .header("Range", "bytes=2-5")
                .header("If-Range", "\"older-hash\""))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content().string("0123456789"));

        MvcResult weakResult = mockMvc.perform(get("/api/client/books/1/file")
                .header("Range", "bytes=2-5")
                .header("If-Range", "W/\"book-hash\""))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(weakResult))
            .andExpect(status().isOk())
            .andExpect(content().string("0123456789"));
    }
}
