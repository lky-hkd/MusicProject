// js/pages/collect.js
// 收藏页业务逻辑

// ========== 加载收藏列表 ==========
async function loadCollectList() {
    const listWrap = document.getElementById("collectList");
    if (!listWrap) return;

    try {
        const json = await request('/api/collect/list');
        if (!json.success) {
            listWrap.innerHTML = `<div class="empty-tip">加载失败：${json.msg || ''}</div>`;
            return;
        }

        const songArr = json.data;
        const normalizedArr = songArr.map(s => ({ ...s, songId: s.songId || s.id }));
        if (!Array.isArray(normalizedArr) || normalizedArr.length === 0) {
            listWrap.innerHTML = `<div class="empty-tip">暂无收藏歌曲，快去收藏喜欢的音乐吧</div>`;
            return;
        }

        // 用 DOM API 构建，避免 innerHTML 拼接 onclick 的 XSS 风险
        listWrap.innerHTML = "";
        normalizedArr.forEach((songItem, index) => {
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

            // 取消收藏按钮
            const btn = document.createElement("button");
            btn.className = "cancel-collect";
            btn.innerText = "取消收藏";
            btn.onclick = () => cancelCollect(songItem.songId);
            div.appendChild(btn);

            listWrap.appendChild(div);
        });
    } catch (err) {
        console.error("[collect] 加载失败：", err);
        listWrap.innerHTML = `<div class="empty-tip">网络异常</div>`;
    }
}

// ========== 取消收藏 ==========
async function cancelCollect(songId) {
    if (!confirm("确定取消收藏这首歌吗？")) return;
    try {
        const json = await request(`/api/collect/cancel?songId=${songId}`, { method: "POST" });
        if (json.success) {
            alert("已取消收藏");
            loadCollectList();  // 刷新列表
        } else {
            if (json.msg === "请先登录") {
                if (confirm("尚未登录，前往登录页面？")) {
                    location.hash = '#/login';
                }
            } else {
                alert(json.msg || "操作失败");
            }
        }
    } catch (err) {
        console.error("[collect] 取消失败：", err);
        alert("网络请求失败");
    }
}

// ========== 注册页面钩子 ==========
registerPageHook('collect', async () => {
    await loadCollectList();
});