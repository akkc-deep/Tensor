package com.akkc.tensor.plugin.api.error;

public final class CalendarUnconfirmedException extends TensorException {
    public CalendarUnconfirmedException() {
        super(ErrorCode.CALENDAR_UNCONFIRMED, "Applicable calendars are unconfirmed");
    }
}
