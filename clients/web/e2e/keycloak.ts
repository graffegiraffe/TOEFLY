import { createHmac } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// The local Keycloak of infra/compose; its admin password comes from the environment or the local .env (test stack only).
const KEYCLOAK = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8180';

function adminPassword(): string {
  if (process.env.KEYCLOAK_ADMIN_PASSWORD) return process.env.KEYCLOAK_ADMIN_PASSWORD;
  const env = readFileSync(fileURLToPath(new URL('../../../infra/compose/.env', import.meta.url)), 'utf8');
  const line = env.split('\n').find((l) => l.startsWith('KEYCLOAK_ADMIN_PASSWORD='));
  if (!line) throw new Error('KEYCLOAK_ADMIN_PASSWORD is not set');
  return line.slice('KEYCLOAK_ADMIN_PASSWORD='.length).trim();
}

async function adminToken(): Promise<string> {
  const response = await fetch(`${KEYCLOAK}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: 'admin-cli',
      username: 'admin',
      password: adminPassword(),
    }),
  });
  if (!response.ok) throw new Error(`Keycloak admin token: ${response.status}`);
  return ((await response.json()) as { access_token: string }).access_token;
}

/** Gives a realm role to a user of the realm `toefly`, as an administrator would in the Keycloak console. */
export async function grantRealmRole(email: string, role: string) {
  const token = await adminToken();
  const headers = { authorization: `Bearer ${token}`, 'content-type': 'application/json' };
  const base = `${KEYCLOAK}/admin/realms/toefly`;
  const users = (await (
    await fetch(`${base}/users?exact=true&email=${encodeURIComponent(email)}`, { headers })
  ).json()) as {
    id: string;
  }[];
  const roleRep = await (await fetch(`${base}/roles/${role}`, { headers })).json();
  const response = await fetch(`${base}/users/${users[0]!.id}/role-mappings/realm`, {
    method: 'POST',
    headers,
    body: JSON.stringify([roleRep]),
  });
  if (!response.ok) throw new Error(`role mapping: ${response.status}`);
}

/** RFC 6238 code (SHA-1, 6 digits, 30 s) for a base32 secret shown by the authenticator setup page. */
export function totp(secret: string, now = Date.now()): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const char of secret.replace(/[\s=]/g, '').toUpperCase()) {
    bits += alphabet.indexOf(char).toString(2).padStart(5, '0');
  }
  const key = Buffer.from(bits.match(/.{8}/g)!.map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(now / 30_000)));
  const hmac = createHmac('sha1', key).update(counter).digest();
  const offset = hmac[hmac.length - 1]! & 0xf;
  const code = (hmac.readUInt32BE(offset) & 0x7fffffff) % 1_000_000;
  return code.toString().padStart(6, '0');
}
