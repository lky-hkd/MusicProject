/**
 * 通用fetch封装，自动携带cookie（用于登录态token）
 * @param {string} url
 * @param {RequestInit} options
 * @returns Promise<any>
 */
async function request(url, options = {}) {
    const baseOpt = {
        credentials: "include",
        ...options
    };
    try {
        const res = await fetch(url, baseOpt);
        // 处理http错误状态码
        if (!res.ok) {
            throw new Error(`请求错误：${res.status}`);
        }
        const json = await res.json();
        if (json && json.success === false) {
            // 统一抛出，由调用方提示；也可以在这里弹 toast
            throw new Error(json.msg || "操作失败");
        }
        return json;
    } catch (err) {
        console.error("request请求异常：", err);
        throw err; // 抛出，让调用方捕获处理提示
    }
}