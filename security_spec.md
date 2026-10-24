# Security Specification (`security_spec.md`)

## 1. Data Invariants
1. **Strict Multi-Tenant Isolation**: Every document under `/users/{userId}/notes/{noteId}` and `/users/{userId}/vault_items/{itemId}` belongs exclusively to `userId == request.auth.uid`, and its `userId` field must equal `request.auth.uid`.
2. **Schema Completeness & Ghost-Field Rejection**: Documents must contain all required fields and only allowlisted fields via `hasAll` and `hasOnly`.
3. **Immutable Identity & Creation Timestamp**: On `update`, `userId` and `createdAt` must strictly equal `resource.data.userId` and `resource.data.createdAt`.
4. **Server Timestamp Bounds**: `createdAt` and `updatedAt` must be valid Firestore `timestamp` objects `<= request.time`.
5. **Query Alignment**: All `list` queries must filter by `userId == request.auth.uid`.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Read**: `unauthDb.collection("users/alice/notes").where("userId", "==", "alice").get()` -> Reject
2. **Cross-User Read**: `aliceDb.doc("users/bob/notes/note_1").get()` -> Reject
3. **Unfiltered List Query**: `aliceDb.collection("users/alice/notes").get()` (missing `.where("userId", "==", "alice")`) -> Reject
4. **Identity Spoofing on Create**: `aliceDb.doc("users/alice/notes/n1").set({ userId: "bob", ... })` -> Reject
5. **Shadow Update / Ghost Field Injection**: `aliceDb.doc("users/alice/notes/n1").update({ isAdmin: true })` -> Reject
6. **Immutable `createdAt` Tampering**: `aliceDb.doc("users/alice/notes/n1").update({ createdAt: newTimestamp })` -> Reject
7. **Immutable `userId` Tampering**: `aliceDb.doc("users/alice/notes/n1").update({ userId: "bob" })` -> Reject
8. **Invalid Category Enum**: `aliceDb.doc("users/alice/notes/n1").set({ category: "Hacked", ... })` -> Reject
9. **Oversized Title Boundary Attack**: `aliceDb.doc("users/alice/notes/n1").set({ title: "A".repeat(300), ... })` -> Reject
10. **Future Timestamp Injection**: `aliceDb.doc("users/alice/notes/n1").set({ createdAt: futureTimestamp, ... })` -> Reject
11. **Negative SizeBytes in VaultItem**: `aliceDb.doc("users/alice/vault_items/v1").set({ sizeBytes: -10, ... })` -> Reject
12. **Invalid ItemType Enum in VaultItem**: `aliceDb.doc("users/alice/vault_items/v1").set({ itemType: "EXECUTABLE", ... })` -> Reject
