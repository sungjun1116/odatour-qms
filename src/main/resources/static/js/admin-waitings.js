(function () {
    const overdueMinutes = 10;
    const minuteMs = 60 * 1000;
    const overdueMs = overdueMinutes * minuteMs;

    function elapsedLabel(elapsedMs) {
        const elapsedMinutes = Math.max(Math.floor(elapsedMs / minuteMs), 0);
        const hours = Math.floor(elapsedMinutes / 60);
        const minutes = elapsedMinutes % 60;

        if (hours === 0) {
            return `${minutes}분 경과`;
        }
        if (minutes === 0) {
            return `${hours}시간 경과`;
        }
        return `${hours}시간 ${minutes}분 경과`;
    }

    function updateCalledCards() {
        document.querySelectorAll('[data-called-at]').forEach((card) => {
            const calledAt = new Date(card.dataset.calledAt);
            if (Number.isNaN(calledAt.getTime())) {
                return;
            }

            const elapsedMs = Date.now() - calledAt.getTime();
            const elapsed = card.querySelector('[data-call-elapsed]');
            if (elapsed) {
                elapsed.textContent = elapsedLabel(elapsedMs);
            }

            const overdueLabel = card.querySelector('[data-overdue-label]');
            if (overdueLabel && elapsedMs >= overdueMs) {
                overdueLabel.classList.remove('hidden');
                card.dataset.callOverdue = 'true';
            }
        });
    }

    updateCalledCards();
    window.setInterval(updateCalledCards, 1000);
}());
