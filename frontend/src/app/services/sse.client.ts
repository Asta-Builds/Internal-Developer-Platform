import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { KeycloakService } from './keycloak.service';

export interface SseMessage {
  /** Value of the SSE `event:` field, or `message` when the server omits it. */
  event: string;
  /** Raw payload from the `data:` field(s). */
  data: string;
}

/**
 * Server-sent events over `fetch`, so the platform bearer token can be attached.
 *
 * <p>The browser's native `EventSource` cannot set request headers, which makes it
 * unusable against authenticated endpoints. Passing the token as a query parameter
 * would work, but tokens in URLs end up in access logs, proxy logs and browser
 * history — so the stream is read manually instead.
 */
@Injectable({ providedIn: 'root' })
export class SseClient {

  private keycloak = inject(KeycloakService);

  /**
   * Opens an authenticated SSE stream. Unsubscribing aborts the underlying request.
   */
  stream(url: string): Observable<SseMessage> {
    return new Observable<SseMessage>(observer => {
      const controller = new AbortController();
      const token = this.keycloak.currentUserSignal().token;

      const headers: Record<string, string> = { Accept: 'text/event-stream' };
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      fetch(url, { headers, signal: controller.signal })
        .then(async response => {
          if (response.status === 401 || response.status === 403) {
            throw new Error(`Stream refused by the authorization policy (${response.status})`);
          }
          if (!response.ok || !response.body) {
            throw new Error(`Stream failed with HTTP ${response.status}`);
          }

          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';

          while (true) {
            const { done, value } = await reader.read();
            if (done) {
              break;
            }
            buffer += decoder.decode(value, { stream: true });

            // Frames are separated by a blank line; keep any partial tail buffered.
            const frames = buffer.split(/\r?\n\r?\n/);
            buffer = frames.pop() ?? '';

            for (const frame of frames) {
              const message = this.parseFrame(frame);
              if (message) {
                observer.next(message);
              }
            }
          }
          observer.complete();
        })
        .catch(error => {
          // An abort is a normal unsubscribe, not a stream failure.
          if (error?.name !== 'AbortError') {
            observer.error(error);
          }
        });

      return () => controller.abort();
    });
  }

  /** Parses one SSE frame, concatenating repeated `data:` lines as the spec requires. */
  private parseFrame(frame: string): SseMessage | null {
    let event = 'message';
    const dataLines: string[] = [];

    for (const line of frame.split(/\r?\n/)) {
      if (!line || line.startsWith(':')) {
        continue; // Blank line or comment/keep-alive.
      }
      const colon = line.indexOf(':');
      const field = colon < 0 ? line : line.slice(0, colon);
      // A single leading space after the colon is part of the framing, not the value.
      const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '');

      if (field === 'event') {
        event = value;
      } else if (field === 'data') {
        dataLines.push(value);
      }
    }

    return dataLines.length > 0 ? { event, data: dataLines.join('\n') } : null;
  }
}
