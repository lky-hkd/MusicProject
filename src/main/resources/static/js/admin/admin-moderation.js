// js/admin/admin-moderation.js  管理员 - 内容审核（待审队列）

const AdminModeration = { type: 'POST' };

const MODERATION_TABS = [
    { key: 'POST', label: '动态' },
    { key: 'COMMENT', label: '评论' },
    { key: 'REVIEW', label: '商品评价' },
    { key: 'NICKNAME', label: '昵称' },
    { key: 'SIGNATURE', label: '个性签名' }
];

function renderAdminModeration() {
    const el = document.getElementById('adminContent');
    el.innerHTML = `
      <div class="mc-toolbar" id="am-tabs">
        ${MODERATION_TABS.map(t =>
            `<button class="am-tab" data-type="${t.key}">${t.label}</button>`).join('')}
        <button id="am-refresh" style="margin-left:auto;">刷新</button>
      </div>
      <div class="admin-hint">
        可疑内容会先隐藏并进入待审：通过后正常展示，驳回按删除处理。昵称/签名的「通过」会直接写回用户资料。
      </div>
      <div id="am-list">加载中...</div>`;

    el.querySelectorAll('.am-tab').forEach(btn => {
        btn.onclick = () => {
            AdminModeration.type = btn.dataset.type;
            loadModerationList();
        };
    });
    document.getElementById('am-refresh').onclick = loadModerationList;
    loadModerationList();
}

async function loadModerationList() {
    const listEl = document.getElementById('am-list');
    if (!listEl) return;
    // 高亮当前类型
    document.querySelectorAll('#am-tabs .am-tab').forEach(btn => {
        btn.classList.toggle('mc-primary', btn.dataset.type === AdminModeration.type);
    });
    listEl.innerHTML = '加载中...';
    try {
        const json = await request(
            `/api/admin/moderation/pending?type=${AdminModeration.type}&limit=50`);
        if (!json.success) throw new Error(json.msg || '加载失败');
        const list = json.data || [];

        let html = `<table class="mc-table"><thead><tr>
          <th>待审ID</th><th>用户ID</th><th>内容</th><th>关联</th><th>提交时间</th><th>操作</th>
        </tr></thead><tbody>`;
        if (!list.length) {
            html += `<tr><td colspan="6">暂无待审内容</td></tr>`;
        }
        list.forEach(item => {
            const relate = item.postId != null ? `动态 #${item.postId}`
                : (item.productId != null ? `商品 #${item.productId}` : '-');
            html += `<tr>
              <td>${item.id}</td>
              <td>${item.userId ?? '-'}</td>
              <td class="am-content">${escapeHtml(item.content || '')}</td>
              <td>${relate}</td>
              <td>${item.createTime ? escapeHtml(String(item.createTime).replace('T', ' ')) : '-'}</td>
              <td>
                <button class="am-approve" data-id="${item.id}">通过</button>
                <button class="am-reject" data-id="${item.id}">驳回</button>
              </td>
            </tr>`;
        });
        html += '</tbody></table>';
        listEl.innerHTML = html;

        listEl.querySelectorAll('.am-approve').forEach(btn => {
            btn.onclick = () => decideModeration(btn.dataset.id, 'approve');
        });
        listEl.querySelectorAll('.am-reject').forEach(btn => {
            btn.onclick = () => decideModeration(btn.dataset.id, 'reject');
        });
    } catch (e) {
        console.error(e);
        listEl.innerHTML = '<div class="empty-tip">加载失败</div>';
    }
}

async function decideModeration(id, action) {
    if (action === 'reject' && !confirm('确认驳回？驳回后该内容不会展示。')) return;
    try {
        await adminPostForm(`/api/admin/moderation/${action}`, {
            type: AdminModeration.type,
            id: id
        });
        await loadModerationList();
    } catch (e) {
        adminError(e);
    }
}
