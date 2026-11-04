#!/bin/sh
set -eu

api_url="${API_URL:-http://localhost:8080}"
user_id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
flight_id="11111111-1111-1111-1111-111111111111"

printf '\n1. Поиск рейсов\n'
curl -fsS "$api_url/flights?origin=SVO&destination=LED&date=2026-04-01"
printf '\n\n2. Создание бронирования\n'
creation=$(curl -fsS -X POST "$api_url/bookings" \
  -H 'Content-Type: application/json' \
  -d "{\"user_id\":\"$user_id\",\"flight_id\":\"$flight_id\",\"passenger_name\":\"Ivan Petrov\",\"passenger_email\":\"ivan@example.com\",\"seat_count\":2}")
printf '%s\n' "$creation"

booking_id=$(printf '%s' "$creation" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
if [ -z "$booking_id" ]; then
  printf 'Не удалось получить booking_id\n' >&2
  exit 1
fi

printf '\n3. Получение и список бронирований\n'
curl -fsS "$api_url/bookings/$booking_id"
printf '\n'
curl -fsS "$api_url/bookings?user_id=$user_id"

printf '\n\n4. Отмена бронирования\n'
curl -fsS -X POST "$api_url/bookings/$booking_id/cancel"
printf '\n'

