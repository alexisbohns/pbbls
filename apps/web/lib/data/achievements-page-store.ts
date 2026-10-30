import {
  achievementsCacheEpoch,
  readAchievementsCache,
  writeAchievementsCache,
  type CacheStorage,
} from "@/lib/data/achievements-cache"
import { decodeAchievementsPage } from "@/lib/data/achievements-payload"
import { CeremonyNotReadyError, type DataProvider } from "@/lib/data/data-provider"
import type { AchievementCard, AchievementCompletion, AchievementsPage, CeremonyStack } from "@/lib/types"

// ---------------------------------------------------------------------------
// The achievements page's state (#1308, spec §4), framework-free so it is
// tested in node; `useAchievementsPage` subscribes to it. The web's twin of
// iOS's AchievementsModel (Android: AchievementsViewModel).
//
// - Stale-while-revalidate: the user's kept copy shows at once, the fresh read
//   replaces it without a blink, and a failed read keeps what is shown (and
//   logs). Only a first visit with nothing kept and no answer is `failed`.
// - Reads can overlap (the page's load and a pull's re-read), so each takes a
//   sequence number, and only a read newer than the one shown replaces it.
// - The store belongs to one user and one zone (the hook makes a new one when
//   either changes). `close()` aborts its reads: a read that lands after it is
//   dropped, never written to the cache, and its cancellation is no error.
// - The ceremony pull is decided on a page read in this session, never on the
//   kept copy (spec §3.4).
// ---------------------------------------------------------------------------

export type AchievementsPageProvider = Pick<
  DataProvider,
  "checkAchievements" | "getAchievementsPayload" | "markAchievementsSeen" | "pullCeremony"
>

export type AchievementsPageState = {
  page: AchievementsPage | null
  loading: boolean
  /** Nothing to show: no kept copy, and the read failed. */
  failed: boolean
  /** The stack whose ceremony is being pulled, so its mark cannot fire twice. */
  pulling: CeremonyStack | null
}

export type CeremonyPullOutcome =
  /** A row came back: the ceremony, with the karma the pull paid (0 included). */
  | { kind: "pulled"; karmaGranted: number }
  /**
   * No row, or no answer, but the read after it shows the completion pulled,
   * which this session read unpulled: this pull landed and its answer was
   * lost. The ceremony plays without a karma line.
   */
  | { kind: "pulledWithoutKarma" }
  /** Already pulled (this session's read shows `pulled_at`), or no row and no `pulled_at` after. Never success. */
  | { kind: "alreadyPulled" }
  /** Not offered yet, or no completion at all (the read, or `not_ready`). */
  | { kind: "notReady" }
  /** Another pull is running; this one did nothing. */
  | { kind: "busy" }
  | { kind: "failed" }

/** The completion a ceremony stack pulls, wherever its card carries it. */
export function completionOf(page: AchievementsPage | null, stack: CeremonyStack): AchievementCompletion | null {
  if (!page) return null
  if (stack === "six_firsts") return page.explorer?.completion ?? null
  if (stack === "emotions") return page.emotions?.completion ?? null
  return page.domains?.completion ?? null
}

type Options = {
  provider: AchievementsPageProvider
  /** The signed-in user the store reads for; its cache entry is theirs. */
  owner: string
  /** The reader's IANA zone, sent with every read and pull. */
  zone: string
  storage: CacheStorage | null
}

export class AchievementsPageStore {
  private state: AchievementsPageState = { page: null, loading: false, failed: false, pulling: null }
  private readonly listeners = new Set<() => void>()
  /** The open session's signal; null while closed. */
  private controller: AbortController | null = null
  /** The session whose load is running, so a reopened store can load again. */
  private loadingFor: AbortSignal | null = null
  /** The last read asked for, and the newest one whose page is shown. */
  private readsAsked = 0
  private readShown = 0

  constructor(private readonly options: Options) {}

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener)
    return () => {
      this.listeners.delete(listener)
    }
  }

  readonly getSnapshot = (): AchievementsPageState => this.state

  /** Starts a session and loads the page. Idempotent while open. */
  open(): Promise<void> {
    if (this.controller) return Promise.resolve()
    this.controller = new AbortController()
    return this.load()
  }

  /** Ends the session: reads in flight are aborted and their answers dropped. */
  close(): void {
    this.controller?.abort()
    this.controller = null
    if (this.loadingFor) {
      this.loadingFor = null
      this.set({ loading: false })
    }
  }

  /**
   * The kept copy when nothing is shown yet, then `check_achievements()` (the
   * retroactive grant, never celebrated here), then the read (D14). A failed
   * check is logged and the read still runs: the next check self-heals.
   */
  async load(): Promise<void> {
    const signal = this.controller?.signal
    if (!signal || this.loadingFor === signal) return
    this.loadingFor = signal
    this.set({ loading: true })
    try {
      if (this.state.page === null && this.readShown === 0) {
        const kept = readAchievementsCache(this.options.storage, this.options.owner, this.options.zone)
        if (kept) this.set({ page: kept })
      }
      try {
        await this.options.provider.checkAchievements()
      } catch (err) {
        console.warn("[achievements] check_achievements failed", err)
      }
      if (signal.aborted) return
      await this.refresh(signal)
    } finally {
      if (this.loadingFor === signal) {
        this.loadingFor = null
        this.set({ loading: false })
      }
    }
  }

  /** Fire-and-forget (spec §4): a lost mark only keeps a "new" mark one visit longer. */
  markSeen(card: AchievementCard): Promise<void> {
    return this.options.provider.markAchievementsSeen(card).catch((err: unknown) => {
      console.warn("[achievements] mark_achievements_seen failed", card, err)
    })
  }

  /**
   * Pulls a ready ceremony (D9), then reads the page again so its mark goes.
   * Before any read has landed in this session it reads first. Only a
   * completion that read shows ready and unpulled is pulled; one already
   * pulled, or not offered, settles without a call. An empty or lost answer
   * is settled by the read after it (spec §3.4).
   */
  async pull(stack: CeremonyStack): Promise<CeremonyPullOutcome> {
    if (this.state.pulling) {
      console.info(`[achievements] pull_ceremony(${stack}) asked while ${this.state.pulling} is pulling`)
      return { kind: "busy" }
    }
    const signal = this.controller?.signal
    if (!signal) {
      console.error(`[achievements] pull_ceremony(${stack}) asked with the page closed`)
      return { kind: "failed" }
    }
    this.set({ pulling: stack })
    try {
      if (this.readShown === 0) await this.refresh(signal)
      if (this.readShown === 0) {
        console.error(`[achievements] pull_ceremony(${stack}) not asked: no page read to decide on`)
        return { kind: "failed" }
      }
      const before = completionOf(this.state.page, stack)
      if (before?.pulledAt) return { kind: "alreadyPulled" }
      if (!before?.ready) return { kind: "notReady" }

      let outcome: CeremonyPullOutcome
      try {
        const row = await this.options.provider.pullCeremony(stack, this.options.zone)
        outcome = row ? { kind: "pulled", karmaGranted: row.karmaGranted } : { kind: "alreadyPulled" }
      } catch (err) {
        if (err instanceof CeremonyNotReadyError) {
          console.warn("[achievements] pull_ceremony is not ready", err)
          outcome = { kind: "notReady" }
        } else {
          console.error("[achievements] pull_ceremony failed", err)
          outcome = { kind: "failed" }
        }
      }
      const reread = await this.refresh(signal)
      if (outcome.kind === "alreadyPulled" || outcome.kind === "failed") {
        if (reread && completionOf(this.state.page, stack)?.pulledAt) {
          outcome = { kind: "pulledWithoutKarma" }
        } else if (outcome.kind === "alreadyPulled") {
          console.error(`[achievements] pull_ceremony(${stack}) returned no row, and the read does not show it pulled`)
        }
      }
      return outcome
    } finally {
      this.set({ pulling: null })
    }
  }

  /** Reads the page; true when the read landed. A read older than the page shown is dropped. */
  private async refresh(signal: AbortSignal): Promise<boolean> {
    this.readsAsked += 1
    const read = this.readsAsked
    const epoch = achievementsCacheEpoch()
    try {
      const payload = await this.options.provider.getAchievementsPayload(this.options.zone, signal)
      if (signal.aborted) return false
      const decoded = decodeAchievementsPage(payload)
      if (!decoded) throw new Error("get_achievements returned no page")
      if (decoded.skipped.length > 0) {
        console.error("[achievements] get_achievements sections dropped", decoded.skipped)
      }
      if (read > this.readShown) {
        this.readShown = read
        writeAchievementsCache(this.options.storage, this.options.owner, this.options.zone, payload, epoch)
        this.set({ page: decoded.page, failed: false })
      }
      return true
    } catch (err) {
      // A read the page gave up on is neither a failure to show nor to log.
      if (signal.aborted) return false
      console.error("[achievements] get_achievements failed", err)
      this.set({ failed: this.state.page === null })
      return false
    }
  }

  private set(patch: Partial<AchievementsPageState>): void {
    this.state = { ...this.state, ...patch }
    for (const listener of this.listeners) listener()
  }
}
