// js/admin/admin-users.js  管理员 - 用户管理

const AdminUsers = { page: 1, size: 10, keyword: '', role: '' };

async function renderAdminUsers() {
  const el = document.getElementById('adminContent');
  el.innerHTML = `
    <div class=\"mc-toolbar\">
      <input id=\"au-kw\" placeholder=\"用户名 / 昵称\" value=\"${escapeHtml(AdminUsers.keyword)}\">
      <select id=\"au-role\">
        <option value=\"\">全部角色</option>
        <option value=\"0\" ${AdminUsers.role==='0'?'selected':''}>普通用户</option>
        <option value=\"1\" ${AdminUsers.role==='1'?'selected':''}>商家</option>
        <option value=\"2\" ${AdminUsers.role==='2'?'selected':''}>管理员</option>
      </select>
      <button id=\"au-search\">搜索</button>
    </div>
    <div id=\"au-list\">加载中...</div>`;
  document.getElementById('au-search').onclick = () => {
    AdminUsers.keyword = document.getElementById('au-kw').value.trim();
    AdminUsers.role = document.getElementById('au-role').value;
    AdminUsers.page = 1; loadAdminUsers();
  };
  loadAdminUsers();
}

async function loadAdminUsers() {
  const listEl = document.getElementById('au-list');
  listEl.innerHTML = '加载中...';
  try {
    const data = await adminPost('/api/admin/user/list', {
      page: AdminUsers.page, size: AdminUsers.size,
      keyword: AdminUsers.keyword || null,
      role: AdminUsers.role !== '' ? Number(AdminUsers.role) : null
    });
    const list = data.records || [];
    const total = Number(data.total || 0);
    let html = `<table class=\"mc-table\"><thead><tr>
      <th>ID</th><th>用户名</th><th>昵称</th><th>角色</th><th>注册时间</th><th>操作</th>
    </tr></thead><tbody>`;
    if (!list.length) html += `<tr><td colspan=\"6\">暂无用户</td></tr>`;
    list.forEach(u => {
      html += `<tr>
        <td>${u.id}</td>
        <td>${escapeHtml(u.username)}</td>
        <td>${escapeHtml(u.nickname || '-')}</td>
        <td>${roleText(u.role)}</td>
        <td>${u.createTime ? escapeHtml(String(u.createTime).replace('T',' ')) : '-'}</td>
        <td><button class=\"au-change\" data-id=\"${u.id}\" data-role=\"${u.role}\">改角色</button></td>
      </tr>`;
    });
    html += `</tbody></table>` + adminPager('au', total, AdminUsers.page, AdminUsers.size);
    listEl.innerHTML = html;

    listEl.querySelectorAll('.au-change').forEach(btn => {
      btn.onclick = () => openUserRoleModal(btn.dataset.id, btn.dataset.role);
    });
    const prev = listEl.querySelector('.au-prev');
    const next = listEl.querySelector('.au-next');
    if (prev) prev.onclick = () => { if (AdminUsers.page > 1) { AdminUsers.page--; loadAdminUsers(); } };
    if (next) next.onclick = () => { AdminUsers.page++; loadAdminUsers(); };
  } catch (e) {
    adminError(e); listEl.innerHTML = '加载失败';
  }
}

function openUserRoleModal(userId, currentRole) {
  const body = `
    <form id=\"au-form\" class=\"mc-form\">
      <input type=\"hidden\" name=\"userId\" value=\"${userId}\">
      <label>角色
        <select name=\"role\">
          <option value=\"0\" ${currentRole==='0'?'selected':''}>普通用户</option>
          <option value=\"1\" ${currentRole==='1'?'selected':''}>商家</option>
          <option value=\"2\" ${currentRole==='2'?'selected':''}>管理员</option>
        </select>
      </label>
      <label style=\"flex-direction:row;align-items:center;gap:8px;\">
        <input type=\"checkbox\" name=\"forceLogout\" value=\"true\" style=\"width:auto;\"> 强制用户重新登录生效
      </label>
    </form>`;
  const footer = `<button type=\"button\" id=\"au-cancel\">取消</button>
    <button type=\"button\" id=\"au-save\" class=\"mc-primary\">保存</button>`;
  adminOpenModal('修改用户角色', body, footer);
  document.getElementById('au-cancel').onclick = adminCloseModal;
  document.getElementById('au-save').onclick = async () => {
    const fd = new FormData(document.getElementById('au-form'));
    try {
      await adminPostForm('/api/admin/user/changeRole', {
        userId: fd.get('userId'),
        role: fd.get('role'),
        forceLogout: fd.get('forceLogout') === 'true'
      });
      adminCloseModal();
      loadAdminUsers();
    } catch (e) { adminError(e); }
  };
}