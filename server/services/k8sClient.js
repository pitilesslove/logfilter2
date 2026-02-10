/**
 * K8s Client Service
 * kubectl CLI를 사용하여 K8s 클러스터와 통신
 */
const { spawn, execSync } = require('child_process');

class K8sClient {
    /**
     * kubectl 명령 실행 (동기)
     */
    execKubectl(args) {
        try {
            const result = execSync(`kubectl ${args.join(' ')}`, {
                encoding: 'utf-8',
                timeout: 30000
            });
            return result.trim();
        } catch (error) {
            console.error(`kubectl 실행 실패: ${error.message}`);
            throw error;
        }
    }

    /**
     * 모든 context 목록 조회
     */
    getContexts() {
        try {
            const output = this.execKubectl(['config', 'get-contexts', '-o', 'name']);
            return output.split('\n').filter(c => c.trim());
        } catch {
            return [];
        }
    }

    /**
     * 현재 context 조회
     */
    getCurrentContext() {
        try {
            return this.execKubectl(['config', 'current-context']);
        } catch {
            return '';
        }
    }

    /**
     * Namespace 목록 조회
     */
    getNamespaces(context = '') {
        try {
            const args = ['get', 'namespaces', '-o', 'jsonpath={.items[*].metadata.name}'];
            if (context) args.push('--context', context);

            const output = this.execKubectl(args);
            return output.split(' ').filter(n => n.trim());
        } catch {
            return ['default'];
        }
    }

    /**
     * Pod 목록 조회
     */
    getPods(namespace = 'default', context = '') {
        try {
            const args = ['get', 'pods', '-n', namespace, '-o', 'json'];
            if (context) args.push('--context', context);

            const output = this.execKubectl(args);
            const data = JSON.parse(output);

            return data.items.map(pod => {
                const containerStatus = pod.status.containerStatuses?.[0];
                return {
                    name: pod.metadata.name,
                    status: pod.status.phase || 'Unknown',
                    ready: containerStatus?.ready ? '1/1' : '0/1',
                    restarts: containerStatus?.restartCount || 0,
                    node: pod.spec.nodeName || ''
                };
            });
        } catch (error) {
            console.error('getPods error:', error.message);
            return [];
        }
    }

    /**
     * Pod 로그 조회 (일회성)
     */
    getLogs(pod, namespace = 'default', context = '', tail = 5000) {
        try {
            const args = ['logs', pod, '-n', namespace, `--tail=${tail}`];
            if (context) args.push('--context', context);

            const output = this.execKubectl(args);
            return output.split('\n');
        } catch (error) {
            return [`Error: ${error.message}`];
        }
    }

    /**
     * Pod 전체 로그 조회 (tail 없음)
     */
    getAllLogs(pod, namespace = 'default', context = '') {
        try {
            const args = ['logs', pod, '-n', namespace];
            if (context) args.push('--context', context);

            // 전체 로그는 시간이 오래 걸릴 수 있으므로 타임아웃 증가
            const result = execSync(`kubectl ${args.join(' ')}`, {
                encoding: 'utf-8',
                timeout: 120000,  // 2분
                maxBuffer: 100 * 1024 * 1024  // 100MB
            });
            return result.trim().split('\n');
        } catch (error) {
            return [`Error: ${error.message}`];
        }
    }

    /**
     * Pod 로그 스트리밍 (SSE용)
     * @returns {ChildProcess} kubectl 프로세스
     */
    streamLogs(pod, namespace = 'default', context = '', tail = 5000) {
        const args = ['logs', '-f', pod, '-n', namespace, `--tail=${tail}`];
        if (context) args.push('--context', context);

        console.log(`[K8s] 스트리밍 시작: kubectl ${args.join(' ')}`);

        const process = spawn('kubectl', args, {
            stdio: ['ignore', 'pipe', 'pipe']
        });

        return process;
    }
}

module.exports = new K8sClient();
