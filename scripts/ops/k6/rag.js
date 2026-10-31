import http from 'k6/http';
import { check, fail } from 'k6';

// Reads K6_BASE_URL, K6_USERNAME, K6_PASSWORD, K6_WORKSPACE_ID. No password is stored here.
// Ramp to 20 VUs in 10s, then hold 5 minutes.
// First byte: http_req_waiting p95 < 3000. Total: http_req_duration p95 < 6000.

export const options = {
  stages: [
    { duration: '10s', target: 20 },
    { duration: '5m', target: 20 },
  ],
  thresholds: {
    http_req_waiting: ['p(95)<3000'],
    http_req_duration: ['p(95)<6000'],
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
  return { baseUrl: env.baseUrl, workspaceId: env.workspaceId, token };
}

export default function (data) {
  const res = http.post(
    `${data.baseUrl}/api/v1/workspaces/${data.workspaceId}/chat/stream`,
    JSON.stringify({ question: '什么是有效订单？' }),
    {
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: `Bearer ${data.token}`,
      },
      timeout: '300s',
    },
  );
  check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
  });
}
