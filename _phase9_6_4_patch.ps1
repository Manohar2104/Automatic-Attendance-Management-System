$servicePath = 'd:\Live Smart Attendance\Automatic-Attendance-Management-System\backend\src\services\heartbeatService.ts'
$serviceText = [System.IO.File]::ReadAllText($servicePath)

if (-not $serviceText.Contains('const MAX_WIFI_ENTRIES = 20;')) { throw 'rotation constant anchor not found' }
$serviceText = $serviceText.Replace('const MAX_WIFI_ENTRIES = 20;', "const MAX_WIFI_ENTRIES = 20;`r`nconst ROLLING_TOKEN_ROTATION_THRESHOLD_MS = 15_000;")

$oldHelperAnchor = @'
async function ensureInitialRollingToken(client: PoolClient, sessionId: string, clientTimestamp: Date) {
  const validFrom = new Date(clientTimestamp);
  const validTo = new Date(validFrom.getTime() + 60_000);
  const tokenHash = createHash('sha256').update(`${sessionId}:${validFrom.toISOString()}:initial`).digest('hex');

  await client.query(
    `INSERT INTO rolling_tokens (session_id, sequence_number, token_hash, valid_from, valid_to)
     SELECT $1, 1, $2, $3, $4
      WHERE NOT EXISTS (
        SELECT 1 FROM rolling_tokens WHERE session_id = $1
      )`,
    [sessionId, tokenHash, validFrom.toISOString(), validTo.toISOString()]
  );

  return loadActiveRollingToken(client, sessionId, clientTimestamp);
}

async function loadLastAcceptedSequence(client: PoolClient, sessionId: string, studentId: string) {
'@
$newHelperBlock = @'
async function ensureInitialRollingToken(client: PoolClient, sessionId: string, clientTimestamp: Date) {
  const validFrom = new Date(clientTimestamp);
  const validTo = new Date(validFrom.getTime() + 60_000);
  const tokenHash = createHash('sha256').update(`${sessionId}:${validFrom.toISOString()}:initial`).digest('hex');

  await client.query(
    `INSERT INTO rolling_tokens (session_id, sequence_number, token_hash, valid_from, valid_to)
     SELECT $1, 1, $2, $3, $4
      WHERE NOT EXISTS (
        SELECT 1 FROM rolling_tokens WHERE session_id = $1
      )`,
    [sessionId, tokenHash, validFrom.toISOString(), validTo.toISOString()]
  );

  return loadActiveRollingToken(client, sessionId, clientTimestamp);
}

function shouldRotateRollingToken(rollingToken: RollingTokenRow, clientTimestamp: Date) {
  const validTo = new Date(rollingToken.valid_to);
  return validTo.getTime() - clientTimestamp.getTime() <= ROLLING_TOKEN_ROTATION_THRESHOLD_MS;
}

async function rotateRollingTokenIfNeeded(client: PoolClient, rollingToken: RollingTokenRow, clientTimestamp: Date) {
  if (!shouldRotateRollingToken(rollingToken, clientTimestamp)) {
    return rollingToken;
  }

  const nextSequenceNumber = rollingToken.sequence_number + 1;
  const nextValidFrom = new Date(clientTimestamp);
  const nextValidTo = new Date(nextValidFrom.getTime() + 60_000);
  const nextTokenHash = createHash('sha256').update(`${rollingToken.session_id}:${nextSequenceNumber}:${nextValidFrom.toISOString()}:rotation`).digest('hex');

  await client.query(
    `INSERT INTO rolling_tokens (session_id, sequence_number, token_hash, valid_from, valid_to)
     SELECT $1, $2, $3, $4, $5
      WHERE NOT EXISTS (
        SELECT 1 FROM rolling_tokens
         WHERE session_id = $1
           AND sequence_number = $2
      )`,
    [rollingToken.session_id, nextSequenceNumber, nextTokenHash, nextValidFrom.toISOString(), nextValidTo.toISOString()]
  );

  return loadActiveRollingToken(client, rollingToken.session_id, clientTimestamp) ?? rollingToken;
}

async function loadLastAcceptedSequence(client: PoolClient, sessionId: string, studentId: string) {
'@
if (-not $serviceText.Contains($oldHelperAnchor)) { throw 'helper anchor not found' }
$serviceText = $serviceText.Replace($oldHelperAnchor, $newHelperBlock)

$oldReturnBlock = @'
    const progress = await updateAttendanceProgress(client, attendance, {
      accepted: true,
      heartbeatSequence: sequenceNumber,
      heartbeatScore: classification.confidenceScore,
      classificationResult: classification.classificationResult,
      heartbeatTime: clientTimestamp.toISOString()
    });

    return {
      accepted: true,
      statusCode: 200,
      heartbeat: {
        id: persistedHeartbeat.id,
        sessionId,
        studentId: trimmedStudentId,
        sequenceNumber,
        confidenceScore: classification.confidenceScore,
        classificationResult: classification.classificationResult,
        status: 'ACCEPTED',
        rejectionReason: null,
        serverTimestamp: persistedHeartbeat.serverTimestamp
      },
      runningPresenceScore: progress.runningPresenceScore,
      confidenceScore: classification.confidenceScore,
      classificationResult: classification.classificationResult
    } satisfies HeartbeatOutcome;
'@
$newReturnBlock = @'
    const progress = await updateAttendanceProgress(client, attendance, {
      accepted: true,
      heartbeatSequence: sequenceNumber,
      heartbeatScore: classification.confidenceScore,
      classificationResult: classification.classificationResult,
      heartbeatTime: clientTimestamp.toISOString()
    });

    await rotateRollingTokenIfNeeded(client, rollingToken, clientTimestamp);

    return {
      accepted: true,
      statusCode: 200,
      heartbeat: {
        id: persistedHeartbeat.id,
        sessionId,
        studentId: trimmedStudentId,
        sequenceNumber,
        confidenceScore: classification.confidenceScore,
        classificationResult: classification.classificationResult,
        status: 'ACCEPTED',
        rejectionReason: null,
        serverTimestamp: persistedHeartbeat.serverTimestamp
      },
      runningPresenceScore: progress.runningPresenceScore,
      confidenceScore: classification.confidenceScore,
      classificationResult: classification.classificationResult
    } satisfies HeartbeatOutcome;
'@
if (-not $serviceText.Contains($oldReturnBlock)) { throw 'return block not found' }
$serviceText = $serviceText.Replace($oldReturnBlock, $newReturnBlock)
[System.IO.File]::WriteAllText($servicePath, $serviceText)

$testPath = 'd:\Live Smart Attendance\Automatic-Attendance-Management-System\backend\tests\heartbeat.unit.test.ts'
$testText = [System.IO.File]::ReadAllText($testPath)
if (-not $testText.Contains("shouldRotateRollingToken")) {
  $insertAnchor = @'\nimport {\n  calculateRunningPresenceScore,\n  normalizeWifiFingerprint,\n  validateSequenceProgress\n} from '../src/services/heartbeatService';\n'@
  $replacementAnchor = @'\nimport {\n  calculateRunningPresenceScore,\n  normalizeWifiFingerprint,\n  shouldRotateRollingToken,\n  validateSequenceProgress\n} from '../src/services/heartbeatService';\n'@
  if (-not $testText.Contains($insertAnchor.TrimStart("`n"))) { throw 'import anchor not found' }
  $testText = $testText.Replace($insertAnchor.TrimStart("`n"), $replacementAnchor.TrimStart("`n"))
}

$append = @'

  it('rotates rolling tokens before expiry threshold', () => {
    const token = {
      valid_to: '2026-06-16T10:01:00.000Z'
    };

    expect(shouldRotateRollingToken(token as any, new Date('2026-06-16T10:00:45.000Z'))).toBe(true);
    expect(shouldRotateRollingToken(token as any, new Date('2026-06-16T10:00:44.000Z'))).toBe(false);
  });
'@
if (-not $testText.Contains("calculates running presence score as a rolling average")) { throw 'append anchor not found' }
$testText = $testText.Replace("  it('calculates running presence score as a rolling average', () => {\r\n    expect(calculateRunningPresenceScore(0, 0, 80)).toBe(80);\r\n    expect(calculateRunningPresenceScore(80, 1, 100)).toBe(90);\r\n  });\r\n", "  it('calculates running presence score as a rolling average', () => {\r\n    expect(calculateRunningPresenceScore(0, 0, 80)).toBe(80);\r\n    expect(calculateRunningPresenceScore(80, 1, 100)).toBe(90);\r\n  });\r\n$append")
[System.IO.File]::WriteAllText($testPath, $testText)
