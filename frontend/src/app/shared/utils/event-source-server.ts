import { Injectable, inject, effect } from '@angular/core';

import { API_URLS } from '../../core/api-urls';
import { HealthService } from '../../core/health.service';

type EventHandler<TMessageEvent> = (event: TMessageEvent) => void;

export const isEmptyObject = (obj: unknown): boolean => Object.keys(obj ?? {}).length === 0;

@Injectable({
  providedIn: 'root',
})
export class EventSourceServerService<T, V> {
  readonly #health = inject(HealthService);

  get eventSourceId(): string {
    return this.#eventSourceId;
  }

  private eventSource!: EventSource;
  /* although the handling of the Types is not "clean" during runtime its clear at compiler time since one needs to inject
   * the proper type for the listing (which is a Literal Type)
   */
  readonly #eventListeners = new Map<T, EventHandler<V>[]>();
  // Native listener wrappers keyed by consumer — required for removeEventListener to work
  readonly #nativeListeners = new Map<EventHandler<V>, EventListener>();
  // ID for the EventSource for this stream
  readonly #eventSourceId: string = crypto.randomUUID();
  #serverToken: string | null = null;
  #stalenessTimer: ReturnType<typeof setTimeout> | null = null;
  #wasConnectionLost = false;

  constructor() {
    this.#connect();
    // When health is restored after a loss, reconnect SSE immediately
    // rather than waiting for the browser's own EventSource retry timer.
    effect(() => {
      const lost = this.#health.connectionLost();
      if (lost) {
        this.#wasConnectionLost = true;
      } else if (this.#wasConnectionLost) {
        this.#wasConnectionLost = false;
        this.eventSource.close();
        this.#connect();
      }
    });
  }

  #connect(): void {
    if (this.#stalenessTimer !== null) {
      clearTimeout(this.#stalenessTimer);
      this.#stalenessTimer = null;
    }
    this.eventSource = new EventSource(API_URLS.sse(this.#eventSourceId));
    this.eventSource.addEventListener('server-hello', (e: Event) => this.#handleServerToken(e));
    this.eventSource.addEventListener('keepalive', (e: Event) => this.#handleServerToken(e));
    this.eventSource.onerror = () => this.#health.notifyServerError();
    // Re-attach consumer listeners onto the new EventSource
    for (const [event, handlers] of this.#eventListeners) {
      for (const handler of handlers) {
        const native = this.#nativeListeners.get(handler);
        if (native) this.#attachNative(event, native);
      }
    }
  }

  #attachNative(event: T, native: EventListener): void {
    this.eventSource.addEventListener(event as string, native);
  }

  #handleServerToken(event: Event): void {
    const token = (event as MessageEvent<string>).data?.trim();
    if (!token) return;
    if (this.#serverToken === null) {
      this.#serverToken = token;
    } else if (this.#serverToken !== token) {
      // Server restarted mid-session — accept new token and notify app.
      this.#serverToken = token;
      this.#health.notifyServerRestart();
    }
    // Reset stale-connection watchdog: reconnect if no keepalive within 40 s.
    // The reconnect triggers a new server-hello; only then reload if the token changed.
    if (this.#stalenessTimer !== null) clearTimeout(this.#stalenessTimer);
    this.#stalenessTimer = setTimeout(() => {
      this.eventSource.close();
      this.#connect();
    }, 40_000);
  }

  addEventListener(event: T, consumer: EventHandler<V>): void {
    const handlers = this.#eventListeners.get(event);
    if (handlers && handlers.length > 0) {
      if (handlers.includes(consumer)) {
        console.error('Consumer already listing', event);
        return;
      }
      handlers.push(consumer);
    } else {
      this.#eventListeners.set(event, [consumer]);
    }

    const nativeListener: EventListener = (messageEvent: Event) => {
      const me = messageEvent as MessageEvent<string>;
      let ret = me.data as unknown as V;
      if (!isEmptyObject(ret)) {
        try {
          ret = JSON.parse(me.data) as V;
        } catch {
          // nothing to be done since the default is already set
        }
      }
      consumer(ret);
    };
    this.#nativeListeners.set(consumer, nativeListener);
    this.#attachNative(event, nativeListener);
  }

  deleteEventListener(event: T, consumer: EventHandler<V>): void {
    const handlers = this.#eventListeners.get(event);
    if (handlers && handlers.length > 0) {
      const idx = handlers.findIndex((chk) => chk === consumer);
      if (idx >= 0) {
        if (handlers.length === 1) {
          // remove the only EventListener we have
          this.#eventListeners.delete(event);
        } else {
          handlers.splice(idx, 1);
        }
        const nativeListener = this.#nativeListeners.get(consumer);
        if (nativeListener) {
          this.eventSource.removeEventListener(event as string, nativeListener); // no #detachNative needed — only called when intentionally unsubscribing
          this.#nativeListeners.delete(consumer);
        }
      } else {
        console.error(`Consumer for ${event as string} not found`);
      }
    } else {
      console.error('No consumer defined for event', event);
    }
  }
}
