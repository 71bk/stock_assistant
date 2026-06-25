# Postgres 16 client (pg_dump/pg_restore matching the pgvector:pg16 server) + rclone for R2.
FROM postgres:16-alpine

RUN apk add --no-cache rclone tzdata

COPY backup.sh restore.sh entrypoint.sh /usr/local/bin/
# Strip any CR added by editing on Windows, then make executable.
RUN sed -i 's/\r$//' /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/entrypoint.sh \
    && chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
