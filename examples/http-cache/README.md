# HTTP cache demo

Пример к теме Backend › Caching › HTTP Cache & CDN: nginx с proxy_cache в роли CDN перед origin на JDK. Нужны JDK 21 и Docker.

```bash
cd examples/http-cache
docker run -d --name kb-nginx -p 8080:8080 --add-host host.docker.internal:host-gateway \
  -v "$PWD/nginx.conf":/etc/nginx/nginx.conf:ro nginx:alpine
java HttpCacheDemo.java
```

Перед повторным запуском очистите кеш: `docker exec kb-nginx sh -c 'rm -rf /tmp/cache/*'; docker restart kb-nginx`.
nginx считает срок свежести в целых секундах, поэтому демо ждёт с запасом.
