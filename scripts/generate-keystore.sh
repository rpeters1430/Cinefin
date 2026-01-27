#!/usr/bin/env bash
#
# Generate a release keystore for signing Android apps
# Usage: ./scripts/generate-keystore.sh
#
# This script will:
# 1. Generate a new keystore file
# 2. Output the base64-encoded keystore for GitHub Actions secrets
# 3. Create a template for local signing configuration
#
# IMPORTANT: Keep your keystore and passwords secure!
# - Never commit the keystore file to version control
# - Back up the keystore file securely (you cannot recover it if lost)
# - Store passwords in a password manager
#

set -e

# Default values
KEYSTORE_NAME="jellyfin-release.jks"
KEY_ALIAS="jellyfin"
VALIDITY_DAYS=10000  # ~27 years

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}==================================================${NC}"
echo -e "${BLUE}  Android Release Keystore Generator${NC}"
echo -e "${BLUE}==================================================${NC}"
echo ""

# Check if keytool is available
if ! command -v keytool &> /dev/null; then
    echo -e "${RED}Error: keytool not found. Please install JDK.${NC}"
    exit 1
fi

# Check if keystore already exists
if [ -f "$KEYSTORE_NAME" ]; then
    echo -e "${YELLOW}Warning: $KEYSTORE_NAME already exists.${NC}"
    read -p "Do you want to overwrite it? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 1
    fi
    rm "$KEYSTORE_NAME"
fi

echo -e "${GREEN}Generating new keystore...${NC}"
echo ""
echo "Please provide the following information:"
echo "(Press Enter to use defaults where shown)"
echo ""

# Get keystore password
while true; do
    read -s -p "Keystore password (min 6 characters): " KEYSTORE_PASSWORD
    echo
    if [ ${#KEYSTORE_PASSWORD} -lt 6 ]; then
        echo -e "${RED}Password must be at least 6 characters.${NC}"
        continue
    fi
    read -s -p "Confirm keystore password: " KEYSTORE_PASSWORD_CONFIRM
    echo
    if [ "$KEYSTORE_PASSWORD" != "$KEYSTORE_PASSWORD_CONFIRM" ]; then
        echo -e "${RED}Passwords do not match. Try again.${NC}"
        continue
    fi
    break
done

# Get key password (can be same as keystore)
echo ""
read -p "Use same password for key? (Y/n): " -n 1 -r SAME_PASSWORD
echo
if [[ $SAME_PASSWORD =~ ^[Nn]$ ]]; then
    while true; do
        read -s -p "Key password (min 6 characters): " KEY_PASSWORD
        echo
        if [ ${#KEY_PASSWORD} -lt 6 ]; then
            echo -e "${RED}Password must be at least 6 characters.${NC}"
            continue
        fi
        read -s -p "Confirm key password: " KEY_PASSWORD_CONFIRM
        echo
        if [ "$KEY_PASSWORD" != "$KEY_PASSWORD_CONFIRM" ]; then
            echo -e "${RED}Passwords do not match. Try again.${NC}"
            continue
        fi
        break
    done
else
    KEY_PASSWORD="$KEYSTORE_PASSWORD"
fi

# Get certificate details
echo ""
echo "Certificate details (for identifying the app publisher):"
read -p "Your name or organization [Jellyfin Android]: " CN
CN=${CN:-"Jellyfin Android"}

read -p "Organizational unit [Development]: " OU
OU=${OU:-"Development"}

read -p "Organization [Personal]: " O
O=${O:-"Personal"}

read -p "City/Locality []: " L
read -p "State/Province []: " ST
read -p "Country code (2 letters) [US]: " C
C=${C:-"US"}

# Build the distinguished name
DNAME="CN=$CN, OU=$OU, O=$O, L=$L, ST=$ST, C=$C"

echo ""
echo -e "${GREEN}Generating keystore with the following details:${NC}"
echo "  Keystore file: $KEYSTORE_NAME"
echo "  Key alias: $KEY_ALIAS"
echo "  Validity: $VALIDITY_DAYS days"
echo "  DN: $DNAME"
echo ""

# Generate the keystore
keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE_NAME" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY_DAYS" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "$DNAME"

echo ""
echo -e "${GREEN}Keystore generated successfully!${NC}"
echo ""

# Generate base64 for GitHub Actions
echo -e "${BLUE}==================================================${NC}"
echo -e "${BLUE}  GitHub Actions Secrets Setup${NC}"
echo -e "${BLUE}==================================================${NC}"
echo ""
echo "Add these secrets to your GitHub repository:"
echo "(Settings > Secrets and variables > Actions > New repository secret)"
echo ""

KEYSTORE_BASE64=$(base64 -w 0 "$KEYSTORE_NAME" 2>/dev/null || base64 "$KEYSTORE_NAME" | tr -d '\n')

echo -e "${YELLOW}1. ANDROID_KEYSTORE_BASE64${NC}"
echo "   Value: (base64 encoded keystore - saved to keystore-base64.txt)"
echo "$KEYSTORE_BASE64" > keystore-base64.txt
echo ""

echo -e "${YELLOW}2. ANDROID_KEYSTORE_PASSWORD${NC}"
echo "   Value: $KEYSTORE_PASSWORD"
echo ""

echo -e "${YELLOW}3. ANDROID_KEY_ALIAS${NC}"
echo "   Value: $KEY_ALIAS"
echo ""

echo -e "${YELLOW}4. ANDROID_KEY_PASSWORD${NC}"
echo "   Value: $KEY_PASSWORD"
echo ""

# Create local properties template
echo -e "${BLUE}==================================================${NC}"
echo -e "${BLUE}  Local Development Setup${NC}"
echo -e "${BLUE}==================================================${NC}"
echo ""
echo "For local release builds, add these to your gradle.properties:"
echo "(Do NOT commit this file if it contains passwords)"
echo ""

cat > signing.properties.template << EOF
# Android signing configuration
# Copy this to gradle.properties or set as environment variables
# WARNING: Do not commit passwords to version control!

ANDROID_KEYSTORE_PATH=$(pwd)/$KEYSTORE_NAME
ANDROID_KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS=$KEY_ALIAS
ANDROID_KEY_PASSWORD=$KEY_PASSWORD
EOF

echo "Template saved to: signing.properties.template"
echo ""

# Security reminders
echo -e "${RED}==================================================${NC}"
echo -e "${RED}  IMPORTANT SECURITY REMINDERS${NC}"
echo -e "${RED}==================================================${NC}"
echo ""
echo "1. BACK UP your keystore file ($KEYSTORE_NAME) securely!"
echo "   - You cannot recover it if lost"
echo "   - You cannot update your app without it"
echo ""
echo "2. Add these to .gitignore (if not already):"
echo "   - *.jks"
echo "   - *.keystore"
echo "   - keystore-base64.txt"
echo "   - signing.properties.template"
echo ""
echo "3. Delete keystore-base64.txt after copying to GitHub Secrets"
echo ""
echo -e "${GREEN}Done! Your keystore is ready for use.${NC}"
