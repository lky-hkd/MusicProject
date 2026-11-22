// js/admin/admin-coupons.js
// 后台：优惠券管理（普通券 / 秒杀券 发放）

async function renderAdminVouchers() {
    const el = document.getElementById('adminContent');
    el.innerHTML = `
      <div class="mc-toolbar">
        <button id="vc-add" class="mc-primary">+ 新建优惠券</button>
      </div>
      <div id="vc-table-wrap">加载中...</div>`;
    document.getElementById('vc-add').onclick = openVoucherForm;
    await loadVoucherTable();
}

async function loadVoucherTable() {
    const wrap = document.getElementById('vc-table-wrap');
    if (!wrap) return;
    try {
        const json = await request('/api/voucher/list');
        if (!json.success) throw new Error(json.msg || '加载失败');
        const list = json.data || [];
        if (list.length === 0) {
            wrap.innerHTML = '<div class="empty-tip">暂无优惠券，点击上方按钮新建</div>';
            return;
        }

        let html = `<table class="mc-table"><thead><tr>
          <th>ID</th><th>标题</th><th>类型</th><th>抵扣</th><th>门槛</th>
          <th>库存</th><th>有效天数</th><th>状态</th><th>操作</th>
        </tr></thead><tbody>`;
        list.forEach(v => {
            const statusText = v.status === 1 ? '上架中' : (v.status === 2 ? '已下架' : '已过期');
            const next = v.status === 1 ? 2 : 1;
            const btnText = v.status === 1 ? '下架' : '上架';
            html += `<tr>
              <td>${v.id}</td>
              <td>${escapeHtml(v.title || '')}</td>
              <td>${v.type === 1 ? '秒杀券' : '普通券'}</td>
              <td>¥${Number(v.discount || 0).toFixed(2)}</td>
              <td>¥${Number(v.threshold || 0).toFixed(2)}</td>
              <td>${v.type === 1 ? (v.stock == null ? 0 : v.stock) : '-'}</td>
              <td>${v.validDays > 0 ? v.validDays + ' 天' : '不限'}</td>
              <td>${statusText}</td>
              <td><button class="vc-toggle" data-id="${v.id}" data-status="${next}">${btnText}</button></td>
            </tr>`;
        });
        html += '</tbody></table>';
        wrap.innerHTML = html;

        wrap.querySelectorAll('.vc-toggle').forEach(btn => {
            btn.onclick = async () => {
                try {
                    await adminPostForm('/api/voucher/status', {
                        id: btn.dataset.id,
                        status: btn.dataset.status
                    });
                    await loadVoucherTable();
                } catch (e) { adminError(e); }
            };
        });
    } catch (e) {
        console.error(e);
        wrap.innerHTML = '<div class="empty-tip">加载失败</div>';
    }
}

function openVoucherForm() {
    adminOpenModal('新建优惠券', `
      <form id="vc-form" class="mc-form">
        <label>券标题<input name="title" required placeholder="例如：满100减10"></label>
        <label>副标题<input name="subTitle" placeholder="例如：全场通用"></label>
        <label>使用规则<input name="rules" placeholder="例如：订单满 100 元可用"></label>
        <label>券类型
          <select name="type">
            <option value="0">普通券（活动中心直接领取）</option>
            <option value="1">秒杀券（限时限量抢购）</option>
          </select>
        </label>
        <label>抵扣金额（元）<input name="discount" type="number" min="0.01" step="0.01" required placeholder="10"></label>
        <label>使用门槛（元，0 为无门槛）<input name="threshold" type="number" min="0" step="0.01" value="0"></label>
        <label>领取后有效天数（0 为不限）<input name="validDays" type="number" min="0" value="30"></label>
        <div class="vc-seckill-fields" style="display:none;">
          <label>秒杀库存<input name="stock" type="number" min="1" value="20"></label>
          <label>开始时间<input name="beginTime" type="datetime-local"></label>
          <label>结束时间<input name="endTime" type="datetime-local"></label>
        </div>
      </form>`,
        `<button type="button" id="vc-save" class="mc-primary">保存</button>`);

    const form = document.getElementById('vc-form');
    const typeSelect = form.querySelector('[name=type]');
    const seckillFields = form.querySelector('.vc-seckill-fields');
    typeSelect.onchange = () => {
        seckillFields.style.display = typeSelect.value === '1' ? 'block' : 'none';
    };

    document.getElementById('vc-save').onclick = async () => {
        if (!form.reportValidity()) return;
        const fd = new FormData(form);
        const type = Number(fd.get('type'));
        const body = {
            title: fd.get('title'),
            subTitle: fd.get('subTitle') || null,
            rules: fd.get('rules') || null,
            type: type,
            actualValue: Math.round(Number(fd.get('discount') || 0) * 100),
            threshold: Math.round(Number(fd.get('threshold') || 0) * 100),
            validDays: Number(fd.get('validDays') || 0),
            payValue: 0,
            status: 1
        };
        if (type === 1) {
            body.stock = Number(fd.get('stock') || 0);
            body.beginTime = fd.get('beginTime');
            body.endTime = fd.get('endTime');
            if (!body.beginTime || !body.endTime) {
                alert('请填写秒杀开始与结束时间');
                return;
            }
        }
        try {
            await adminPost('/api/voucher/seckill', body);
            adminCloseModal();
            await loadVoucherTable();
        } catch (e) { adminError(e); }
    };
}
