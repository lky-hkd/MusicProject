// js/pages/history.js
// 播放历史页业务逻辑

// ========== 加载历史列表 ==========
async function loadHistoryList() {
    const listWrap = document.getElementById("historyList");
    if (!listWrap) return;

    try {
        const json = await request('/api/song_history/list');
        if (!json.success) {
            listWrap.innerHTML = `<div class="empty-tip">加载失败：${json.msg || ''}</div>`;
            return;
        }

        const songArr = json.data;
        const normalizedArr = songArr.map(s => ({ ...s, songId: s.songId || s.id }));
        if (!Array.isArray(songArr) || songArr.length === 0) {
            listWrap.innerHTML = `<div class="empty-tip">暂无播放记录，快去听歌吧</div>`;
            return;
        }

        listWrap.innerHTML = "";
        songArr.forEach((songItem, index) => {
            const div = document.createElement("div");
            div.className = "song-item";

            // 封面
            const cover = document.createElement("img");
            cover.className = "song-cover";
            cover.src = songItem.coverUrl || "";
            cover.alt = "封面";
            div.appendChild(cover);

            // 歌曲信息（点击播放）
            const info = document.createElement("div");
            info.className = "song-info";
            info.onclick = () => {
                AudioManager.setQueue(normalizedArr, songItem.songId);
                AudioManager.loadSongById(songItem.songId);
            };
            info.innerHTML = `
                <div class="song-name">${escapeHtml(songItem.songName)}</div>
                <div class="song-author">${escapeHtml(songItem.singer || "未知歌手")}</div>
            `;
            div.appendChild(info);

            // 删除按钮
            const btn = document.createElement("button");
            btn.className = "del-btn";
            btn.title = "删除";
            btn.innerHTML = DEL_ICON;
            btn.onclick = () => deleteHistory(songItem.songId);
            div.appendChild(btn);

            listWrap.appendChild(div);
        });
    } catch (err) {
        console.error("[history] 加载失败：", err);
        listWrap.innerHTML = `<div class="empty-tip">网络异常</div>`;
    }
}

// ========== 删除单条历史 ==========
async function deleteHistory(songId) {
    if (!confirm("确定删除这条播放记录吗？")) return;
    try {
        const json = await request(`/api/song_history/delete?songId=${songId}`, { method: "POST" });
        if (json.success) {
            loadHistoryList();
        } else {
            if (json.msg === "请先登录") {
                if (confirm("尚未登录，前往登录页面？")) {
                    location.hash = '#/login';
                }
            } else {
                alert(json.msg || "删除失败");
            }
        }
    } catch (err) {
        console.error("[history] 删除失败：", err);
        alert("网络请求失败");
    }
}

// ========== 清空全部历史 ==========
async function clearAllHistory() {
    if (!confirm("确定清空所有播放历史吗？此操作不可恢复！")) return;
    try {
        const json = await request('/api/song_history/clearAll', { method: "POST" });
        if (json.success) {
            loadHistoryList();
        } else {
            if (json.msg === "请先登录") {
                if (confirm("尚未登录，前往登录页面？")) {
                    location.hash = '#/login';
                }
            } else {
                alert(json.msg || "清空失败");
            }
        }
    } catch (err) {
        console.error("[history] 清空失败：", err);
        alert("网络请求失败");
    }
}

// ========== 注册页面钩子 ==========
registerPageHook('history', async () => {
    await loadHistoryList();
});