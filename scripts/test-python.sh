#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/../python-engine"
PYTHONPATH=src python -m pytest
