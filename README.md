# ufos-installer #

Инсталятор УФОС

### Примеры команд ###

#### Первичная установка ####

```
# Через jar
java -jar ufos-installer.jar \
  --mode init \
  --stand-dir /opt/ufos/STAND \
  --artifacts-dir /opt/ufos/install/artifacts \
  --jetty-template /opt/ufos/install/artifacts/jetty-template-12.1.10.zip \
  --core-war /opt/ufos/install/artifacts/sufd-server-8.23.165.war \
  --libs-zip /opt/ufos/install/artifacts/sufd-server-libs-8.23.165.zip \
  --app-zip /opt/ufos/install/artifacts/func-demo.zip \
  --init-sql /opt/ufos/install/artifacts/init-database.sql \
  --db-host localhost \
  --db-port 5432 \
  --db-name sufd \
  --db-user sufd \
  --db-password secret \
  --chown-user ufos

# Через обёртку:
./init-stand.sh \
  --stand-dir /opt/ufos/STAND \
  --artifacts-dir /opt/ufos/install/artifacts \
  --jetty-template /opt/ufos/install/artifacts/jetty-template-12.1.10.zip \
  --core-war /opt/ufos/install/artifacts/sufd-server-8.23.165.war \
  --libs-zip /opt/ufos/install/artifacts/sufd-server-libs-8.23.165.zip \
  --app-zip /opt/ufos/install/artifacts/func-demo.zip \
  --init-sql /opt/ufos/install/artifacts/init-database.sql \
  --db-host localhost \
  --db-port 5432 \
  --db-name sufd \
  --db-user sufd \
  --db-password secret \
  --chown-user ufos
```

#### Обновление ядра и приклада ####
```
# Через jar
java -jar ufos-installer.jar \
  --mode update-core \
  --stand-dir /opt/ufos/STAND \
  --artifacts-dir /opt/ufos/install/artifacts \
  --core-war /opt/ufos/install/artifacts/sufd-server-8.23.165.war \
  --libs-zip /opt/ufos/install/artifacts/sufd-server-libs-8.23.165.zip \
  --app-zip /opt/ufos/install/artifacts/func-demo.zip

# Через обёртку:
./update-stand.sh \
  --stand-dir /opt/ufos/STAND \
  --artifacts-dir /opt/ufos/install/artifacts \
  --core-war /opt/ufos/install/artifacts/sufd-server-8.23.165.war \
  --libs-zip /opt/ufos/install/artifacts/sufd-server-libs-8.23.165.zip \
  --app-zip /opt/ufos/install/artifacts/func-demo.zip
```

#### Dry-run ####
```
java -jar ufos-installer.jar \
  --mode update-core \
  --stand-dir /opt/ufos/STAND \
  --artifacts-dir /opt/ufos/install/artifacts \
  --dry-run
```