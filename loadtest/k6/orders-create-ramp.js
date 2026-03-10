import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const SYMBOL = __ENV.SYMBOL || 'BTCUSDT';
const PRICE = __ENV.PRICE || '50000';
const QUANTITY = __ENV.QUANTITY || '1';

export const options = {
  stages: [
    { duration: '1m', target: 10 },
    { duration: '2m', target: 30 },
    { duration: '2m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
};

function buildPayload(iteration) {
  const side = iteration % 2 === 0 ? 'BUY' : 'SELL';
  return JSON.stringify({
    clientOrderId: `ramp-${side.toLowerCase()}-${__VU}-${iteration}-${Date.now()}`,
    symbol: SYMBOL,
    orderSide: side,
    orderType: 'LIMIT',
    timeInForce: 'GTC',
    price: PRICE,
    quantity: QUANTITY,
  });
}

export default function () {
  const res = http.post(`${BASE_URL}/api/orders/create`, buildPayload(__ITER), {
    headers: { 'Content-Type': 'application/json' },
    timeout: '10s',
  });

  check(res, {
    'status is 202': (r) => r.status === 202,
  });

  sleep(0.1);
}
