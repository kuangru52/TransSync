// ==UserScript==
// @name         TransSync一键下载工具
// @namespace    http://tampermonkey.net/
// @version      4.7
// @description  kuangru52@163.com   https://github.com/kuangru52/TransSync
// @author       You
// @match        https://*/detail/*
// @match        https://*/details.*
// @match        https://*/t/*
// @match        https://h5.m-team.cc/*
// @match        http://h5.m-team.cc/*
// @match        https://sunnypt.top/*
// @match        http://sunnypt.top/*
// @match        https://*/torrent/*
// @match        http://*/torrent/*
// @grant        none
 // @license      MIT
// ==/UserScript==

(function() {
    'use strict';

    // 基础配置参数
    const APP_PACKAGE_NAME = 'com.kuangru52.TransSync';
    const APP_SCHEME = 'transsync';
    const FALLBACK_URL = 'https://github.com/kuangru52/TransSync/releases/latest';

    // 全局缓存 M-Team dlv2 签名直链与锚点元素
    window.__mteam_cached_dlv2_url = '';
    window.__transsync_cached_anchor = null;

    // 检测当前设备是否为手机端 (屏幕宽度小于 768px 或移动端 UA)
    function isMobileDevice() {
        return window.innerWidth < 768 || /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
    }

    // 在 M-Team 页面自动拦截所有 fetch 请求，捕获后端返回的 dlv2 签名直链
    if (window.location.host.includes('m-team')) {
        const originalFetch = window.fetch;
        window.fetch = async function(...args) {
            const response = await originalFetch.apply(this, args);
            try {
                let url = args[0];
                if (typeof url === 'string' && url.includes('dlv2') && url.includes('sign=')) {
                    window.__mteam_cached_dlv2_url = url;
                }
                let clone = response.clone();
                let json = await clone.json().catch(() => null);
                if (json && json.data) {
                    let dataStr = JSON.stringify(json.data);
                    let match = dataStr.match(/(https?:\/\/[^\s"'<>]+dlv2[^\s"'<>]*)/i) || dataStr.match(/(https?:\/\/[^\s"'<>]+\bpasskey=[^\s"'<>]*)/i);
                    if (match && match[1]) {
                        window.__mteam_cached_dlv2_url = match[1];
                    }
                }
            } catch (_) {}
            return response;
        };
    }

    // 检查 URL 是否符合种子下载链接特征
    function isValidTorrentUrl(url) {
        if (!url || typeof url !== 'string' || url.startsWith('javascript:')) return false;
        let lower = url.toLowerCase();

        // 排除常见广告或第三方推广链接
        if (lower.includes('jd.com') || lower.includes('taobao.com') || lower.includes('tmall.com') ||
            lower.includes('union') || lower.includes('affiliate') || lower.includes('redirect') ||
            lower.includes('cps') || lower.includes('pinduoduo') || lower.includes('baidupcs')) {
            return false;
        }

        return (
            lower.includes('download.php') ||
            lower.includes('down.php') ||
            lower.includes('downhash=') ||
            lower.includes('passkey=') ||
            lower.includes('/api/torrent/download') ||
            lower.includes('/api/rss/dlv2') ||
            lower.endsWith('.torrent') ||
            lower.includes('/dl/') ||
            lower.startsWith('magnet:') ||
            lower.includes('download') ||
            lower.includes('torrent')
        );
    }

    // 获取 M-Team 真实的 dlv2 签名直链（优先使用网络拦截缓存，其次剪切板）
    async function getMTeamClipboardOrApiUrl() {
        if (window.__mteam_cached_dlv2_url && isValidTorrentUrl(window.__mteam_cached_dlv2_url)) {
            return window.__mteam_cached_dlv2_url;
        }

        try {
            if (navigator.clipboard && navigator.clipboard.readText) {
                let clipText = await navigator.clipboard.readText();
                if (clipText && isValidTorrentUrl(clipText)) {
                    return clipText.trim();
                }
            }
        } catch (e) {
            console.warn('剪切板读取受限:', e);
        }

        let match = window.location.pathname.match(/\/detail\/([a-zA-Z0-9]+)/) || window.location.href.match(/id=([a-zA-Z0-9]+)/) || window.location.hash.match(/\/torrent\/([a-zA-Z0-9]+)/);
        if (match && match[1]) {
            let torrentId = match[1];
            let apiDl = `${window.location.origin}/api/torrent/download?id=${torrentId}`;
            return apiDl;
        }

        let fallbackData = findTorrentAnchorAndUrl();
        return fallbackData ? fallbackData.url : '';
    }

    // 获取 SunnyPT 复制链接按钮触发的直链
    async function getSunnyPTUrl() {
        return new Promise((resolve) => {
            let capturedUrl = '';
            let isResolved = false;
            const originalWriteText = (navigator.clipboard && navigator.clipboard.writeText) ? navigator.clipboard.writeText : null;

            if (navigator.clipboard) {
                navigator.clipboard.writeText = async function(text) {
                    if (text && isValidTorrentUrl(text)) {
                        capturedUrl = text;
                        if (!isResolved) {
                            isResolved = true;
                            if (originalWriteText) {
                                try { await originalWriteText.call(navigator.clipboard, text); } catch (_) {}
                            }
                            resolve(text);
                            return;
                        }
                    }
                    if (originalWriteText) {
                        try { await originalWriteText.call(navigator.clipboard, text); } catch (_) {}
                    }
                };
            }

            let copyBtn = Array.from(document.querySelectorAll('button')).find(b => (b.textContent || '').includes('复制链接'));
            if (copyBtn) {
                try {
                    copyBtn.click();
                } catch (e) {
                    console.error('模拟点击 SunnyPT 复制按钮失败:', e);
                }
            }

            setTimeout(() => {
                if (isResolved) return;
                isResolved = true;

                if (originalWriteText && navigator.clipboard) {
                    navigator.clipboard.writeText = originalWriteText;
                }

                if (capturedUrl) {
                    resolve(capturedUrl);
                    return;
                }

                let dlA = document.querySelector('a[href*="download"]') || document.querySelector('a[href*="torrent"]');
                resolve(dlA ? dlA.href : window.location.href);
            }, 1200);
        });
    }

    // 从锚点元素解析或模拟点击获取下载链接
    async function getCopiedUrlFromAnchor(anchorElem) {
        return new Promise((resolve) => {
            if (!anchorElem) {
                resolve('');
                return;
            }

            let capturedUrl = '';
            let isResolved = false;
            const originalWriteText = (navigator.clipboard && navigator.clipboard.writeText) ? navigator.clipboard.writeText : null;

            if (navigator.clipboard) {
                navigator.clipboard.writeText = async function(text) {
                    if (text && isValidTorrentUrl(text)) {
                        capturedUrl = text;
                        if (!isResolved) {
                            isResolved = true;
                            if (originalWriteText) {
                                try { await originalWriteText.call(navigator.clipboard, text); } catch (_) {}
                            }
                            resolve(text);
                            return;
                        }
                    }
                    if (originalWriteText) {
                        try { await originalWriteText.call(navigator.clipboard, text); } catch (_) {}
                    }
                };
            }

            let href = anchorElem.href || anchorElem.value || anchorElem.getAttribute('value') || anchorElem.getAttribute('data-url') || anchorElem.getAttribute('data-link') || '';
            let onclick = anchorElem.getAttribute('onclick') || '';
            let match = onclick.match(/(?:copyToClip|doCopyLink|doCopy|copy|link|passkey)\(\s*['"]([^'"]+)['"]/i);
            let directUrl = (match && match[1]) ? match[1] : href;

            if (directUrl && isValidTorrentUrl(directUrl) && !directUrl.includes('javascript:')) {
                if (originalWriteText && navigator.clipboard) {
                    navigator.clipboard.writeText = originalWriteText;
                }
                resolve(new URL(directUrl, window.location.href).href);
                return;
            }

            try {
                anchorElem.click();
            } catch (e) {
                console.error('模拟点击锚点失败:', e);
            }

            setTimeout(() => {
                if (isResolved) return;
                isResolved = true;

                if (originalWriteText && navigator.clipboard) {
                    navigator.clipboard.writeText = originalWriteText;
                }

                if (capturedUrl) {
                    resolve(capturedUrl);
                    return;
                }

                resolve(directUrl && !directUrl.includes('javascript:') ? new URL(directUrl, window.location.href).href : '');
            }, 1200);
        });
    }

    // 查找种子详情页中的下载锚点及直链
    function findTorrentAnchorAndUrl() {
        let allAnchors = Array.from(document.querySelectorAll('a, button, input, [onclick], [data-url], [data-link]'));

        function extractUrlFromElement(el) {
            let href = el.href || el.value || el.getAttribute('value') || el.getAttribute('data-url') || el.getAttribute('data-link') || '';
            let onclick = el.getAttribute('onclick') || '';
            let match = onclick.match(/(?:copyToClip|doCopyLink|doCopy|copy|link)\(\s*['"]([^'"]+)['"]/i);
            let url = (match && match[1]) ? match[1] : href;
            if (isValidTorrentUrl(url) && !url.includes('javascript:')) {
                return new URL(url, window.location.href).href;
            }
            return null;
        }

        let passkeyElem = allAnchors.find(el => {
            let val = el.value || el.getAttribute('value') || el.href || el.getAttribute('data-url') || el.getAttribute('data-link') || '';
            let onclick = el.getAttribute('onclick') || '';
            let fullText = (val + onclick + (el.innerText || el.textContent || '')).toLowerCase();
            return fullText.includes('passkey');
        });
        if (passkeyElem) {
            let url = extractUrlFromElement(passkeyElem);
            if (url) {
                return { anchor: passkeyElem, url: url };
            }
        }

        let equalPriorityKeywords = [
            '点击复制到剪切板',
            '点击复制',
            '右键查看',
            '下载链接',
            '[下载地址]',
            '下载地址',
            '点击复制种子链接',
            '下载种子'
        ];

        for (let keyword of equalPriorityKeywords) {
            let cleanKeyword = keyword.replace(/[\[\]]/g, '');
            let found = allAnchors.find(el => {
                let txt = (el.innerText || el.textContent || '').trim();
                return txt.includes(cleanKeyword) || txt.includes(keyword);
            });

            if (found) {
                return { anchor: found, url: extractUrlFromElement(found) || '' };
            }
        }

        let fallbackElem = allAnchors.find(el => {
            let url = extractUrlFromElement(el);
            return url !== null;
        });

        if (fallbackElem) {
            let url = extractUrlFromElement(fallbackElem);
            return { anchor: fallbackElem, url: url || '' };
        }

        let match = window.location.pathname.match(/\/detail\/([a-zA-Z0-9]+)/) || window.location.href.match(/id=([a-zA-Z0-9]+)/) || window.location.hash.match(/\/torrent\/([a-zA-Z0-9]+)/);
        if (match && match[1]) {
            let torrentId = match[1];
            let mteamDlUrl = `${window.location.origin}/api/torrent/download?id=${torrentId}`;
            let dummyBtn = document.querySelector('button') || document.body;
            return { anchor: dummyBtn, url: mteamDlUrl };
        }

        return null;
    }

    // 唤起客户端下载协议
    async function triggerTransSync() {
        let targetUrl = '';
        let host = window.location.host;

        if (host.includes('m-team') || window.location.pathname.includes('/detail/') || window.location.hash.includes('/torrent/')) {
            targetUrl = await getMTeamClipboardOrApiUrl();
        } else if (host.includes('sunnypt')) {
            targetUrl = await getSunnyPTUrl();
        } else {
            let result = window.__transsync_cached_anchor ? { anchor: window.__transsync_cached_anchor } : findTorrentAnchorAndUrl();
            if (result && result.anchor) {
                targetUrl = await getCopiedUrlFromAnchor(result.anchor);
            } else {
                let data = getGeneralTorrentData();
                targetUrl = data ? data.url : '';
            }
        }

        if (!targetUrl) {
            alert('未能获取到当前种子的下载链接');
            return;
        }

        let encodedUrl = encodeURIComponent(targetUrl.trim());
        let encodedTitle = encodeURIComponent(document.title.trim());
        let schemeUrl = `${APP_SCHEME}://download?url=${encodedUrl}&title=${encodedTitle}`;

        try {
            window.location.href = schemeUrl;
        } catch (err) {
            console.error('唤起客户端失败:', err);
        }
    }

    // 为悬浮按钮添加平滑拖拽功能
    function makeDraggable(elm) {
        let startX = 0, startY = 0, initialX = 0, initialY = 0;
        let isDragging = false;
        let hasMoved = false;

        const dragStart = (e) => {
            isDragging = true;
            hasMoved = false;
            let clientX = e.touches ? e.touches[0].clientX : e.clientX;
            let clientY = e.touches ? e.touches[0].clientY : e.clientY;
            startX = clientX;
            startY = clientY;

            let rect = elm.getBoundingClientRect();
            initialX = rect.left;
            initialY = rect.top;

            elm.style.position = 'fixed';
            elm.style.right = 'auto';
            elm.style.bottom = 'auto';
            elm.style.left = initialX + 'px';
            elm.style.top = initialY + 'px';
        };

        const dragMove = (e) => {
            if (!isDragging) return;
            let clientX = e.touches ? e.touches[0].clientX : e.clientX;
            let clientY = e.touches ? e.touches[0].clientY : e.clientY;

            let dx = clientX - startX;
            let dy = clientY - startY;

            if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                hasMoved = true;
            }

            let newX = initialX + dx;
            let newY = initialY + dy;

            let maxX = window.innerWidth - elm.offsetWidth;
            let maxY = window.innerHeight - elm.offsetHeight;
            newX = Math.max(0, Math.min(newX, maxX));
            newY = Math.max(0, Math.min(newY, maxY));

            elm.style.left = newX + 'px';
            elm.style.top = newY + 'px';
        };

        const dragEnd = (e) => {
            if (!isDragging) return;
            isDragging = false;

            if (hasMoved) {
                e.preventDefault();
                e.stopPropagation();
            }
        };

        elm.addEventListener('mousedown', dragStart);
        window.addEventListener('mousemove', dragMove);
        window.addEventListener('mouseup', dragEnd);

        elm.addEventListener('touchstart', dragStart, { passive: true });
        window.addEventListener('touchmove', dragMove, { passive: true });
        window.addEventListener('touchend', dragEnd);
    }

    // 创建 Liquid Glass (Frosted Glass) 样式悬浮下载按钮 (FAB)
    function injectFloatingButton() {
        let existing = document.getElementById('transsync-floating-btn');
        if (existing) {
            if (existing.parentElement !== document.body) {
                document.body.appendChild(existing);
            }
            return true;
        }

        let btn = document.createElement('button');
        btn.type = 'button';
        btn.id = 'transsync-floating-btn';
        btn.innerHTML = `<span>⚡ TransSync</span>`;
        btn.style.cssText = `
            position: fixed !important;
            bottom: 24px !important;
            right: 20px !important;
            z-index: 2147483647 !important;
            padding: 12px 24px !important;
            background: rgba(0, 176, 255, 0.25) !important;
            backdrop-filter: blur(16px) saturate(180%) !important;
            -webkit-backdrop-filter: blur(16px) saturate(180%) !important;
            color: #ffffff !important;
            border-radius: 9999px !important;
            font-size: 15px !important;
            font-weight: bold !important;
            cursor: pointer !important;
            display: flex !important;
            align-items: center !important;
            justify-content: center !important;
            box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37), inset 0 0 0 1px rgba(255, 255, 255, 0.18) !important;
            border: 1px solid rgba(255, 255, 255, 0.3) !important;
            text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3) !important;
            touch-action: none !important;
            pointer-events: auto !important;
            width: auto !important;
            height: 44px !important;
            box-sizing: border-box !important;
            white-space: nowrap !important;
        `;

        let handled = false;
        const handleTap = (e) => {
            if (handled) return;
            handled = true;
            setTimeout(() => { handled = false; }, 600);

            triggerTransSync();
        };

        btn.addEventListener('click', handleTap);
        makeDraggable(btn);

        document.body.appendChild(btn);
        return true;
    }

    // 创建 Liquid Glass (Frosted Glass) 样式内联下载按钮
    function createTransSyncButton(idName) {
        let btn = document.createElement('button');
        btn.type = 'button';
        btn.id = idName;
        btn.innerHTML = `<span>⚡ TransSync</span>`;
        btn.style.cssText = `
            margin-left: 8px !important;
            padding: 8px 16px !important;
            background: rgba(0, 176, 255, 0.25) !important;
            backdrop-filter: blur(16px) saturate(180%) !important;
            -webkit-backdrop-filter: blur(16px) saturate(180%) !important;
            color: #ffffff !important;
            border-radius: 9999px !important;
            font-size: 13px !important;
            font-weight: bold !important;
            cursor: pointer !important;
            display: inline-flex !important;
            align-items: center !important;
            justify-content: center !important;
            vertical-align: middle !important;
            box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37), inset 0 0 0 1px rgba(255, 255, 255, 0.18) !important;
            border: 1px solid rgba(255, 255, 255, 0.3) !important;
            text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3) !important;
            touch-action: manipulation !important;
            height: 36px !important;
        `;

        let handled = false;
        const handleTap = (e) => {
            if (e) {
                e.preventDefault();
                e.stopPropagation();
            }
            if (handled) return;
            handled = true;
            setTimeout(() => { handled = false; }, 600);

            triggerTransSync();
        };

        btn.addEventListener('click', handleTap);
        btn.addEventListener('touchend', handleTap);

        return btn;
    }

    // 桌面版 M-Team 下载按钮注入
    function injectMTeamButton() {
        if (document.getElementById('transsync-mteam-item')) return true;

        let allBtns = Array.from(document.querySelectorAll('button'));
        let downloadBtn = allBtns.find(b => {
            let txt = (b.textContent || '').trim();
            let parent = b.closest('header, nav, .navbar');
            if (parent) return false;
            return txt.includes('下载') || txt.includes('下載');
        });

        if (!downloadBtn) {
            let copyBtn = allBtns.find(b => {
                let parent = b.closest('header, nav, .navbar');
                if (parent) return false;
                let html = b.innerHTML || '';
                let aria = b.getAttribute('aria-label') || '';
                return aria.includes('copy') || html.includes('anticon-copy') || html.includes('copy');
            });
            downloadBtn = copyBtn;
        }

        if (!downloadBtn) return false;

        let targetSpaceItem = downloadBtn.closest('.ant-space-item');
        let antSpace = targetSpaceItem ? targetSpaceItem.parentNode : document.querySelector('.ant-space');

        if (!antSpace) return false;

        let spaceItem = document.createElement('div');
        spaceItem.id = 'transsync-mteam-item';
        spaceItem.className = 'ant-space-item';

        let btn = createTransSyncButton('transsync-mteam-btn');
        spaceItem.appendChild(btn);

        targetSpaceItem.parentNode.insertBefore(spaceItem, targetSpaceItem.nextSibling);
        return true;
    }

    // 经典 PT 站点下载按钮注入
    function injectClassicButton() {
        if (document.getElementById('transsync-classic-btn')) return true;

        let result = findTorrentAnchorAndUrl();
        if (!result || !result.anchor) {
            return false;
        }

        window.__transsync_cached_anchor = result.anchor;

        let btn = createTransSyncButton('transsync-classic-btn');
        result.anchor.parentNode.insertBefore(btn, result.anchor.nextSibling);
        return true;
    }

    // 检查当前页面是否为种子详情页
    function isDetailPage() {
        let host = window.location.host;
        let pathname = window.location.pathname.toLowerCase();
        let search = window.location.search.toLowerCase();
        let hash = window.location.hash.toLowerCase();

        if (pathname.endsWith('torrents.php') || pathname.endsWith('browse.php') || pathname.endsWith('index.php') || pathname.endsWith('/torrents') || pathname.endsWith('/browse') || pathname === '/' || pathname === '') {
            if (!search.includes('id=') && !hash.includes('id=')) {
                return false;
            }
        }

        if (host.includes('sunnypt')) {
            return pathname.includes('/torrent/');
        }

        if (host.includes('h5.m-team') || hash.includes('/torrent/')) {
            return true;
        }

        if (host.includes('m-team') && pathname.includes('/detail/')) {
            return true;
        }

        if (pathname.includes('/detail/')) {
            return true;
        }

        if (pathname.includes('/t/') && /\/t\/\d+/.test(pathname)) {
            return true;
        }

        if (pathname.includes('details') || pathname.includes('plugin_details')) {
            return search.includes('id=') || pathname.includes('details.php');
        }

        if ((pathname.endsWith('torrent.php') || pathname.endsWith('torrents.php')) && (search.includes('id=') || search.includes('torrentid='))) {
            return true;
        }

        return false;
    }

    // 页面注入调度器 (M-Team 移动版与 SunnyPT 强制悬浮，其他站点根据设备尺寸：手机端内联，平板/电脑悬浮)
    function runInjection() {
        if (!isDetailPage()) {
            let existingBtn = document.getElementById('transsync-floating-btn');
            if (existingBtn) existingBtn.remove();
            return false;
        }

        let host = window.location.host;
        let hash = window.location.hash.toLowerCase();

        // 1. M-Team 移动版 H5 与 SunnyPT 强制悬浮显示
        if (host.includes('h5.m-team') || host.includes('sunnypt') || hash.includes('/torrent/')) {
            return injectFloatingButton();
        }

        // 2. 其他站点：手机端内联网页显示，平板/电脑等大尺寸设备悬浮显示
        if (isMobileDevice()) {
            let existingBtn = document.getElementById('transsync-floating-btn');
            if (existingBtn) existingBtn.remove();

            if (host.includes('m-team')) {
                return injectMTeamButton();
            } else {
                return injectClassicButton();
            }
        } else {
            return injectFloatingButton();
        }
    }

    // 监视 DOM 变动并执行注入
    const observer = new MutationObserver(() => {
        runInjection();
    });

    if (document.body || document.documentElement) {
        observer.observe(document.body || document.documentElement, {
            childList: true,
            subtree: true
        });
    }

    window.addEventListener('hashchange', () => {
        setTimeout(runInjection, 300);
    });

    window.addEventListener('popstate', () => {
        setTimeout(runInjection, 500);
    });

    setInterval(runInjection, 500);

})();
