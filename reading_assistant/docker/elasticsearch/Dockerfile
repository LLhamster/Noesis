ARG ELASTICSEARCH_VERSION=8.13.4
FROM docker.elastic.co/elasticsearch/elasticsearch:${ELASTICSEARCH_VERSION}

ARG ELASTICSEARCH_IK_VERSION=8.13.4
RUN bin/elasticsearch-plugin install --batch \
    "https://get.infini.cloud/elasticsearch/analysis-ik/${ELASTICSEARCH_IK_VERSION}"
