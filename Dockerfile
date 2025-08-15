FROM songjinghe/tgraph-demo-test:4.4-latest
MAINTAINER Jinghe Song <songjh@buaa.edu.cn>

# ENV MAVEN_OPTS "-Xmx512m"

WORKDIR /db/bin/temporal-storage
RUN git pull --ff-only && mvn -B install -Dmaven.test.skip=true

WORKDIR /db/bin/temporal-neo4j
RUN git pull --ff-only && mvn -B install -Dmaven.test.skip=true -Dlicense.skip=true -Dlicensing.skip=true -Dcheckstyle.skip -Doverwrite -pl org.neo4j:neo4j-kernel -am

ADD . /db/bin/tgraphdb-http-server

WORKDIR /db/bin/tgraphdb-http-server

RUN wget -r https://demo.tgraphdb.cn && \
    mv demo.tgraphdb.cn/* /db/bin/tgraphdb-http-server/src/main/resources/static/ && \
    rm -rf demo.tgraphdb.cn

RUN mvn -B compile -Dmaven.test.skip=true

VOLUME /db/bin/tgraphdb-http-server/target
EXPOSE 7474

ENTRYPOINT mvn -B --offline compile exec:java -Dexec.mainClass=app.Application
