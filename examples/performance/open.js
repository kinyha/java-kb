// Открытая модель: запросы приходят с заданной частотой независимо от ответов.
import http from 'k6/http';
export const options = {
  scenarios: { open: { executor: 'constant-arrival-rate', rate: __ENV.RATE, timeUnit: '1s',
    duration: __ENV.DUR || '20s', preAllocatedVUs: 200, maxVUs: 2000 } },
};
export default function () { http.get('http://target:8601/pay'); }
