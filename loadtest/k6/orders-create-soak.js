import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const SYMBOL = __ENV.SYMBOL || 'BTCUSDT';
const PRICE = __ENV.PRICE || '50000';
const QUANTITY = __ENV.QUANTITY || '1';
const DURATION = __ENV.DURATION || '15m';

export const options = {
  vus: 10,
  duration: DURATION,
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.05'],
  },
};

function buildPayload() {
  const side = (__ITER % 2 === 0) ? 'BUY' : 'SELL';
  return JSON.stringify({
    clientOrderId: `soak-${side.toLowerCase()}-${__VU}-${__ITER}-${Date.now()}`,
    symbol: SYMBOL,
    orderSide: side,
    orderType: 'LIMIT',
    timeInForce: 'GTC',
    price: PRICE,
    quantity: QUANTITY,
  });
}

export default function () {
  const res = http.post(
    `${BASE_URL}/api/orders/create`,
    buildPayload(),
    {
      headers: { 'Content-Type': 'application/json' },
      timeout: '10s',
    },
  );

  check(res, {
    'status is 202': (r) => r.status === 202,
    'body has Success marker': (r) => r.body && r.body.includes('"Success"'),
  });

  sleep(0.1);
}
