#!/bin/bash

# Script to confirm a user's email in Supabase
# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Get the project root (parent of scripts directory)
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"
ENV_FILE="$PROJECT_ROOT/.env"

# Check if .env file exists
if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}Error: .env file not found at $ENV_FILE${NC}"
    exit 1
fi

# Source environment variables from project root
export $(cat "$ENV_FILE" | grep -v '^#' | xargs)

# Check required variables
if [ -z "$SUPABASE_URL" ] || [ -z "$SUPABASE_SERVICE_ROLE" ]; then
    echo -e "${RED}Error: Missing required environment variables${NC}"
    echo "Required: SUPABASE_URL, SUPABASE_SERVICE_ROLE"
    exit 1
fi

# Get email from argument or environment variable
EMAIL="${1:-${CONFIRM_EMAIL}}"

if [ -z "$EMAIL" ]; then
    echo -e "${RED}Error: Email not provided${NC}"
    echo "Usage: $0 [email]"
    echo "   OR: CONFIRM_EMAIL=email@example.com $0"
    exit 1
fi

# Extract project ref from URL
PROJECT_REF=$(echo $SUPABASE_URL | sed -E 's|https?://([^.]+)\..*|\1|')

echo -e "${BLUE}Confirming email for user: $EMAIL${NC}"
echo ""

# First, get the user ID by email
echo "Looking up user..."
USER_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "https://${PROJECT_REF}.supabase.co/auth/v1/admin/users?email=${EMAIL}" \
  -H "apikey: $SUPABASE_SERVICE_ROLE" \
  -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE" \
  -H "Content-Type: application/json")

HTTP_CODE=$(echo "$USER_RESPONSE" | tail -n1)
USER_BODY=$(echo "$USER_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -ne 200 ]; then
    echo -e "${RED}Failed to find user (HTTP $HTTP_CODE)${NC}"
    echo "$USER_BODY"
    exit 1
fi

# Extract user ID from response
USER_ID=$(echo "$USER_BODY" | jq -r '.users[0].id // empty')

if [ -z "$USER_ID" ] || [ "$USER_ID" = "null" ]; then
    echo -e "${RED}User not found: $EMAIL${NC}"
    exit 1
fi

echo -e "${GREEN}Found user ID: $USER_ID${NC}"
echo ""

# Update user to confirm email
echo "Confirming email..."
UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "https://${PROJECT_REF}.supabase.co/auth/v1/admin/users/${USER_ID}" \
  -H "apikey: $SUPABASE_SERVICE_ROLE" \
  -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE" \
  -H "Content-Type: application/json" \
  -d '{
    "email_confirm": true
  }')

UPDATE_HTTP_CODE=$(echo "$UPDATE_RESPONSE" | tail -n1)
UPDATE_BODY=$(echo "$UPDATE_RESPONSE" | sed '$d')

if [ "$UPDATE_HTTP_CODE" -eq 200 ] || [ "$UPDATE_HTTP_CODE" -eq 201 ]; then
    echo -e "${GREEN}✓ Email confirmed successfully!${NC}"
    echo ""
    echo "User details:"
    echo "$UPDATE_BODY" | jq '.' 2>/dev/null || echo "$UPDATE_BODY"
else
    echo -e "${RED}✗ Failed to confirm email (HTTP $UPDATE_HTTP_CODE)${NC}"
    echo ""
    echo "Response:"
    echo "$UPDATE_BODY" | jq '.' 2>/dev/null || echo "$UPDATE_BODY"
    exit 1
fi
