#!/bin/bash
# Ensure all environment variables from Railway reach the Java application.
# Log important email variables for debugging.

set -e

# Log configuration for debugging (no secrets in logs)
echo "[Railway-Tomcat-Start] Environment check:"
echo "[Railway-Tomcat-Start] SMTP_HOST=${SMTP_HOST:-not-set}"
echo "[Railway-Tomcat-Start] SMTP_PORT=${SMTP_PORT:-not-set}"
echo "[Railway-Tomcat-Start] SMTP_FROM=${SMTP_FROM:-not-set}"
echo "[Railway-Tomcat-Start] SMTP_USER=${SMTP_USER:-not-set}"
echo "[Railway-Tomcat-Start] SMTP_PASS=${SMTP_PASS:-not-set}"
echo "[Railway-Tomcat-Start] SMTP_USERNAME=${SMTP_USERNAME:-not-set}"
echo "[Railway-Tomcat-Start] SMTP_PASSWORD=${SMTP_PASSWORD:-not-set}"
echo "[Railway-Tomcat-Start] BREVO_API_KEY=${BREVO_API_KEY:+set}"
echo "[Railway-Tomcat-Start] RESEND_API_KEY=${RESEND_API_KEY:+set}"

# Export all current environment variables so they're available to child processes
export $(compgen -e | tr '\n' ' ')

# Run Tomcat with PORT and CATALINA_OPTS set
CATALINA_OPTS="-Dport.http=${PORT:-8080} ${CATALINA_OPTS:-}"
export CATALINA_OPTS

exec catalina.sh run

