# CLAUDE.md

Vocab Trainer: a Spring Boot 3 web app (Java 17, Maven). See README.md for features, the project structure and local runs.

## Build and test

    mvn test
    mvn package          # -> target/VocabLearningApp-1.0-SNAPSHOT.jar (the Dockerfile copies this jar)

The Dockerfile does not build the jar itself, so always run `mvn package` before `docker build`.

## Deployment to the NAS

The app runs on the home QNAP NAS as a Docker Compose service. The NAS pulls the image from Docker Hub
and updates it every night. To deploy, push a new image to Docker Hub; there is no need to copy files to the NAS.

### Facts

| What | Value |
|---|---|
| Docker Hub image | `ninitran/vocab-learning-app:latest` (Docker Hub user `ninitran`, logged in on the Mac via Docker Desktop) |
| NAS | QNAP "AnaNAS", `192.168.178.130`, SSH as `jank@192.168.178.130` (key auth, no password) |
| NAS CPU | **x86_64 / amd64**. This Mac is **arm64**, so images **must** be built with `--platform linux/amd64` |
| Docker on the NAS | Container Station; the binary is `/share/CACHEDEV1_DATA/.qpkg/container-station/bin/docker` (not on PATH for SSH sessions) |
| Compose file | `/share/CACHEDEV1_DATA/homes/jank/compose.yaml` (project dir = `/share/CACHEDEV1_DATA/homes/jank`) |
| App data on the NAS | `/share/CACHEDEV1_DATA/homes/jank/vocab-learning-app/` (holds `vocab/<Learner>/` and `access-code.txt`). In jank's home, because jank can't create folders in Container Station's `appdata` without sudo |
| URL | `http://192.168.178.130:7896` (container port 8080; port 8080 on the NAS is used by QNAP itself, and 7890–7895 by other services) |
| From the internet | `https://g3.myftp.org` (No-IP DDNS name): FRITZ!Box forwards TCP 443 to the NAS, where the `caddy` service terminates HTTPS and proxies to `vocab-learning-app:8080`. Caddyfile and certificates in `/share/CACHEDEV1_DATA/homes/jank/caddy/` |
| Auto update | `update.sh` in the compose dir, run by root cron at 05:00 daily (entry in `/etc/config/crontab`), log in `~/logs/update-compose/cron.log`. It runs `docker compose up -d --no-deps --pull always <svc>` for every **running** service |

### Release a new version

    mvn package
    docker buildx build --platform linux/amd64 -t ninitran/vocab-learning-app:latest --push .

Docker Desktop must be running. The NAS picks up the image at the next 05:00 run. To deploy right away:

    ssh jank@192.168.178.130 'cd /share/CACHEDEV1_DATA/homes/jank && PATH=/share/CACHEDEV1_DATA/.qpkg/container-station/bin:$PATH docker compose up -d --no-deps --pull always vocab-learning-app'

### Compose service (in the NAS compose.yaml)

```yaml
  vocab-learning-app:
    container_name: vocab-learning-app
    image: ninitran/vocab-learning-app:latest
    restart: unless-stopped
    logging:
      driver: json-file
    ports:
      - 7896:8080
    environment:
      - TZ=Europe/Amsterdam
    volumes:
      - /share/CACHEDEV1_DATA/homes/jank/vocab-learning-app/vocab:/app/vocab
      - /share/CACHEDEV1_DATA/homes/jank/vocab-learning-app/access-code.txt:/app/access-code.txt
```

### HTTPS from the internet (Caddy, in the same compose.yaml)

```yaml
  caddy:
    container_name: caddy
    image: caddy:2
    restart: unless-stopped
    logging:
      driver: json-file
    ports:
      - 443:443
    environment:
      - TZ=Europe/Amsterdam
    volumes:
      - /share/CACHEDEV1_DATA/homes/jank/caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - /share/CACHEDEV1_DATA/homes/jank/caddy/data:/data
      - /share/CACHEDEV1_DATA/homes/jank/caddy/config:/config
```

The Caddyfile is just `g3.myftp.org { reverse_proxy vocab-learning-app:8080 }`. Caddy gets and renews the
certificate itself (TLS-ALPN challenge on port 443, so no port 80 is needed; port 80 on the NAS is QNAP's).
Check it with `docker logs caddy`. Port 7896 should only be reachable at home, not forwarded in the FRITZ!Box.

### First-time setup on the NAS

1. Create the data dir and an **empty file** for the access code. If the file is missing, Docker creates a
   directory with that name and the app can't save the code:

       ssh jank@192.168.178.130 'D=/share/CACHEDEV1_DATA/homes/jank/vocab-learning-app; mkdir -p $D/vocab && touch $D/access-code.txt'

2. Optionally copy vocabulary from the Mac into a learner's folder: `scp vocab/Nini/*.txt jank@192.168.178.130:<data dir>/vocab/Nini/`
3. Add the service above to compose.yaml, then start it with the "deploy right away" command.
4. Get the access code from the container log: `docker logs vocab-learning-app`, or read `access-code.txt` in the data dir.

### Gotchas

- **Wrong architecture:** an image built without `--platform linux/amd64` fails on the NAS with `exec format error`.
- **Private repo:** if the Docker Hub repo is private, the nightly cron runs as **root**, so root on the NAS needs
  `sudo docker login` once (it asks for a password, so the user has to do it). A public repo needs no login.
- **The update only touches running services:** a stopped `vocab-learning-app` is not updated or restarted.
- **No loopback access in Docker:** AccessFilter trusts loopback only ("this Mac"). In Docker every browser,
  even on the NAS, connects through the bridge network, so every device needs the access code.
- **Vocab data lives only on the NAS:** `vocab/` and `access-code.txt` are git-ignored, so the files in the NAS data dir are
  the only copy of the vocabulary entered there. Never delete that dir or remove the volume mounts.
- **Old images:** each update leaves the old image behind (`<none>`). Clean up with `docker image prune -f` on the NAS.
