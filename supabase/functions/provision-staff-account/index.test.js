import assert from "node:assert/strict";
import { test } from "node:test";
import { createStaffAccount } from "./index.js";

const config = { url: "https://project.example", serviceKey: "server-key" };
const profile = { email: "Kitchen@example.test", name: "Dapur", role: "kitchen", staff_id: 2, password: "Initial-test-password!" };
const request = (headers, body = profile) => new Request(`${config.url}/function`, {
  method: "POST", headers, body: JSON.stringify(body),
});
const userHeaders = { authorization: "Bearer user-session", apikey: "publishable" };

test("missing session stops before Auth Admin", async () => {
  assert.equal((await createStaffAccount(request({}), config)).status, 401);
});

test("role denial or missing migration stops before Auth Admin", async t => {
  for (const status of [403, 404]) {
    const fetch = t.mock.method(globalThis, "fetch", async url => {
      if (url.endsWith("/auth/v1/user")) return Response.json({ id: "actual-user" });
      assert.ok(url.endsWith("/rest/v1/rpc/rf_check_staff_account"));
      return Response.json({}, { status });
    });
    assert.equal((await createStaffAccount(request(userHeaders), config)).status, status === 403 ? 403 : 503);
    assert.equal(fetch.mock.callCount(), 2);
    fetch.mock.restore();
  }
});

test("a missing initial password never creates an Auth user", async t => {
  const fetch = t.mock.method(globalThis, "fetch", async () => Response.json({ id: "actual-user" }));
  const response = await createStaffAccount(request(userHeaders, { ...profile, password: "" }), config);
  assert.equal(response.status, 400);
  assert.equal(fetch.mock.callCount(), 1);
});

test("active admin calls Auth Admin with allowlisted metadata and verified actor", async t => {
  const fetch = t.mock.method(globalThis, "fetch", async (url, options) => {
    if (url.endsWith("/auth/v1/user")) return Response.json({ id: "actual-user" });
    if (url.endsWith("/rest/v1/rpc/rf_check_staff_account")) {
      assert.equal(options.headers.Authorization, "Bearer user-session");
      assert.deepEqual(JSON.parse(options.body), { p: { name: "Dapur", role: "kitchen", staff_id: 2 } });
      return Response.json(true);
    }
    assert.ok(url.endsWith("/auth/v1/admin/users"));
    assert.equal(options.headers.Authorization, "Bearer server-key");
    assert.deepEqual(JSON.parse(options.body), {
      email: "kitchen@example.test", password: profile.password, email_confirm: true,
      app_metadata: { restoflow_staff_account: { actor: "actual-user", name: "Dapur", role: "kitchen", staff_id: 2 } },
    });
    return Response.json({ id: "new-user" });
  });
  const response = await createStaffAccount(request(userHeaders, { ...profile, actor: "forged", email_confirm: false }), config);
  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), { id: "new-user" });
  assert.equal(fetch.mock.callCount(), 3);
});

test("supports creating new staff identity atomically when create_identity is true", async t => {
  const fetch = t.mock.method(globalThis, "fetch", async (url, options) => {
    if (url.endsWith("/auth/v1/user")) return Response.json({ id: "actual-user" });
    if (url.endsWith("/rest/v1/rpc/rf_check_staff_account")) {
      assert.deepEqual(JSON.parse(options.body), { p: { name: "Dapur Baru", role: "kitchen", create_identity: true } });
      return Response.json(true);
    }
    assert.ok(url.endsWith("/auth/v1/admin/users"));
    return Response.json({ id: "created-with-new-staff" });
  });
  const response = await createStaffAccount(request(userHeaders, {
    email: "newkitchen@example.test",
    name: "Dapur Baru",
    role: "kitchen",
    create_identity: true,
    password: "Initial-test-password!",
  }), config);
  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), { id: "created-with-new-staff" });
  assert.equal(fetch.mock.callCount(), 3);
});
