import { setupZoneTestEnv } from 'jest-preset-angular/setup-env/zone';

setupZoneTestEnv();

// jsdom does not ship ReadableStream for the SSE tests; expose the Node web
// streams implementation instead. TextEncoder/TextDecoder come from util.
import { ReadableStream } from 'node:stream/web';
import { TextEncoder, TextDecoder } from 'node:util';

(globalThis as Record<string, unknown>).ReadableStream = ReadableStream;
(globalThis as Record<string, unknown>).TextEncoder = TextEncoder;
(globalThis as Record<string, unknown>).TextDecoder = TextDecoder;
