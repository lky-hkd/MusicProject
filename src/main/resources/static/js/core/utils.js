/**
 * URL 参数获取
 *
 * 兼容两种 URL：
 *   1. 普通 URL：http://xxx.com/page?id=3     → 从 location.search 取
 *   2. Hash 路由：http://xxx.com/#/product?id=3 → 从 location.hash 里取
 *
 * @param {string} name 参数名
 * @returns {string|null}
 */
function getUrlParam(name) {
    // 1. 先从 location.search 里找（普通 URL 参数）
    const searchParams = new URLSearchParams(location.search);
    if (searchParams.has(name)) {
        return searchParams.get(name);
    }

    // 2. 再从 hash 里的 ? 后面找（SPA 路由参数）
    const hash = location.hash || '';
    const questionIdx = hash.indexOf('?');
    if (questionIdx === -1) {
        return null;
    }
    const hashQuery = hash.substring(questionIdx + 1);   // "id=3&xxx=yyy"
    const hashParams = new URLSearchParams(hashQuery);
    return hashParams.get(name);
}

/**
 * 时间格式化：秒 => 00:00
 * @param {number} s
 * @returns {string}
 */
function formatTime(s) {
    if (isNaN(s)) return "00:00";
    const m = Math.floor(s / 60).toString().padStart(2, "0");
    const sec = Math.floor(s % 60).toString().padStart(2, "0");
    return `${m}:${sec}`;
}

/**
 * LRC歌词解析
 * @param {string} lrcStr
 * @returns Array<{time:number, text:string}>
 */
function parseLrc(lrcStr) {
    if (!lrcStr) return [];
    const lines = lrcStr.split(/\n/);
    const result = [];
    const reg = /\[(\d{2}):(\d{2})\.(\d{2})\](.*)/;
    lines.forEach(line => {
        const match = line.match(reg);
        if (match) {
            const m = parseInt(match[1]);
            const s = parseInt(match[2]);
            const ms = parseInt(match[3]);
            const time = m * 60 + s + ms / 100;
            const text = match[4].trim();
            if (text) {
                result.push({ time, text });
            }
        }
    });
    return result.sort((a, b) => a.time - b.time);
}

/**
 * HTML 转义（防 XSS）
 * @param {string} str
 * @returns {string}
 */
function escapeHtml(str) {
    if (!str) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

/**
 * 删除按钮内的垃圾桶图标（配合 .del-btn 样式使用）
 */
const DEL_ICON = `<svg class="svgIcon" viewBox="0 0 448 512" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
<path d="M135.2 17.7L128 32H32C14.3 32 0 46.3 0 64S14.3 96 32 96H416c17.7 0 32-14.3 32-32s-14.3-32-32-32H320l-7.2-14.3C307.4 6.8 296.3 0 284.2 0H163.8c-12.1 0-23.2 6.8-28.6 17.7zM416 128H32L53.2 467c1.6 25.3 22.6 45 47.9 45H346.9c25.3 0 46.3-19.7 47.9-45L416 128z"/>
</svg>`;