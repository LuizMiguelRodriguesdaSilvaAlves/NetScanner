"""Run the NetScanner engine as a JSON-over-stdio process."""

from .cli import main

if __name__ == "__main__":
    raise SystemExit(main())
