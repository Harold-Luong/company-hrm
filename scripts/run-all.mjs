import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

// Keep Bash alive while Maven/npm runs so Windows taskkill /T includes its JVMs.
// Values and command arguments are positional, never interpolated into shell code.
const launchScript = 'set -e; if [[ -f .env ]]; then set -a; source ./.env; set +a; fi; "$@"; status=$?; exit "$status"';

async function stopTree(child) {
    if (!child.pid) return;
    if (process.platform === 'win32') {
        if (child.exitCode !== null || child.signalCode !== null) return;
        await new Promise((resolve, reject) => {
            const killer = spawn('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true });
            let details = '';
            killer.stdout.on('data', data => { details += data; });
            killer.stderr.on('data', data => { details += data; });
            killer.once('error', reject);
            killer.once('exit', code => {
                if (code === 0 || child.exitCode !== null || child.signalCode !== null) resolve();
                else reject(new Error(`Không dừng được cây tiến trình ${child.pid}: ${details.trim()}`));
            });
        });
    } else {
        // detached creates a separate Unix process group without an external setsid.
        try { process.kill(-child.pid, 'SIGTERM'); }
        catch (error) { if (error.code !== 'ESRCH') throw error; }
    }
}

export async function runApplications({ apps, bash, signal, log = console.log }) {
    const children = [];
    let resolveStop;
    const stopped = new Promise(resolve => { resolveStop = resolve; });
    const interrupt = () => resolveStop(130);
    const terminate = () => resolveStop(143);
    process.once('SIGINT', interrupt);
    process.once('SIGTERM', terminate);
    signal?.addEventListener('abort', interrupt, { once: true });
    if (signal?.aborted) interrupt();
    try {
        if (!signal?.aborted) for (const app of apps) {
            log(`[run-all] Khởi động ${app.name}`);
            const child = spawn(bash, ['-c', launchScript, '_', ...app.command], {
                cwd: app.directory,
                detached: true,
                windowsHide: true,
                // Preserve cmd.exe switches when called through Git Bash.
                env: process.platform === 'win32'
                    ? { ...process.env, MSYS2_ARG_CONV_EXCL: '*' } : process.env,
                stdio: ['ignore', 'pipe', 'pipe'],
            });
            children.push(child);
            for (const stream of [child.stdout, child.stderr]) {
                createInterface({ input: stream }).on('line', line => log(`[${app.name}] ${line}`));
            }
            child.once('error', error => {
                log(`[run-all] Không chạy được ${app.name}: ${error.message}`);
                resolveStop(1);
            });
            child.once('exit', (code, exitSignal) => {
                log(`[run-all] ${app.name} đã thoát (${code ?? exitSignal}).`);
                resolveStop(code ?? 1);
            });
        }
        log(`[run-all] Đã khởi chạy ${children.length} ứng dụng; chờ log sẵn sàng. Nhấn Ctrl+C để dừng toàn bộ.`);
        const status = await stopped;
        log('[run-all] Đang dừng tất cả ứng dụng...');
        const results = await Promise.allSettled(children.map(stopTree));
        const errors = results.filter(result => result.status === 'rejected');
        for (const error of errors) log(`[run-all] ${error.reason.message}`);
        return errors.length ? 1 : status;
    } finally {
        process.removeListener('SIGINT', interrupt);
        process.removeListener('SIGTERM', terminate);
        signal?.removeEventListener('abort', interrupt);
    }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
    const root = fileURLToPath(new URL('../', import.meta.url));
    const modules = [
        ['auth', 'auth-service-main'], ['employee', 'employee-service'],
        ['calendar', 'calendar-service'], ['gateway', 'gateway'],
        ['leave', 'leave-service'], ['attendance', 'attendance-service'],
        ['notification', 'notification-service'],
    ];
    const apps = modules.map(([name, directory]) => ({
        name, directory: path.join(root, 'back-end/modules', directory),
        // MSYS exec in the Unix Maven/npm scripts can orphan native processes
        // on Windows. Batch entrypoints keep cmd.exe alive as their parent.
        command: process.platform === 'win32'
            ? ['cmd.exe', '/d', '/c', name === 'notification' ? 'mvn.cmd' : '.\\mvnw.cmd', 'spring-boot:run']
            : [name === 'notification' ? 'mvn' : './mvnw', 'spring-boot:run'],
    }));
    apps.push({ name: 'frontend', directory: path.join(root, 'frontend'), command: process.platform === 'win32'
        ? ['cmd.exe', '/d', '/c', 'npm.cmd', 'run', 'dev'] : ['npm', 'run', 'dev'] });
    console.log('[run-all] Frontend: http://localhost:5173, Gateway: http://localhost:8080');
    process.exitCode = await runApplications({ apps, bash: process.argv[2] || 'bash' });
}
