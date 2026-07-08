import asyncio
import time
import httpx

BASE_URL = "http://localhost:8000"
NUM_DEVICES = 50
TIMEOUT_SECONDS = 30.0
BATCH_SIZE = 5

async def check_health(client: httpx.AsyncClient):
    try:
        resp = await client.get(f"{BASE_URL}/health")
        return resp.status_code == 200
    except Exception as e:
        print(f"Health check failed: {e}")
        return False

async def get_token_for_student(client: httpx.AsyncClient, i: int):
    email = f"load_student_{i}@test.com"
    password = "password123"
    
    # Try registration first
    try:
        resp = await client.post(
            f"{BASE_URL}/register",
            json={"email": email, "password": password},
            timeout=TIMEOUT_SECONDS
        )
        if resp.status_code == 200:
            return resp.json()["access_token"]
    except Exception:
        pass
        
    # If registration failed (e.g. already exists or timed out on server side), login
    resp = await client.post(
        f"{BASE_URL}/login",
        json={"email": email, "password": password},
        timeout=TIMEOUT_SECONDS
    )
    if resp.status_code == 200:
        return resp.json()["access_token"]
    else:
        raise Exception(f"Failed to auth student {email}: {resp.text}")

async def bind_device(client: httpx.AsyncClient, token: str, i: int):
    fingerprint = f"load_fingerprint_{i}"
    resp = await client.post(
        f"{BASE_URL}/register-device",
        headers={"Authorization": f"Bearer {token}"},
        json={"device_fingerprint": fingerprint},
        timeout=TIMEOUT_SECONDS
    )
    if resp.status_code not in (200, 400):
        raise Exception(f"Failed to bind device {fingerprint}: {resp.text}")

async def post_presence(client: httpx.AsyncClient, i: int, session_id: str):
    fingerprint = f"load_fingerprint_{i}"
    start = time.perf_counter()
    resp = await client.post(
        f"{BASE_URL}/presence",
        json={
            "device_fingerprint": fingerprint,
            "session_id": session_id,
            "event_type": "ENTER",
            "location": "Room Load"
        },
        timeout=TIMEOUT_SECONDS
    )
    latency = time.perf_counter() - start
    if resp.status_code != 200:
        raise Exception(f"Failed to post presence for {fingerprint}: {resp.text}")
    return latency

async def main():
    print("=== STARTING SMART ATTENDANCE LOAD TEST ===")
    print(f"Targeting: {BASE_URL} with {NUM_DEVICES} concurrent devices.")
    
    limits = httpx.Limits(max_keepalive_connections=50, max_connections=100)
    async with httpx.AsyncClient(limits=limits, headers={"x-bypass-rate-limit": "true"}) as client:
        # Clean up database from previous runs
        print("Cleaning up previous load test database entries...")
        try:
            from app.db import get_engine
            from sqlalchemy import text
            engine = get_engine()
            async with engine.begin() as conn:
                # Clean up overrides
                await conn.execute(text("""
                    DELETE FROM attendance_overrides 
                    WHERE admin_id IN (SELECT id FROM users WHERE email LIKE 'load_student_%')
                       OR attendance_id IN (SELECT id FROM attendances WHERE student_id IN (SELECT id FROM users WHERE email LIKE 'load_student_%'))
                """))
                # Clean up attendances
                await conn.execute(text("DELETE FROM attendances WHERE student_id IN (SELECT id FROM users WHERE email LIKE 'load_student_%')"))
                # Clean up events
                await conn.execute(text("DELETE FROM events WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'load_student_%')"))
                # Clean up bindings
                await conn.execute(text("DELETE FROM device_bindings WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'load_student_%') OR device_fingerprint LIKE 'load_fingerprint_%'"))
                # Clean up users
                await conn.execute(text("DELETE FROM users WHERE email LIKE 'load_student_%'"))
                # Clean up sessions
                await conn.execute(text("DELETE FROM sessions WHERE course_id = 'CS-LOAD'"))
            print("Database cleanup completed successfully.")
        except Exception as e:
            print(f"Warning: Database cleanup failed (continuing anyway): {e}")

        # 1. Health check
        if not await check_health(client):
            print("Error: Backend is not reachable or healthy. Aborting.")
            return
            
        print("Backend is healthy. Authenticating students in batches...")
        
        # 2. Acquire JWT tokens (authenticated student list) - Batched to prevent bcrypt CPU saturation
        start_auth = time.perf_counter()
        tokens = []
        try:
            for start_idx in range(1, NUM_DEVICES + 1, BATCH_SIZE):
                end_idx = min(NUM_DEVICES + 1, start_idx + BATCH_SIZE)
                batch_tokens = await asyncio.gather(*(
                    get_token_for_student(client, i) for i in range(start_idx, end_idx)
                ))
                tokens.extend(batch_tokens)
            print(f"Successfully authenticated {len(tokens)} students in {time.perf_counter() - start_auth:.2f}s.")
        except Exception as e:
            print(f"Error during authentication phase: {e}")
            return
            
        # 3. Register device bindings
        print("Registering device bindings...")
        start_bind = time.perf_counter()
        try:
            for start_idx in range(0, NUM_DEVICES, BATCH_SIZE):
                end_idx = min(NUM_DEVICES, start_idx + BATCH_SIZE)
                await asyncio.gather(*(
                    bind_device(client, tokens[i], i + 1) for i in range(start_idx, end_idx)
                ))
            print(f"Device bindings confirmed in {time.perf_counter() - start_bind:.2f}s.")
        except Exception as e:
            print(f"Error during binding phase: {e}")
            return
            
        # 4. Create an active session using Admin credentials
        print("Creating a new class session...")
        admin_token_resp = await client.post(
            f"{BASE_URL}/login",
            json={"email": "admin@example.com", "password": "admin123"},
            timeout=TIMEOUT_SECONDS
        )
        if admin_token_resp.status_code != 200:
            print("Error: Could not login as admin. Ensure database is seeded.")
            return
        admin_token = admin_token_resp.json()["access_token"]
        
        import datetime
        now = datetime.datetime.now(datetime.timezone.utc)
        start_time = now.isoformat()
        end_time = (now + datetime.timedelta(hours=1)).isoformat()
        
        session_resp = await client.post(
            f"{BASE_URL}/sessions/create",
            params={
                "course_id": "CS-LOAD",
                "room_id": "room-load",
                "location": "Room Load",
                "scheduled_start": start_time,
                "scheduled_end": end_time
            },
            headers={"Authorization": f"Bearer {admin_token}"},
            timeout=TIMEOUT_SECONDS
        )
        if session_resp.status_code != 200:
            print(f"Error: Failed to create session: {session_resp.text}")
            return
        session_id = session_resp.json()["id"]
        print(f"Session CS-LOAD created with ID: {session_id}")
        
        # 5. Simulate concurrent presence entries (Load Event)
        print(f"Simulating {NUM_DEVICES} concurrent presence submissions...")
        start_load = time.perf_counter()
        latencies = []
        try:
            latencies = await asyncio.gather(*(post_presence(client, i + 1, session_id) for i in range(NUM_DEVICES)))
            total_duration = time.perf_counter() - start_load
            avg_latency = sum(latencies) / len(latencies)
            throughput = NUM_DEVICES / total_duration
            
            print("\n--- PRESENCE INGESTION LOAD METRICS ---")
            print(f"Total time elapsed:    {total_duration:.3f} seconds")
            print(f"Average latency:       {avg_latency * 1000:.1f} ms")
            print(f"Throughput:            {throughput:.1f} requests/second")
            print("---------------------------------------\n")
        except Exception as e:
            print(f"Error during presence load simulation: {e}")
            return
            
        # 6. Compute attendance and measure DB processing latency
        print("Computing attendance for all students...")
        start_compute = time.perf_counter()
        compute_resp = await client.post(
            f"{BASE_URL}/compute-attendance/{session_id}?location_filter=Room%20Load",
            timeout=TIMEOUT_SECONDS
        )
        compute_duration = time.perf_counter() - start_compute
        if compute_resp.status_code != 200:
            print(f"Error: Attendance computation failed: {compute_resp.text}")
            return
        
        compute_data = compute_resp.json()
        print("\n--- ATTENDANCE ENGINE SCALE SUMMARY ---")
        print(f"Processing time:        {compute_duration:.3f} seconds")
        print(f"Bound devices detected: {compute_data['bound_devices']}")
        print(f"Location validated:     {compute_data['location_validated']}")
        results = compute_data["results"]
        p_count = sum(1 for r in results if r["status"] == "PRESENT")
        print(f"Students marked PRESENT: {p_count} / {len(results)}")
        print("---------------------------------------\n")
        
        print("Load test completed successfully!")

if __name__ == "__main__":
    asyncio.run(main())
