FROM confluentinc/cp-kafka:latest
COPY --chmod=0755 create-topics.sh /usr/local/bin/create-topics.sh
ENTRYPOINT ["/usr/local/bin/create-topics.sh"]
