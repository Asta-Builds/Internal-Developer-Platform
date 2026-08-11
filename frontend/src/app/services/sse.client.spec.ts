import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SseClient, SseMessage } from './sse.client';
import { KeycloakService } from './keycloak.service';

describe('SseClient', () => {
  let sseClient: SseClient;

  const mockStream = (body: ReadableStream<Uint8Array> | null, status = 200) => {
    global.fetch = jest.fn().mockResolvedValue({
      status,
      ok: status >= 200 && status < 300,
      body,
    } as Response);
  };

  let encoder: TextEncoder;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SseClient,
        KeycloakService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    encoder = new TextEncoder();
    sseClient = TestBed.inject(SseClient);
    TestBed.inject(KeycloakService).currentUserSignal.set({
      username: 'alice',
      email: 'a@x.io',
      roles: [],
      token: 'tok',
      isAuthenticated: true,
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('parses a single SSE frame with an event name and data', done => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: JOB_PROGRESS\ndata: {"progress": 10}\n\n'));
        controller.close();
      },
    });
    mockStream(stream);

    sseClient.stream('/api/v1/stream').subscribe({
      next: (message: SseMessage) => {
        expect(message.event).toBe('JOB_PROGRESS');
        expect(message.data).toBe('{"progress": 10}');
        done();
      },
    });
  });

  it('concatenates repeated data lines and defaults the event name', done => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('data: line1\ndata: line2\n\n'));
        controller.close();
      },
    });
    mockStream(stream);

    sseClient.stream('/stream').subscribe({
      next: (message: SseMessage) => {
        expect(message.event).toBe('message');
        expect(message.data).toBe('line1\nline2');
        done();
      },
    });
  });

  it('buffers a partial frame across chunks', done => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: A\ndata: part1'));
        controller.enqueue(encoder.encode('\n\n'));
        controller.close();
      },
    });
    mockStream(stream);

    sseClient.stream('/stream').subscribe({
      next: (message: SseMessage) => {
        expect(message.event).toBe('A');
        expect(message.data).toBe('part1');
        done();
      },
    });
  });

  it('attaches the bearer token and Accept header', done => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('data: x\n\n'));
        controller.close();
      },
    });
    mockStream(stream);

    sseClient.stream('http://localhost:8088/api/v1/telemetry/logs/stream').subscribe(() => {
      const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
      expect(url).toBe('http://localhost:8088/api/v1/telemetry/logs/stream');
      expect(init.headers).toEqual({
        Accept: 'text/event-stream',
        Authorization: 'Bearer tok',
      });
      done();
    });
  });

  it('errors when the stream is refused with 403', done => {
    mockStream(null, 403);

    sseClient.stream('/stream').subscribe({
      error: (error: Error) => {
        expect(error.message).toContain('authorization policy');
        done();
      },
    });
  });

  it('ignores comment and keep-alive lines', done => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode(': keep-alive\n\n'));
        controller.enqueue(encoder.encode('data: real\n\n'));
        controller.close();
      },
    });
    mockStream(stream);

    const messages: SseMessage[] = [];
    sseClient.stream('/stream').subscribe({
      next: message => messages.push(message),
      complete: () => {
        expect(messages).toHaveLength(1);
        expect(messages[0].data).toBe('real');
        done();
      },
    });
  });
});
