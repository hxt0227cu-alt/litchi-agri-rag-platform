import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

export const options = {
  scenarios: {
    agent: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 1),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: Number(__ENV.VUS || 5),
      maxVUs: Number(__ENV.MAX_VUS || 20),
    },
  },
  thresholds: {
    agent_duration: ['p(95)<12000'],
    agent_success: ['rate>0.995'],
  },
};

const duration = new Trend('agent_duration');
const success = new Rate('agent_success');

export default function () {
  const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080/api';
  const token = __ENV.TOKEN || '';
  const payload = JSON.stringify({
    goal: '连续降雨后荔枝叶片出现褐色病斑，请综合果园上下文、知识资料和图谱给出处理顺序。',
    sessionId: `load-${__VU}-${__ITER}`,
    maxSteps: 3,
  });
  const started = Date.now();
  const response = http.post(`${baseUrl}/v1/agent-runs`, payload, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
  });
  const accepted = check(response, { 'accepted': (r) => r.status === 202 });
  let completed = false;
  if (accepted) {
    const runId = response.json('runId');
    for (let attempt = 0; attempt < 24; attempt += 1) {
      sleep(0.5);
      const status = http.get(`${baseUrl}/v1/agent-runs/${runId}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const value = status.json('status');
      if (['completed', 'degraded', 'failed', 'canceled'].includes(value)) {
        completed = value === 'completed' || value === 'degraded';
        break;
      }
    }
  }
  duration.add(Date.now() - started);
  success.add(accepted && completed);
}
