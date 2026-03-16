import { Injectable } from '@angular/core';

import { API_URLS } from '../../core/api-urls';

type EventHandler<TMessageEvent> = (event: TMessageEvent) => void;

export const isEmptyObject = (obj: unknown): boolean => Object.keys(obj ?? {}).length === 0;

@Injectable({
  providedIn: 'root',
})
export class EventSourceServerService<T, V> {
  get eventSourceId(): string {
    return this.#eventSourceId;
  }

  private readonly eventSource: EventSource;
  /* although the handling of the Types is not "clean" during runtime its clear at compiler time since one needs to inject
   * the proper type for the listing (which is a Literal Type)
   */
  readonly #eventListeners = new Map<T, EventHandler<V>[]>();
  // Native listener wrappers keyed by consumer — required for removeEventListener to work
  readonly #nativeListeners = new Map<EventHandler<V>, EventListener>();
  // ID for the EventSource for this stream
  readonly #eventSourceId: string = crypto.randomUUID();

  constructor() {
    this.eventSource = new EventSource(API_URLS.sse(this.#eventSourceId));
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
    this.eventSource.addEventListener(event as string, nativeListener);
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
          this.eventSource.removeEventListener(event as string, nativeListener);
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
