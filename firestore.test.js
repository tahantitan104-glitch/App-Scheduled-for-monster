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

test("Unauthenticated user: denied across users, devices, sync, lock_commands, presets, schedules, and unknown paths", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sync").doc("lock_command").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("lock_commands").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("presets").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("schedules").get());
  await assertFails(unauthDb.collection("other_collection").doc("doc1").get());

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      schemaVersion: 1,
      displayName: "Unauth",
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("User profile: owner create/update/clear avatar succeed; shadow updates, oversized avatar & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      schemaVersion: 1,
      displayName: "Alice Focus",
      email: "alice@example.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      schemaVersion: 1,
      avatarBase64: "abc123base64thumbnail",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      avatarBase64: "",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 200),
    })
  );

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
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      avatarBase64: "x".repeat(160001),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Connected devices: owner CRUD & delete (unlink) succeed; invalid metadata, future ts & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_A).set({
      userId: ALICE_UID,
      schemaVersion: 1,
      deviceName: "Pixel 8",
      platform: "android",
      androidVersion: 35,
      appVersion: "1.0.0",
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

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_A).delete());

  // Unlink succeeds for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_A).delete());

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_B).set({
      userId: ALICE_UID,
      schemaVersion: 1,
      deviceName: "Future Phone",
      platform: "android",
      syncEnabled: true,
      lastSeenAt: new Date(Date.now() + 60000),
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Lock commands & legacy lock_command: valid payloads succeed; mismatches, invalid modes & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const nowMs = Date.now();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lock_commands").doc("cmd_uuid_1").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      commandId: "cmd_uuid_1",
      originDeviceId: DEVICE_A,
      originDeviceName: "Pixel 8",
      mode: "INSTANT",
      freeUseDurationMinutes: 0,
      lockDurationMinutes: 45,
      issuedAtWallMs: nowMs,
      intendedStartWallMs: nowMs,
      freeUseDeadlineWallMs: 0,
      lockExpirationWallMs: nowMs + 45 * 60000,
      scheduleId: "",
      occurrenceId: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Query mirrored from CloudAccountRepository.observeRecentLockCommands
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lock_commands")
      .orderBy("issuedAtWallMs", "desc").limit(20).get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync").doc("lock_command").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      commandId: "cmd_uuid_1",
      originDeviceId: DEVICE_A,
      originDeviceName: "Pixel 8",
      mode: "DELAYED",
      freeUseDurationMinutes: 15,
      lockDurationMinutes: 60,
      issuedAtWallMs: nowMs,
      intendedStartWallMs: nowMs,
      freeUseDeadlineWallMs: nowMs + 15 * 60000,
      lockExpirationWallMs: nowMs + 75 * 60000,
      scheduleId: "",
      occurrenceId: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Command docId mismatch rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("lock_commands").doc("cmd_a").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      commandId: "cmd_b",
      originDeviceId: DEVICE_A,
      originDeviceName: "Pixel 8",
      mode: "INSTANT",
      freeUseDurationMinutes: 0,
      lockDurationMinutes: 30,
      issuedAtWallMs: nowMs,
      intendedStartWallMs: nowMs,
      freeUseDeadlineWallMs: 0,
      lockExpirationWallMs: nowMs + 30 * 60000,
      scheduleId: "",
      occurrenceId: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("lock_commands").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync").doc("lock_command").update({
      mode: "UNLIMITED_HACK",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Delayed presets & synced schedules: owner CRUD/list succeed; out-of-range, extra PII/allowlist fields & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const nowMs = Date.now();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_deep").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      presetId: "preset_deep",
      name: "Deep Study",
      freeUseMinutes: 10,
      lockDurationMinutes: 50,
      originDeviceId: DEVICE_A,
      updatedAtWallMs: nowMs,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_deep").update({
      name: "Deep Study 60",
      lockDurationMinutes: 60,
      updatedAtWallMs: nowMs + 1000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_morning").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      scheduleId: "sched_morning",
      name: "Morning Block",
      type: "DAILY",
      oneTimeDateEpochDay: -1,
      weekdaysMask: 0,
      startHour: 8,
      startMinute: 30,
      lockDurationMinutes: 45,
      enabled: true,
      syncAcrossDevices: true,
      originDeviceId: DEVICE_A,
      updatedAtWallMs: nowMs,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Per-device allowlists must NOT be writable on synced schedules
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_bad").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      scheduleId: "sched_bad",
      name: "Bad Schedule",
      type: "DAILY",
      oneTimeDateEpochDay: -1,
      weekdaysMask: 0,
      startHour: 8,
      startMinute: 30,
      lockDurationMinutes: 45,
      allowedPackages: ["com.secret.app"],
      enabled: true,
      syncAcrossDevices: true,
      originDeviceId: DEVICE_A,
      updatedAtWallMs: nowMs,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("presets").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("schedules").get());
});

test("Lock profiles, Flip Clock settings & Flip Timer presets: owner CRUD succeed; brightness field, invalid values & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const nowMs = Date.now();

  // Lock command with strictMode, showFlipClockShortcut, allowSelectedApps & phoneAccessEnabled succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lock_commands").doc("cmd_strict_1").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      commandId: "cmd_strict_1",
      originDeviceId: DEVICE_A,
      originDeviceName: "Pixel 8",
      mode: "INSTANT",
      freeUseDurationMinutes: 0,
      lockDurationMinutes: 30,
      issuedAtWallMs: nowMs,
      intendedStartWallMs: nowMs,
      freeUseDeadlineWallMs: 0,
      lockExpirationWallMs: nowMs + 30 * 60000,
      scheduleId: "",
      occurrenceId: "",
      strictMode: true,
      showFlipClockShortcut: true,
      allowSelectedApps: false,
      phoneAccessEnabled: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("lock_commands").doc("cmd_bad_type").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      commandId: "cmd_bad_type",
      originDeviceId: DEVICE_A,
      originDeviceName: "Pixel 8",
      mode: "INSTANT",
      freeUseDurationMinutes: 0,
      lockDurationMinutes: 30,
      issuedAtWallMs: nowMs,
      intendedStartWallMs: nowMs,
      freeUseDeadlineWallMs: 0,
      lockExpirationWallMs: nowMs + 30 * 60000,
      scheduleId: "",
      occurrenceId: "",
      strictMode: true,
      allowSelectedApps: "invalid_string",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Synced schedule with strictMode, allowSelectedApps & showFlipClockShortcut succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_strict").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      scheduleId: "sched_strict",
      name: "Strict Deep Block",
      type: "DAILY",
      oneTimeDateEpochDay: -1,
      weekdaysMask: 0,
      startHour: 9,
      startMinute: 0,
      lockDurationMinutes: 60,
      enabled: true,
      syncAcrossDevices: true,
      strictMode: true,
      allowSelectedApps: false,
      showFlipClockShortcut: false,
      originDeviceId: DEVICE_A,
      updatedAtWallMs: nowMs,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Lock profile CRUD
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lock_profiles").doc("INSTANT").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      profileMode: "INSTANT",
      strictMode: true,
      allowSelectedApps: false,
      showFlipClockShortcut: true,
      allowedPackages: ["com.example.docs"],
      originDeviceId: DEVICE_A,
      updatedAtWallMs: nowMs,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lock_profiles").doc("INSTANT").update({
      allowSelectedApps: true,
      updatedAtWallMs: nowMs + 1000,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("lock_profiles").doc("INVALID_MODE").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      profileMode: "INVALID_MODE",
      strictMode: false,
      allowSelectedApps: true,
      showFlipClockShortcut: false,
      allowedPackages: [],
      originDeviceId: DEVICE_A,
      updatedAtWallMs: nowMs,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("lock_profiles").get());

  // Flip Clock settings CRUD (never includes brightness)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync").doc("flip_clock_settings").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      docId: "flip_clock_settings",
      use24HourFormat: true,
      keepScreenAwake: true,
      showDate: true,
      autoRepeat: false,
      zoomFraction: 1.15,
      originDeviceId: DEVICE_A,
      updatedAtWallMs: nowMs,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync").doc("flip_clock_settings").update({
      autoRepeat: true,
      updatedAtWallMs: nowMs + 500,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  // Reject local brightness leak into cloud doc
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync").doc("flip_clock_settings").update({
      lastBrightnessPercent: 55,
      updatedAtWallMs: nowMs + 1000,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Flip Timer presets CRUD
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("flip_timer_presets").doc("flip_custom_focus").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      presetId: "flip_custom_focus",
      name: "Deep Sprint",
      durationSeconds: 2700,
      builtIn: false,
      sortOrder: 9,
      originDeviceId: DEVICE_A,
      updatedAtWallMs: nowMs,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("flip_timer_presets").doc("flip_custom_bad").set({
      userId: ALICE_UID,
      schemaVersion: 1,
      presetId: "flip_custom_bad",
      name: "Zero Sec",
      durationSeconds: 0,
      builtIn: false,
      sortOrder: 10,
      originDeviceId: DEVICE_A,
      updatedAtWallMs: nowMs,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("flip_timer_presets").get());
});

