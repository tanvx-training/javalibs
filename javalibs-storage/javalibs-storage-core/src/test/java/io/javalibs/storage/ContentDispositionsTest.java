package io.javalibs.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentDispositionsTest {

    @Test
    void encodesVietnameseFileNamePerRfc5987() {
        String d = ContentDispositions.attachment("báo cáo quý.pdf");
        assertThat(d).startsWith("attachment; ");
        assertThat(d).contains("filename=\"b_o c_o qu_.pdf\"");
        assertThat(d).contains("filename*=UTF-8''b%C3%A1o%20c%C3%A1o%20qu%C3%BD.pdf");
    }

    @Test
    void inlineKeepsAsciiNameIntact() {
        assertThat(ContentDispositions.inline("report.pdf"))
                .isEqualTo("inline; filename=\"report.pdf\"; filename*=UTF-8''report.pdf");
    }

    @Test
    void stripsQuotesFromFallbackName() {
        assertThat(ContentDispositions.attachment("a\"b.pdf")).doesNotContain("\"a\"b");
    }
}
