$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "../python-engine")
$env:PYTHONPATH = "src"
py -m pytest
