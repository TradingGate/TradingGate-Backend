import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const SYMBOL = __ENV.SYMBOL || 'BTCUSDT';
const PRICE = __ENV.PRICE || '50000';
const QUANTITY = __ENV.QUANTITY || '1';

export const options = {
  vus: 5,
  duration: '1m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

function buildPayload(iteration) {
  const side = iteration % 2 === 0 ? 'BUY' : 'SELL';
  return JSON.stringify({
    clientOrderId: `smoke-${side.toLowerCase()}-${__VU}-${iteration}-${Date.now()}`,
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
    'body has Success marker': (r) => r.body && r.body.includes('"Success"'),
  });

  sleep(0.2);
}
