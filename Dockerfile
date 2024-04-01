FROM openjdk:8

RUN apt-get install -y apt-transport-https ca-certificates curl gnupg
RUN curl -fsSL 'https://packages.clickhouse.com/rpm/lts/repodata/repomd.xml.key' | gpg --dearmor -o /usr/share/keyrings/clickhouse-keyring.gpg  \
    && echo "deb [signed-by=/usr/share/keyrings/clickhouse-keyring.gpg] https://packages.clickhouse.com/deb stable main" | tee \
    /etc/apt/sources.list.d/clickhouse.list
RUN apt-get update
RUN apt-get install -y clickhouse-client

WORKDIR /usr/local/app
COPY target/clickhouse-format-wrapper-1.0-SNAPSHOT.jar .
CMD ["java","-Xmx256m", "-jar", "clickhouse-format-wrapper-1.0-SNAPSHOT.jar"]