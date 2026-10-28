package com.resolveiq.common.exception;

/** Neither a service-specific nor a default SLA policy exists for the incident's priority. */
public class MissingSlaConfigurationException extends ItsmException {

    public MissingSlaConfigurationException(String serviceName, String priority) {
        super(ErrorCode.MISSING_SLA_CONFIGURATION,
                "No active SLA policy is configured for priority " + priority + " (service: " + serviceName
                        + "). Ask an administrator to configure one.");
    }
}
