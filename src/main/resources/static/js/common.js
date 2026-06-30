/**
 * 票务系统 - 前端公共 JS
 */

// ==================== API 基础配置 ====================
const API_BASE = '';  // 同源，无需前缀

// ==================== Token 管理 ====================
const TokenManager = {
  getToken() {
    return localStorage.getItem('token');
  },
  setToken(token) {
    localStorage.setItem('token', token);
  },
  removeToken() {
    localStorage.removeItem('token');
  },
  getUser() {
    const user = localStorage.getItem('userInfo');
    return user ? JSON.parse(user) : null;
  },
  setUser(user) {
    localStorage.setItem('userInfo', JSON.stringify(user));
  },
  clear() {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
  },
  isLoggedIn() {
    return !!this.getToken();
  },
  getRole() {
    const user = this.getUser();
    return user ? user.role : null;
  }
};

// ==================== HTTP 请求封装 ====================
const http = {
  async request(method, url, data, options = {}) {
    const headers = { 'Content-Type': 'application/json' };
    const token = TokenManager.getToken();
    if (token) {
      headers['Authorization'] = 'Bearer ' + token;
    }
    // 幂等键（写操作自动添加）
    if (['POST', 'PUT', 'DELETE'].includes(method) && !options.skipIdempotency) {
      const cleanUrl = url.split('?')[0];
      headers['X-Idempotency-Key'] = `${TokenManager.getUser()?.userId || 'anon'}:${cleanUrl}:${Date.now()}`;
    }

    const config = {
      method,
      headers,
    };
    if (data && method !== 'GET') {
      config.body = JSON.stringify(data);
    }

    try {
      const response = await fetch(API_BASE + url, config);
      const result = await response.json();

      // 401 未登录
      if (response.status === 401) {
        TokenManager.clear();
        showToast('登录已过期，请重新登录', 'error');
        setTimeout(() => { window.location.href = '/login.html'; }, 1500);
        return null;
      }

      // 业务错误
      if (result.code !== 200 && result.code !== 0) {
        if (options.silent !== true) {
          showToast(result.message || '请求失败', 'error');
        }
        return null;
      }

      return result;
    } catch (e) {
      console.error('请求失败:', e);
      if (options.silent !== true) {
        showToast('网络错误，请稍后重试', 'error');
      }
      return null;
    }
  },

  get(url, options) { return this.request('GET', url, null, options); },
  post(url, data, options) { return this.request('POST', url, data, options); },
  put(url, data, options) { return this.request('PUT', url, data, options); },
  delete(url, options) { return this.request('DELETE', url, null, options); },

  // 文件上传
  async upload(url, formData) {
    const headers = {};
    const token = TokenManager.getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;

    try {
      const response = await fetch(API_BASE + url, {
        method: 'POST',
        headers,
        body: formData,
      });
      return await response.json();
    } catch (e) {
      showToast('上传失败', 'error');
      return null;
    }
  }
};

// ==================== 消息提示 ====================
function showToast(message, type = 'info') {
  let container = document.querySelector('.toast-container');
  if (!container) {
    container = document.createElement('div');
    container.className = 'toast-container';
    document.body.appendChild(container);
  }
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    toast.style.transition = 'all 0.3s';
    setTimeout(() => toast.remove(), 300);
  }, 3000);
}

// ==================== 导航栏渲染 ====================
function renderNavbar(activePage) {
  const user = TokenManager.getUser();
  const isLoggedIn = TokenManager.isLoggedIn();
  const role = TokenManager.getRole();

  let navLinks = '';
  if (isLoggedIn) {
    navLinks += `<a href="/index.html" class="${activePage === 'home' ? 'active' : ''}">首页</a>`;
    navLinks += `<a href="/orders.html" class="${activePage === 'orders' ? 'active' : ''}">我的订单</a>`;
    if (role === 'merchant' || role === 'admin') {
      navLinks += `<a href="/merchant.html" class="${activePage === 'merchant' ? 'active' : ''}">商家中心</a>`;
    }
    if (role === 'admin') {
      navLinks += `<a href="/admin.html" class="${activePage === 'admin' ? 'active' : ''}">管理后台</a>`;
    }
  }

  let userArea = '';
  if (isLoggedIn) {
    const roleLabel = { admin: '管理员', merchant: '商家', user: '用户' }[role] || '用户';
    userArea = `
      <div class="navbar-user">
        <span class="role-tag">${roleLabel}</span>
        <span style="font-size:0.9rem;color:var(--text-secondary)">${user.nickname || user.phone || user.email || '用户'}</span>
        <button onclick="logout()" class="btn btn-sm btn-outline">退出</button>
      </div>`;
  } else {
    userArea = `<a href="/login.html" class="btn btn-sm btn-primary">登录</a>`;
  }

  document.getElementById('navbar').innerHTML = `
    <div class="navbar-inner">
      <div class="navbar-brand" onclick="location.href='/index.html'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="2" y="4" width="20" height="16" rx="2"/>
          <path d="M2 8h20M8 4v4M16 4v4"/>
        </svg>
        票务系统
      </div>
      <nav class="navbar-nav">${navLinks}</nav>
      ${userArea}
    </div>`;
}

// ==================== 登出 ====================
async function logout() {
  await http.post('/auth/logout');
  TokenManager.clear();
  window.location.href = '/login.html';
}

// ==================== 全局状态检查 ====================
// 页面加载时检查用户状态（被封禁的用户立即踢出）
document.addEventListener('DOMContentLoaded', async () => {
  if (TokenManager.isLoggedIn()) {
    // 调用一个轻量级的鉴权接口验证用户状态
    const result = await http.get('/user/check', { silent: true });
    if (!result) {
      // 用户已被封禁或 token 失效
      TokenManager.clear();
      showToast('您的账号已被封禁或登录已过期', 'error');
      setTimeout(() => { window.location.href = '/login.html'; }, 1500);
    }
  }
});

// ==================== 工具函数 ====================
function formatTime(dt) {
  if (!dt) return '-';
  const d = new Array.isArray(dt) ? new Date(dt[0], dt[1]-1, dt[2], dt[3], dt[4]) : new Date(dt);
  if (isNaN(d.getTime())) return dt;
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function formatDate(dt) {
  if (!dt) return '-';
  if (Array.isArray(dt)) {
    return `${dt[0]}-${String(dt[1]).padStart(2,'0')}-${String(dt[2]).padStart(2,'0')}`;
  }
  const d = new Date(dt);
  return d.toLocaleDateString('zh-CN');
}

function formatPrice(price) {
  if (price === null || price === undefined) return '-';
  return '¥' + Number(price).toFixed(2);
}

function getOrderStatusText(status) {
  const map = { 0: '待支付', 1: '支付中', 2: '已支付', 3: '已取消', 4: '已退款' };
  return map[status] ?? '未知';
}

function getOrderStatusTag(status) {
  const map = {
    0: 'tag-warning', 1: 'tag-info', 2: 'tag-success', 3: 'tag-danger', 4: 'tag-danger'
  };
  return `<span class="tag ${map[status] || 'tag-info'}">${getOrderStatusText(status)}</span>`;
}

function getLifecycleText(status) {
  const map = { 0: '草稿', 1: '待上架', 2: '售票中', 3: '已结束', 4: '已下架' };
  return map[status] ?? '未知';
}

function getLifecycleTag(status) {
  const map = {
    0: 'tag-info', 1: 'tag-warning', 2: 'tag-success', 3: 'tag-info', 4: 'tag-danger'
  };
  return `<span class="tag ${map[status] || 'tag-info'}">${getLifecycleText(status)}</span>`;
}

// URL 参数
function getQueryParam(key) {
  const params = new URLSearchParams(window.location.search);
  return params.get(key);
}

// 确认对话框
function confirmAction(message) {
  return new Promise(resolve => {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.innerHTML = `
      <div class="modal">
        <div class="modal-header"><h3>确认操作</h3></div>
        <div class="modal-body"><p>${message}</p></div>
        <div class="modal-footer">
          <button class="btn btn-outline" id="confirmCancel">取消</button>
          <button class="btn btn-primary" id="confirmOk">确定</button>
        </div>
      </div>`;
    document.body.appendChild(overlay);
    overlay.querySelector('#confirmCancel').onclick = () => { overlay.remove(); resolve(false); };
    overlay.querySelector('#confirmOk').onclick = () => { overlay.remove(); resolve(true); };
  });
}
