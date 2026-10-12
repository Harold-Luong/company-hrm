import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { mkdir, mkdtemp, readFile, writeFile, rm } from 'node:fs/promises';
import path from 'node:path';
import { setTimeout as sleep } from 'node:timers/promises';
import { runApplications } from './run-all.mjs';

const bash = process.env.GIT_BASH || (process.platform === 'win32'
    ? path.join(process.env.ProgramFiles || 'C:/Program Files', 'Git/usr/bin/bash.exe') : '/bin/bash');
const scratch = path.resolve('.cache/run-all-tests');
async function until(check) {
    const end = Date.now() + 15000;
    while (!await check()) {
        assert.ok(Date.now() < end, 'Timed out waiting for test application');
        await sleep(50);
    }
}
function alive(pid) {
    try { process.kill(pid, 0); return true; }
    catch (error) { if (error.code === 'ESRCH') return false; throw error; }
}

for (const entrypoint of process.platform === 'win32' ? ['native', 'batch'] : ['native'])
for (const mode of ['application exit', 'SIGINT']) test(`Git Bash launcher (${entrypoint}): ${mode} stops native descendants`, {
    skip: !existsSync(bash), timeout: 30000,
}, async t => {
    await mkdir(scratch, { recursive: true });
    const directory = await mkdtemp(path.join(scratch, 'apps with spaces-'));
    const abort = new AbortController();
    let done;
    t.after(async () => {
        abort.abort();
        await done;
        assert.equal(path.dirname(directory), scratch);
        assert.ok(path.basename(directory).startsWith('apps with spaces-'));
        await rm(directory, { recursive: true, force: true });
    });
    const worker = path.join(directory, 'worker.mjs');
    await writeFile(worker, `
        import { spawn } from 'node:child_process';
        import fs from 'node:fs';
        const child = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { stdio: 'ignore' });
        fs.writeFileSync('ready.json', JSON.stringify({ pid: process.pid, descendant: child.pid,
            value: process.env.APP_VALUE, arg: process.argv[2], cwd: process.cwd() }));
        setInterval(() => { if (fs.existsSync('exit-now')) process.exit(7); }, 50);
    `);
    const apps = [];
    for (const name of ['first', 'second']) {
        const cwd = path.join(directory, name);
        await mkdir(cwd);
        await writeFile(path.join(cwd, '.env'), `APP_VALUE='${name} with spaces $literal'\n`);
        apps.push({ name, directory: cwd, command: [process.execPath.replaceAll('\\', '/'), worker.replaceAll('\\', '/'), 'literal $value; not a command'] });
        if (entrypoint === 'batch') {
            await writeFile(path.join(cwd, 'worker.cmd'), `@"${process.execPath}" "${worker}" "literal $value; not a command"\r\n`);
            apps.at(-1).command = ['cmd.exe', '/d', '/c', '.\\worker.cmd'];
        }
    }
    const logs = [];
    done = runApplications({ apps, bash, signal: abort.signal, log: line => logs.push(line) });
    try {
        await until(() => apps.every(app => existsSync(path.join(app.directory, 'ready.json'))));
    } catch (error) {
        throw new Error(`${error.message}\n${logs.join('\n')}`, { cause: error });
    }
    const state = await Promise.all(apps.map(async app => JSON.parse(await readFile(path.join(app.directory, 'ready.json'), 'utf8'))));
    for (let i = 0; i < apps.length; i++) {
        assert.equal(state[i].value, `${apps[i].name} with spaces $literal`);
        assert.equal(state[i].arg, 'literal $value; not a command');
        assert.equal(path.resolve(state[i].cwd), apps[i].directory);
    }
    // The first worker exits naturally; make it close its own child first so this
    // scenario models Maven completing. The other application's tree must be stopped.
    if (mode === 'application exit') {
        process.kill(state[0].descendant);
        await writeFile(path.join(apps[0].directory, 'exit-now'), '');
    } else process.emit('SIGINT');
    assert.equal(await done, mode === 'application exit' ? 7 : 130);
    await until(() => state.every(app => !alive(app.pid) && !alive(app.descendant)));
    assert.ok(logs.some(line => line.includes('Đang dừng tất cả ứng dụng')));
});
