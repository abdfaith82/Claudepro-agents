const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_mesh_123";
const BOB_UID = "bob_mesh_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsNow() {
  return new Date(Date.now() - 1000);
}

function validAgentPayload(uid) {
  return {
    userId: uid,
    name: "Vertex Primary Node",
    provider: "VERTEX_AI",
    googleAccountEmail: "ops@example.com",
    region: "us-central1",
    modelName: "gemini-3.8-flash",
    role: "Primary Reasoning",
    status: "ACTIVE",
    priority: 9,
    currentLoadPct: 24,
    maxConcurrency: 10,
    tasksCompleted: 0,
    failoversHandled: 0,
    avgLatencyMs: 140,
    autoFailoverEnabled: true,
    endpointUrl: "https://us-central1-aiplatform.googleapis.com",
    createdAt: tsNow(),
    updatedAt: tsNow(),
  };
}

function validTaskPayload(uid, status = "RUNNING") {
  return {
    userId: uid,
    title: "Analyze Cloud Telemetry",
    prompt: "Evaluate agent latency and failover thresholds.",
    category: "ANALYSIS",
    status,
    assignedAgentId: "agent_1",
    assignedAgentName: "Vertex Primary Node",
    failoverFromAgentName: "",
    failoverCount: 0,
    latencyMs: 0,
    resultSummary: "",
    checkpointNote: "Initialized",
    createdAt: tsNow(),
    updatedAt: tsNow(),
  };
}

function validLogPayload(uid) {
  return {
    userId: uid,
    eventType: "AGENT_CONNECTED",
    agentId: "agent_1",
    agentName: "Vertex Primary Node",
    taskId: "",
    message: "Agent authenticated with Google and joined mesh.",
    metadataJson: "{\"region\":\"us-central1\"}",
    latencyMs: 45,
    severity: "SUCCESS",
    createdAt: tsNow(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot list or create agents", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/agents`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/agents`).doc("agent_1").set(validAgentPayload(ALICE_UID))
  );
});

test("Authenticated owner can create, update, and list own cloud agents", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/agents`).doc("agent_1");
  await assertSucceeds(ref.set(validAgentPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/agents`).get());
  await assertSucceeds(
    ref.update({
      status: "OFFLINE",
      priority: 10,
      updatedAt: tsNow(),
    })
  );
});

test("Cross-user access to agents, tasks, and logs is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection(`users/${ALICE_UID}/agents`).doc("agent_1").set(validAgentPayload(ALICE_UID));
  });
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/agents`).doc("agent_1").get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/agents`).get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/agents`).doc("agent_2").set(validAgentPayload(ALICE_UID))
  );
});

test("Shadow update with ghost field or invalid priority is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/agents`).doc("agent_1");
  await assertSucceeds(ref.set(validAgentPayload(ALICE_UID)));

  // Ghost field
  await assertFails(
    ref.update({
      isAdmin: true,
      updatedAt: tsNow(),
    })
  );

  // Out of bounds priority
  await assertFails(
    ref.update({
      priority: 99,
      updatedAt: tsNow(),
    })
  );
});

test("Task terminal state lock prevents modifying COMPLETED task", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_1");
  await assertSucceeds(taskRef.set(validTaskPayload(ALICE_UID, "RUNNING")));
  await assertSucceeds(
    taskRef.update({
      status: "COMPLETED",
      resultSummary: "Done",
      latencyMs: 320,
      updatedAt: tsNow(),
    })
  );
  await assertFails(
    taskRef.update({
      status: "RUNNING",
      updatedAt: tsNow(),
    })
  );
});

test("Logs are append-only (create succeeds, update/delete fail)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logRef = aliceDb.collection(`users/${ALICE_UID}/logs`).doc("log_1");
  await assertSucceeds(logRef.set(validLogPayload(ALICE_UID)));
  await assertFails(logRef.update({ message: "Tampered" }));
  await assertFails(logRef.delete());
});
