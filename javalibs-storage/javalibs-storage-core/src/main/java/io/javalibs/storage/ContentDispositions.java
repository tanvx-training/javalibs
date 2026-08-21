package io.javalibs.storage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds {@code Content-Disposition} values carrying non-ASCII file names safely:
 * an ASCII fallback in {@code filename} plus the RFC 5987 encoded {@code filename*}.
 */
public final class ContentDispositions {

    private ContentDispositions() {
    }

    public static String attachment(String fileName) {
        return of("attachment", fileName);
    }

    public static String inline(String fileName) {
        return of("inline", fileName);
    }

    private static String of(String type, String fileName) {
        String ascii = fileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return type + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
