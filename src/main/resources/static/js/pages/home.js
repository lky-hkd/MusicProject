// js/pages/home.js
// 首页业务逻辑（从原 indexBiz.js 迁移改造）

// VIP 角标
function vipBadge(song) {
    return (song && (song.isVip === 1 || song.isVip === true))
        ? '<span style="background:linear-gradient(135deg,#a099d8,#c084fc);color:#7000ff;font-size:10px;padding:1px 5px;border-radius:4px;margin-left:6px;vertical-align:middle;">VIP</span>'
        : '';
}

// ========== 排行榜加载 ==========
async function loadRank() {
    const wrap = document.getElementById("rankWrap");
    if (!wrap) return;
    wrap.innerHTML = `<div style="padding:16px">加载中...</div>`;
    try {
        const res = await fetch("/api/song/rank", { credentials: "include" });
        const json = await res.json();
        if (!json.success || !Array.isArray(json.data)) {
            wrap.innerHTML = `<div style="padding:16px">榜单加载失败</div>`;
            return;
        }
        const list = json.data;
        const normalizedList = list.map(s => ({ ...s, songId: s.songId || s.id }));
        wrap.innerHTML = "";
        normalizedList.forEach((song, idx) => {
            const div = document.createElement("div");
            div.className = "rank-item";
            div.onclick = () => goPlayHot(song);
            let numClass = "";
            if (idx === 0) numClass = "top1";
            if (idx === 1) numClass = "top2";
            if (idx === 2) numClass = "top3";
            div.innerHTML = `
                <div class="rank-num ${numClass}">${idx + 1}</div>
                <div class="rank-name">${escapeHtml(song.songName)}${vipBadge(song)}<span class="rank-author">${escapeHtml(song.singer)}</span></div>
            `;
            wrap.appendChild(div);
        });
    } catch (err) {
        wrap.innerHTML = `<div style="padding:16px">网络异常，加载失败</div>`;
        console.error(err);
    }
}

// ========== 分类加载 ==========
async function loadByCategory(type) {
    document.getElementById("searchResultTitle").style.display = "none";
    document.getElementById("searchResultWrap").style.display = "none";
    document.getElementById("backRankWrap").style.display = "none";
    document.getElementById("rankWrap").style.display = "block";
    document.getElementById("search-input").value = "";

    if (type === "") { loadRank(); return; }

    const wrap = document.getElementById("rankWrap");
    wrap.innerHTML = `<div style="padding:16px">加载中...</div>`;
    try {
        const res = await fetch(`/api/song/type?type=${type}`, { credentials: "include" });
        const json = await res.json();
        if (!json.success || !Array.isArray(json.data)) {
            wrap.innerHTML = `<div style="padding:16px">分类歌曲加载失败</div>`;
            return;
        }
        const list = json.data;
        const normalizedList = list.map(s => ({ ...s, songId: s.songId || s.id }));
        wrap.innerHTML = "";
        normalizedList.forEach((song, idx) => {
            const div = document.createElement("div");
            div.className = "rank-item";
            div.onclick = () => goPlayHot(song);
            div.innerHTML = `
                <div class="rank-num">${idx + 1}</div>
                <div class="rank-name">${escapeHtml(song.songName)}${vipBadge(song)}<span class="rank-author">${escapeHtml(song.singer)}</span></div>
            `;
            wrap.appendChild(div);
        });
    } catch (err) {
        wrap.innerHTML = `<div style="padding:16px">网络异常，加载失败</div>`;
        console.error(err);
    }
}

// ========== 点击歌曲播放（热门榜）==========
function goPlayHot(songItem) {
    // 队列就是当前展示的榜
    const list = Array.from(document.querySelectorAll('#rankWrap .rank-item'));
    // 简化处理：只播这一首，队列为单曲
    AudioManager.setQueue([songItem], songItem.songId);
    AudioManager.loadSongById(songItem.songId);
}

// ========== 搜索结果点击播放 ==========
function goPlay(songItem, index, songArr) {
    AudioManager.setQueue(songArr, songItem.songId);
    AudioManager.loadSongById(songItem.songId);
}

// ========== 用户信息渲染 ==========
async function renderUserHeader() {
    try {
        const res = await fetch("/api/user/getUserInfo", { credentials: "include" });
        const json = await res.json();
        const logined = document.querySelector(".user-logined");
        const notLogin = document.querySelector(".user-not-login");
        const vipEntry = document.getElementById("vipEntry");
        if (json.success && json.data) {
            if (logined) logined.style.display = "flex";
            if (notLogin) notLogin.style.display = "none";
            const nickEl = document.getElementById("topNickname");
            if (nickEl) nickEl.innerText = json.data.nickname || json.data.username || "";
            if (json.data.avatar) {
                const av = document.getElementById("topAvatar");
                if (av) av.src = json.data.avatar;
            }
            // 已登录:非 VIP 显示开通入口,VIP 用户隐藏
            if (vipEntry) vipEntry.style.display = json.data.vip ? "none" : "block";
        } else {
            if (logined) logined.style.display = "none";
            if (notLogin) notLogin.style.display = "flex";
            if (vipEntry) vipEntry.style.display = "none";
        }
    } catch (err) {
        console.error(err);
        const logined = document.querySelector(".user-logined");
        const notLogin = document.querySelector(".user-not-login");
        if (logined) logined.style.display = "none";
        if (notLogin) notLogin.style.display = "flex";
    }
}

// ========== 绑定首页交互事件（只在第一次进入首页时绑一次）==========
let homeEventsBound = false;
function bindHomeEvents() {
    if (homeEventsBound) return;
    homeEventsBound = true;

    // 搜索按钮
    const searchBtn = document.getElementById("search-btn");
    if (searchBtn) {
        searchBtn.onclick = async function () {
            const kw = document.getElementById("search-input").value.trim();
            if (!kw) { alert("请输入歌曲名称"); return; }
            try {
                const res = await fetch("/api/searchLyric", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ query: kw }),
                    credentials: "include"
                });
                const result = await res.json();

                const searchWrap = document.getElementById("searchResultWrap");
                const searchTitle = document.getElementById("searchResultTitle");
                const rankWrap = document.getElementById("rankWrap");
                const backBtnWrap = document.getElementById("backRankWrap");

                searchWrap.innerHTML = "";
                if (result.success && Array.isArray(result.data)) {
                    const songList = result.data;
                    if (songList.length === 0) {
                        searchWrap.innerHTML = "<div style='padding:16px'>没有找到匹配歌曲</div>";
                    } else {
                        songList.forEach((song, idx) => {
                            const div = document.createElement("div");
                            div.className = "rank-item";
                            div.onclick = () => goPlay(song, idx, songList);
                            div.innerHTML = `
                                <div class="rank-num">${idx + 1}</div>
                                <div class="rank-name">${escapeHtml(song.songName)}${vipBadge(song)}<span class="rank-author">${escapeHtml(song.singer)}</span></div>
                            `;
                            searchWrap.appendChild(div);
                        });
                    }
                    searchTitle.style.display = "block";
                    searchWrap.style.display = "block";
                    backBtnWrap.style.display = "block";
                    rankWrap.style.display = "none";
                } else {
                    alert(result.msg || "搜索失败，请重试");
                }
            } catch (err) {
                console.error("搜索异常", err);
                alert("搜索失败，请稍后重试");
            }
        };
    }

    // 回车搜索
    const searchInput = document.getElementById("search-input");
    if (searchInput) {
        searchInput.onkeydown = function (e) {
            if (e.key === "Enter") {
                const btn = document.getElementById("search-btn");
                if (btn) btn.click();
            }
        };
    }

    // 返回排行榜
    const backBtn = document.getElementById("backRankBtn");
    if (backBtn) {
        backBtn.onclick = function () {
            document.getElementById("searchResultWrap").innerHTML = "";
            document.getElementById("searchResultTitle").style.display = "none";
            document.getElementById("searchResultWrap").style.display = "none";
            document.getElementById("backRankWrap").style.display = "none";
            document.getElementById("rankWrap").style.display = "block";
            document.getElementById("search-input").value = "";
            loadRank();
        };
    }

    // 分类标签
    document.querySelectorAll(".tag-item").forEach(tag => {
        tag.onclick = function () {
            document.querySelectorAll(".tag-item").forEach(t => t.classList.remove("active"));
            this.classList.add("active");
            loadByCategory(this.dataset.type);
        };
    });

    // 曲风卡片
    document.querySelectorAll(".type-card").forEach(card => {
        card.onclick = function () {
            const type = this.dataset.type;
            document.querySelectorAll(".tag-item").forEach(t => t.classList.remove("active"));
            const target = document.querySelector(`.tag-item[data-type="${type}"]`);
            if (target) target.classList.add("active");
            loadByCategory(type);
        };
    });

    // 退出登录
    const logoutBtn = document.getElementById("logoutBtn");
    if (logoutBtn) {
        logoutBtn.onclick = async function (e) {
            e.stopPropagation();
            if (!confirm("确定退出登录吗？")) return;
            const res = await fetch("/api/user/logout", { method: "POST", credentials: "include" });
            const json = await res.json();
            if (json.success) {
                alert("已退出");
                location.reload();
            } else {
                alert(json.msg);
            }
        };
    }
}

// ========== 注册页面钩子 ==========
registerPageHook('home', async () => {
    bindHomeEvents();
    await renderUserHeader();
    await loadRank();
});