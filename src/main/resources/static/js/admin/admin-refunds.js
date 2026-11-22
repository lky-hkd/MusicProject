// js/admin/admin-refunds.js  管理员 - 退款管理

const AdminRefunds = { page: 1, size: 10, keyword: '', status: '' };

async function renderAdminRefunds() {
  const el = document.getElementById('adminContent');
  el.innerHTML = `
    <div class=\"mc-toolbar\">
      <input id=\"ar-kw\" placeholder=\"订单号 / 退款单号\" value=\"${escapeHtml(AdminRefunds.keyword)}\">
      <select id=\"ar-status\">
        <option value=\"\">全部状态</option>
        ${[1,2,3,4,5].map(s =>
          `<option value=\"${s}\" ${AdminRefunds.status===String(s)?'selected':''}>${refundStatusText(s)}</option>`
        ).join('')}
      </select>
      <button id=\"ar-search\">搜索</button>
    </div>
    <div id=\"ar-list\">加载中...</div>`;
  document.getElementById('ar-search').onclick = () => {
    AdminRefunds.keyword = document.getElementById('ar-kw').value.trim();
    AdminRefunds.status = document.getElementById('ar-status').value;
    AdminRefunds.page = 1; loadAdminRefunds();
  };
  loadAdminRefunds();
}

async function loadAdminRefunds() {
  const listEl = document.getElementById('ar-list');
  listEl.innerHTML = '加载中...';
  try {
    const data = await adminPost('/api/admin/refund/list', {
      page: AdminRefunds.page, size: AdminRefunds.size,
      keyword: AdminRefunds.keyword || null,
      status: AdminRefunds.status !== '' ? Number(AdminRefunds.status) : null
    });
    const list = data.records || [];
    const total = Number(data.total || 0);
    let html = `<table class=\"mc-table\"><thead><tr>
      <th>退款单号</th><th>订单号</th><th>退款金额</th><th>原因</th><th>状态</th><th>申请时间</th><th>操作</th>
    </tr></thead><tbody>`;
    if (!list.length) html += `<tr><td colspan=\"7\">暂无退款单</td></tr>`;
    list.forEach(r => {
      html += `<tr>
        <td>${escapeHtml(r.refundNo)}</td>
        <td>${escapeHtml(r.orderNo)}</td>
        <td>¥${r.refundAmount ?? 0}</td>
        <td style=\"max-width:200px;overflow:hidden;text-overflow:ellipsis;\">${escapeHtml(r.reason || '-')}</td>
        <td>${refundStatusText(r.status)}</td>
        <td>${r.createTime ? escapeHtml(String(r.createTime).replace('T',' ')) : '-'}</td>
        <td>${r.status === 1 ? `
          <button class=\"ar-approve\" data-id=\"${r.id}\">通过</button>
          <button class=\"ar-reject\" data-id=\"${r.id}\">拒绝</button>` : '-'}</td>
      </tr>`;
    });
    html += `</tbody></table>` + adminPager('ar', total, AdminRefunds.page, AdminRefunds.size);
    listEl.innerHTML = html;

    listEl.querySelectorAll('.ar-approve').forEach(btn => {
      btn.onclick = () => approveRefund(btn.dataset.id);
    });
    listEl.querySelectorAll('.ar-reject').forEach(btn => {
      btn.onclick = () => openRejectModal(btn.dataset.id);
    });
    const prev = listEl.querySelector('.ar-prev');
    const next = listEl.querySelector('.ar-next');
    if (prev) prev.onclick = () => { if (AdminRefunds.page > 1) { AdminRefunds.page--; loadAdminRefunds(); } };
    if (next) next.onclick = () => { AdminRefunds.page++; loadAdminRefunds(); };
  } catch (e) {
    adminError(e); listEl.innerHTML = '加载失败';
  }
}

async function approveRefund(refundId) {
  if (!confirm('确定通过该退款申请？将调用支付宝发起退款。')) return;
  try {
    await adminPostForm('/api/admin/refund/approve', { refundId });
    loadAdminRefunds();
  } catch (e) { adminError(e); }
}

function openRejectModal(refundId) {
  const body = `
    <form id=\"ar-form\" class=\"mc-form\">
      <input type=\"hidden\" name=\"refundId\" value=\"${refundId}\">
      <label>拒绝理由<input name=\"reason\" required></label>
    </form>`;
  const footer = `<button type=\"button\" id=\"ar-cancel\">取消</button>
    <button type=\"button\" id=\"ar-save\" class=\"mc-primary\">确认拒绝</button>`;
  adminOpenModal('拒绝退款', body, footer);
  document.getElementById('ar-cancel').onclick = adminCloseModal;
  document.getElementById('ar-save').onclick = async () => {
    const fd = new FormData(document.getElementById('ar-form'));
    const reason = (fd.get('reason') || '').trim();
    if (!reason) { alert('请填写拒绝理由'); return; }
    try {
      await adminPostForm('/api/admin/refund/reject', { refundId: fd.get('refundId'), reason });
      adminCloseModal();
      loadAdminRefunds();
    } catch (e) { adminError(e); }
  };
}