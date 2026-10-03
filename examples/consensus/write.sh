#!/bin/sh
# Хранилище за блокировкой. Принимает запись с токеном (ревизией блокировки etcd).
# naive.log — пишет всё; fenced.log — отклоняет токен меньше уже виденного.
token=$1; who=$2
echo "$who token=$token" >> /data/naive.log
last=$(cat /data/last 2>/dev/null || echo 0)
if [ "$token" -lt "$last" ]; then
  echo "$who token=$token REJECTED (seen $last)" >> /data/fenced.log
else
  echo "$token" > /data/last
  echo "$who token=$token accepted" >> /data/fenced.log
fi
