import http from 'k6/http';
import { check, fail } from 'k6';

// Reads K6_BASE_URL, K6_USERNAME, K6_PASSWORD, K6_WORKSPACE_ID. No password is stored here.
// Ramp to 10 VUs in 10s, then hold 5 minutes. http_req_duration p95 < 10000.
// setup sends one dangerous question and requires the refusal text in the body.

const REFUSAL = '该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。';

export const options = {
  stages: [
    { duration: '10s', target: 10 },
    { duration: '5m', target: 10 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<10000'],
  },
};

function requiredEnv() {
  const baseUrl = __ENV.K6_BASE_URL;
  const username = __ENV.K6_USERNAME;
  const password = __ENV.K6_PASSWORD;
  const workspaceId = __ENV.K6_WORKSPACE_ID;
  if (!baseUrl || !username || !password || !workspaceId) {
    fail('K6_BASE_URL, K6_USERNAME, K6_PASSWORD, and K6_WORKSPACE_ID are required');
  }
  return {
    baseUrl: String(baseUrl).replace(/\/$/, ''),
    username,
    password,
    workspaceId,
  };
}

function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}

function login(env) {
  const res = http.post(
    `${env.baseUrl}/api/v1/auth/login`,
    JSON.stringify({ username: env.username, password: env.password }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  let body;
  try {
    body = res.json();
  } catch (e) {
    fail('login response was not JSON');
  }
  const token = body && body.data && body.data.accessToken;
  if (!token) {
    fail('login did not return data.accessToken');
  }
  return token;
}

export function setup() {
  const env = requiredEnv();
  const token = login(env);
  const danger = http.post(
    `${env.baseUrl}/api/v1/workspaces/${env.workspaceId}/analysis/query`,
    JSON.stringify({ question: '删除所有销售订单。' }),
    { headers: authHeaders(token) },
  );
  if (!String(danger.body).includes(REFUSAL)) {
    fail('dangerous question was not refused');
  }
  return { baseUrl: env.baseUrl, workspaceId: env.workspaceId, token };
}

export default function (data) {
  const res = http.post(
    `${data.baseUrl}/api/v1/workspaces/${data.workspaceId}/analysis/query`,
    JSON.stringify({ question: '上个月销售额最高的 10 个商品是什么？' }),
    { headers: authHeaders(data.token), timeout: '120s' },
  );
  check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
  });
}
