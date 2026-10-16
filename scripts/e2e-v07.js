// v0.7 企业基础层 API 验收：注册登录、401、工作区隔离、会话隔离、成员停用
// 用法：node scripts/e2e-v07.js（需 Java 主服务已启动）
const BASE = process.env.E2E_API_URL || 'http://localhost:8080';

async function request(method, path, { token, body } = {}) {
  const response = await fetch(BASE + path, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  return { status: response.status, json };
}

async function register(prefix) {
  const username = `${prefix}_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
  const result = await request('POST', '/api/v1/auth/register', {
    body: { username, password: 'strong-password', displayName: prefix },
  });
  if (result.status !== 200 || result.json?.code !== 0) {
    throw new Error('register failed: ' + JSON.stringify(result));
  }
  return { username, ...result.json.data };
}

function fail(message) {
  console.error('VERIFY FAILED:', message);
  process.exit(1);
}

(async () => {
  const report = {};

  const unauth = await request('GET', '/api/v1/workspaces');
  if (unauth.status !== 401) {
    fail('未登录访问应返回 401，实际 ' + unauth.status);
  }
  report.s7_unauth = 401;

  const alice = await register('alice');
  const login = await request('POST', '/api/v1/auth/login', {
    body: { username: alice.username, password: 'strong-password' },
  });
  if (login.json?.code !== 0 || !login.json.data.accessToken) {
    fail('登录失败');
  }
  await request('POST', '/api/v1/auth/logout', { body: { refreshToken: login.json.data.refreshToken } });
  report.s6_auth = 'register/login/logout ok';

  const created = await request('POST', '/api/v1/workspaces', {
    token: alice.accessToken,
    body: { name: 'Team A', code: 'team-a-' + Date.now() },
  });
  const workspaceA = created.json?.data?.id;
  if (!workspaceA) {
    fail('创建工作区失败: ' + JSON.stringify(created));
  }

  const session = await request('POST', `/api/v1/workspaces/${workspaceA}/chat/sessions`, {
    token: alice.accessToken,
  });
  const sessionId = session.json?.data?.sessionId;
  if (!sessionId) {
    fail('创建会话失败');
  }

  const bob = await register('bob');
  const bobSeesAlice = await request('GET', `/api/v1/workspaces/${workspaceA}/knowledge/docs`, {
    token: bob.accessToken,
  });
  if (bobSeesAlice.status !== 200 || bobSeesAlice.json?.code !== 403) {
    fail('Workspace B 应无法访问 Workspace A，实际 ' + JSON.stringify(bobSeesAlice));
  }
  report.s8_workspace = 'isolated';

  const bobSeesSession = await request('GET', `/api/v1/workspaces/${workspaceA}/chat/sessions/${sessionId}`, {
    token: bob.accessToken,
  });
  if (bobSeesSession.json?.code !== 403) {
    fail('用户 B 不应看到用户 A 的会话: ' + JSON.stringify(bobSeesSession));
  }
  report.s9_session = 'isolated';

  const addBob = await request('POST', `/api/v1/workspaces/${workspaceA}/members`, {
    token: alice.accessToken,
    body: { username: bob.username, role: 'EDITOR' },
  });
  if (addBob.json?.code !== 0) {
    fail('添加成员失败: ' + JSON.stringify(addBob));
  }
  const members = await request('GET', `/api/v1/workspaces/${workspaceA}/members?page=1&pageSize=20`, {
    token: alice.accessToken,
  });
  const bobMember = (members.json?.data?.items || []).find((item) => item.username === bob.username);
  if (!bobMember) {
    fail('成员列表未找到 bob');
  }
  const suspend = await request('PATCH', `/api/v1/workspaces/${workspaceA}/members/${bobMember.memberId}/status`, {
    token: alice.accessToken,
    body: { status: 'SUSPENDED' },
  });
  if (suspend.json?.code !== 0) {
    fail('停用成员失败: ' + JSON.stringify(suspend));
  }
  const afterSuspend = await request('GET', `/api/v1/workspaces/${workspaceA}/knowledge/docs`, {
    token: bob.accessToken,
  });
  if (afterSuspend.json?.code !== 403) {
    fail('停用后仍可访问: ' + JSON.stringify(afterSuspend));
  }
  report.s10_suspend = 'denied';

  console.log(JSON.stringify(report, null, 2));
})().catch((e) => fail(e.message));
