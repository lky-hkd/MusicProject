// js/pages/my-orders.js
// 我的订单列表
const ORDER_STATUS_MAP = {
    0: { text: '待付款', color: '#fb923c' },
    1: { text: '已付款', color: '#22c55e' },
    2: { text: '已发货', color: '#3b82f6' },
    3: { text: '已完成', color: '#94a3b8' },
    4: { text: '已取消', color: '#64748b' },
    5: { text: '已退款', color: '#ef4444' }
};

//加载订单列表
async function loadMyOrders(){
    const wrap = document.getElementById('myOrderList');
    if(!wrap) return;
    wrap.innerHTML='<div class="empty-tip">加载中...</div>';
    try{
        const json = await request(`/api/order/list`);
        if(!json.success){
            wrap.innerHTML=`
                <div class="cart-empty">
                    ${json.msg || '加载失败'}
                    <a href="javascript:void(0)" onclick="location.hash='#/login'">去登录</a>
                </div>
            `;
            return;
        }
        const list = json.data || [];
        if(list.length===0){
            wrap.innerHTML = `
                <div class="cart-empty">
                    还没有任何订单，
                    <a href="javascript:void(0)" onclick="location.hash='#/shop'">去逛逛</a>
                </div>
            `;
            return;
        }
        renderMyOrders(list);
    }catch (e) {
        console.error('[my-orders] 加载失败：', e);
        wrap.innerHTML = '<div class="empty-tip">网络异常</div>';
    }
}

//渲染订单列表
function renderMyOrders(list){
    const wrap= document.getElementById('myOrderList');
    wrap.innerHTML='';
    list.forEach(order=>{
        const status = ORDER_STATUS_MAP[order.status] || { text: '未知', color: '#999' };
        const div = document.createElement('div');
        div.innerHTML=`
            <div class="my-order-header">
            <span class="my-order-no">订单号：${order.orderNo}</span>
            <span class="my-order-status" style="color:${status.color};">${status.text}</span>
        </div>
        <div class="my-order-body">
            <div class="my-order-time">下单时间：${formatDate(order.createTime)}</div>
            <div class="my-order-amount">
                实付：<span class="amount">¥${Number(order.payAmount || 0).toFixed(2)}</span>
            </div>
        </div>
        <div class="my-order-actions">
            <button class="my-order-btn detail-btn" data-order-no="${order.orderNo}">查看详情</button>
            ${order.status === 0 ? `
                    <button class="my-order-btn pay-btn" data-order-no="${order.orderNo}">去支付</button>
                ` : ''}
            ${order.canCancel ? `
                    <button class="my-order-btn cancel-btn" data-order-no="${order.orderNo}">取消订单</button>
                ` : ''}
        </div>
            `;

        wrap.appendChild(div);
    });

   // 绑定按钮
    wrap.querySelectorAll('.detail-btn').forEach(btn=>{
        btn.onclick=()=>{
            location.hash= '#/order-detail';
            localStorage.setItem('viewOrderNo',btn.dataset.orderNo);
        };
    });
    wrap.querySelectorAll('.pay-btn').forEach(btn=>{
        btn.onclick=()=>{
            localStorage.setItem('payOrderNo',btn.dataset.orderNo);
            location.hash = '#/order-pay';
        };
    });
    wrap.querySelectorAll('.cancel-btn').forEach(btn=>{
        btn.onclick= async ()=>{
            const orderNo = btn.dataset.orderNo;
            if(!confirm('确定取消该订单吗？')) return;
            btn.disabled = true;
            btn.innerText = '取消中...';
            try{
                const json = await request(`/api/order/cancel?orderNo=${orderNo}`, { method:'POST' })
                if(json.success){
                    alert('订单已取消');
                    await loadMyOrders();  // 刷新列表
                }else{
                    alert(json.msg || '取消失败');
                    btn.disabled = false;
                    btn.innerText = '取消订单';
                }
            }catch (e) {
                console.error('[my-orders] 取消失败：', e);
                alert('网络异常');
                btn.disabled = false;
                btn.innerText = '取消订单';
            }
        };
    });
}

// 列表页判断订单是否可取消
// 优先用后端 canCancel；没有则保守判断：待付款一定可取消；
// 已付款/已发货因列表无商品类型，不在此处显示取消（进详情页取消）
function canCelInList(order){
    if(!order) return false;
    if(typeof order.canCancel === 'boolean') return order.canCancel;
    return order.status === 0;
}

//时间格式化
function formatDate(dt){
    if(!dt) return '';
    // dt 可能是 "2026-10-17T18:24:36"
    return dt.replace('T', ' ').substring(0, 19);
}
//注册
registerPageHook('my-orders',async()=>{
    await loadMyOrders();
} )
