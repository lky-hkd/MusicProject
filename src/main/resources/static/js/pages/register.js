// js/pages/register.js
// 注册页业务逻辑：手机号 + 密码

const REG_PHONE_REGEX = /^1[3-9]\d{9}$/;

let registerEventsBound = false;
function bindRegisterEvents() {
    if (registerEventsBound) return;
    registerEventsBound = true;

    const registerBtn = document.getElementById("registerSubmit");
    if (!registerBtn) return;

    registerBtn.onclick = async function () {
        const phoneEl = document.getElementById("reg-phone");
        const passwordEl = document.getElementById("reg-password");
        const confirmEl = document.getElementById("reg-confirmPwd");

        const phone = phoneEl.value.trim();
        const password = passwordEl.value.trim();
        const confirmPwd = confirmEl.value.trim();

        if (!phone || !password || !confirmPwd) {
            alert("所有输入框不能为空");
            return;
        }
        if (!REG_PHONE_REGEX.test(phone)) {
            alert("请输入正确的手机号");
            return;
        }
        if (password !== confirmPwd) {
            alert("两次输入密码不一致");
            return;
        }
        if (password.length < 6) {
            alert("密码长度至少6位");
            return;
        }

        try {
            const res = await fetch("/api/user/register", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ phone, password })
            });
            const json = await res.json();

            if (json.success) {
                alert(json.msg || "注册成功，请前往登录");
                // SPA 里改成切换 hash
                location.hash = '#/login';
            } else {
                alert(json.msg || "注册失败");
            }
        } catch (err) {
            console.error("[register] 注册失败：", err);
            alert("网络请求失败，请检查后端服务");
        }
    };

    // 回车提交
    const confirmEl = document.getElementById("reg-confirmPwd");
    if (confirmEl) {
        confirmEl.onkeydown = function (e) {
            if (e.key === "Enter") {
                const btn = document.getElementById("registerSubmit");
                if (btn) btn.click();
            }
        };
    }
}

// ========== 注册页面钩子 ==========
registerPageHook('register', async () => {
    // 清空输入框
    const phoneEl = document.getElementById("reg-phone");
    const passwordEl = document.getElementById("reg-password");
    const confirmEl = document.getElementById("reg-confirmPwd");
    if (phoneEl) phoneEl.value = "";
    if (passwordEl) passwordEl.value = "";
    if (confirmEl) confirmEl.value = "";

    bindRegisterEvents();
});
