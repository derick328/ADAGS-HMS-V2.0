"""Log in and trigger the explicit WHO 2019 import without storing credentials."""

import getpass
import json
import sys
from urllib import error, request


BASE_URL = "http://localhost:8080"


def post(path: str, payload: dict | None = None, token: str | None = None) -> dict:
    headers = {"Accept": "application/json"}
    body = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(payload).encode("utf-8")
    if token is not None:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(BASE_URL + path, data=body if body is not None else b"", headers=headers, method="POST")
    with request.urlopen(req, timeout=600) as response:
        text = response.read().decode("utf-8")
        return json.loads(text) if text else {}


def main() -> int:
    username = input("Username (REFERENCE_IMPORT account): ").strip()
    password = getpass.getpass("Password: ")
    if not username or not password:
        print("Username and password are required.", file=sys.stderr)
        return 1
    try:
        login = post("/auth/login", {"username": username, "password": password})
        token = login.get("accessToken")
        if not token:
            print("Login returned no access token.", file=sys.stderr)
            return 1
        result = post("/api/reference/icd10/import/who-2019", token=token)
        print(json.dumps(result, indent=2))
        return 0
    except error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace").strip()
        print(f"HTTP {exc.code} at {exc.url}: {detail or exc.reason}", file=sys.stderr)
        return 1
    except error.URLError as exc:
        print(f"Cannot reach {BASE_URL}: {exc.reason}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
