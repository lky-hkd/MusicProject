// js/pages/messages.js
// 消息中心：会话列表 + 搜索用户发起聊天 + 平台客服入口

(function () {
    let bound = false;

    function avatarHtml(url, name) {
        if (url) {
            return `<img class="conv-avatar" src="${url}" alt="">`;
        }
        const ch = (name || '?').trim().charAt(0).toUpperCase();
        return `<div class="conv-avatar conv-avatar-text">${escapeHtml(ch)}</div>`;
    }

    function renderConversation(c) {
        const last = c.lastMsg ? escapeHtml(c.lastMsg) : '（暂无消息）';
        const unread = c.unread > 0
            ? `<span class="conv-unread">${c.unread > 99 ? '99+' : c.unread}</span>` : '';
        const tag = c.type === 1 ? '<span class="conv-tag">客服</span>' : '';
        return `
            <div class="conv-item" onclick="location.hash='#/chat?id=${c.id}'">
                ${avatarHtml(c.peerAvatar, c.peerName)}
                <div class="conv-main">
                    <div class="conv-top">
                        <span class="conv-name">${escapeHtml(c.peerName || '')}${tag}</span>
                        <span class="conv-time">${c.lastMsgTime || ''}</span>
                    </div>
                    <div class="conv-last">${last}</div>
                </div>
                ${unread}
            </div>`;
    }

    async function loadConversations() {
        const box = document.getElementById('conversationList');
        if (!box) return;
        box.innerHTML = '<div class="empty-tip">加载中...</div>';
        try {
            const json = await request('/api/chat/list');
            const list = json.data || [];
            if (!list.length) {
                box.innerHTML = '<div class="empty-tip">还没有会话，搜索用户或联系平台客服开始聊天</div>';
                return;
            }
            box.innerHTML = list.map(renderConversation).join('');
        } catch (err) {
            box.innerHTML = '<div class="empty-tip">请先登录后查看消息</div>';
        }
    }

    async function openWith(targetId) {
        try {
            const json = await request('/api/chat/open', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ targetId: Number(targetId) })
            });
            location.hash = '#/chat?id=' + json.data.id;
        } catch (err) {
            alert(err.message || '打开会话失败');
        }
    }
    window.openChatWithUser = openWith;

    async function openService() {
        try {
            const json = await request('/api/chat/service', { method: 'POST' });
            location.hash = '#/chat?id=' + json.data.id;
        } catch (err) {
            alert(err.message || '联系客服失败');
        }
    }

    async function searchUsers() {
        const input = document.getElementById('chatUserSearch');
        const result = document.getElementById('chatSearchResult');
        const kw = (input.value || '').trim();
        if (!kw) {
            result.innerHTML = '';
            return;
        }
        result.innerHTML = '<div class="empty-tip">搜索中...</div>';
        try {
            const json = await request('/api/chat/search-user?keyword=' + encodeURIComponent(kw));
            const list = json.data || [];
            if (!list.length) {
                result.innerHTML = '<div class="empty-tip">没有找到匹配的用户</div>';
                return;
            }
            result.innerHTML = list.map(u => `
                <div class="conv-item">
                    ${avatarHtml(u.avatar, u.name)}
                    <div class="conv-main">
                        <div class="conv-name">${escapeHtml(u.name || '')}</div>
                    </div>
                    <button class="conv-start-btn" onclick="window.openChatWithUser(${u.id})">发消息</button>
                </div>`).join('');
        } catch (err) {
            result.innerHTML = '<div class="empty-tip">搜索失败</div>';
        }
    }

    function bindOnce() {
        if (bound) return;
        bound = true;

        const serviceBtn = document.getElementById('serviceChatBtn');
        if (serviceBtn) serviceBtn.onclick = openService;

        const searchBtn = document.getElementById('chatUserSearchBtn');
        if (searchBtn) searchBtn.onclick = searchUsers;

        const input = document.getElementById('chatUserSearch');
        if (input) {
            input.addEventListener('keydown', e => {
                if (e.key === 'Enter') searchUsers();
            });
        }

        // 收到新消息时刷新会话列表与角标
        ChatClient.on(data => {
            if (data.event !== 'message') return;
            const messagesView = document.getElementById('view-messages');
            if (messagesView && messagesView.classList.contains('active')) {
                loadConversations();
            }
        });

        // 重连成功后补一次未读数和会话列表，避免断线期间漏掉
        ChatClient.onOpen(() => {
            refreshChatBadge().catch(() => {});
            const messagesView = document.getElementById('view-messages');
            if (messagesView && messagesView.classList.contains('active')) {
                loadConversations();
            }
        });
    }

    registerPageHook('messages', () => {
        bindOnce();
        initChatClient();
        loadConversations();
    });
})();
