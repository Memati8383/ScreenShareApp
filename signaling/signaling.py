#!/usr/bin/env python3
"""WebRTC sinyal sunucusu: SDP offer/answer ve ICE adaylari iki telefon arasinda yonlendirir.
Video/audio asla bu sunucudan gecmez, sadece kurulum mesajlari.

Kurulum:  pip install websockets
Calistir: python signaling.py --host 0.0.0.0 --port 8765
"""
import argparse
import asyncio
import json

import websockets

ROOMS = {}  # {"oda_adi": {"sender": ws, "viewer": ws}}


async def handler(ws):
    room_id = None
    role = None
    try:
        async for raw in ws:
            msg = json.loads(raw)
            mtype = msg.get("type")

            if mtype == "join":
                room_id = msg["room"]
                role = msg.get("role", "viewer")
                ROOMS.setdefault(room_id, {})[role] = ws
                print(f"[+] {role} odasina katildi: {room_id}")

                # Diger taraf zaten odadaysa iki tarafa da haber ver
                target = "sender" if role == "viewer" else "viewer"
                existing = ROOMS.get(room_id, {}).get(target)
                if existing is not None:
                    await ws.send(json.dumps({"type": "peer-joined", "role": role}))
                    await existing.send(json.dumps({"type": "peer-joined", "role": target}))
                continue

            if room_id is None:
                continue

            # offer/answer/ice mesajlarini karsi role yonlendir
            if mtype in ("offer", "answer", "ice"):
                target = "sender" if role == "viewer" else "viewer"
                peer = ROOMS.get(room_id, {}).get(target)
                if peer is not None:
                    await peer.send(raw)

    except websockets.ConnectionClosed:
        pass
    finally:
        if room_id and role and room_id in ROOMS:
            ROOMS[room_id].pop(role, None)
            if not ROOMS[room_id]:
                del ROOMS[room_id]
            print(f"[-] {role} odadan ayirildi: {room_id}")


async def main(host: str, port: int) -> None:
    print(f"[*] Sinyal sunucusu: ws://{host}:{port}")
    async with websockets.serve(handler, host, port):
        await asyncio.Future()  # suresiz calis


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8765)
    args = ap.parse_args()
    asyncio.run(main(args.host, args.port))
