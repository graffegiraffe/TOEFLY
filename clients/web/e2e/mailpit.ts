const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025';

interface MessageSummary {
  ID: string;
}

/** Waits for the first message to the address and returns the Keycloak action link in it. */
export async function actionLinkFor(address: string, timeoutMs = 20_000): Promise<string> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const search = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${address}`)}`);
    const { messages } = (await search.json()) as { messages: MessageSummary[] };
    const first = messages[0];
    if (first) {
      const message = (await (await fetch(`${MAILPIT}/api/v1/message/${first.ID}`)).json()) as { Text: string };
      const link = message.Text.match(/https?:\/\/\S+action-token\S+/)?.[0];
      if (link) {
        return link;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No email for ${address}`);
}
