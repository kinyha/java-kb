# Elasticsearch demo

Пример к теме Databases › NoSQL › Elasticsearch. Нужны Docker и python3.

```bash
docker run -d --name kb-es -p 9200:9200 -e discovery.type=single-node \
  -e xpack.security.enabled=false -e ES_JAVA_OPTS="-Xms1g -Xmx1g" elasticsearch:9.2.0
cd examples/elasticsearch
./demo.sh
```

Значения _score зависят от версии Lucene и состава индекса.
