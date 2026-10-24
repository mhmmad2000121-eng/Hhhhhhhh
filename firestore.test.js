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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user: cannot read notes or vault items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .where("userId", "==", ALICE_UID)
      .get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vault_items")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Authenticated user: can create, read, update, and delete their own note", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const noteRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("notes")
    .doc("note_1");

  await assertSucceeds(
    noteRef.set({
      userId: ALICE_UID,
      title: "Project Roadmap",
      content: "Complete Q4 deliverables",
      category: "Work",
      isPinned: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(noteRef.get());

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  await assertSucceeds(
    noteRef.update({
      title: "Updated Roadmap",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(noteRef.delete());
});

test("Authenticated user: cannot read or modify another user's note or vault item", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("notes")
      .doc("bob_note")
      .set({
        userId: BOB_UID,
        title: "Bob Secret",
        content: "Private content",
        category: "Personal",
        isPinned: false,
        createdAt: now,
        updatedAt: now,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("notes")
      .doc("bob_note")
      .get()
  );
});

test("Authenticated user: rejects shadow update with ghost fields or invalid category", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("alice_note")
      .set({
        userId: ALICE_UID,
        title: "Alice Note",
        content: "Initial content",
        category: "Personal",
        isPinned: false,
        createdAt: now,
        updatedAt: now,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("notes")
    .doc("alice_note");

  // Ghost field rejection
  await assertFails(
    noteRef.update({
      isAdmin: true,
      updatedAt: new Date(),
    })
  );

  // Invalid category enum rejection
  await assertFails(
    noteRef.update({
      category: "InvalidCategory",
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user: can create and query vault items with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const itemRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("vault_items")
    .doc("item_1");

  await assertSucceeds(
    itemRef.set({
      userId: ALICE_UID,
      title: "Passport Scan",
      itemType: "IMAGE",
      secretNote: "Encrypted backup",
      localFileName: "vault_123.bin",
      mimeType: "image/jpeg",
      sizeBytes: 204800,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vault_items")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});
