package com.resolveiq.common.exception;

import java.util.Map;

/** The technician already holds their maximum number of concurrent active incidents. */
public class WorkloadExceededException extends ItsmException {

    private final String technicianName;
    private final int activeIncidents;
    private final int capacity;

    public WorkloadExceededException(String technicianName, int activeIncidents, int capacity) {
        super(ErrorCode.WORKLOAD_EXCEEDED, technicianName + " already has " + activeIncidents
                + " active incidents (capacity " + capacity + ")");
        this.technicianName = technicianName;
        this.activeIncidents = activeIncidents;
        this.capacity = capacity;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("technician", technicianName, "activeIncidents", activeIncidents, "capacity", capacity);
    }
}
