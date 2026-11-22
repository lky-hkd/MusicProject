// js/pages/follow.js
// 我的关注 / 粉丝列表

(function () {
    let tab = 'following';
    let bound = false;

    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.innerText = value;
    }

    function avatarHtml(url, name) {
        if (url) {
            return `<img class="follow-avatar" src="${url}" alt="">`;
        }
        const ch = (name || '?').trim().charAt(0).toUpperCase();
        return `<div class="follow-avatar follow-avatar-text">${escapeHtml(ch)}</div>`;
    }

    function renderItem(u) {
        const name = u.nickname || u.username || '';
        const followed = !!u.followed;
        return `
            <div class="follow-item">
                ${avatarHtml(u.avatar, name)}
                <div class="follow-info">
                    <div class="follow-name">${escapeHtml(name)}</div>
                    <div class="follow-sub">关注 ${u.followingCount || 0} · 粉丝 ${u.followerCount || 0}</div>
                </div>
                <button class="follow-btn ${followed ? 'followed' : ''}"
                        data-user-id="${u.id}" data-followed="${followed ? 1 : 0}">
                    ${followed ? '已关注' : '关注'}
                </button>
            </div>`;
    }

    async function loadCounts() {
        try {
            const json = await request('/api/user/getUserInfo');
            const user = json.data || {};
            setText('followingTabCount', user.followingCount || 0);
            setText('followersTabCount', user.followerCount || 0);
        } catch (e) {
            // 未登录时保持 0
        }
    }

    async function loadList() {
        const box = document.getElementById('followList');
        if (!box) return;
        box.innerHTML = '<div class="empty-tip">加载中...</div>';
        try {
            const json = await request(`/api/follow/${tab}?page=1&size=50`);
            const list = json.data || [];
            if (!list.length) {
                box.innerHTML = `<div class="empty-tip">${tab === 'following' ? '还没有关注任何人' : '还没有粉丝'}</div>`;
                return;
            }
            box.innerHTML = list.map(renderItem).join('');
        } catch (err) {
            box.innerHTML = '<div class="empty-tip">请先登录后查看</div>';
        }
    }

    async function toggleFollow(userId, followed) {
        try {
            await request('/api/follow/' + userId, { method: followed ? 'DELETE' : 'POST' });
            await loadList();
            await loadCounts();
        } catch (err) {
            alert(err.message || '操作失败');
        }
    }

    function bindOnce() {
        if (bound) return;
        bound = true;

        const tabs = document.querySelectorAll('.follow-tab');
        tabs.forEach(el => {
            el.onclick = () => {
                tab = el.dataset.tab;
                tabs.forEach(t => t.classList.toggle('active', t === el));
                loadList();
            };
        });

        const list = document.getElementById('followList');
        if (list) {
            list.onclick = (e) => {
                const btn = e.target.closest('button.follow-btn');
                if (!btn) return;
                const userId = Number(btn.dataset.userId);
                const followed = btn.dataset.followed === '1';
                if (userId) toggleFollow(userId, followed);
            };
        }
    }

    registerPageHook('follow', () => {
        bindOnce();
        loadCounts();
        loadList();
    });
})();
