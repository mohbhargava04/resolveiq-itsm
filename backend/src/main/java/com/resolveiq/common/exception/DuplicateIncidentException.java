package com.resolveiq.common.exception;

import java.util.List;
import java.util.Map;

/**
 * The same reporter submitted an incident that is almost identical to one of their own open
 * incidents. The client can show the matches and resubmit with {@code acknowledgeDuplicates=true}.
 */
public class DuplicateIncidentException extends ItsmException {

    private final List<Map<String, Object>> matches;

    public DuplicateIncidentException(List<Map<String, Object>> matches) {
        super(ErrorCode.DUPLICATE_INCIDENT,
                "You already have an open incident describing the same problem");
        this.matches = List.copyOf(matches);
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("matches", matches);
    }
}
