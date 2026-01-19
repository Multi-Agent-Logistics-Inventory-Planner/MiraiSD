#!/bin/bash

# Script to list all Supabase Auth users
# Usage: ./scripts/list-users.sh

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

# Extract project ref from URL (e.g., https://xxxxx.supabase.co -> xxxxx)
PROJECT_REF=$(echo $SUPABASE_URL | sed -E 's|https?://([^.]+)\..*|\1|')

echo -e "${BLUE}Fetching users from Supabase...${NC}"
echo "Project: $PROJECT_REF"
echo ""

# Make the API call to list users
# Supabase Admin API endpoint for listing users
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "https://${PROJECT_REF}.supabase.co/auth/v1/admin/users?per_page=1000" \
  -H "apikey: $SUPABASE_SERVICE_ROLE" \
  -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE" \
  -H "Content-Type: application/json")

# Extract HTTP status code (last line)
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
# Extract response body (all but last line)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    # Check if jq is available for pretty printing
    if command -v jq &> /dev/null; then
        echo -e "${GREEN}✓ Found users:${NC}"
        echo ""
        
        # Count users
        USER_COUNT=$(echo "$BODY" | jq '.users | length')
        echo -e "${BLUE}Total users: $USER_COUNT${NC}"
        echo ""
        
        # List users with details
        echo "$BODY" | jq -r '.users[] | 
          "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
          "ID:      \(.id)\n" +
          "Email:   \(.email // "N/A")\n" +
          "Name:    \(.user_metadata.name // "N/A")\n" +
          "Role:    \(.user_metadata.role // "N/A")\n" +
          "Created: \(.created_at // "N/A")\n" +
          "Updated: \(.updated_at // "N/A")\n" +
          "Confirmed: \(if .email_confirmed_at then "Yes" else "No" end)\n" +
          "Phone:   \(.phone // "N/A")\n" +
          ""'
        
        # Summary by role
        echo ""
        echo -e "${BLUE}Summary by Role:${NC}"
        echo "$BODY" | jq -r '.users[] | .user_metadata.role // "NONE"' | sort | uniq -c | awk '{printf "  %-15s %d users\n", $2, $1}'
        
    else
        echo -e "${YELLOW}Note: Install 'jq' for better formatting (brew install jq)${NC}"
        echo ""
        echo -e "${GREEN}Raw response:${NC}"
        echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
    fi
else
    echo -e "${RED}✗ Failed to fetch users (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo "Response:"
    echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
    exit 1
fi
