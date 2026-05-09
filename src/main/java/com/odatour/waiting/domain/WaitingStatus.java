package com.odatour.waiting.domain;

public enum WaitingStatus {
    WAITING("대기중"),
    CALLED("호출됨"),
    ARRIVED("현장도착"),
    ENTERED("입장완료"),
    NO_SHOWED("노쇼"),
    CANCELED("취소");

    private final String label;

    WaitingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean active() {
        return this == WAITING || this == CALLED || this == ARRIVED;
    }

    public boolean cancellable() {
        return this == WAITING;
    }
}
