"""Simple find3 WebSocket simulator.

Run this locally to simulate a find3 server pushing presence messages to our subscriber.

Usage:
  python server/tools/find3_simulator.py

It listens on ws://0.0.0.0:8765 by default and sends a sample message every 5 seconds.
"""
import asyncio
import json
import websockets
import datetime

HOST = '0.0.0.0'
PORT = 8765

SAMPLE_DEVICES = [
    'sim-dev-1',
    'sim-dev-2',
    'sim-dev-3',
]

async def handler(websocket):
    print('client connected')
    try:
        i = 0
        while True:
            # rotate devices
            device = SAMPLE_DEVICES[i % len(SAMPLE_DEVICES)]
            # Use timezone-aware UTC timestamps and include a simple sequence counter
            payload = {
                'deviceFingerprint': device,
                'sessionId': 'sim-session-1',
                'location': 'sim-room-101',
                'eventType': 'ENTER',
                'timestamp': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                'seq': i,
            }
            msg = json.dumps(payload)
            await websocket.send(msg)
            print('sent', msg)
            i += 1
            await asyncio.sleep(5)
    except websockets.ConnectionClosed:
        print('client disconnected')

async def main():
    print(f'starting simulator on ws://{HOST}:{PORT}')
    async with websockets.serve(handler, HOST, PORT):
        await asyncio.Future()

if __name__ == '__main__':
    asyncio.run(main())
