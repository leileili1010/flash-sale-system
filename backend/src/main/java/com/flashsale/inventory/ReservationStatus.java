package com.flashsale.inventory;

import com.flashsale.common.error.ErrorCode;

/** Outcome codes returned by {@code reserve.lua}, in script order. */
public enum ReservationStatus {
    RESERVED(null),
    SOLD_OUT(ErrorCode.SOLD_OUT),
    ALREADY_PURCHASED(ErrorCode.ALREADY_PURCHASED),
    NOT_STARTED(ErrorCode.DROP_NOT_STARTED),
    ENDED(ErrorCode.DROP_ENDED),
    /** The drop is not loaded into Redis: unknown, unpublished, or not yet preheated. */
    NOT_LOADED(ErrorCode.NOT_FOUND);

    private final ErrorCode errorCode;

    ReservationStatus(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    /** The API error for a rejected reservation; {@code null} when reserved. */
    public ErrorCode errorCode() {
        return errorCode;
    }

    static ReservationStatus fromScriptCode(long code) {
        return values()[(int) code];
    }
}
