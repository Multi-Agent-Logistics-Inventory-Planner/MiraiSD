#!/bin/bash

# Script to create an admin user in Supabase
# Usage: ./scripts/create-admin-user.sh

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Get the project root (parent of scripts directory)
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"
ENV_FILE="$PROJECT_ROOT/.env"

# Check if .env file exists
if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}Error: .env file not found at $ENV_FILE${NC}"
    echo "Please create a .env file in the project root with your Supabase credentials"
    exit 1
fi

# Source environment variables from project root
export $(cat "$ENV_FILE" | grep -v '^#' | xargs)

# Check required variables
if [ -z "$SUPABASE_URL" ] || [ -z "$SUPABASE_SERVICE_ROLE" ]; then
    echo -e "${RED}Error: Missing required environment variables${NC}"
    echo "Required: SUPABASE_URL, SUPABASE_SERVICE_ROLE"
    echo ""
    echo "To get these values:"
    echo "1. Go to Supabase Dashboard: https://app.supabase.com"
    echo "2. Select your project"
    echo "3. Go to Settings → API"
    echo "4. Copy 'Project URL' for SUPABASE_URL"
    echo "5. Copy 'service_role' key (secret) for SUPABASE_SERVICE_ROLE"
    exit 1
fi

# Default values (can be overridden via environment variables)
EMAIL="${ADMIN_EMAIL:-admin@clinic.com}"
PASSWORD="${ADMIN_PASSWORD:-admin123}"
NAME="${ADMIN_NAME:-admin}"
ROLE="${ADMIN_ROLE:-ADMIN}"

echo -e "${YELLOW}Creating admin user in Supabase...${NC}"
echo "URL: $SUPABASE_URL"
echo "Email: $EMAIL"
echo "Role: $ROLE"
echo ""

# Extract project ref from URL (e.g., https://xxxxx.supabase.co -> xxxxx)
PROJECT_REF=$(echo $SUPABASE_URL | sed -E 's|https?://([^.]+)\..*|\1|')

# Make the API call to create user
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "https://${PROJECT_REF}.supabase.co/auth/v1/admin/users" \
  -H "apikey: $SUPABASE_SERVICE_ROLE" \
  -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$EMAIL\",
    \"password\": \"$PASSWORD\",
    \"email_confirm\": true,
    \"user_metadata\": {
      \"name\": \"$NAME\",
      \"role\": \"$ROLE\"
    }
  }")

# Extract HTTP status code (last line)
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
# Extract response body (all but last line)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 201 ]; then
    echo -e "${GREEN}✓ User created successfully!${NC}"
    echo ""
    echo "Response:"
    echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
else
    echo -e "${RED}✗ Failed to create user (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo "Response:"
    echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
    exit 1
fi

