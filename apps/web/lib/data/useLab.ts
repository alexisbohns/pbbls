"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import type { SupabaseClient } from "@supabase/supabase-js"
import { createClient } from "@/lib/supabase/client"
import { useAuth } from "@/lib/data/auth-context"
import {
  fetchAnnouncement,
  fetchAnnouncements,
  fetchBacklog,
  fetchChangelog,
  fetchInitiatives,
  fetchMyReactions,
  react,
  unreact,
} from "@/lib/data/logs-api"
import type { Log } from "@/lib/types"
import { LAB_CONFIG } from "@/lib/config/lab"

// Lazy module-level singleton — `createClient()` reads env vars and must
// not run during SSR/static prerender (where they're absent). Mirrors the
// pattern in `useSupabaseAuth.ts`.
let supabaseInstance: SupabaseClient | null = null
function getSupabase(): SupabaseClient | null {
  if (typeof window === "undefined") return null
  if (!supabaseInstance) supabaseInstance = createClient()
  return supabaseInstance
}

function adjustCount(logs: Log[], id: string, delta: number): Log[] {
  return logs.map((log) =>
    log.id === id
      ? { ...log, reaction_count: Math.max(0, log.reaction_count + delta) }
      : log,
  )
}

function toggleSet(set: Set<string>, id: string, add: boolean): Set<string> {
  const next = new Set(set)
  if (add) next.add(id)
  else next.delete(id)
  return next
}

// ---------------------------------------------------------------------------
// useLabFeed — drives the /lab page (4 sections + my reactions).
// ---------------------------------------------------------------------------

export function useLabFeed() {
  const { user } = useAuth()
  const userId = user?.id ?? null

  const [announcements, setAnnouncements] = useState<Log[]>([])
  const [changelog, setChangelog] = useState<Log[]>([])
  const [initiatives, setInitiatives] = useState<Log[]>([])
  const [backlog, setBacklog] = useState<Log[]>([])
  const [reactedIds, setReactedIds] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  const activeUserIdRef = useRef<string | null>(null)

  useEffect(() => {
    let cancelled = false
    activeUserIdRef.current = userId

    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      const supabase = getSupabase()
      if (!supabase) return
      setLoading(true)
      setError(null)
      try {
        const [ann, chg, init, back, reactions] = await Promise.all([
          fetchAnnouncements(supabase),
          fetchChangelog(supabase, { limit: LAB_CONFIG.feedLimit }),
          fetchInitiatives(supabase),
          fetchBacklog(supabase, { limit: LAB_CONFIG.feedLimit }),
          fetchMyReactions(supabase, userId),
        ])
        if (cancelled || activeUserIdRef.current !== userId) return
        setAnnouncements(ann)
        setChangelog(chg)
        setInitiatives(init)
        setBacklog(back)
        setReactedIds(reactions)
        setLoading(false)
      } catch (err) {
        if (cancelled) return
        console.error("[useLabFeed] failed to load", err)
        setError(err instanceof Error ? err : new Error("Failed to load Lab"))
        setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [userId])

  const toggleReaction = useCallback(
    async (logId: string) => {
      if (!userId) return
      const supabase = getSupabase()
      if (!supabase) return
      const wasReacted = reactedIds.has(logId)
      setReactedIds((prev) => toggleSet(prev, logId, !wasReacted))
      setBacklog((prev) => adjustCount(prev, logId, wasReacted ? -1 : 1))

      try {
        if (wasReacted) await unreact(supabase, userId, logId)
        else await react(supabase, userId, logId)
      } catch (err) {
        console.error("[useLabFeed] reaction toggle failed", err)
        setReactedIds((prev) => toggleSet(prev, logId, wasReacted))
        setBacklog((prev) => adjustCount(prev, logId, wasReacted ? 1 : -1))
      }
    },
    [userId, reactedIds],
  )

  return {
    announcements,
    changelog,
    initiatives,
    backlog,
    reactedIds,
    loading,
    error,
    toggleReaction,
  }
}

// ---------------------------------------------------------------------------
// useLogList — drives /lab/changelog and /lab/backlog (full lists).
// ---------------------------------------------------------------------------

export type LogListMode = "changelog" | "backlog"

export function useLogList(mode: LogListMode) {
  const { user } = useAuth()
  const userId = user?.id ?? null

  const [logs, setLogs] = useState<Log[]>([])
  const [reactedIds, setReactedIds] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let cancelled = false
    const fetchLogs = mode === "changelog" ? fetchChangelog : fetchBacklog

    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      const supabase = getSupabase()
      if (!supabase) return
      setLoading(true)
      setError(null)
      try {
        const [list, reactions] = await Promise.all([
          fetchLogs(supabase),
          fetchMyReactions(supabase, userId),
        ])
        if (cancelled) return
        setLogs(list)
        setReactedIds(reactions)
        setLoading(false)
      } catch (err) {
        if (cancelled) return
        console.error(`[useLogList:${mode}] failed to load`, err)
        setError(err instanceof Error ? err : new Error("Failed to load list"))
        setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [userId, mode])

  const toggleReaction = useCallback(
    async (logId: string) => {
      if (!userId) return
      const supabase = getSupabase()
      if (!supabase) return
      const wasReacted = reactedIds.has(logId)
      setReactedIds((prev) => toggleSet(prev, logId, !wasReacted))
      setLogs((prev) => adjustCount(prev, logId, wasReacted ? -1 : 1))

      try {
        if (wasReacted) await unreact(supabase, userId, logId)
        else await react(supabase, userId, logId)
      } catch (err) {
        console.error(`[useLogList:${mode}] reaction toggle failed`, err)
        setReactedIds((prev) => toggleSet(prev, logId, wasReacted))
        setLogs((prev) => adjustCount(prev, logId, wasReacted ? 1 : -1))
      }
    },
    [userId, mode, reactedIds],
  )

  return { logs, reactedIds, loading, error, toggleReaction }
}

// ---------------------------------------------------------------------------
// useAnnouncement — drives /lab/announcements/[id].
// ---------------------------------------------------------------------------

export function useAnnouncement(id: string) {
  const [log, setLog] = useState<Log | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let cancelled = false

    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      const supabase = getSupabase()
      if (!supabase) return
      setLoading(true)
      setError(null)
      try {
        const found = await fetchAnnouncement(supabase, id)
        if (cancelled) return
        setLog(found)
        setLoading(false)
      } catch (err) {
        if (cancelled) return
        console.error("[useAnnouncement] failed to load", err)
        setError(err instanceof Error ? err : new Error("Failed to load announcement"))
        setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [id])

  return { log, loading, error }
}
