package com.resolveiq.incident;

import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Urgency;

/**
 * Input for reporting an incident. {@code categoryCode} is optional: when absent the AI classifier
 * picks one. {@code acknowledgeDuplicates} lets a reporter submit anyway after seeing a duplicate warning.
 */
public record ReportIncidentCommand(String title, String description, String serviceCode, String categoryCode,
                                    Impact impact, Urgency urgency, Integer affectedUsers,
                                    boolean acknowledgeDuplicates) {
}
