#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export POSTGRES_PASSWORD="$(
    grep '^POSTGRES_PASSWORD=' "$SCRIPT_DIR/.env" | cut -d= -f2-
)"