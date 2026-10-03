// Закрытая модель: N виртуальных пользователей, каждый шлёт следующий запрос после ответа.
import http from 'k6/http';
export const options = { scenarios: { closed: { executor: 'constant-vus', vus: __ENV.VUS, duration: __ENV.DUR || '20s' } } };
export default function () { http.get('http://target:8601/pay'); }
