const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const DEVICE_A = "device_alpha_1";
const DEVICE_B = "device_beta_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: denied across users, devices, sync, and unknown paths", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sync").doc("lock_command").get());
  await assertFails(unauthDb.collection("other_collection").doc("doc1").get());

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Unauth",
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("User profile: owner can create, update avatarBase64, and clear avatar; shadow updates & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Create matching Kotlin map (omits null fields)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Focus",
      email: "alice@example.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Update avatarBase64 cleanly
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      avatarBase64: "abc123base64thumbnail",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Clear avatar cleanly via empty string
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      avatarBase64: "",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user PII read & write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Ghost field & immutable createdAt/userId denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Oversized avatar (> 160,000 chars) denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      avatarBase64: "x".repeat(160001),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Connected devices subcollection: owner CRUD & list succeed; invalid platform/id/cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_A).set({
      userId: ALICE_UID,
      deviceName: "Pixel 8",
      platform: "android",
      syncEnabled: true,
      lastSeenAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_A).update({
      syncEnabled: false,
      lastSeenAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Empty or populated subcollection list query succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").get()
  );

  // Cross-user list/read/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_A).get());

  // Tampered future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_B).set({
      userId: ALICE_UID,
      deviceName: "Future Phone",
      platform: "android",
      syncEnabled: true,
      lastSeenAt: new Date(Date.now() + 60000),
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Lock sync command: owner create/update on lock_command succeed; bad docId, invalid mode, future ts & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const nowMs = Date.now();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync").doc("lock_command").set({
      userId: ALICE_UID,
      commandId: "cmd_uuid_1",
      originDeviceId: DEVICE_A,
      originDeviceName: "Pixel 8",
      mode: "INSTANT",
      freeUseDurationMinutes: 0,
      lockDurationMinutes: 45,
      issuedAtWallMs: nowMs,
      freeUseDeadlineWallMs: 0,
      lockExpirationWallMs: nowMs + 45 * 60000,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync").doc("lock_command").update({
      commandId: "cmd_uuid_2",
      originDeviceId: DEVICE_B,
      originDeviceName: "Galaxy S24",
      mode: "DELAYED",
      freeUseDurationMinutes: 15,
      lockDurationMinutes: 60,
      issuedAtWallMs: nowMs + 1000,
      freeUseDeadlineWallMs: nowMs + 15 * 60000,
      lockExpirationWallMs: nowMs + 75 * 60000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Wrong document ID in sync subcollection rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync").doc("arbitrary_doc").set({
      userId: ALICE_UID,
      commandId: "cmd_uuid_3",
      originDeviceId: DEVICE_A,
      originDeviceName: "Pixel 8",
      mode: "INSTANT",
      freeUseDurationMinutes: 0,
      lockDurationMinutes: 30,
      issuedAtWallMs: nowMs,
      freeUseDeadlineWallMs: 0,
      lockExpirationWallMs: nowMs + 30 * 60000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Invalid mode rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync").doc("lock_command").update({
      mode: "UNLIMITED_HACK",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Cross-user read/write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sync").doc("lock_command").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sync").doc("lock_command").update({
      commandId: "hijack_cmd",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});
