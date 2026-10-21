# Marketplace API

Домашнее задание №2. Сервис реализует CRUD товаров, заказы, промокоды, JWT-авторизацию и ролевой доступ.

## Запуск

Нужны Docker и Docker Compose.

```bash
docker compose up --build -d
curl -i http://localhost:8080/health
```

Ожидаемый ответ:

```json
{"status":"ok","service":"marketplace-api"}
```

Остановить сервисы можно командой `docker compose down`.

## Демонстрация

После запуска выполните:

```bash
make demo
```

Скрипт регистрирует продавца и покупателя, создаёт товар и заказ, показывает записи в PostgreSQL, отменяет заказ и архивирует товар.

Открыть базу вручную:

```bash
make db
```

Примеры запросов в БД:

```sql
SELECT id, name, stock, status FROM products;
SELECT id, user_id, status, total_amount, discount_amount FROM orders;
SELECT order_id, product_id, quantity, price_at_order FROM order_items;
SELECT code, current_uses FROM promo_codes;
SELECT user_id, operation_type, created_at FROM user_operations;
```

## API

Контракт находится в [`src/main/resources/openapi/marketplace.yaml`](src/main/resources/openapi/marketplace.yaml).

| Метод | Endpoint | Доступ |
|---|---|---|
| POST | `/auth/register`, `/auth/login`, `/auth/refresh` | Без токена |
| GET | `/products`, `/products/{id}` | USER, SELLER, ADMIN |
| POST | `/products` | SELLER, ADMIN |
| PUT, DELETE | `/products/{id}` | Владелец SELLER или ADMIN |
| POST | `/orders` | USER, ADMIN |
| GET, PUT | `/orders/{id}` | Владелец USER или ADMIN |
| POST | `/orders/{id}/cancel` | Владелец USER или ADMIN |
| POST | `/promo-codes` | SELLER, ADMIN |

`DELETE /products/{id}` не удаляет строку. Он меняет статус товара на `ARCHIVED`.

## Кодогенерация

DTO, интерфейсы и контроллеры создаются из OpenAPI во время Maven-сборки. Каталог `target` не хранится в Git.

```bash
make generate
make test
```

При обычном запуске отдельная генерация не нужна. Команда `docker compose up --build` выполняет её сама.

## Основные решения

- OpenAPI является источником DTO, валидации и интерфейсов API. Ручных DTO нет.
- Flyway создаёт схему, индекс `idx_products_status` и триггеры для `updated_at`.
- Резервирование товаров и создание заказа выполняются в одной транзакции.
- Строки товаров блокируются через `PESSIMISTIC_WRITE`, чтобы два заказа не списали один остаток.
- Цена копируется в `order_items.price_at_order`. Изменение каталога не меняет старый заказ.
- Access token живёт 20 минут, refresh token живёт 14 дней. Роль хранится в claim `role`.
- Каждый запрос получает `X-Request-Id`. API-логи записываются в JSON, а пароли маскируются.
- Интервал между операциями с заказом задаётся переменной `ORDER_OPERATION_COOLDOWN`.

## Структура

```text
src/main/resources/openapi/       OpenAPI-контракт
src/main/resources/db/migration/  миграции Flyway
src/main/java/.../api/            реализации сгенерированных интерфейсов
src/main/java/.../service/        бизнес-правила и транзакции
src/main/java/.../security/       JWT и роли
scripts/demo.sh                   E2E-сценарий для защиты
```
