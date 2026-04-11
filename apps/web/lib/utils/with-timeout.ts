/**
 * Wraps a promise with a timeout. If the promise doesn't resolve or reject
 * within `ms` milliseconds, the returned promise rejects with a descriptive
 * error. Use this on any async operation that blocks rendering or user
 * interaction (auth calls, database queries, etc.) to prevent silent hangs.
 */
export function withTimeout<T>(
  promise: Promise<T> | PromiseLike<T>,
  ms: number,
  label: string,
): Promise<T> {
  return Promise.race([
    Promise.resolve(promise),
    new Promise<never>((_, reject) =>
      setTimeout(
        () => reject(new Error(`[timeout] ${label} did not respond after ${ms}ms`)),
        ms,
      ),
    ),
  ])
}
