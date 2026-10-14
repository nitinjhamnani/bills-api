#!/usr/bin/env python3
"""Seed enough live bill/wallet activity for every report to show rows.

Uses the real pay / pick-up / mark-paid / top-up / credit APIs (no money-rule
shortcuts). Safe to re-run: new cycles and unique idempotency keys each time.
"""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

BASE = "http://localhost:8081"
PASSWORD = "Password@123"


def login(phone: str) -> dict:
    return post("/api/v1/auth/password/login", {"identifier": phone, "password": PASSWORD})


def req(method: str, path: str, token: str | None = None, body=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode()[:400]
        raise RuntimeError(f"{method} {path} -> {exc.code} {detail}") from exc


def get(path: str, token: str):
    return req("GET", path, token)


def post(path: str, body, token: str | None = None):
    return req("POST", path, token, body)


def stamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")


def wallet_balance(client_token: str, client_id: str) -> float:
    data = get(f"/api/v1/clients/{client_id}/wallet", client_token)
    if isinstance(data, dict):
        return float(data.get("walletBalance") or data.get("balance") or 0)
    return 0.0


def pay_some(client_token: str, client_id: str, count: int) -> list[str]:
    pending = [
        row
        for row in get(f"/api/v1/clients/{client_id}/my-payments", client_token)
        if row.get("paymentStatus") == "PENDING" and row.get("orgStatus") == "NA"
    ]
    pending.sort(key=lambda row: float(row.get("amount") or 0))
    budget = wallet_balance(client_token, client_id)
    chosen = []
    running = 0.0
    for row in pending:
        amount = float(row.get("amount") or 0)
        if len(chosen) >= count:
            break
        if running + amount > budget + 0.001:
            continue
        chosen.append(row)
        running += amount
    if not chosen:
        print(f"  no affordable unpaid cycles for {client_id} (balance {budget:.2f})")
        return []
    ids = [row["id"] for row in chosen]
    post(f"/api/v1/clients/{client_id}/my-payments/pay", {"billPaymentIds": ids}, client_token)
    print(f"  paid {len(ids)} cycles ({running:.2f}) from balance {budget:.2f}")
    return ids


def settle_mix(org_token: str, payment_ids: list[str], paid: int, in_process: int) -> None:
    remaining = list(payment_ids)
    to_pay = remaining[:paid]
    remaining = remaining[paid:]
    to_hold = remaining[:in_process]
    for payment_id in to_pay + to_hold:
        try:
            post(f"/api/v1/organisation/my-payments/{payment_id}/pick-up", {}, org_token)
        except RuntimeError as exc:
            print(f"  pick-up skip {payment_id}: {exc}")
    for i, payment_id in enumerate(to_pay):
        post(
            f"/api/v1/organisation/my-payments/{payment_id}/mark-paid",
            {
                "bbpsTransactionId": f"BBPS-RPT-{stamp()}-{i}",
                "billType": "BBPS",
            },
            org_token,
        )
    print(f"  settled {len(to_pay)} paid, left {len(to_hold)} in-process, {len(remaining[in_process:])} pending pick-up")


def seed_topups(client_token: str, client_id: str, org_token: str, label: str) -> None:
    key = f"rpt-{label}-{stamp()}"
    completed = post(
        f"/api/v1/clients/{client_id}/wallet/topup",
        {
            "amount": 2500,
            "paymentMode": "NEFT",
            "paymentReference": f"NEFT-{key}-OK",
            "idempotencyKey": f"{key}-ok",
        },
        client_token,
    )
    post(f"/api/v1/organisation/wallet-topups/{completed['id']}/review", {"approve": True}, org_token)
    rejected = post(
        f"/api/v1/clients/{client_id}/wallet/topup",
        {
            "amount": 750,
            "paymentMode": "UPI",
            "paymentReference": f"UPI-{key}-NO",
            "idempotencyKey": f"{key}-no",
        },
        client_token,
    )
    post(f"/api/v1/organisation/wallet-topups/{rejected['id']}/review", {"approve": False}, org_token)
    post(
        f"/api/v1/clients/{client_id}/wallet/topup",
        {
            "amount": 1800,
            "paymentMode": "RTGS",
            "paymentReference": f"RTGS-{key}-WAIT",
            "idempotencyKey": f"{key}-wait",
        },
        client_token,
    )
    print(f"  top-ups: approved 2500, rejected 750, pending 1800")


def credit(org_token: str, client_id: str, amount: str, note: str) -> None:
    post(
        f"/api/v1/organisation/wallets/{client_id}/credit",
        {"amount": amount, "note": note},
        org_token,
    )
    print(f"  manual credit {amount} ({note})")


def seed_tenant(org_phone: str, clients: list[tuple[str, str, int]]) -> None:
    org = login(org_phone)
    org_token = org["accessToken"]
    print(f"\n== {org['user']['displayName']} ({org_phone}) ==")
    for phone, label, pay_count in clients:
        client = login(phone)
        client_id = client["user"]["clientId"]
        client_token = client["accessToken"]
        print(f"-- {label} {client_id}")
        credit(org_token, client_id, "25000.00", f"Report seed float for {label}")
        paid_ids = pay_some(client_token, client_id, pay_count)
        if paid_ids:
            settle_mix(org_token, paid_ids, paid=max(1, pay_count // 3), in_process=max(1, pay_count // 4))
        seed_topups(client_token, client_id, org_token, label.replace(" ", "").lower())
        credit(org_token, client_id, "1500.00", f"Report seed credit for {label}")


def settle_existing_queue(org_phone: str, mark_paid: int) -> None:
    org = login(org_phone)
    org_token = org["accessToken"]
    queue = get("/api/v1/organisation/my-payments", org_token)
    in_process = [row["id"] for row in queue if row.get("orgStatus") == "IN_PROCESS"]
    for i, payment_id in enumerate(in_process[:mark_paid]):
        try:
            post(
                f"/api/v1/organisation/my-payments/{payment_id}/mark-paid",
                {"bbpsTransactionId": f"BBPS-Q-{stamp()}-{i}", "billType": "BBPS"},
                org_token,
            )
        except RuntimeError as exc:
            print(f"  existing settle skip {payment_id}: {exc}")
    print(f"  marked {min(mark_paid, len(in_process))} existing in-process as paid")


def main() -> None:
    seed_tenant(
        "9876543210",
        [
            ("9111111111", "FastPay", 9),
            ("9222222222", "QuickPay", 6),
        ],
    )
    seed_tenant(
        "9977909484",
        [
            ("9425505123", "PayvittAcme", 8),
            ("9333312222", "Harbor", 6),
        ],
    )
    settle_existing_queue("9977909484", 3)
    print("\nSeed complete.")


if __name__ == "__main__":
    main()
