// js/pages/feed.js
// 广场：发布动态、广场/关注双 Tab、点赞、删除

(function () {
    const MAX_IMAGES = 9;

    let tab = 'square';
    let lastId = 0;
    let hasMore = true;
    let loading = false;
    let bound = false;
    let pendingImages = [];

    function avatarHtml(url, name, cls) {
        const c = cls || 'post-avatar';
        if (url) return `<img class="${c}" src="${url}" alt="">`;
        const ch = (name || '?').trim().charAt(0).toUpperCase();
        return `<div class="${c} avatar-fallback">${escapeHtml(ch)}</div>`;
    }

    function renderImages(images) {
        if (!images || !images.length) return '';
        const cls = images.length === 1 ? ' single' : '';
        return `<div class="post-imgs${cls}">` + images.map(src =>
            `<img src="${src}" alt="" onclick="window.open(this.src)">`).join('') + `</div>`;
    }

    const PostUI = {
        myRole: 0,
        myId: null,
        loggedIn: false,

        async loadMe() {
            try {
                const json = await request('/api/user/getUserInfo');
                const user = json.data || {};
                this.myRole = Number(user.role) || 0;
                this.myId = user.id;
                this.loggedIn = true;
            } catch (e) {
                this.myRole = 0;
                this.myId = null;
                this.loggedIn = false;
            }
            return this.loggedIn;
        },

        avatar: avatarHtml,

        renderCard(p) {
            const canDelete = p.mine || this.myRole >= 2;
            const badge = p.authorRole === 2 ? '<span class="post-badge">管理员</span>'
                : (p.authorRole === 1 ? '<span class="post-badge">商家</span>' : '');
            return `
            <article class="post-card" data-id="${p.id}">
              <div class="post-head">
                ${avatarHtml(p.authorAvatar, p.authorName)}
                <div class="post-meta">
                  <div class="post-author">${escapeHtml(p.authorName || '')}${badge}</div>
                  <div class="post-time">${p.createTime || ''}</div>
                </div>
                ${canDelete ? `<button class="post-del" data-del="${p.id}">删除</button>` : ''}
              </div>
              <div class="post-content">${escapeHtml(p.content || '')}</div>
              ${renderImages(p.images)}
              <div class="post-actions">
                <button class="post-action ${p.liked ? 'active' : ''}" data-like="${p.id}" data-liked="${p.liked ? 1 : 0}">
                  👍 <em>${p.likeCount || 0}</em>
                </button>
                <button class="post-action" data-open="${p.id}">
                  💬 <em>${p.commentCount || 0}</em>
                </button>
                <button class="post-action" data-open="${p.id}">详情</button>
              </div>
            </article>`;
        },

        bindCardActions(root, onDeleted) {
            if (!root) return;
            root.addEventListener('click', async (e) => {
                const likeBtn = e.target.closest('[data-like]');
                if (likeBtn) {
                    e.stopPropagation();
                    await toggleLike(likeBtn);
                    return;
                }

                const delBtn = e.target.closest('[data-del]');
                if (delBtn) {
                    e.stopPropagation();
                    if (!confirm('确定删除这条动态吗？')) return;
                    try {
                        await request('/api/post/' + delBtn.dataset.del, { method: 'DELETE' });
                        const card = delBtn.closest('.post-card');
                        if (card) card.remove();
                        if (typeof onDeleted === 'function') onDeleted(delBtn.dataset.del);
                    } catch (err) {
                        alert(err.message || '删除失败');
                    }
                    return;
                }

                const openBtn = e.target.closest('[data-open]');
                if (openBtn) {
                    location.hash = '#/post?id=' + openBtn.dataset.open;
                }
            });
        },

        async toggleLike(btn) {
            await toggleLike(btn);
        }
    };
    window.PostUI = PostUI;

    async function toggleLike(btn) {
        const id = btn.dataset.like;
        const liked = btn.dataset.liked === '1';
        try {
            await request('/api/post/' + id + '/like', { method: liked ? 'DELETE' : 'POST' });
            btn.dataset.liked = liked ? '0' : '1';
            btn.classList.toggle('active', !liked);
            const em = btn.querySelector('em');
            const n = Number(em.innerText) || 0;
            em.innerText = Math.max(0, n + (liked ? -1 : 1));
        } catch (err) {
            alert(err.message || '操作失败');
        }
    }

    async function loadFeed(reset) {
        const box = document.getElementById('postList');
        const moreBtn = document.getElementById('postLoadMore');
        if (!box) return;

        if (reset) {
            lastId = 0;
            hasMore = true;
            loading = false;
            box.innerHTML = '<div class="empty-tip">加载中...</div>';
        }
        if (loading || !hasMore) return;

        loading = true;
        try {
            const json = await request(`/api/post/feed?tab=${tab}&lastId=${lastId}&size=10`);
            const list = json.data || [];
            if (reset) box.innerHTML = '';

            if (!list.length) {
                if (reset) {
                    box.innerHTML = `<div class="empty-tip">${tab === 'following'
                        ? '关注的人还没发动态，去广场逛逛吧'
                        : '还没有动态，来发第一条吧'}</div>`;
                }
                hasMore = false;
            } else {
                box.insertAdjacentHTML('beforeend', list.map(p => PostUI.renderCard(p)).join(''));
                lastId = list[list.length - 1].id;
                hasMore = list.length === 10;
            }
        } catch (err) {
            if (reset) {
                box.innerHTML = '<div class="empty-tip">请先登录后查看广场</div>';
            }
            hasMore = false;
        } finally {
            loading = false;
            if (moreBtn) moreBtn.style.display = hasMore ? '' : 'none';
        }
    }

    function renderPreview() {
        const box = document.getElementById('postImagePreview');
        const tip = document.getElementById('postPublishTip');
        if (box) {
            box.innerHTML = pendingImages.map((url, i) =>
                `<div class="post-preview">
                    <img src="${url}" alt="">
                    <button type="button" data-remove="${i}">×</button>
                 </div>`).join('');
        }
        if (tip) tip.innerText = `最多 ${MAX_IMAGES} 张图片（${pendingImages.length}/${MAX_IMAGES}）`;
    }

    async function uploadImages(files) {
        const tip = document.getElementById('postPublishTip');
        for (const file of files) {
            if (pendingImages.length >= MAX_IMAGES) break;
            if (!file.type || !file.type.startsWith('image/')) continue;

            const form = new FormData();
            form.append('file', file);
            try {
                if (tip) tip.innerText = '图片上传中...';
                const json = await request('/api/post/upload', { method: 'POST', body: form });
                pendingImages.push(json.data);
                renderPreview();
            } catch (err) {
                alert(err.message || '图片上传失败');
            }
        }
        renderPreview();
    }

    async function publish() {
        const textarea = document.getElementById('postContent');
        const btn = document.getElementById('postPublishBtn');
        const content = (textarea.value || '').trim();
        if (!content && !pendingImages.length) {
            alert('写点什么或选张图片吧');
            return;
        }

        btn.disabled = true;
        try {
            await request('/api/post', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content, images: pendingImages })
            });
            textarea.value = '';
            pendingImages = [];
            renderPreview();

            tab = 'square';
            document.querySelectorAll('.feed-tab').forEach(t =>
                t.classList.toggle('active', t.dataset.tab === 'square'));
            loadFeed(true);
        } catch (err) {
            alert(err.message || '发布失败');
        } finally {
            btn.disabled = false;
        }
    }

    function setPublishEnabled(enabled) {
        const btn = document.getElementById('postPublishBtn');
        const tip = document.getElementById('postPublishTip');
        const box = document.getElementById('postPublishBox');
        if (btn) btn.disabled = !enabled;
        if (tip) tip.innerText = enabled ? `最多 ${MAX_IMAGES} 张图片（${pendingImages.length}/${MAX_IMAGES}）` : '登录后才能发布动态';
        if (box) box.classList.toggle('disabled', !enabled);
    }

    function bindOnce() {
        if (bound) return;
        bound = true;

        const imgBtn = document.getElementById('postImageBtn');
        const imgInput = document.getElementById('postImageInput');
        if (imgBtn && imgInput) {
            imgBtn.onclick = () => imgInput.click();
            imgInput.onchange = () => {
                const files = Array.from(imgInput.files || []);
                imgInput.value = '';
                uploadImages(files);
            };
        }

        const preview = document.getElementById('postImagePreview');
        if (preview) {
            preview.onclick = (e) => {
                const btn = e.target.closest('[data-remove]');
                if (!btn) return;
                pendingImages.splice(Number(btn.dataset.remove), 1);
                renderPreview();
            };
        }

        const publishBtn = document.getElementById('postPublishBtn');
        if (publishBtn) publishBtn.onclick = publish;

        document.querySelectorAll('.feed-tab').forEach(el => {
            el.onclick = () => {
                tab = el.dataset.tab;
                document.querySelectorAll('.feed-tab').forEach(t =>
                    t.classList.toggle('active', t === el));
                loadFeed(true);
            };
        });

        const moreBtn = document.getElementById('postLoadMore');
        if (moreBtn) moreBtn.onclick = () => loadFeed(false);

        PostUI.bindCardActions(document.getElementById('postList'));
    }

    registerPageHook('feed', async () => {
        bindOnce();
        pendingImages = [];
        renderPreview();
        const ok = await PostUI.loadMe();
        setPublishEnabled(ok);
        loadFeed(true);
    });
})();
