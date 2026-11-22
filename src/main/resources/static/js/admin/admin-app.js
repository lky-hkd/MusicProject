// js/admin/admin-app.js  后台入口：权限校验 + 菜单渲染 + 路由
document.addEventListener('DOMContentLoaded', initAdminApp);

async function initAdminApp() {
  document.getElementById('adminModalClose').onclick = adminCloseModal;
  document.getElementById('adminModal').addEventListener('click', (e) => {
    if (e.target.id === 'adminModal') adminCloseModal();
  });

  let user;
  try {
    const res = await fetch('/api/user/getUserInfo', { credentials: 'include' });
    const json = await res.json();
    if (!json.success || !json.data) {
      alert('请先登录');
      location.href = '/index.html#/login';
      return;
    }
    user = json.data;
  } catch (e) {
    alert('获取用户信息失败');
    location.href = '/index.html#/home';
    return;
  }

  AdminCtx.role = user.role;
  AdminCtx.userId = user.id;
  AdminCtx.username = user.nickname || user.username;

  if (AdminCtx.role < 1) {
    alert('无后台访问权限');
    location.href = '/index.html#/home';
    return;
  }

  const roleName = AdminCtx.role === 2 ? '管理员' : '商家';
  document.getElementById('adminUserInfo').textContent = `${AdminCtx.username}（${roleName}）`;
  document.getElementById('adminRoleBadge').textContent = roleName + '后台';

  renderAdminMenu();

  const first = getVisibleMenus()[0];
  if (first) switchAdminMenu(first.key);
}

function getVisibleMenus() {
  return ADMIN_MENUS.filter(m => !m.group && AdminCtx.role >= m.minRole);
}

function renderAdminMenu() {
  const nav = document.getElementById('adminMenu');
  let html = '';
  ADMIN_MENUS.forEach(m => {
    if (AdminCtx.role < m.minRole) return;
    if (m.group) {
      html += `<div class="admin-menu-group">${m.group}</div>`;
    } else {
      html += `<div class="admin-menu-item" data-key="${m.key}">${m.title}</div>`;
    }
  });
  nav.innerHTML = html;
  nav.querySelectorAll('.admin-menu-item').forEach(item => {
    item.onclick = () => switchAdminMenu(item.dataset.key);
  });
}

function switchAdminMenu(key) {
  const menu = ADMIN_MENUS.find(m => m.key === key);
  if (!menu || AdminCtx.role < menu.minRole) return;

  AdminCtx.current = key;
  document.querySelectorAll('.admin-menu-item').forEach(el =>
    el.classList.toggle('active', el.dataset.key === key));
  document.getElementById('adminPageTitle').textContent = menu.title;

  try {
    menu.render();
  } catch (e) {
    console.error(e);
    document.getElementById('adminContent').innerHTML = '页面加载失败';
  }
}

// ============ 管理员看板 ============
async function renderAdminDashboard() {
  const el = document.getElementById('adminContent');
  el.innerHTML = '加载中...';
  try {
    const d = await adminPost('/api/admin/dashboard/summary');
    el.innerHTML = `
      <div class="mc-cards">
        <div class="mc-card"><div class="mc-card-num">${d.totalUsers ?? 0}</div><div class="mc-card-label">总用户数</div></div>
        <div class="mc-card"><div class="mc-card-num">${d.totalMerchants ?? 0}</div><div class="mc-card-label">店铺总数</div></div>
        <div class="mc-card"><div class="mc-card-num">${d.enabledMerchants ?? 0}</div><div class="mc-card-label">正常营业店铺</div></div>
        <div class="mc-card"><div class="mc-card-num">${d.totalOrders ?? 0}</div><div class="mc-card-label">总订单数</div></div>
        <div class="mc-card"><div class="mc-card-num">${d.pendingRefund ?? 0}</div><div class="mc-card-label">待处理退款</div></div>
      </div>`;
  } catch (e) { adminError(e); el.innerHTML = '加载失败'; }
}