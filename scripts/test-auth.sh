#!/bin/bash

# Quick test script for authentication endpoint
# Usage: ./test-auth.sh [token]
#        OR set TOKEN environment variable

TOKEN="${1:-${TOKEN}}"

if [ -z "$TOKEN" ]; then
    echo "Error: Token not provided"
    echo "Usage: ./test-auth.sh [token]"
    echo "   OR: TOKEN=your-token ./test-auth.sh"
    echo ""
    echo "To get a token:"
    echo "  cd scripts && node get-token.mjs"
    exit 1
fi

echo "Testing authentication endpoint..."
echo "URL: http://localhost:4000/api/auth/validate"
echo ""

# Test the validate endpoint with verbose output
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST http://localhost:4000/api/auth/validate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "Response:"
echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
echo ""
echo "HTTP Status: $HTTP_CODE"
echo ""

# Additional diagnostics
if [ "$HTTP_CODE" -ne 200 ]; then
    echo "⚠️  Request failed. Checking if service is running..."
    if ! curl -s -f http://localhost:4000/actuator/health > /dev/null 2>&1; then
        echo "Service might not be running or not responding."
        echo "Try: cd services/inventory-service && mvn spring-boot:run"
    fi
fi