const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/native-rpc.js'), 'utf8');

function fixture(timeoutMs = 30000, sendOverride) {
    let now = 0, nextTimer = 0;
    const timers = new Map(), sent = [];
    const context = {
        window: {},
        setTimeout(callback, delay) {
            const id = ++nextTimer;
            timers.set(id, {callback, due: now + delay});
            return id;
        },
        clearTimeout(id) { timers.delete(id); }
    };
    vm.runInNewContext(source, context);
    const rpc = context.window.PocketRpc.create({
        send: sendOverride === undefined ? (request, action, payload) => sent.push({request, action, payload}) : sendOverride,
        timeoutMs,
        unavailableMessage: 'host unavailable',
        timeoutMessage: 'request timed out',
        formatError: error => 'localized: ' + error
    });
    return {
        rpc, sent, timers,
        advance(ms) {
            now += ms;
            for (const [id, timer] of [...timers]) {
                if (timer.due <= now) { timers.delete(id); timer.callback(); }
            }
        }
    };
}

(async () => {
    const concurrent = fixture();
    const first = concurrent.rpc.call('snapshot');
    const second = concurrent.rpc.call('rename', {title: 'Запись "тест"\n🙂'});
    assert.deepEqual(concurrent.sent, [
        {request: '1', action: 'snapshot', payload: '{}'},
        {request: '2', action: 'rename', payload: JSON.stringify({title: 'Запись "тест"\n🙂'})}
    ], 'unchanged three-string Android wire format, default args and Unicode');
    assert.equal(concurrent.rpc.receive({request: '2', value: {ok: true}}), true);
    assert.equal(concurrent.rpc.receive({request: '1', value: {items: []}}), true);
    assert.deepEqual(await Promise.all([first, second]), [{items: []}, {ok: true}], 'out-of-order replies resolve correct calls');
    assert.equal(concurrent.timers.size, 0, 'successful calls release timers');
    assert.equal(concurrent.rpc.receive({request: '1', value: 'duplicate'}), false);
    assert.equal(concurrent.rpc.receive({request: 'unknown', value: 'stray'}), false);
    assert.equal(concurrent.rpc.receive(null), false);
    assert.equal(concurrent.rpc.receive({}), false);

    const errorCall = concurrent.rpc.call('delete');
    const rejected = assert.rejects(errorCall, /localized: vault locked/);
    concurrent.rpc.receive({request: '3', error: 'vault locked', value: {ok: true}});
    await rejected;
    assert.equal(concurrent.timers.size, 0, 'native errors release timers and beat values');

    for (const timeoutMs of [30000, 60000]) {
        const timeout = fixture(timeoutMs);
        const expired = assert.rejects(timeout.rpc.call('importVideo'), /request timed out/);
        timeout.advance(timeoutMs - 1);
        assert.equal(timeout.timers.size, 1, 'screen-specific deadline retained');
        timeout.advance(1);
        await expired;
        assert.equal(timeout.timers.size, 0);
        assert.equal(timeout.rpc.receive({request: '1', value: 'late result'}), false);
        assert.equal(timeout.sent.length, 1, 'timeout does not repeat a mutating native action');
        const next = timeout.rpc.call('detail');
        assert.equal(timeout.sent[1].request, '2', 'expired request IDs are never reused');
        timeout.rpc.receive({request: '1', error: 'late error'});
        timeout.rpc.receive({request: '2', value: 0});
        assert.equal(await next, 0, 'late replies cannot settle a newer call; falsy values are valid');
    }

    const transportError = new Error('native bridge threw');
    const broken = fixture(60000, () => { throw transportError; });
    await assert.rejects(broken.rpc.call('process'), error => error === transportError);
    assert.equal(broken.timers.size, 0, 'synchronous scanner bridge failure releases pending timer');
    assert.equal(broken.rpc.receive({request: '1', value: 'after exception'}), false, 'failed send releases callback too');

    const cyclic = {}; cyclic.self = cyclic;
    const invalid = fixture();
    await assert.rejects(invalid.rpc.call('edit', cyclic), /circular/i);
    assert.equal(invalid.timers.size, 0, 'serialization failure retains no timer');
    assert.equal(invalid.sent.length, 0, 'invalid payload never reaches native code');

    const unavailable = fixture(30000, null);
    await assert.rejects(unavailable.rpc.call('create'), /host unavailable/);
    assert.equal(unavailable.timers.size, 0);

    // Some native/test adapters can answer before send() returns.
    let synchronous;
    synchronous = fixture(30000, request => synchronous.rpc.receive({request, value: 'immediate'}));
    assert.equal(await synchronous.rpc.call('flush'), 'immediate');
    assert.equal(synchronous.timers.size, 0);

    // Browser fixtures serve all files; Android serves only UiHost's allowlist.
    const host = fs.readFileSync(path.join(root, 'app/src/main/java/dev/pocketllama/UiHost.java'), 'utf8');
    const allowed = new Set([...host.match(/ASSETS[\s\S]*?\)\);/)[0].matchAll(/"([^"]+)"/g)].map(m => m[1]));
    for (const name of ['studio', 'photos', 'notes']) {
        const html = fs.readFileSync(path.join(root, 'app/src/main/assets/' + name + '.html'), 'utf8');
        const scripts = [...html.matchAll(/<script src="([^"]+)"[^>]*>/g)].map(m => m[1]);
        assert(scripts.includes('native-rpc.js') && scripts.indexOf('native-rpc.js') < scripts.indexOf(name + '.js'), 'RPC loads before ' + name);
        for (const script of scripts) {
            assert(allowed.has(script), script + ' must be served by the Android asset origin');
            assert(fs.existsSync(path.join(root, 'app/src/main/assets', script)));
        }
    }
    console.log('PASS: native RPC correlation, deadlines, errors, cleanup, wire format and Android asset integration');
})().catch(error => { console.error(error); process.exitCode = 1; });
