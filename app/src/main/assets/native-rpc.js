(function (root) {
    'use strict';

    // The host adapter sends three strings; the screen routes native reply data
    // to receive(). No Android globals, UI state, network or automatic retries.
    function create(options) {
        var serial = 0;
        var pending = new Map();
        var formatError = options.formatError || String;

        function take(request) {
            var entry = pending.get(request);
            if (entry) {
                pending.delete(request);
                clearTimeout(entry.timer);
            }
            return entry;
        }

        function call(action, args) {
            return new Promise(function (resolve, reject) {
                if (!options.send) {
                    reject(new Error(options.unavailableMessage));
                    return;
                }
                // Serialize before retaining a callback or scheduling a timer.
                var payload = JSON.stringify(args || {});
                var request = String(++serial);
                var timer = setTimeout(function () {
                    var entry = take(request);
                    if (entry) entry.reject(new Error(options.timeoutMessage));
                }, options.timeoutMs);
                pending.set(request, {resolve: resolve, reject: reject, timer: timer});
                try {
                    options.send(request, action, payload);
                } catch (error) {
                    take(request);
                    reject(error);
                }
            });
        }

        function receive(reply) {
            var entry = reply && take(reply.request);
            if (!entry) return false; // Unknown, duplicate or timed-out reply.
            if (reply.error) {
                try {
                    entry.reject(new Error(formatError(reply.error)));
                } catch (error) {
                    entry.reject(error);
                }
            } else {
                entry.resolve(reply.value);
            }
            return true;
        }

        return {call: call, receive: receive};
    }

    root.PocketRpc = {create: create};
})(window);
