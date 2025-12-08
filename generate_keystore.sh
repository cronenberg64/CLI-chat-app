#!/bin/bash

# Remove existing keystore if it exists
rm -f chat.jks

# Generate new keystore with self-signed certificate
# Password is 'password' for simplicity in this demo
keytool -genkey -alias chat \
    -keyalg RSA \
    -keysize 2048 \
    -validity 365 \
    -keystore chat.jks \
    -storepass password \
    -keypass password \
    -dname "CN=localhost, OU=ChatApp, O=Ritsumeikan, L=Kyoto, S=Kyoto, C=JP"

echo "Keystore 'chat.jks' generated successfully."
