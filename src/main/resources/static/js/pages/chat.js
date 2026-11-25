// js/pages/chat.js
// 聊天窗口：发送失败保留草稿、clientMsgId 幂等重试、重连增量补拉

(function () {
    let currentConvId = null;
    let peerId = null;
    let renderedIds = new Set();
    let lastMessageId = 0;
    let bound = false;
    let sending = false;

    // 发送失败待重试：同一段文字复用同一个 clientMsgId，避免重试产生重复消息
    let retryContent = null;
    let retryClientMsgId = null;

    function box() {
        return document.getElementById('chatMessages');
    }

    function scrollBottom() {
        const el = box();
        if (el) el.scrollTop = el.scrollHeight;
    }

    function newClientMsgId() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return window.crypto.randomUUID();
        }
        return 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
    }

    // 发送成功后的飞机起飞 + “已发送”动画
    function flashSent(btn) {
        if (!btn) return;
        btn.classList.remove('is-sent');
        void btn.offsetWidth;
        btn.classList.add('is-sent');
        btn.disabled = true;
        setTimeout(() => {
            btn.classList.remove('is-sent');
            btn.disabled = false;
        }, 1500);
    }

    function appendMessage(m, scroll) {
        const el = box();
        if (!el || renderedIds.has(m.id)) return;
        renderedIds.add(m.id);
        if (m.id && m.id > lastMessageId) lastMessageId = m.id;

        const div = document.createElement('div');
        div.className = 'chat-msg ' + (m.mine ? 'mine' : 'peer');
        if (m.id) div.dataset.msgId = m.id;
        if (Number(m.status) === 1) div.classList.add('chat-msg-recalled');
        const bubble = m.type === 1
            ? `<img class="chat-img" src="${m.content}" onclick="window.open(this.src)">`
            : escapeHtml(m.content);
        div.innerHTML = `
            <div class="chat-bubble">${bubble}</div>
            <div class="chat-time">${m.createTime || ''}</div>`;
        el.appendChild(div);
        if (scroll !== false) scrollBottom();
    }

    async function markRead(conversationId) {
        try {
            await request('/api/chat/read', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ conversationId })
            });
            refreshChatBadge().catch(() => {});
        } catch (e) { /* ignore */ }
    }

    /** 发送失败会抛错，由 sendText 负责保留草稿；clientMsgId 由调用方生成 */
    async function sendMessage(content, type, clientMsgId) {
        if (!currentConvId) throw new Error('会话不存在');
        const json = await request('/api/chat/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ conversationId: currentConvId, content, type, clientMsgId })
        });
        appendMessage(json.data);
        return json.data;
    }

    async function sendText() {
        const input = document.getElementById('chatInput');
        const content = (input.value || '').trim();
        if (!content || !currentConvId || sending) return;

        // 同一段失败文本重试时复用 clientMsgId，服务端据此幂等
        const clientMsgId = (retryContent === content && retryClientMsgId)
            ? retryClientMsgId
            : newClientMsgId();

        const sendBtn = document.getElementById('chatSendBtn');
        sending = true;
        if (sendBtn) sendBtn.disabled = true;
        try {
            await sendMessage(content, 0, clientMsgId);
            retryContent = null;
            retryClientMsgId = null;
            flashSent(sendBtn);
            // 只有内容没被改动过才清空，避免清掉用户刚输入的新内容
            if ((input.value || '').trim() === content) input.value = '';
        } catch (err) {
            // 失败保留草稿，并记住这条消息的 clientMsgId
            retryContent = content;
            retryClientMsgId = clientMsgId;
            input.value = content;
            alert(err.message || '发送失败，请重试');
        } finally {
            sending = false;
            if (sendBtn && !sendBtn.classList.contains('is-sent')) {
                sendBtn.disabled = false;
            }
        }
    }

    async function sendImage(file) {
        if (!file) return;
        const form = new FormData();
        form.append('file', file);
        try {
            const json = await request('/api/chat/upload', { method: 'POST', body: form });
            await sendMessage(json.data, 1, newClientMsgId());
        } catch (err) {
            alert(err.message || '图片发送失败');
        }
    }

    /** WebSocket 重连后，补拉断线期间别人发来的消息 */
    async function loadNewMessages() {
        if (!currentConvId || !lastMessageId) return;
        const view = document.getElementById('view-chat');
        if (!view || !view.classList.contains('active')) return;
        try {
            const json = await request(
                `/api/chat/messages?conversationId=${currentConvId}&afterId=${lastMessageId}&size=50`);
            const list = (json.data && json.data.messages) || [];
            if (!list.length) return;
            list.forEach(m => appendMessage(m, false));
            scrollBottom();
            await markRead(currentConvId);
        } catch (e) {
            // 补拉失败不打扰用户，下次进入页面会全量拉取
        }
    }

    function setFollowBtn(following) {
        const btn = document.getElementById('chatFollowBtn');
        if (!btn) return;
        btn.dataset.following = following ? '1' : '0';
        btn.innerText = following ? '已关注' : '关注';
        btn.classList.toggle('followed', following);
    }

    async function loadFollowStatus(id) {
        const btn = document.getElementById('chatFollowBtn');
        if (!btn) return;
        if (!id) {
            btn.style.display = 'none';
            return;
        }
        try {
            const json = await request('/api/follow/status/' + id);
            if (json.data && json.data.self) {
                btn.style.display = 'none';
                return;
            }
            btn.style.display = '';
            setFollowBtn(!!(json.data && json.data.following));
        } catch (e) {
            btn.style.display = 'none';
        }
    }

    function bindOnce() {
        if (bound) return;
        bound = true;

        const followBtn = document.getElementById('chatFollowBtn');
        if (followBtn) {
            followBtn.onclick = async () => {
                if (!peerId) return;
                const following = followBtn.dataset.following === '1';
                try {
                    await request('/api/follow/' + peerId, { method: following ? 'DELETE' : 'POST' });
                    setFollowBtn(!following);
                } catch (err) {
                    alert(err.message || '操作失败');
                }
            };
        }

        const sendBtn = document.getElementById('chatSendBtn');
        if (sendBtn) sendBtn.onclick = sendText;

        const input = document.getElementById('chatInput');
        if (input) {
            input.addEventListener('keydown', e => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    sendText();
                }
            });
        }

        const imgBtn = document.getElementById('chatImageBtn');
        const imgInput = document.getElementById('chatImageInput');
        if (imgBtn && imgInput) {
            imgBtn.onclick = () => imgInput.click();
            imgInput.onchange = () => {
                const f = imgInput.files && imgInput.files[0];
                imgInput.value = '';
                sendImage(f);
            };
        }

        ChatClient.on(data => {
            if (data.event === 'recall') {
                const view = document.getElementById('view-chat');
                if (!view || !view.classList.contains('active')) return;
                if (Number(data.conversationId) !== currentConvId) return;
                const node = document.querySelector(`[data-msg-id="${data.id}"]`);
                if (node) {
                    node.classList.add('chat-msg-recalled');
                    const bubbleEl = node.querySelector('.chat-bubble');
                    if (bubbleEl) bubbleEl.textContent = data.content;
                }
                return;
            }
            if (data.event !== 'message') return;
            const view = document.getElementById('view-chat');
            if (!view || !view.classList.contains('active')) return;
            if (Number(data.conversationId) !== currentConvId) return;
            // 对方发来的消息：追加并标记已读
            appendMessage(data);
            if (!data.mine) markRead(currentConvId);
        });

        ChatClient.onOpen(() => {
            loadNewMessages();
        });
    }

    async function initChatPage() {
        bindOnce();
        initChatClient();

        const id = Number(getUrlParam('id'));
        const el = box();
        if (!id) {
            if (el) el.innerHTML = '<div class="empty-tip">会话不存在</div>';
            return;
        }

        currentConvId = id;
        peerId = null;
        renderedIds = new Set();
        lastMessageId = 0;
        retryContent = null;
        retryClientMsgId = null;
        const followBtn = document.getElementById('chatFollowBtn');
        if (followBtn) {
            followBtn.style.display = 'none';
            followBtn.dataset.following = '0';
        }
        if (el) el.innerHTML = '<div class="empty-tip">加载中...</div>';

        try {
            const json = await request(`/api/chat/messages?conversationId=${id}&size=30`);
            const peer = (json.data && json.data.peer) || {};
            const nameEl = document.getElementById('chatPeerName');
            if (nameEl) nameEl.innerText = peer.name || '聊天';
            peerId = peer.id || null;
            loadFollowStatus(peerId);

            if (el) el.innerHTML = '';
            (json.data.messages || []).forEach(m => appendMessage(m, false));
            scrollBottom();
            await markRead(id);
        } catch (err) {
            if (el) el.innerHTML = '<div class="empty-tip">加载失败：' + (err.message || '') + '</div>';
        }
    }

    registerPageHook('chat', initChatPage);
})();
