// js/admin/admin-music.js  管理员 - 音乐管理（新增 / 修改 / 上下架）

const AdminSongs = { page: 1, size: 10, keyword: '', status: '' };

function renderAdminSongs() {
    const el = document.getElementById('adminContent');
    el.innerHTML = `
      <div class="mc-toolbar">
        <input id="as-kw" placeholder="歌曲名 / 歌手" value="${escapeHtml(AdminSongs.keyword)}">
        <select id="as-status">
          <option value="">全部状态</option>
          <option value="0">已上架</option>
          <option value="1">已下架</option>
        </select>
        <button id="as-search">搜索</button>
        <button id="as-add" class="mc-primary" style="margin-left:auto;">+ 新增歌曲</button>
      </div>
      <div id="as-list">加载中...</div>`;

    document.getElementById('as-status').value = AdminSongs.status;
    document.getElementById('as-search').onclick = () => {
        AdminSongs.keyword = document.getElementById('as-kw').value.trim();
        AdminSongs.status = document.getElementById('as-status').value;
        AdminSongs.page = 1;
        loadAdminSongs();
    };
    document.getElementById('as-add').onclick = () => openSongForm(null);
    loadAdminSongs();
}

async function loadAdminSongs() {
    const listEl = document.getElementById('as-list');
    if (!listEl) return;
    listEl.innerHTML = '加载中...';
    try {
        const data = await adminPost('/api/admin/song/list', {
            page: AdminSongs.page,
            size: AdminSongs.size,
            keyword: AdminSongs.keyword || null,
            status: AdminSongs.status !== '' ? Number(AdminSongs.status) : null
        });
        const list = data.records || [];
        const total = Number(data.total || 0);

        let html = `<table class="mc-table"><thead><tr>
          <th>ID</th><th>歌曲名</th><th>歌手</th><th>曲风</th><th>时长</th>
          <th>VIP</th><th>播放量</th><th>状态</th><th>操作</th>
        </tr></thead><tbody>`;
        if (!list.length) {
            html += `<tr><td colspan="9">暂无歌曲，点击右上角新增</td></tr>`;
        }
        list.forEach(s => {
            const offline = Number(s.status) === 1;
            html += `<tr>
              <td>${s.id}</td>
              <td>${escapeHtml(s.songName || '')}</td>
              <td>${escapeHtml(s.singer || '')}</td>
              <td>${escapeHtml(s.type || '-')}</td>
              <td>${formatDuration(s.duration)}</td>
              <td>${Number(s.isVip) === 1 ? 'VIP' : '免费'}</td>
              <td>${s.playCount ?? 0}</td>
              <td>${offline ? '<span style="color:#e74c3c;">已下架</span>' : '已上架'}</td>
              <td>
                <button class="as-edit" data-id="${s.id}">编辑</button>
                <button class="as-toggle" data-id="${s.id}" data-status="${offline ? 0 : 1}">
                  ${offline ? '上架' : '下架'}
                </button>
              </td>
            </tr>`;
        });
        html += '</tbody></table>' + adminPager('as', total, AdminSongs.page, AdminSongs.size);
        listEl.innerHTML = html;

        listEl.querySelectorAll('.as-edit').forEach(btn => {
            btn.onclick = () => {
                const song = list.find(x => String(x.id) === btn.dataset.id);
                if (song) openSongForm(song);
            };
        });
        listEl.querySelectorAll('.as-toggle').forEach(btn => {
            btn.onclick = async () => {
                const next = Number(btn.dataset.status);
                const tip = next === 1 ? '确认下架该歌曲？下架后前台不再展示。' : '确认上架该歌曲？';
                if (!confirm(tip)) return;
                try {
                    await adminPostForm('/api/admin/song/status', {
                        id: btn.dataset.id,
                        status: next
                    });
                    await loadAdminSongs();
                } catch (e) { adminError(e); }
            };
        });
        const prev = listEl.querySelector('.as-prev');
        const next = listEl.querySelector('.as-next');
        if (prev) prev.onclick = () => { if (AdminSongs.page > 1) { AdminSongs.page--; loadAdminSongs(); } };
        if (next) next.onclick = () => { AdminSongs.page++; loadAdminSongs(); };
    } catch (e) {
        console.error(e);
        listEl.innerHTML = '<div class="empty-tip">加载失败</div>';
    }
}

function openSongForm(song) {
    const s = song || {};
    const body = `
      <form id="as-form" class="mc-form">
        <input type="hidden" name="id" value="${s.id ?? ''}">
        <label>歌曲名<input name="songName" required value="${escapeHtml(s.songName || '')}"></label>
        <label>歌手<input name="singer" required value="${escapeHtml(s.singer || '')}"></label>
        <label>曲风<input name="type" placeholder="流行 / 国风 / 说唱" value="${escapeHtml(s.type || '')}"></label>
        <label>时长（秒）<input name="duration" type="number" min="0" value="${s.duration ?? ''}"></label>
        <label>音频地址<input name="audioUrl" placeholder="/audio/xxx.mp3" value="${escapeHtml(s.audioUrl || '')}"></label>
        <label>封面地址<input name="coverUrl" placeholder="/img/xxx.jpg" value="${escapeHtml(s.coverUrl || '')}"></label>
        <label>是否 VIP
          <select name="isVip">
            <option value="0" ${Number(s.isVip) === 1 ? '' : 'selected'}>免费</option>
            <option value="1" ${Number(s.isVip) === 1 ? 'selected' : ''}>VIP 专属</option>
          </select>
        </label>
        <label>状态
          <select name="status">
            <option value="0" ${Number(s.status) === 1 ? '' : 'selected'}>上架</option>
            <option value="1" ${Number(s.status) === 1 ? 'selected' : ''}>下架</option>
          </select>
        </label>
        <label>歌词<textarea name="lyric" rows="5">${escapeHtml(s.lyric || '')}</textarea></label>
      </form>`;
    const footer = `<button type="button" id="as-cancel">取消</button>
      <button type="button" id="as-save" class="mc-primary">保存</button>`;
    adminOpenModal(song ? '编辑歌曲' : '新增歌曲', body, footer);

    document.getElementById('as-cancel').onclick = adminCloseModal;
    document.getElementById('as-save').onclick = async () => {
        const fd = new FormData(document.getElementById('as-form'));
        const id = fd.get('id');
        const duration = fd.get('duration');
        const payload = {
            id: id ? Number(id) : null,
            songName: fd.get('songName'),
            singer: fd.get('singer'),
            type: fd.get('type') || null,
            duration: duration === '' ? null : Number(duration),
            audioUrl: fd.get('audioUrl') || null,
            coverUrl: fd.get('coverUrl') || null,
            isVip: Number(fd.get('isVip') || 0),
            status: Number(fd.get('status') || 0),
            lyric: fd.get('lyric') || null
        };
        try {
            await adminPost('/api/admin/song/save', payload);
            adminCloseModal();
            await loadAdminSongs();
        } catch (e) { adminError(e); }
    };
}

function formatDuration(seconds) {
    const n = Number(seconds);
    if (!n || n <= 0) return '-';
    const m = Math.floor(n / 60);
    const sec = n % 60;
    return `${m}:${String(sec).padStart(2, '0')}`;
}
