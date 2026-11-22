// js/core/chat.js
// 站内消息：WebSocket 实时推送 + 未读角标

const ChatClient = (() => {
    let ws = null;
    let reconnectTimer = null;
    let pingTimer = null;
    let started = false;
    let listeners = [];
    let openListeners = [];

    function buildUrl() {
        const proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
        return proto + location.host + '/ws/chat';
    }

    function connect() {
        if (!started) return;
        if (ws && (ws.readyState === WebSocket.CONNECTING || ws.readyState === WebSocket.OPEN)) return;

        try {
            ws = new WebSocket(buildUrl());
        } catch (e) {
            scheduleReconnect();
            return;
        }

        ws.onopen = () => {
            console.log('[chat] WebSocket 已连接');
            clearInterval(pingTimer);
            pingTimer = setInterval(() => {
                if (ws && ws.readyState === WebSocket.OPEN) ws.send('ping');
            }, 25000);
            // 通知页面：连接建立/重连成功，用于补拉断线期间的消息
            openListeners.slice().forEach(fn => {
                try { fn(); } catch (err) { console.error('[chat] onOpen 监听器异常', err); }
            });
        };

        ws.onmessage = (e) => {
            if (e.data === 'pong') return;
            let data;
            try {
                data = JSON.parse(e.data);
            } catch (err) {
                return;
            }
            listeners.slice().forEach(fn => {
                try { fn(data); } catch (err) { console.error('[chat] 监听器异常', err); }
            });
            if (data.event === 'message') refreshChatBadge();
        };

        ws.onclose = () => {
            clearInterval(pingTimer);
            ws = null;
            scheduleReconnect();
        };

        ws.onerror = () => {
            if (ws) { try { ws.close(); } catch (e) { /* ignore */ } }
        };
    }

    function scheduleReconnect() {
        if (!started) return;
        clearTimeout(reconnectTimer);
        reconnectTimer = setTimeout(connect, 5000);
    }

    return {
        start() {
            if (started) return;
            started = true;
            connect();
        },
        stop() {
            started = false;
            clearTimeout(reconnectTimer);
            clearInterval(pingTimer);
            if (ws) { try { ws.close(); } catch (e) { /* ignore */ } }
            ws = null;
        },
        on(fn) { listeners.push(fn); },
        off(fn) { listeners = listeners.filter(f => f !== fn); },
        onOpen(fn) { openListeners.push(fn); },
        offOpen(fn) { openListeners = openListeners.filter(f => f !== fn); }
    };
})();

/** 刷新未读角标，返回是否已登录 */
async function refreshChatBadge() {
    const json = await request('/api/chat/unread');
    const total = (json.data && json.data.total) || 0;

    const badge = document.getElementById('chatBadge');
    if (badge) {
        badge.style.display = total > 0 ? 'inline-block' : 'none';
        badge.innerText = total > 99 ? '99+' : String(total);
    }
    const msgCount = document.getElementById('msgCount');
    if (msgCount) msgCount.innerText = total > 0 ? (total > 99 ? '99+' : String(total)) : '💬';
    return true;
}

/** 已登录才建立 WebSocket，避免游客每 5 秒重连 */
async function initChatClient() {
    try {
        await refreshChatBadge();
        ChatClient.start();
    } catch (e) {
        // 未登录，忽略
    }
}

document.addEventListener('DOMContentLoaded', initChatClient);
