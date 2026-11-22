// js/pages/post.js
// 动态详情与评论

(function () {
    let postId = null;
    let bound = false;
    let loadingComments = false;

    function renderComment(c) {
        const reply = c.replyToName
            ? ` 回复 <b>${escapeHtml(c.replyToName)}</b>` : '';
        const canDelete = c.mine || PostUI.myRole >= 2;
        return `
        <div class="comment-item">
            ${PostUI.avatar(c.authorAvatar, c.authorName, 'comment-avatar')}
            <div class="comment-main">
                <div class="comment-name">${escapeHtml(c.authorName || '')}${reply}</div>
                <div class="comment-text">${escapeHtml(c.content || '')}</div>
                <div class="comment-time">${c.createTime || ''}</div>
            </div>
            ${canDelete ? `<button class="comment-del" data-cdel="${c.id}">删除</button>` : ''}
        </div>`;
    }

    async function loadDetail() {
        const box = document.getElementById('postDetail');
        if (!box) return;
        box.innerHTML = '<div class="post-card"><div class="empty-tip">加载中...</div></div>';
        const json = await request('/api/post/' + postId);
        box.innerHTML = PostUI.renderCard(json.data);
    }

    async function loadComments() {
        if (loadingComments) return;
        const box = document.getElementById('commentList');
        if (!box) return;
        loadingComments = true;
        try {
            const json = await request(`/api/post/${postId}/comments?size=50`);
            const list = json.data || [];
            box.innerHTML = list.length
                ? list.map(renderComment).join('')
                : '<div class="empty-tip">还没有评论，来抢沙发</div>';
        } catch (e) {
            box.innerHTML = '<div class="empty-tip">评论加载失败</div>';
        } finally {
            loadingComments = false;
        }
    }

    // 评论发送成功后的飞机起飞 + “已发送”动画
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

    async function submitComment() {
        const input = document.getElementById('commentInput');
        const btn = document.getElementById('commentSubmitBtn');
        const content = (input.value || '').trim();
        if (!content) {
            alert('评论不能为空');
            return;
        }

        btn.disabled = true;
        try {
            await request(`/api/post/${postId}/comments`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content })
            });
            input.value = '';
            flashSent(btn);
            await loadComments();
            await loadDetail();
        } catch (err) {
            alert(err.message || '评论失败');
        } finally {
            if (!btn.classList.contains('is-sent')) {
                btn.disabled = false;
            }
        }
    }

    function bindOnce() {
        if (bound) return;
        bound = true;

        const btn = document.getElementById('commentSubmitBtn');
        if (btn) btn.onclick = submitComment;

        const input = document.getElementById('commentInput');
        if (input) {
            input.addEventListener('keydown', e => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    submitComment();
                }
            });
        }

        const list = document.getElementById('commentList');
        if (list) {
            list.addEventListener('click', async (e) => {
                const del = e.target.closest('[data-cdel]');
                if (!del) return;
                if (!confirm('确定删除这条评论吗？')) return;
                try {
                    await request('/api/comment/' + del.dataset.cdel, { method: 'DELETE' });
                    await loadComments();
                    await loadDetail();
                } catch (err) {
                    alert(err.message || '删除失败');
                }
            });
        }

        PostUI.bindCardActions(document.getElementById('postDetail'), () => {
            location.hash = '#/feed';
        });
    }

    registerPageHook('post', async () => {
        bindOnce();
        await PostUI.loadMe();

        postId = Number(getUrlParam('id'));
        const detail = document.getElementById('postDetail');
        const comments = document.getElementById('commentList');
        if (!postId) {
            if (detail) detail.innerHTML = '<div class="empty-tip">动态不存在</div>';
            if (comments) comments.innerHTML = '';
            return;
        }

        try {
            await loadDetail();
            await loadComments();
        } catch (err) {
            if (detail) {
                detail.innerHTML = '<div class="empty-tip">' + escapeHtml(err.message || '加载失败') + '</div>';
            }
            if (comments) comments.innerHTML = '';
        }
    });
})();
