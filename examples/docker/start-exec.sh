#!/bin/sh
# exec заменяет процесс sh на java: сигналы получает java.
echo "starting"
exec java App
