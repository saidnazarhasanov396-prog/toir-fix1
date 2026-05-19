#!/bin/bash

# Configuration
URL="https://api-toir.tenzorsoft.uz/api/v1/auth/login"
USERNAME="admin"
PASSWORD="Root123456"

# Get the token
RESPONSE=$(curl -s -X POST "$URL" \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"$USERNAME\", \"password\":\"$PASSWORD\"}")

# Debug: show response if extraction fails
if ! echo "$RESPONSE" | grep -q "accessToken"; then
    echo "Xatolik yuz berdi. Server javobi:"
    echo "$RESPONSE"
    exit 1
fi

# Extract accessToken using grep/sed
TOKEN=$(echo "$RESPONSE" | grep -oP '"accessToken":"\K[^"]+')

echo "token:"
echo "$TOKEN"
