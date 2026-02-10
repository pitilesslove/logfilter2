/**
 * Log Store Service
 * 로그 데이터를 메모리에 저장하고 공유
 */

class LogStore {
    constructor() {
        this.logs = [];
    }

    /**
     * 로그 설정 (기존 로그 교체)
     */
    setLogs(logs) {
        this.logs = logs;
    }

    /**
     * 로그 추가
     */
    addLog(log) {
        this.logs.push(log);
    }

    /**
     * 여러 로그 추가
     */
    addLogs(logs) {
        this.logs.push(...logs);
    }

    /**
     * 모든 로그 조회
     */
    getLogs() {
        return this.logs;
    }

    /**
     * 로그 클리어
     */
    clear() {
        this.logs = [];
    }

    /**
     * 로그 수 조회
     */
    count() {
        return this.logs.length;
    }
}

module.exports = new LogStore();
