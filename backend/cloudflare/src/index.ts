/**
 * BitChord Listen Together on Cloudflare Workers — the same party server as `backend/` (Go),
 * speaking the same protocol, for free hosting with no card.
 *
 * One Durable Object per party, named by its code: it holds the members and the playback state
 * and every socket of the party, so a party lives in one place and needs no locks. Sockets use the
 * hibernation API, so an idle party costs nothing; what it holds is kept in its storage, since a
 * hibernated object forgets its memory.
 *
 * Timing is as the Go server's (see backend/README.md): the server holds a position and the server
 * time it was true at, devices work out "where now" themselves, and the truth is re-stated on a
 * heartbeat.
 */

import { DurableObject } from "cloudflare:workers";

export interface Env {
  PARTY: DurableObjectNamespace<PartyRoom>;
  REGISTRY: DurableObjectNamespace<Registry>;
}

// ---- Configuration (the Go server's defaults) ---------------------------------------------------

const STATE_HEARTBEAT_MS = 5_000;
const PLAY_LEAD_MS = 350;
const DISCONNECT_GRACE_MS = 45_000;
const EMPTY_PARTY_TTL_MS = 120_000;
const PARTY_MAX_AGE_MS = 12 * 60 * 60 * 1000;
const CONTROL_RATE_PER_SECOND = 25;
const FRAME_RATE_PER_SECOND = 30;
const MAX_UPCOMING_QUEUE = 25;
const MAX_QUEUE_LENGTH = 1 + MAX_UPCOMING_QUEUE;
const CREATE_RATE_PER_MINUTE = 2;
const REQUEST_MAX_BYTES = 16 * 1024;
const WEBSOCKET_MAX_BYTES = 16 * 1024;
const CONNECTION_IDLE_MS = 15 * 60 * 1000;
const DEFAULT_MAX_MEMBERS = 5;
/** How often an empty party looks at whether it is time to go. */
const SWEEP_MS = 15_000;

const SOCKET_PROTOCOL = "bitchord.v1";
const SOCKET_TOKEN_PREFIX = "bitchord.token.";

const CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTUVWXYZ";
const CODE_LENGTH = 6;

const CONTROL_ACTIONS = new Set([
  "play", "pause", "seek", "setTrack", "setQueue", "queueAdd", "queueRemove", "queueClear",
  "queueMove", "next", "previous", "setAutoplay",
]);

// ---- Model ---------------------------------------------------------------------------------------

interface Track {
  videoId: string;
  title: string;
  artist: string;
  thumbnailUrl?: string;
  durationMs?: number;
  fromAutoplay: boolean;
}

interface Playback {
  track: Track | null;
  queue: Track[];
  queueIndex: number;
  isPlaying: boolean;
  positionMs: number;
  anchorMs: number;
  seq: number;
  queueSeq: number;
  updatedBy: string | null;
  updatedAtMs: number;
  startedBy: string | null;
  startedByName: string | null;
  autoplayEnabled: boolean;
}

interface Member {
  memberId: string;
  userId: string;
  deviceId: string;
  displayName: string;
  avatarUrl: string | null;
  token: string;
  isHost: boolean;
  connected: boolean;
  joinedAtMs: number;
  lastSeenMs: number;
}

interface PartyState {
  code: string;
  members: Record<string, Member>;
  playback: Playback;
  maxMembers: number;
  hostOnlyControl: boolean;
  createdAtMs: number;
  touchedAtMs: number;
  emptySinceMs: number | null;
}

interface JoinRequest {
  userId: string;
  deviceId: string;
  displayName: string;
  avatarUrl: string | null;
  maxMembers?: number;
  autoplayEnabled?: boolean;
}

class PartyError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

type Json = Record<string, unknown>;

// ---- Helpers -------------------------------------------------------------------------------------

const now = () => Date.now();

function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

function jsonError(status: number, code: string, message: string): Response {
  return json(status, { error: code, message });
}

function fromPartyError(error: unknown): Response {
  if (error instanceof PartyError) return jsonError(error.status, error.code, error.message);
  return jsonError(500, "server_error", String((error as Error)?.message ?? error));
}

function randomHex(bytes: number): string {
  const buffer = new Uint8Array(bytes);
  crypto.getRandomValues(buffer);
  return [...buffer].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function newCode(): string {
  let out = "";
  const buffer = new Uint8Array(1);
  while (out.length < CODE_LENGTH) {
    crypto.getRandomValues(buffer);
    // Rejection sampling, so every character is as likely as every other.
    if (buffer[0] >= 256 - (256 % CODE_ALPHABET.length)) continue;
    out += CODE_ALPHABET[buffer[0] % CODE_ALPHABET.length];
  }
  return out;
}

/** Lower case, spaces, and O / I / L typed for 0 / 1 all reach the same party. */
function normaliseCode(code: string): string {
  let out = "";
  for (const ch of code.trim().toUpperCase()) {
    if (!/[\p{L}\p{N}]/u.test(ch)) continue;
    out += ch === "I" || ch === "L" ? "1" : ch === "O" ? "0" : ch;
  }
  return out;
}

function sameSecret(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function bearerToken(request: Request): string {
  const auth = request.headers.get("Authorization") ?? "";
  const [scheme, value] = auth.split(" ", 2);
  return scheme?.toLowerCase() === "bearer" && value ? value.trim() : "";
}

function offeredProtocols(request: Request): string[] {
  return (request.headers.get("Sec-WebSocket-Protocol") ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}

/** The party token from the Authorization header (the apps) or an offered subprotocol (browsers). */
function socketToken(request: Request): string {
  const bearer = bearerToken(request);
  if (bearer) return bearer;
  const offered = offeredProtocols(request).find((p) => p.startsWith(SOCKET_TOKEN_PREFIX));
  return offered ? offered.slice(SOCKET_TOKEN_PREFIX.length) : "";
}

async function readJson(request: Request): Promise<Json | Response> {
  const text = await request.text();
  if (text.length > REQUEST_MAX_BYTES) return jsonError(413, "request_too_large", "Request body is too large.");
  try {
    const parsed = JSON.parse(text);
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      return jsonError(422, "invalid_json", "Request body must contain one JSON object.");
    }
    return parsed as Json;
  } catch {
    return jsonError(422, "invalid_json", "Malformed JSON body.");
  }
}

function validateJoin(body: Json): JoinRequest | string {
  const str = (v: unknown) => (typeof v === "string" ? v.trim() : "");
  const userId = str(body.userId);
  if (!userId || userId.length > 128) return "userId must be between 1 and 128 characters";
  const deviceId = str(body.deviceId);
  if (!deviceId || deviceId.length > 128) return "deviceId must be between 1 and 128 characters";
  const displayName = str(body.displayName);
  if (!displayName || displayName.length > 80) return "displayName must be between 1 and 80 characters";
  let avatarUrl: string | null = null;
  if (typeof body.avatarUrl === "string" && body.avatarUrl.trim()) {
    const trimmed = body.avatarUrl.trim();
    if (trimmed.length > 1000) return "avatarUrl must not exceed 1000 characters";
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return "avatarUrl must be an http(s) URL";
    avatarUrl = trimmed;
  }
  let maxMembers: number | undefined;
  if (body.maxMembers !== undefined && body.maxMembers !== null) {
    if (typeof body.maxMembers !== "number" || body.maxMembers < 2 || body.maxMembers > 10) {
      return "maxMembers must be between 2 and 10";
    }
    maxMembers = Math.trunc(body.maxMembers);
  }
  const autoplayEnabled = typeof body.autoplayEnabled === "boolean" ? body.autoplayEnabled : undefined;
  return { userId, deviceId, displayName, avatarUrl, maxMembers, autoplayEnabled };
}

function trackFromWire(raw: unknown): Track | null {
  if (!raw || typeof raw !== "object") return null;
  const r = raw as Json;
  const videoId = typeof r.videoId === "string" ? r.videoId.slice(0, 128) : "";
  if (!videoId) return null;
  const track: Track = {
    videoId,
    title: typeof r.title === "string" ? r.title.slice(0, 300) : "",
    artist: typeof r.artist === "string" ? r.artist.slice(0, 300) : "",
    fromAutoplay: r.fromAutoplay === true,
  };
  if (typeof r.thumbnailUrl === "string" && r.thumbnailUrl) track.thumbnailUrl = r.thumbnailUrl.slice(0, 1000);
  if (typeof r.durationMs === "number" && r.durationMs > 0) track.durationMs = Math.trunc(r.durationMs);
  return track;
}

function num(v: unknown): number | undefined {
  return typeof v === "number" && Number.isFinite(v) ? Math.trunc(v) : undefined;
}

// ---- Playback ------------------------------------------------------------------------------------

function newPlayback(): Playback {
  const t = now();
  return {
    track: null, queue: [], queueIndex: -1, isPlaying: false, positionMs: 0, anchorMs: t,
    seq: 0, queueSeq: 0, updatedBy: null, updatedAtMs: t, startedBy: null, startedByName: null,
    autoplayEnabled: false,
  };
}

function positionAt(p: Playback, serverMs: number): number {
  if (!p.isPlaying) return p.positionMs;
  const position = p.positionMs + Math.max(0, serverMs - p.anchorMs);
  const duration = p.track?.durationMs;
  return duration && duration > 0 && position > duration ? duration : position;
}

function touch(p: Playback, by: string | null) {
  p.seq++;
  p.updatedBy = by;
  p.updatedAtMs = now();
}

function touchQueue(p: Playback, by: string | null) {
  p.queueSeq++;
  p.updatedBy = by;
  p.updatedAtMs = now();
}

function play(p: Playback, by: string, positionMs?: number) {
  const t = now();
  p.positionMs = Math.max(0, positionMs ?? positionAt(p, t));
  p.isPlaying = true;
  p.anchorMs = t + PLAY_LEAD_MS;
  touch(p, by);
}

function pause(p: Playback, by: string, positionMs?: number) {
  const t = now();
  p.positionMs = Math.max(0, positionMs ?? positionAt(p, t));
  p.isPlaying = false;
  p.anchorMs = t;
  touch(p, by);
}

function seek(p: Playback, by: string, positionMs: number) {
  p.positionMs = Math.max(0, positionMs);
  p.anchorMs = now() + (p.isPlaying ? PLAY_LEAD_MS : 0);
  touch(p, by);
}

/** [videoId] in [queue] nearest [around]: just after it, then just before, then the closest. */
function nearestIndexOf(queue: Track[], videoId: string, around: number): number {
  if (around + 1 >= 0 && around + 1 < queue.length && queue[around + 1].videoId === videoId) return around + 1;
  if (around - 1 >= 0 && around - 1 < queue.length && queue[around - 1].videoId === videoId) return around - 1;
  let best = -1;
  let bestDistance = 0;
  queue.forEach((item, i) => {
    if (item.videoId !== videoId) return;
    let distance = i - around;
    if (distance < 0) distance = -distance * 2 + 1;
    if (best === -1 || distance < bestDistance) {
      best = i;
      bestDistance = distance;
    }
  });
  return best;
}

function setTrack(p: Playback, by: string, track: Track, positionMs: number, isPlaying: boolean, queueIndex: number | undefined, byName: string) {
  p.track = track;
  p.positionMs = Math.max(0, positionMs);
  p.isPlaying = isPlaying;
  p.anchorMs = now() + (p.isPlaying ? PLAY_LEAD_MS : 0);
  if (queueIndex !== undefined && queueIndex >= 0 && queueIndex < p.queue.length && p.queue[queueIndex].videoId === track.videoId) {
    p.queueIndex = queueIndex;
  } else {
    p.queueIndex = nearestIndexOf(p.queue, track.videoId, p.queueIndex);
  }
  p.startedBy = by;
  p.startedByName = byName;
  touch(p, by);
}

function setQueue(p: Playback, by: string, queue: Track[], queueIndex: number) {
  if (p.track && (queueIndex < 0 || queueIndex >= queue.length || queue[queueIndex].videoId !== p.track.videoId)) {
    queueIndex = nearestIndexOf(queue, p.track.videoId, queueIndex);
  }
  if (queueIndex >= 0 && queueIndex < queue.length) {
    const end = Math.min(queue.length, queueIndex + 1 + MAX_UPCOMING_QUEUE);
    p.queue = queue.slice(0, end);
    p.queueIndex = queueIndex;
  } else {
    p.queue = queue.slice(0, MAX_QUEUE_LENGTH);
    p.queueIndex = queueIndex >= 0 && queueIndex < p.queue.length ? queueIndex : -1;
  }
  touchQueue(p, by);
}

/** Drops AutoPlay suggestions already waiting, or repeated within the batch. */
function withoutQueuedAutoplay(queue: Track[], queueIndex: number, tracks: Track[]): Track[] {
  const waiting = new Set<string>();
  for (let i = Math.max(0, queueIndex + 1); i < queue.length; i++) waiting.add(queue[i].videoId);
  const out: Track[] = [];
  for (const t of tracks) {
    if (t.fromAutoplay) {
      if (waiting.has(t.videoId)) continue;
      waiting.add(t.videoId);
    }
    out.push(t);
  }
  return out;
}

function addUpcoming(p: Playback, by: string, tracks: Track[], playNext: boolean): [boolean, string] {
  const upcoming = p.queueIndex >= 0 ? Math.max(0, p.queue.length - 1 - p.queueIndex) : p.queue.length;
  const slotsLeft = MAX_UPCOMING_QUEUE - upcoming;
  if (slotsLeft <= 0) return [false, "queue_full"];
  let toAdd = withoutQueuedAutoplay(p.queue, p.queueIndex, tracks);
  if (toAdd.length === 0 && tracks.length > 0) return [true, ""];
  toAdd = toAdd.slice(0, slotsLeft);
  if (toAdd.length === 0) return [false, "no_tracks"];
  if (playNext && p.queueIndex >= 0 && p.queueIndex < p.queue.length) {
    p.queue.splice(p.queueIndex + 1, 0, ...toAdd);
  } else {
    p.queue.push(...toAdd);
  }
  touchQueue(p, by);
  return [true, ""];
}

function removeUpcoming(p: Playback, by: string, videoId: string): boolean {
  const match = p.queue.findIndex((t) => t.videoId === videoId);
  if (match === -1 || match === p.queueIndex) return false;
  p.queue.splice(match, 1);
  if (p.queueIndex > match) p.queueIndex--;
  touchQueue(p, by);
  return true;
}

function clearUpcoming(p: Playback, by: string): boolean {
  if (p.queueIndex >= 0) {
    if (p.queue.length <= p.queueIndex + 1) return false;
    p.queue = p.queue.slice(0, p.queueIndex + 1);
  } else {
    if (p.queue.length === 0) return false;
    p.queue = [];
  }
  touchQueue(p, by);
  return true;
}

function moveUpcoming(p: Playback, by: string, from: number, to: number, videoId: string): boolean {
  if (videoId) {
    const match = p.queue.findIndex((t) => t.videoId === videoId);
    if (match === -1) return false;
    from = match;
  }
  if (from < 0 || from >= p.queue.length || to < 0 || to >= p.queue.length) return false;
  if (from <= p.queueIndex || to <= p.queueIndex) return false;
  if (from === to) return true;
  const [item] = p.queue.splice(from, 1);
  p.queue.splice(to, 0, item);
  touchQueue(p, by);
  return true;
}

function step(p: Playback, by: string, delta: number, byName: string): boolean {
  const target = p.queueIndex + delta;
  if (target < 0 || target >= p.queue.length) return false;
  setTrack(p, by, p.queue[target], 0, true, target, byName);
  return true;
}

function playbackWire(p: Playback, serverMs: number): Json {
  return {
    seq: p.seq,
    track: p.track,
    queueSeq: p.queueSeq,
    queueIndex: p.queueIndex,
    queueLength: p.queue.length,
    isPlaying: p.isPlaying,
    positionMs: p.positionMs,
    anchorMs: p.anchorMs,
    effectivePositionMs: positionAt(p, serverMs),
    updatedBy: p.updatedBy,
    startedBy: p.startedBy,
    startedByName: p.startedByName,
    autoplayEnabled: p.autoplayEnabled,
    updatedAtMs: p.updatedAtMs,
  };
}

function queueWire(p: Playback): Json {
  return { seq: p.queueSeq, index: p.queueIndex, items: p.queue };
}

function memberWire(m: Member): Json {
  return {
    memberId: m.memberId,
    userId: m.userId,
    displayName: m.displayName,
    avatarUrl: m.avatarUrl,
    isHost: m.isHost,
    connected: m.connected,
    joinedAtMs: m.joinedAtMs,
    lastSeenMs: m.lastSeenMs,
  };
}

function orderedMembers(s: PartyState): Member[] {
  return Object.values(s.members).sort((a, b) => a.joinedAtMs - b.joinedAtMs);
}

function partyWire(s: PartyState): Json {
  const t = now();
  return {
    code: s.code,
    createdAtMs: s.createdAtMs,
    maxMembers: s.maxMembers,
    hostOnlyControl: s.hostOnlyControl,
    members: orderedMembers(s).map(memberWire),
    playback: playbackWire(s.playback, t),
    queue: queueWire(s.playback),
    serverMs: t,
  };
}

function stateFrame(s: PartyState): Json {
  return { type: "state", playback: playbackWire(s.playback, now()), serverMs: now() };
}

function queueFrame(s: PartyState): Json {
  return { type: "queue", queue: queueWire(s.playback), serverMs: now() };
}

function membersFrame(s: PartyState): Json {
  return {
    type: "members",
    members: orderedMembers(s).map(memberWire),
    maxMembers: s.maxMembers,
    hostOnlyControl: s.hostOnlyControl,
    serverMs: now(),
  };
}

function activityFrame(member: Member, action: string, frame: Json): Json {
  const titleOf = (raw: unknown) => (raw && typeof raw === "object" && typeof (raw as Json).title === "string" ? ((raw as Json).title as string) : "");
  let detail = action;
  switch (action) {
    case "setTrack": {
      const title = titleOf(frame.track);
      if (title) detail = `Changed the song to "${title}"`;
      break;
    }
    case "queueAdd": {
      const tracks = Array.isArray(frame.tracks) ? frame.tracks : frame.track ? [frame.track] : [];
      const title = tracks.length ? titleOf(tracks[0]) : "";
      if (title) detail = tracks.length === 1 ? `Added "${title}" to the queue` : `Added "${title}" and ${tracks.length - 1} more to the queue`;
      break;
    }
    case "setQueue":
      if (Array.isArray(frame.queue)) detail = `Replaced the queue with ${frame.queue.length} tracks`;
      break;
    case "queueRemove": detail = "Removed a track from the queue"; break;
    case "queueClear": detail = "Cleared upcoming tracks"; break;
    case "queueMove": detail = "Reordered the upcoming queue"; break;
    case "next": detail = "Skipped to the next song"; break;
    case "previous": detail = "Went back to the previous song"; break;
    case "play": detail = "Started playback"; break;
    case "pause": detail = "Paused playback"; break;
    case "seek": detail = "Changed the playback position"; break;
    case "kick": detail = "Removed a listener from the party"; break;
    case "setMaxMembers": detail = "Changed the party size"; break;
    case "setAutoplay": detail = "Changed AutoPlay"; break;
  }
  return { type: "activity", action, by: member.displayName, detail, atMs: now() };
}

// ---- The party ----------------------------------------------------------------------------------

interface Budget {
  control: number;
  controlAt: number;
  frame: number;
  frameAt: number;
}

export class PartyRoom extends DurableObject<Env> {
  private state: PartyState | null = null;
  /** Rate budgets per member: memory only, and a reset after hibernation only forgives. */
  private budgets = new Map<string, Budget>();

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    ctx.blockConcurrencyWhile(async () => {
      this.state = (await ctx.storage.get<PartyState>("party")) ?? null;
    });
  }

  private async save() {
    if (this.state) await this.ctx.storage.put("party", this.state);
  }

  private async schedule(delayMs: number) {
    const at = now() + delayMs;
    const current = await this.ctx.storage.getAlarm();
    if (current === null || current > at) await this.ctx.storage.setAlarm(at);
  }

  private sockets(memberId?: string): WebSocket[] {
    return memberId ? this.ctx.getWebSockets(memberId) : this.ctx.getWebSockets();
  }

  private send(ws: WebSocket, frame: Json) {
    try {
      ws.send(JSON.stringify(frame));
    } catch {
      // A socket closing under the send; its close event tidies up.
    }
  }

  private sendTo(memberId: string, frame: Json) {
    for (const ws of this.sockets(memberId)) this.send(ws, frame);
  }

  private broadcast(frame: Json, skipMemberId = "") {
    const text = JSON.stringify(frame);
    for (const ws of this.sockets()) {
      const id = (ws.deserializeAttachment() as { memberId?: string } | null)?.memberId;
      if (skipMemberId && id === skipMemberId) continue;
      try {
        ws.send(text);
      } catch {
        // As above.
      }
    }
  }

  private refreshEmptiness(s: PartyState) {
    const anyConnected = Object.values(s.members).some((m) => m.connected);
    if (anyConnected) s.emptySinceMs = null;
    else if (s.emptySinceMs === null) s.emptySinceMs = now();
  }

  private join(s: PartyState, req: JoinRequest): Member {
    const t = now();
    for (const m of Object.values(s.members)) {
      if (m.deviceId === req.deviceId) {
        m.displayName = req.displayName;
        m.avatarUrl = req.avatarUrl;
        m.userId = req.userId;
        m.lastSeenMs = t;
        m.token = randomHex(24);
        s.touchedAtMs = t;
        return m;
      }
    }
    if (Object.keys(s.members).length >= s.maxMembers) {
      throw new PartyError(409, "party_full", `This party is full (${s.maxMembers} devices).`);
    }
    const member: Member = {
      memberId: randomHex(8),
      userId: req.userId,
      deviceId: req.deviceId,
      displayName: req.displayName,
      avatarUrl: req.avatarUrl,
      token: randomHex(24),
      isHost: Object.keys(s.members).length === 0,
      connected: false,
      joinedAtMs: t,
      lastSeenMs: t,
    };
    s.members[member.memberId] = member;
    s.touchedAtMs = t;
    return member;
  }

  private remove(s: PartyState, memberId: string): Member | null {
    const m = s.members[memberId];
    if (!m) return null;
    delete s.members[memberId];
    this.budgets.delete(memberId);
    if (m.isHost) {
      const next = orderedMembers(s)[0];
      if (next) next.isHost = true;
    }
    s.touchedAtMs = now();
    this.refreshEmptiness(s);
    return m;
  }

  private authenticate(s: PartyState, token: string): Member {
    for (const m of Object.values(s.members)) if (token && sameSecret(m.token, token)) return m;
    throw new PartyError(401, "bad_token", "This device is not a member of that party.");
  }

  private spend(memberId: string, kind: "control" | "frame"): boolean {
    const t = now();
    const rate = kind === "control" ? CONTROL_RATE_PER_SECOND : FRAME_RATE_PER_SECOND;
    let b = this.budgets.get(memberId);
    if (!b) {
      b = { control: CONTROL_RATE_PER_SECOND, controlAt: t, frame: FRAME_RATE_PER_SECOND, frameAt: t };
      this.budgets.set(memberId, b);
    }
    const at = kind === "control" ? b.controlAt : b.frameAt;
    const value = Math.min(rate, (kind === "control" ? b.control : b.frame) + (Math.max(0, t - at) / 1000) * rate);
    if (kind === "control") b.controlAt = t;
    else b.frameAt = t;
    if (value < 1) {
      if (kind === "control") b.control = value;
      else b.frame = value;
      return false;
    }
    if (kind === "control") b.control = value - 1;
    else b.frame = value - 1;
    return true;
  }

  private gone(): Response {
    return jsonError(404, "no_such_party", "No party with that code.");
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const route = url.pathname;

    if (request.headers.get("Upgrade")?.toLowerCase() === "websocket") return this.acceptSocket(request);

    if (route === "/create" && request.method === "POST") {
      if (this.state) return jsonError(409, "code_taken", "That code is in use.");
      const body = (await request.json()) as { code: string; join: JoinRequest };
      const t = now();
      const s: PartyState = {
        code: body.code,
        members: {},
        playback: newPlayback(),
        maxMembers: body.join.maxMembers ?? DEFAULT_MAX_MEMBERS,
        hostOnlyControl: false,
        createdAtMs: t,
        touchedAtMs: t,
        emptySinceMs: t,
      };
      if (body.join.autoplayEnabled !== undefined) s.playback.autoplayEnabled = body.join.autoplayEnabled;
      const member = this.join(s, body.join);
      this.state = s;
      await this.save();
      await this.schedule(SWEEP_MS);
      return json(201, { code: s.code, token: member.token, you: memberWire(member), party: partyWire(s) });
    }

    const s = this.state;
    if (!s) return this.gone();

    try {
      if (route === "/join" && request.method === "POST") {
        const req = (await request.json()) as JoinRequest;
        const member = this.join(s, req);
        await this.save();
        return json(200, { code: s.code, token: member.token, you: memberWire(member), party: partyWire(s) });
      }
      if (route === "/preview") {
        const members = orderedMembers(s);
        return json(200, {
          code: s.code,
          hostName: members.find((m) => m.isHost)?.displayName ?? "",
          memberCount: members.length,
          maxMembers: s.maxMembers,
          isFull: members.length >= s.maxMembers,
          members: members.map((m) => ({ displayName: m.displayName, avatarUrl: m.avatarUrl, isHost: m.isHost })),
        });
      }
      if (route === "/get") {
        this.authenticate(s, bearerToken(request));
        return json(200, partyWire(s));
      }
      if (route === "/leave" && request.method === "POST") {
        const member = this.authenticate(s, bearerToken(request));
        this.remove(s, member.memberId);
        await this.save();
        this.sendTo(member.memberId, { type: "bye", reason: "left" });
        this.broadcast(membersFrame(s), member.memberId);
        return json(200, { ok: true });
      }
      if (route === "/invite-info") {
        return json(200, {
          title: s.playback.track?.title ?? "",
          artist: s.playback.track?.artist ?? "",
          memberCount: Object.keys(s.members).length,
        });
      }
    } catch (error) {
      return fromPartyError(error);
    }
    return jsonError(404, "not_found", "Not found.");
  }

  private async acceptSocket(request: Request): Promise<Response> {
    const s = this.state;
    if (!s) return new Response("no such party", { status: 404 });
    const token = socketToken(request);
    if (!token) return new Response("missing token", { status: 401 });
    let member: Member;
    try {
      member = this.authenticate(s, token);
    } catch {
      return new Response("bad token", { status: 401 });
    }

    // One socket per member: a new one replaces the old, as the Go hub does.
    for (const old of this.sockets(member.memberId)) {
      try {
        old.close(4000, "replaced");
      } catch {
        // Already closing.
      }
    }

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server, [member.memberId]);
    server.serializeAttachment({ memberId: member.memberId });

    member.connected = true;
    member.lastSeenMs = now();
    s.touchedAtMs = now();
    this.refreshEmptiness(s);
    await this.save();

    this.send(server, { type: "welcome", you: memberWire(member), party: partyWire(s), serverMs: now() });
    this.broadcast(membersFrame(s));
    await this.schedule(STATE_HEARTBEAT_MS);

    const headers: Record<string, string> = {};
    if (offeredProtocols(request).includes(SOCKET_PROTOCOL)) headers["Sec-WebSocket-Protocol"] = SOCKET_PROTOCOL;
    return new Response(null, { status: 101, webSocket: client, headers });
  }

  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer) {
    const s = this.state;
    const memberId = (ws.deserializeAttachment() as { memberId?: string } | null)?.memberId ?? "";
    const member = s?.members[memberId];
    if (!s || !member) {
      ws.close(4001, "not a member");
      return;
    }
    const text = typeof message === "string" ? message : new TextDecoder().decode(message);
    if (text.length > WEBSOCKET_MAX_BYTES) {
      ws.close(1009, "message too large");
      return;
    }
    let frame: Json;
    try {
      frame = JSON.parse(text);
    } catch {
      return;
    }
    const kind = typeof frame?.type === "string" ? frame.type : "";
    if (!kind) return;

    if (!this.spend(memberId, "frame")) {
      this.send(ws, { type: "error", error: "rate_limited", message: "Too many messages at once." });
      return;
    }
    member.lastSeenMs = now();

    switch (kind) {
      case "ping":
        this.send(ws, { type: "pong", clientMs: frame.clientMs, serverMs: now() });
        return;
      case "sync":
        this.send(ws, stateFrame(s));
        return;
      case "syncQueue":
        this.send(ws, queueFrame(s));
        return;
      case "report":
        return;
      case "control":
        break;
      default:
        return;
    }

    if (!this.spend(memberId, "control")) {
      this.send(ws, { type: "error", error: "rate_limited", message: "Too many controls at once." });
      return;
    }
    const action = typeof frame.action === "string" ? frame.action : "";
    const queueBefore = s.playback.queueSeq;
    const [ok, errorCode, errorMessage] = this.applyControl(s, member, action, frame);
    if (!ok) {
      if (errorCode) this.send(ws, { type: "error", error: errorCode, message: errorMessage });
      return;
    }
    s.touchedAtMs = now();
    await this.save();
    if (s.playback.queueSeq !== queueBefore) this.broadcast(queueFrame(s));
    this.broadcast(stateFrame(s));
    if (action === "kick" || action === "setMaxMembers" || action === "setHostOnlyControl") this.broadcast(membersFrame(s));
    this.broadcast(activityFrame(member, action, frame));
  }

  private applyControl(s: PartyState, member: Member, action: string, frame: Json): [boolean, string, string] {
    const p = s.playback;
    const by = member.memberId;
    if (CONTROL_ACTIONS.has(action) && s.hostOnlyControl && !member.isHost) {
      return [false, "host_only", "Only the host can control the music in this party."];
    }
    const position = num(frame.positionMs);
    switch (action) {
      case "play":
        play(p, by, position);
        return [true, "", ""];
      case "pause":
        pause(p, by, position);
        return [true, "", ""];
      case "seek":
        if (position === undefined) return [false, "missing_position", "seek requires positionMs"];
        seek(p, by, position);
        return [true, "", ""];
      case "setTrack": {
        const track = trackFromWire(frame.track);
        if (!track) return [false, "invalid_track", "Track must include videoId"];
        const isPlaying = typeof frame.isPlaying === "boolean" ? frame.isPlaying : true;
        setTrack(p, by, track, position ?? 0, isPlaying, num(frame.queueIndex), member.displayName);
        return [true, "", ""];
      }
      case "setQueue": {
        const items = Array.isArray(frame.queue) ? frame.queue : [];
        const tracks = items.map(trackFromWire).filter((t): t is Track => t !== null);
        setQueue(p, by, tracks, num(frame.queueIndex) ?? -1);
        return [true, "", ""];
      }
      case "queueAdd": {
        let tracks: Track[] = [];
        if (frame.track && typeof frame.track === "object") {
          const t = trackFromWire(frame.track);
          if (t) tracks = [t];
        } else if (Array.isArray(frame.tracks)) {
          tracks = frame.tracks.map(trackFromWire).filter((t): t is Track => t !== null);
        }
        const [ok, reason] = addUpcoming(p, by, tracks, frame.playNext === true);
        if (!ok) {
          return reason === "queue_full"
            ? [false, "queue_full", `Queue is full (maximum ${MAX_UPCOMING_QUEUE} upcoming songs).`]
            : [false, reason, "Could not add track to queue."];
        }
        return [true, "", ""];
      }
      case "queueRemove": {
        let videoId = typeof frame.videoId === "string" ? frame.videoId : "";
        if (!videoId) {
          const index = num(frame.index);
          if (index !== undefined && index >= 0 && index < p.queue.length) videoId = p.queue[index].videoId;
        }
        if (!videoId) return [false, "missing_track", "queueRemove requires videoId or index"];
        if (!removeUpcoming(p, by, videoId)) return [false, "not_found", "Track is not in the upcoming queue"];
        return [true, "", ""];
      }
      case "queueClear":
        if (!clearUpcoming(p, by)) return [false, "no_upcoming", "No upcoming tracks to clear"];
        return [true, "", ""];
      case "queueMove": {
        const from = num(frame.fromIndex);
        const to = num(frame.toIndex);
        if (from === undefined || to === undefined) return [false, "missing_indices", "queueMove requires fromIndex and toIndex"];
        const videoId = typeof frame.videoId === "string" ? frame.videoId : "";
        if (!moveUpcoming(p, by, from, to, videoId)) return [false, "invalid_move", "Invalid queue move indices"];
        return [true, "", ""];
      }
      case "next":
        if (!step(p, by, 1, member.displayName)) return [false, "end_of_queue", "Already at the end of the queue."];
        return [true, "", ""];
      case "previous":
        if (!step(p, by, -1, member.displayName)) return [false, "start_of_queue", "Already at the beginning of the queue."];
        return [true, "", ""];
      case "setMaxMembers": {
        const value = num(frame.maxMembers);
        if (value === undefined) return [false, "invalid_capacity", "Choose a party size between 2 and 10."];
        if (!member.isHost) return [false, "host_only", "Only the host can change the party size."];
        if (value < 2 || value > 10) return [false, "invalid_capacity", "Party size must be between 2 and 10."];
        if (value < Object.keys(s.members).length) return [false, "party_too_small", "Party size cannot be smaller than the current member count."];
        s.maxMembers = value;
        return [true, "", ""];
      }
      case "setAutoplay": {
        if (typeof frame.enabled !== "boolean") return [false, "invalid_autoplay", "AutoPlay must be enabled or disabled."];
        if (p.autoplayEnabled !== frame.enabled) {
          p.autoplayEnabled = frame.enabled;
          p.updatedBy = by;
          p.updatedAtMs = now();
        }
        return [true, "", ""];
      }
      case "setHostOnlyControl": {
        if (typeof frame.enabled !== "boolean") return [false, "invalid_control_policy", "Host-only control must be enabled or disabled."];
        if (!member.isHost) return [false, "host_only", "Only the host can change who controls the music."];
        s.hostOnlyControl = frame.enabled;
        return [true, "", ""];
      }
      case "kick": {
        const target = typeof frame.memberId === "string" ? frame.memberId : "";
        if (!member.isHost) return [false, "host_only", "Only the host can remove listeners."];
        if (!target || target === member.memberId) return [false, "invalid_member", "Choose another listener to remove."];
        if (!this.remove(s, target)) return [false, "not_found", "That listener is no longer in this party."];
        this.sendTo(target, { type: "bye", reason: "kicked", message: "The host removed you from this party." });
        for (const ws of this.sockets(target)) {
          try {
            ws.close(4002, "kicked");
          } catch {
            // Already closing.
          }
        }
        return [true, "", ""];
      }
      default:
        return [false, "unknown_action", `Unknown control action '${action}'`];
    }
  }

  private async socketGone(ws: WebSocket) {
    const s = this.state;
    const memberId = (ws.deserializeAttachment() as { memberId?: string } | null)?.memberId ?? "";
    const member = s?.members[memberId];
    if (!s || !member) return;
    // Still connected if a newer socket of the same member replaced this one.
    member.connected = this.sockets(memberId).some((other) => other !== ws && other.readyState === WebSocket.OPEN);
    member.lastSeenMs = now();
    s.touchedAtMs = now();
    this.refreshEmptiness(s);
    await this.save();
    this.broadcast(membersFrame(s));
    await this.schedule(SWEEP_MS);
  }

  async webSocketClose(ws: WebSocket) {
    await this.socketGone(ws);
  }

  async webSocketError(ws: WebSocket) {
    await this.socketGone(ws);
  }

  /** The heartbeat while anyone is connected, and the sweep that ends members and the party. */
  async alarm() {
    const s = this.state;
    if (!s) return;
    const t = now();
    const open = this.sockets();

    // Sockets silent for longer than the idle limit are let go, as the Go server's read deadline does.
    for (const ws of open) {
      const id = (ws.deserializeAttachment() as { memberId?: string } | null)?.memberId ?? "";
      const member = s.members[id];
      if (!member || t - member.lastSeenMs > CONNECTION_IDLE_MS) {
        try {
          ws.close(4003, "idle");
        } catch {
          // Already closing.
        }
      }
    }

    if (open.length > 0) this.broadcast(stateFrame(s));

    let changed = false;
    for (const m of Object.values(s.members)) {
      if (!m.connected && t - m.lastSeenMs > DISCONNECT_GRACE_MS) {
        this.remove(s, m.memberId);
        changed = true;
      }
    }

    const expired = t - s.createdAtMs > PARTY_MAX_AGE_MS ||
      (s.emptySinceMs !== null && t - s.emptySinceMs > EMPTY_PARTY_TTL_MS);
    if (expired) {
      for (const ws of this.sockets()) {
        try {
          ws.close(4004, "party ended");
        } catch {
          // Already closing.
        }
      }
      this.state = null;
      await this.ctx.storage.deleteAll();
      return;
    }

    if (changed) {
      await this.save();
      this.broadcast(membersFrame(s));
    }
    await this.ctx.storage.setAlarm(t + (open.length > 0 ? STATE_HEARTBEAT_MS : SWEEP_MS));
  }
}

// ---- Service-wide limits ------------------------------------------------------------------------

/** Party creations per address per minute. Memory only: forgetting after an eviction only forgives. */
export class Registry extends DurableObject<Env> {
  private creates = new Map<string, { startedMs: number; count: number }>();

  async allowCreate(ip: string): Promise<boolean> {
    const t = now();
    for (const [key, entry] of this.creates) if (t - entry.startedMs >= 60_000) this.creates.delete(key);
    const entry = this.creates.get(ip);
    if (!entry) {
      this.creates.set(ip, { startedMs: t, count: 1 });
      return true;
    }
    if (entry.count >= CREATE_RATE_PER_MINUTE) return false;
    entry.count++;
    return true;
  }
}

// ---- The invite page ----------------------------------------------------------------------------

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}

function invitePage(code: string, origin: string, info: { title: string; artist: string; memberCount: number } | null): string {
  const deepLink = `bitchord://party/${encodeURIComponent(code)}?server=${encodeURIComponent(origin)}`;
  const intentUri = `intent://party/${encodeURIComponent(code)}?server=${encodeURIComponent(origin)}#Intent;scheme=bitchord;end`;
  const body = info
    ? `<div class="badge"><span class="dot"></span><span>Listen Together • ${info.memberCount} in party</span></div>
       <h1>Join the Music Party</h1>
       <p class="sub">Opening BitChord to sync playback in real time.</p>
       <div class="box"><div class="label">Party Code</div><div class="code">${escapeHtml(code)}</div>
       ${info.title ? `<div class="np">🎵 ${escapeHtml(info.title)} - ${escapeHtml(info.artist)}</div>` : ""}</div>
       <a class="btn" href="${escapeHtml(intentUri)}">Join Party in BitChord</a>
       <p class="note">Didn’t open automatically? Tap the button above.<br>
       Don't have BitChord yet? <a href="https://github.com/felixforestya-alt/BitChord2/releases" target="_blank" rel="noopener">Download it here</a>.</p>
       <script>setTimeout(function(){location.href=/Android/i.test(navigator.userAgent)?${JSON.stringify(intentUri)}:${JSON.stringify(deepLink)}},100)</script>`
    : `<div class="badge"><span class="dot off"></span><span>Party Inactive</span></div>
       <h1>Party Not Found</h1>
       <p class="sub">This party code has expired or does not exist on this server.</p>
       <div class="box"><div class="label">Party Code</div><div class="code" style="color:#a1a1aa">${escapeHtml(code)}</div></div>
       <p class="note">Please ask the host for a new invite link.</p>`;
  return `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>BitChord Listen Together - Party ${escapeHtml(code)}</title><style>
*{box-sizing:border-box;margin:0;padding:0}body{background:#0b0b0e;color:#f3f3f7;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}
.c{background:rgba(22,22,30,.85);border:1px solid rgba(255,255,255,.08);border-radius:28px;max-width:440px;width:100%;padding:36px 28px;text-align:center}
.badge{display:inline-flex;align-items:center;gap:6px;background:rgba(124,77,255,.15);color:#b388ff;font-size:13px;font-weight:600;padding:6px 14px;border-radius:9999px;margin-bottom:20px}
.dot{width:8px;height:8px;background:#00e676;border-radius:50%}.dot.off{background:#ff5252}h1{font-size:24px;margin-bottom:8px}.sub{color:#9e9ea7;font-size:15px;margin-bottom:28px}
.box{background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.1);border-radius:18px;padding:16px 20px;margin-bottom:28px}.label{font-size:12px;color:#71717a;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:6px}
.code{font-family:ui-monospace,Consolas,monospace;font-size:32px;font-weight:800;letter-spacing:6px}.np{font-size:14px;color:#d1d1d6;margin-top:10px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.btn{display:block;padding:16px 24px;background:linear-gradient(135deg,#7c4dff,#3d5afe);color:#fff;font-weight:600;text-decoration:none;border-radius:16px}.note{margin-top:24px;font-size:13px;color:#71717a;line-height:1.5}.note a{color:#b388ff}
</style></head><body><div class="c">${body}</div></body></html>`;
}

// ---- The front door -----------------------------------------------------------------------------

function partyStub(env: Env, code: string) {
  return env.PARTY.get(env.PARTY.idFromName(code));
}

async function createParty(request: Request, env: Env): Promise<Response> {
  const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
  const registry = env.REGISTRY.get(env.REGISTRY.idFromName("global"));
  if (!(await registry.allowCreate(ip))) {
    return jsonError(429, "create_rate_limited", "You can create up to two parties per minute. Please try again shortly.");
  }
  const body = await readJson(request);
  if (body instanceof Response) return body;
  const join = validateJoin(body);
  if (typeof join === "string") return jsonError(422, "validation_error", join);
  for (let i = 0; i < 12; i++) {
    const code = newCode();
    const response = await partyStub(env, code).fetch("https://party/create", {
      method: "POST",
      body: JSON.stringify({ code, join }),
    });
    if (response.status !== 409 || (await response.clone().json<Json>()).error !== "code_taken") return response;
  }
  return jsonError(503, "code_exhausted", "Could not allocate a party code.");
}

async function route(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname;
  const method = request.method;

  if (method === "OPTIONS") return new Response(null, { status: 204 });
  if (method === "GET" && path === "/") return json(200, { service: "bitchord-listen-together", serverMs: now() });
  if (method === "GET" && path === "/healthz") return json(200, { ok: true, serverMs: now() });
  if (method === "GET" && path === "/api/time") return json(200, { serverMs: now() });
  if (method === "POST" && path === "/api/parties") return createParty(request, env);

  let match = path.match(/^\/api\/parties\/([^/]+)(\/join|\/preview|\/leave)?$/);
  if (match) {
    const code = normaliseCode(decodeURIComponent(match[1]));
    const action = match[2] ?? "";
    const stub = partyStub(env, code);
    if (action === "/join" && method === "POST") {
      const body = await readJson(request);
      if (body instanceof Response) return body;
      const join = validateJoin(body);
      if (typeof join === "string") return jsonError(422, "validation_error", join);
      return stub.fetch("https://party/join", { method: "POST", body: JSON.stringify(join) });
    }
    if (action === "/preview" && method === "GET") return stub.fetch("https://party/preview");
    if (action === "/leave" && method === "POST") {
      if (!bearerToken(request)) return jsonError(401, "unauthorized", "Missing bearer token.");
      return stub.fetch("https://party/leave", { method: "POST", headers: { Authorization: request.headers.get("Authorization")! } });
    }
    if (action === "" && method === "GET") {
      if (!bearerToken(request)) return jsonError(401, "unauthorized", "Missing bearer token.");
      return stub.fetch("https://party/get", { headers: { Authorization: request.headers.get("Authorization")! } });
    }
  }

  match = path.match(/^\/ws\/parties\/([^/]+)$/);
  if (match && method === "GET") {
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") return new Response("expected a websocket", { status: 426 });
    if (!socketToken(request)) return new Response("missing token", { status: 401 });
    const code = normaliseCode(decodeURIComponent(match[1]));
    return partyStub(env, code).fetch(request);
  }

  match = path.match(/^\/invite\/([^/]+)$/);
  if (match && method === "GET") {
    const code = normaliseCode(decodeURIComponent(match[1]));
    const html = (status: number, info: Parameters<typeof invitePage>[2]) =>
      new Response(invitePage(code, url.origin, info), { status, headers: { "Content-Type": "text/html; charset=utf-8" } });
    if (code.length !== CODE_LENGTH) return html(400, null);
    const response = await partyStub(env, code).fetch("https://party/invite-info");
    return response.ok ? html(200, await response.json()) : html(404, null);
  }

  return jsonError(404, "not_found", "Not found.");
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      return await route(request, env);
    } catch (error) {
      return jsonError(500, "server_error", String((error as Error)?.message ?? error));
    }
  },
} satisfies ExportedHandler<Env>;
