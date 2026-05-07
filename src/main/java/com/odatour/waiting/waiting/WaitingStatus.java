package com.odatour.waiting.waiting;

public enum WaitingStatus {
    WAITING("대기 중"),
    CALLED("호출 완료"),
    ENTERED("입장 완료"),
    NO_SHOW("노쇼 처리"),
    CANCELED("취소 완료");

    private final String label;

    WaitingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean active() {
        return this == WAITING || this == CALLED;
    }
}
