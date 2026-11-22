// js/pages/user.js
// 用户页业务逻辑

// ========== 按角色显示功能入口 ==========
function applyRoleEntries(role) {
    const applyCard = document.getElementById('merchantApplyCard');
    const centerCard = document.getElementById('merchantCenterCard');
    const adminCard = document.getElementById('adminEntryCard');

    const r = (role === null || role === undefined) ? -1 : Number(role);
    // 0 普通用户 / 1 商家 / 2 管理员
    if (applyCard) applyCard.style.display = (r === 0) ? '' : 'none';
    if (centerCard) centerCard.style.display = (r === 1) ? '' : 'none';
    if (adminCard) adminCard.style.display = (r >= 2) ? '' : 'none';
}

// ========== 加载用户信息 ==========
async function loadUserInfo() {
    try {
        const res = await fetch("/api/user/getUserInfo", { credentials: "include" });
        const json = await res.json();
        const nicknameEl = document.getElementById('nickname');
        const userDescEl = document.getElementById('userDesc');
        const avatarEl = document.getElementById('avatar');

        if (json.success && json.data) {
            const user = json.data;
            if (nicknameEl) nicknameEl.innerText = user.nickname || user.username || '用户';
            if (userDescEl) userDescEl.innerText = "这个人很懒，什么都没写";
            if (avatarEl && user.avatar) {
                avatarEl.src = user.avatar;
            }
            // 关注 / 粉丝数量
            const followingEl = document.getElementById('followingCount');
            if (followingEl) followingEl.innerText = user.followingCount != null ? user.followingCount : 0;
            const followSummaryEl = document.getElementById('followSummary');
            if (followSummaryEl) {
                followSummaryEl.innerText = `粉丝 ${user.followerCount != null ? user.followerCount : 0} 人 · 点击查看`;
            }
            applyRoleEntries(user.role);
        } else {
            if (nicknameEl) nicknameEl.innerText = "未登录";
            if (userDescEl) userDescEl.innerText = "请先登录";
            const followingEl = document.getElementById('followingCount');
            if (followingEl) followingEl.innerText = "-";
            const followSummaryEl = document.getElementById('followSummary');
            if (followSummaryEl) followSummaryEl.innerText = "粉丝 - 人 · 点击查看";
            applyRoleEntries(null);
        }
    } catch (e) {
        console.error("[user] 加载用户信息失败", e);
        const nicknameEl = document.getElementById('nickname');
        if (nicknameEl) nicknameEl.innerText = "加载失败";
        applyRoleEntries(null);
    }
}

// ========== 加载收藏数量 + 历史数量 ==========
async function loadCount() {
    // 收藏数
    try {
        const collectRes = await fetch("/api/collect/count", { credentials: "include" });
        const collectJson = await collectRes.json();
        const collectCountEl = document.getElementById("collectCount");
        if (collectCountEl) {
            collectCountEl.innerText = (collectJson.success && collectJson.data != null)
                ? collectJson.data : 0;
        }
    } catch (e) {
        console.error("[user] 读取收藏数量失败", e);
        const collectCountEl = document.getElementById("collectCount");
        if (collectCountEl) collectCountEl.innerText = "0";
    }

    // 历史数
    try {
        const historyRes = await fetch("/api/song_history/count", { credentials: "include" });
        const historyJson = await historyRes.json();
        const historyCountEl = document.getElementById("historyCount");
        if (historyCountEl) {
            historyCountEl.innerText = (historyJson.success && historyJson.data != null)
                ? historyJson.data : 0;
        }
    } catch (e) {
        console.error("[user] 读取历史数量失败", e);
        const historyCountEl = document.getElementById("historyCount");
        if (historyCountEl) historyCountEl.innerText = "0";
    }
}

// ========== 注册页面钩子 ==========
registerPageHook('user', async () => {
    await loadUserInfo();
    await loadCount();
});