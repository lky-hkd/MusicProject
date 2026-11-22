// js/pages/order-confirm.js
// 订单确认页

const orderConfirmState ={
    items:[],
    totalAmount:0,
    needAddress: false,
    addresses: [],
    selectedAddressId: null,
    source: 'direct',
    coupons: [],
    selectedCouponId: null,
    discount: 0
};

//加载订单确认数据
async function  loadOrderConfirm(){
    const goodsList = document.getElementById('orderGoodsList');
    const addressSection = document.getElementById('orderAddressSection');
    if(!goodsList) return;

    const raw = localStorage.getItem('checkoutData');
    if(!raw){
        goodsList.innerHTML='<div class="empty-tip">没有待结算商品</div>';
        return;
    }

    const checkoutData = JSON.parse(raw);
    orderConfirmState.items = checkoutData.items ||[];
    orderConfirmState.source = checkoutData.source ||"direct";

    if(orderConfirmState.items.length===0){
        goodsList.innerHTML = '<div class="empty-tip">没有待结算商品</div>';
        return;
    }

    try{
        const json= await request('/api/order/confirm', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ items: orderConfirmState.items })
        });
        if(!json.success){
            goodsList.innerHTML = `<div class="empty-tip">${json.msg || '加载失败'}</div>`;
            return;
        }

        const data = json.data;
        orderConfirmState.needAddress = data.needAddress;
        orderConfirmState.addresses = data.addresses || [];
        orderConfirmState.totalAmount = Number(data.totalAmount || 0);
        renderOrderGoods(data.items || []);
    //     渲染
        if(data.needAddress){
            addressSection.style.display='block';
            renderAddressList(orderConfirmState.addresses);
        }else{
            addressSection.style.display='none';
        }

    //     总价 + 优惠券
        orderConfirmState.discount = 0;
        orderConfirmState.selectedCouponId = null;
        updateOrderAmount();
        await loadOrderCoupons(orderConfirmState.totalAmount);

    }catch (e){
        console.error('[order-confirm] 加载失败：', e);
        goodsList.innerHTML = '<div class="empty-tip">网络异常</div>';
    }
}

//渲染商品清单
function renderOrderGoods(items){
    const wrap = document.getElementById('orderGoodsList');
    wrap.innerHTML = '';
    items.forEach(item=>{
            const div = document.createElement('div');
            div.className = 'order-goods-item';
            div.innerHTML = `
            <img class="order-goods-cover" src="${item.coverUrl || ''}" alt="${item.name}">
            <div class="order-goods-info">
                <div class="order-goods-name">${escapeHtml(item.name)}</div>
                <div class="order-goods-meta">
                    <span class="order-goods-price">¥${item.price}</span>
                    <span class="order-goods-qty">×${item.quantity}</span>
                </div>
            </div>
            <div class="order-goods-subtotal">¥${Number(item.subtotal).toFixed(2)}</div>
        `;
            wrap.appendChild(div);
    });
}

//地址选择
function renderAddressList(address){
    const wrap = document.getElementById('orderAddressList');
    wrap.innerHTML='';
    if(address.length===0){
        wrap.innerHTML = `
            <div class="empty-tip">
                还没有收货地址，
                <a href="javascript:void(0)" onclick="location.hash='#/address'" style="color:#60a5fa;">去添加</a>
            </div>
        `;
        return;
    }

    // 默认选中第一个（或之前选过的）
    if(!orderConfirmState.selectedAddressId){
        const defaultAddr = address.find(a=>a.isDefault ===1)||address[0];
        orderConfirmState.selectedAddressId = defaultAddr.id;
    }

    address.forEach(addr=>{
        const div = document.createElement('div');
        div.className='order-address-item';
        if(addr.id===orderConfirmState.selectedAddressId){
            div.classList.add('active');
        }
        div.innerHTML=`
            <div class="order-address-main">
                <div class="order-address-header">
                    <span class="order-address-receiver">${escapeHtml(addr.receiver)}</span>
                    <span class="order-address-phone">${escapeHtml(addr.phone)}</span>
                    ${addr.isDefault === 1 ? '<span class="order-address-default">默认</span>' : ''}
                </div>
                <div class="order-address-detail">
                    ${escapeHtml(addr.province || '')} ${escapeHtml(addr.city || '')} ${escapeHtml(addr.district || '')} ${escapeHtml(addr.detail)}
                </div>
            </div>
        `;
        div.onclick = () => {
            document.querySelectorAll('.order-address-item').forEach(i => i.classList.remove('active'));
            div.classList.add('active');
            orderConfirmState.selectedAddressId = addr.id;
        };
        wrap.appendChild(div);
    });
}
// ======================= 优惠券选择 =======================
async function loadOrderCoupons(totalAmount) {
    const wrap = document.getElementById('orderCouponList');
    if (!wrap) return;
    try {
        const json = await request(`/api/coupon/available?amount=${encodeURIComponent(totalAmount)}`);
        orderConfirmState.coupons = json.data || [];
        renderOrderCoupons();
    } catch (e) {
        console.error('[order-confirm] 加载优惠券失败：', e);
        wrap.innerHTML = '<div class="empty-tip">优惠券加载失败</div>';
    }
}

function renderOrderCoupons() {
    const wrap = document.getElementById('orderCouponList');
    if (!wrap) return;

    if (orderConfirmState.coupons.length === 0) {
        wrap.innerHTML = '<div class="empty-tip">暂无可用优惠券</div>';
        return;
    }

    let html = `<div class="oc-item ${orderConfirmState.selectedCouponId ? '' : 'active'}" data-id="">不使用优惠券</div>`;
    orderConfirmState.coupons.forEach(c => {
        const active = orderConfirmState.selectedCouponId === c.id ? 'active' : '';
        const thresholdText = Number(c.threshold) > 0
            ? '满 ¥' + Number(c.threshold).toFixed(2) + ' 可用'
            : '无门槛';
        html += `
          <div class="oc-item ${active}" data-id="${c.id}">
            <span class="oc-amount">¥${Number(c.discount).toFixed(2)}</span>
            <span class="oc-title">${escapeHtml(c.title || '优惠券')}</span>
            <span class="oc-threshold">${thresholdText}</span>
          </div>`;
    });
    wrap.innerHTML = html;
    wrap.querySelectorAll('.oc-item').forEach(item => {
        item.onclick = () => selectOrderCoupon(item.dataset.id);
    });
}

function selectOrderCoupon(id) {
    orderConfirmState.selectedCouponId = id ? Number(id) : null;
    const coupon = orderConfirmState.coupons.find(c => c.id === orderConfirmState.selectedCouponId);
    orderConfirmState.discount = coupon
        ? Math.min(Number(coupon.discount || 0), orderConfirmState.totalAmount)
        : 0;
    renderOrderCoupons();
    updateOrderAmount();
}

function updateOrderAmount() {
    const pay = Math.max(0, orderConfirmState.totalAmount - orderConfirmState.discount);
    const totalEl = document.getElementById('orderTotalAmount');
    if (totalEl) totalEl.innerText = '¥' + pay.toFixed(2);
    const line = document.getElementById('orderDiscountLine');
    if (line) line.style.display = orderConfirmState.discount > 0 ? 'inline-block' : 'none';
    const discountEl = document.getElementById('orderDiscountAmount');
    if (discountEl) discountEl.innerText = orderConfirmState.discount.toFixed(2);
}

//提交提单
async function submitOrder(){
    if(orderConfirmState.needAddress && !orderConfirmState.selectedAddressId){
        alert('请选择收货地址');
        return;
    }

    const btn = document.getElementById('orderSubmitBtn');

    // 防止多次点击
    if (btn.disabled) return;
    btn.disabled = true;
    btn.innerText='提交中...';

    // 2. 构造请求参数
    const reqData = {
        items: orderConfirmState.items,
        addressId: orderConfirmState.needAddress ? orderConfirmState.selectedAddressId : null,
        source: orderConfirmState.source,
        userCouponId: orderConfirmState.selectedCouponId
    };
    console.log('准备提交给后端的参数:', reqData);
    try{
        const json = await request('/api/order/create',{
            method: 'POST',
            headers:{ 'Content-Type': 'application/json'},
            body:JSON.stringify(reqData)
        });
        if(!json.success){
            alert(json.msg || '下单失败');
            return;
        }
        // 下单成功 → 清掉 localStorage，跳支付页
        localStorage.removeItem('checkoutData');
        const data = json.data || {}
        console.log('下单返回的数据:', data);
        localStorage.setItem('payNo', data.payNo || '');
        localStorage.setItem('payOrderNo', data.orderNos?.[0] || data.orderNo || ''); // 兼容单订单场景
        localStorage.setItem('payAmount', data.payAmount ?? '');
        // 热点商品走异步落库，需等订单真正入库后再进支付页；同步下单会立即返回 SUCCESS
        await waitOrderCreated(data.payNo);
        location.hash='#/order-pay';
    }catch(e){
        console.error('[order-confirm] 提交失败：', e);
        alert(e.message || '网络异常或服务器开小差了！');
    }finally {
        btn.disabled=false;
        btn.innerText= '提交订单';
    }
}

// 提交订单成功后，result.data = { payNo, orderNos, totalAmount }
async function waitOrderCreated(payNo,timeoutMs = 10000){
    const start = Date.now();
    while(Date.now()-start<timeoutMs){
        const res = await request(`/api/order/status/${payNo}`);
        // res.data: { status: 'CREATING'|'SUCCESS'|'FAILED', orderNos: [] }
        if(res.data.status === 'SUCCESS'){
            return res.data;   // 落库完成，带着真实 orderNos 去支付页
        }
        if(res.data.status === 'FAILED'){
            throw new Error('下单失败，请稍后重试');
        }
        await new Promise(r => setTimeout(r, 500));   // 每 500ms 轮询
    }
    throw new Error('下单处理超时，请到「我的订单」查看');
}

//绑定事件
let orderConfirmBound = false;
function bindOrderConfirmEvent(){
    if(orderConfirmBound) return;
    orderConfirmBound = true;

    const submitBtn = document.getElementById('orderSubmitBtn');
    if(submitBtn) submitBtn.onclick=submitOrder;
}
//注册
registerPageHook('order-confirm',async ()=>{
    bindOrderConfirmEvent();
    await loadOrderConfirm();
})