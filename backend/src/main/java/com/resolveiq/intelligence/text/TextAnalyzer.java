package com.resolveiq.intelligence.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Natural-language preprocessing shared by every intelligent feature:
 * lower-casing → tokenising → stop-word removal → light stemming → synonym normalisation.
 *
 * <p>Synonym normalisation is what lets "payment application unavailable", "transaction failing"
 * and "unable to pay" be recognised as the same problem: all three map onto the canonical terms
 * {@code payment} and {@code outage}.</p>
 */
public final class TextAnalyzer {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "or", "but", "if", "then", "so", "of", "to", "in", "on", "at", "for", "from",
            "by", "with", "about", "as", "into", "is", "are", "was", "were", "be", "been", "being", "am", "do",
            "does", "did", "doing", "have", "has", "had", "having", "it", "its", "this", "that", "these", "those",
            "i", "me", "my", "we", "our", "us", "you", "your", "he", "she", "they", "them", "their", "his", "her",
            "what", "which", "who", "whom", "when", "where", "why", "how", "all", "any", "both", "each", "few",
            "more", "most", "other", "some", "such", "no", "nor", "only", "own", "same", "than", "too", "very",
            "can", "will", "just", "should", "would", "could", "now", "also", "please", "hi", "hello", "team",
            "since", "today", "morning", "still", "again", "getting", "get", "got", "seems", "seem", "anyone",
            "help", "urgent", "asap", "issue", "problem", "facing", "able", "while", "after", "before", "there",
            "here", "one", "two", "every", "everyone", "users", "user", "people", "some", "being", "make", "use",
            "using", "used", "trying", "try", "tried", "keep", "keeps", "showing", "shows", "show"));

    private static final Map<String, String> SYNONYMS = new HashMap<>();

    static {
        group("outage", "down", "outage", "outages", "unavailable", "unreachable", "offline", "inaccessible", "crash",
                "crashed", "crashes", "crashing", "fail", "fails", "failing", "failed", "failure", "failures", "broken",
                "dead", "unable", "cannot", "cant", "unresponsive", "stopped", "working", "won't", "wont", "blank");
        group("payment", "payment", "payments", "pay", "paying", "paid", "transaction", "transactions", "checkout",
                "upi", "card", "cards", "billing", "refund", "refunds", "invoice", "gateway");
        group("login", "login", "logins", "logon", "signin", "sign", "authenticate", "authentication", "auth", "sso",
                "credential", "credentials", "password", "passwords", "otp", "mfa", "2fa", "locked", "lockout",
                "account", "accounts");
        group("slow", "slow", "slowness", "sluggish", "latency", "lag", "laggy", "lagging", "timeout", "timeouts",
                "timing", "delay", "delayed", "delays", "hang", "hangs", "hanging", "freeze", "freezes", "freezing",
                "loading", "spinning");
        group("network", "network", "networks", "wifi", "wi", "fi", "internet", "connectivity", "connection",
                "connections", "lan", "ethernet", "router", "dns", "packet", "bandwidth");
        group("vpn", "vpn", "remote", "tunnel", "anyconnect", "forticlient");
        group("email", "email", "emails", "mail", "mails", "outlook", "inbox", "smtp", "mailbox");
        group("database", "database", "databases", "db", "sql", "postgres", "postgresql", "mysql", "oracle", "query",
                "queries", "deadlock", "replication");
        group("printer", "printer", "printers", "print", "printing", "printed", "toner", "scanner");
        group("storage", "storage", "disk", "disks", "drive", "drives", "onedrive", "sharepoint", "quota", "space",
                "upload", "uploads", "backup", "backups", "bucket", "s3", "file", "files");
        group("laptop", "laptop", "laptops", "computer", "pc", "desktop", "machine", "workstation", "monitor",
                "keyboard", "mouse", "screen", "battery", "charger");
        group("security", "phishing", "malware", "virus", "ransomware", "breach", "hacked", "suspicious", "spam",
                "compromised", "unauthorized", "unauthorised", "attack", "security", "intrusion");
        group("cloud", "cloud", "aws", "azure", "gcp", "kubernetes", "k8s", "container", "containers", "vm", "instance",
                "instances", "server", "servers");
        group("software", "software", "install", "installation", "installed", "update", "updates", "upgrade",
                "license", "licence", "patch", "excel", "word", "teams", "zoom", "office");
    }

    private TextAnalyzer() {
    }

    private static void group(String canonical, String... words) {
        for (String w : words) {
            SYNONYMS.put(w, canonical);
        }
    }

    /** Converts free text into normalised tokens (duplicates kept - frequency matters for TF-IDF). */
    public static List<String> analyze(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String cleaned = text.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", " ");
        for (String raw : cleaned.split("\\s+")) {
            if (raw.length() < 2 || STOP_WORDS.contains(raw) || raw.chars().allMatch(Character::isDigit)) {
                continue;
            }
            String canonical = SYNONYMS.get(raw);
            if (canonical == null) {
                String stemmed = stem(raw);
                canonical = SYNONYMS.getOrDefault(stemmed, stemmed);
            }
            if (canonical.length() >= 2 && !STOP_WORDS.contains(canonical)) {
                tokens.add(canonical);
            }
        }
        return tokens;
    }

    /** A deliberately light suffix stripper (a full Porter stemmer is unnecessary for short incident text). */
    static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) {
            return w.substring(0, w.length() - 3);
        }
        if (w.length() > 4 && w.endsWith("ied")) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.length() > 4 && w.endsWith("ed")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.length() > 4 && w.endsWith("ies")) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.length() > 4 && (w.endsWith("sses") || w.endsWith("xes") || w.endsWith("ches") || w.endsWith("shes"))) {
            return w.substring(0, w.length() - 2);
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }
}
