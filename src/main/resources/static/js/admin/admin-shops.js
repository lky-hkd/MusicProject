// js/admin/admin-shops.js  管理员 - 店铺管理

const AdminShops = { page: 1, size: 10, keyword: '', status: '' };

async function renderAdminShops() {
  const el = document.getElementById('adminContent');
  el.innerHTML = `
    <div class=\"mc-toolbar\">
      <input id=\"as-kw\" placeholder=\"店铺名称\" value=\"${escapeHtml(AdminShops.keyword)}\">
      <select id=\"as-status\">
        <option value=\"\">全部状态</option>
        <option value=\"1\" ${AdminShops.status==='1'?'selected':''}>正常</option>
        <option value=\"0\" ${AdminShops.status==='0'?'selected':''}>禁用</option>
      </select>
      <button id=\"as-search\">搜索</button>
    </div>
    <div id=\"as-list\">加载中...</div>`;
  document.getElementById('as-search').onclick = () => {
    AdminShops.keyword = document.getElementById('as-kw').value.trim();
    AdminShops.status = document.getElementById('as-status').value;
    AdminShops.page = 1; loadAdminShops();
  };
  loadAdminShops();
}

async function loadAdminShops() {
  const listEl = document.getElementById('as-list');
  listEl.innerHTML = '加载中...';
  try {
    const data = await adminPost('/api/admin/merchant/list', {
      page: AdminShops.page, size: AdminShops.size,
      keyword: AdminShops.keyword || null,
      status: AdminShops.status !== '' ? Number(AdminShops.status) : null
    });
    const list = data.records || [];
    const total = Number(data.total || 0);
    let html = `<table class=\"mc-table\"><thead><tr>
      <th>ID</th><th>店铺名称</th><th>联系电话</th><th>简介</th><th>状态</th><th>创建时间</th><th>操作</th>
    </tr></thead><tbody>`;
    if (!list.length) html += `<tr><td colspan=\"7\">暂无店铺</td></tr>`;
    list.forEach(m => {
      const next = m.status === 1 ? 0 : 1;
      html += `<tr>
        <td>${m.id}</td>
        <td>${escapeHtml(m.shopName)}</td>
        <td>${escapeHtml(m.contactPhone || '-')}</td>
        <td style=\"max-width:220px;overflow:hidden;text-overflow:ellipsis;\">${escapeHtml(m.description || '-')}</td>
        <td>${m.status === 1 ? '正常' : '禁用'}</td>
        <td>${m.createTime ? escapeHtml(String(m.createTime).replace('T',' ')) : '-'}</td>
        <td><button class=\"as-toggle\" data-id=\"${m.id}\" data-status=\"${next}\">${m.status === 1 ? '禁用' : '启用'}</button></td>
      </tr>`;
    });
    html += `</tbody></table>` + adminPager('as', total, AdminShops.page, AdminShops.size);
    listEl.innerHTML = html;

    listEl.querySelectorAll('.as-toggle').forEach(btn => {
      btn.onclick = () => toggleShopStatus(btn.dataset.id, btn.dataset.status);
    });
    const prev = listEl.querySelector('.as-prev');
    const next = listEl.querySelector('.as-next');
    if (prev) prev.onclick = () => { if (AdminShops.page > 1) { AdminShops.page--; loadAdminShops(); } };
    if (next) next.onclick = () => { AdminShops.page++; loadAdminShops(); };
  } catch (e) {
    adminError(e); listEl.innerHTML = '加载失败';
  }
}

async function toggleShopStatus(id, status) {
  const action = status === 1 ? '启用' : '禁用';
  if (!confirm(`确定${action}该店铺？`)) return;
  try {
    await adminPost('/api/admin/merchant/status', { id: Number(id), status: Number(status) });
    loadAdminShops();
  } catch (e) { adminError(e); }
}