import http from 'k6/http';
import { check, fail } from 'k6';
import { Rate } from 'k6/metrics';

const sseDataFrames = new Rate('sse_data_frames');

// Reads K6_BASE_URL, K6_USERNAME, K6_PASSWORD, K6_WORKSPACE_ID. No password is stored here.
// Ramp to 50 VUs in 30s, then hold 3 minutes, so the 10r/s auth/API limit is not hit at connect time.
// http_req_failed rate < 0.01. Only a 2xx body is sampled; it must contain data:.

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '3m', target: 50 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    sse_data_frames: ['rate==1'],
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
  const status2xx = res.status >= 200 && res.status < 300;
  const hasFrame = String(res.body).includes('data:');
  if (status2xx) {
    sseDataFrames.add(hasFrame);
  }
  check(res, {
    'status is 2xx': () => status2xx,
    'body contains data:': () => hasFrame,
  });
}
