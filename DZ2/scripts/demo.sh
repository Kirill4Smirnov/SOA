#!/bin/sh
set -eu

base_url="${BASE_URL:-http://localhost:8080}"
suffix="$(date +%s)"
seller="seller_${suffix}"
buyer="buyer_${suffix}"

post_json() {
    curl --fail --silent --show-error \
        -H "Content-Type: application/json" "$@"
}

json_value() {
    key="$1"
    sed -n "s/.*\"${key}\":\"\([^\"]*\)\".*/\1/p"
}

echo "1. Register seller and buyer"
post_json -d "{\"username\":\"${seller}\",\"password\":\"seller-pass\",\"role\":\"SELLER\"}" \
    "${base_url}/auth/register" >/dev/null
post_json -d "{\"username\":\"${buyer}\",\"password\":\"buyer-pass\",\"role\":\"USER\"}" \
    "${base_url}/auth/register" >/dev/null

seller_login="$(post_json -d "{\"username\":\"${seller}\",\"password\":\"seller-pass\"}" "${base_url}/auth/login")"
buyer_login="$(post_json -d "{\"username\":\"${buyer}\",\"password\":\"buyer-pass\"}" "${base_url}/auth/login")"
seller_token="$(printf '%s' "$seller_login" | json_value access_token)"
buyer_token="$(printf '%s' "$buyer_login" | json_value access_token)"

echo "2. Seller creates a product"
product="$(post_json -H "Authorization: Bearer ${seller_token}" \
    -d '{"name":"Mechanical keyboard","price":5990,"stock":10,"category":"electronics","status":"ACTIVE"}' \
    "${base_url}/products")"
product_id="$(printf '%s' "$product" | json_value id)"
printf '%s\n' "$product"

echo "3. Buyer creates an order"
order="$(post_json -H "Authorization: Bearer ${buyer_token}" \
    -d "{\"items\":[{\"product_id\":\"${product_id}\",\"quantity\":2}]}" \
    "${base_url}/orders")"
order_id="$(printf '%s' "$order" | json_value id)"
printf '%s\n' "$order"

echo "4. Data stored in PostgreSQL"
docker compose exec -T postgres psql -U marketplace -d marketplace \
    -c "SELECT id, name, stock, status FROM products WHERE id = '${product_id}';"
docker compose exec -T postgres psql -U marketplace -d marketplace \
    -c "SELECT id, user_id, status, total_amount FROM orders WHERE id = '${order_id}';"
docker compose exec -T postgres psql -U marketplace -d marketplace \
    -c "SELECT order_id, product_id, quantity, price_at_order FROM order_items WHERE order_id = '${order_id}';"

echo "5. Buyer cancels the order, stock returns"
curl --fail --silent --show-error -X POST -H "Authorization: Bearer ${buyer_token}" \
    "${base_url}/orders/${order_id}/cancel"
echo

echo "6. Seller archives the product"
curl --fail --silent --show-error -X DELETE -H "Authorization: Bearer ${seller_token}" \
    "${base_url}/products/${product_id}"
docker compose exec -T postgres psql -U marketplace -d marketplace \
    -c "SELECT id, stock, status FROM products WHERE id = '${product_id}';"
