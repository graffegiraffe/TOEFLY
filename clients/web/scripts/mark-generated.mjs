// The OpenAPI client is generated code: it is excluded from type checks of our strict rules.
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const MARK = '// @ts-nocheck\n';

function mark(directory) {
  for (const name of readdirSync(directory)) {
    const path = join(directory, name);
    if (statSync(path).isDirectory()) {
      mark(path);
    } else if (path.endsWith('.ts')) {
      const source = readFileSync(path, 'utf8');
      if (!source.startsWith(MARK)) {
        writeFileSync(path, MARK + source);
      }
    }
  }
}

mark(new URL('../src/api/generated', import.meta.url).pathname);
