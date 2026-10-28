package com.resolveiq.common.exception;

public class ServiceNotFoundException extends ResourceNotFoundException {

    public ServiceNotFoundException(String serviceCode) {
        super(ErrorCode.SERVICE_NOT_FOUND, "IT service '" + serviceCode + "' was not found");
    }
}
