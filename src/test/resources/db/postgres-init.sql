-- Начальная инициализация тестовой БД PostgreSQL для Testcontainers.
-- Устанавливаем схему по умолчанию и кодировку

SET client_encoding = 'UTF8';
SET timezone = 'UTC';

-- Создаём тестовую таблицу, которая должна быть доступна всем тестам.
-- (не путать с таблицей из init.sql, которую создаёт инсталлятор)
CREATE TABLE IF NOT EXISTS test_container_ready
(
    id    INT PRIMARY KEY,
    value TEXT
);

INSERT INTO test_container_ready (id, value)
VALUES (1, 'container is ready');