package com.resolveiq.attachment;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Optional;

/**
 * Works out what a file really is from its first bytes ("magic numbers"), not from the name or the
 * browser-supplied type, both of which an attacker controls. Only an allow-list of harmless types is
 * accepted: common images, PDF, plain text/log/CSV, and Office documents. SVG and HTML are refused
 * because they can contain scripts.
 */
public final class FileTypeDetector {

    /** An accepted type with the extensions that are allowed to carry it. */
    public enum FileType {
        PNG("image/png", "png"),
        JPEG("image/jpeg", "jpg", "jpeg"),
        GIF("image/gif", "gif"),
        WEBP("image/webp", "webp"),
        PDF("application/pdf", "pdf"),
        TEXT("text/plain", "txt", "log", "csv", "json", "xml", "yml", "yaml", "md"),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
        PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");

        private final String mime;
        private final String[] extensions;

        FileType(String mime, String... extensions) {
            this.mime = mime;
            this.extensions = extensions;
        }

        public String mime() {
            return mime;
        }

        boolean allowsExtension(String ext) {
            for (String e : extensions) {
                if (e.equals(ext)) {
                    return true;
                }
            }
            return false;
        }
    }

    private FileTypeDetector() {
    }

    public static Optional<FileType> detect(byte[] data, String fileName) {
        String ext = extension(fileName);
        FileType byContent = sniff(data, ext);
        if (byContent == null || !byContent.allowsExtension(ext)) {
            return Optional.empty();
        }
        return Optional.of(byContent);
    }

    private static FileType sniff(byte[] d, String ext) {
        if (starts(d, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return FileType.PNG;
        }
        if (starts(d, 0xFF, 0xD8, 0xFF)) {
            return FileType.JPEG;
        }
        if (starts(d, 'G', 'I', 'F', '8')) {
            return FileType.GIF;
        }
        if (d.length >= 12 && starts(d, 'R', 'I', 'F', 'F') && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return FileType.WEBP;
        }
        if (starts(d, '%', 'P', 'D', 'F', '-')) {
            return FileType.PDF;
        }
        if (starts(d, 'P', 'K', 0x03, 0x04)) {
            // Office Open XML documents are ZIP files; the extension says which one
            return switch (ext) {
                case "docx" -> FileType.DOCX;
                case "xlsx" -> FileType.XLSX;
                case "pptx" -> FileType.PPTX;
                default -> null; // a bare ZIP archive is not accepted
            };
        }
        return isPlainText(d) ? FileType.TEXT : null;
    }

    /** Valid UTF-8 with no NUL bytes and no markup that a browser would run. */
    private static boolean isPlainText(byte[] d) {
        for (byte b : d) {
            if (b == 0) {
                return false;
            }
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(d)).toString();
            String head = text.substring(0, Math.min(text.length(), 1024)).toLowerCase(Locale.ROOT);
            return !head.contains("<script") && !head.contains("<html") && !head.contains("<svg") && !head.contains("<!doctype html");
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static boolean starts(byte[] d, int... magic) {
        if (d.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((d[i] & 0xFF) != (magic[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    static String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
