import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { authoringError, useAuthoringScope, useOwnerCourse } from "../hooks/useCourseAuthoring";
import { formatDuration, isSafeEmbed } from "../services/courseService";
import { guessProvider, parseDuration, type LessonInput } from "../services/courseAuthoringService";

const HASH_RE = /^[0-9a-fA-F]{64}$/;

/**
 * Job: add or edit one lesson from a video link (owner may also pin official media).
 * Primary: Add lesson (new) or Save (edit).
 * Next: {root}/:courseId
 * Hidden: other lessons, publishing, access grants; teach scope has no official pin fields.
 */
export function OwnerLessonFormPage() {
  const { courseId = "", sectionId, lessonId } = useParams();
  const editing = Boolean(lessonId);
  const navigate = useNavigate();
  const { scope, root } = useAuthoringScope();
  const ownerPin = scope === "owner";
  const { loading, error, outline, actions } = useOwnerCourse(Number(courseId), scope);
  const existing = editing
    ? outline?.sections.flatMap((s) => s.lessons).find((l) => l.id === Number(lessonId))
    : undefined;
  const section = outline?.sections.find((s) =>
    editing ? s.section.id === existing?.sectionId : s.section.id === Number(sectionId)
  )?.section;

  const [title, setTitle] = useState("");
  const [videoUrl, setVideoUrl] = useState("");
  const [duration, setDuration] = useState("");
  const [description, setDescription] = useState("");
  const [resources, setResources] = useState("");
  const [practicePrompt, setPracticePrompt] = useState("");
  const [freePreview, setFreePreview] = useState(false);
  const [active, setActive] = useState(true);
  const [contentHash, setContentHash] = useState("");
  const [storageRef, setStorageRef] = useState("");
  const [keyId, setKeyId] = useState("");
  const [clearOfficial, setClearOfficial] = useState(false);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const provider = guessProvider(videoUrl);
  const seconds = parseDuration(duration);
  const wasPinned = Boolean(existing?.contentHash || existing?.storageRef || existing?.keyId);

  useEffect(() => {
    if (!existing) return;
    setTitle(existing.title);
    setVideoUrl(existing.videoUrl ?? "");
    setDuration(formatDuration(existing.durationSec));
    setDescription(existing.description ?? "");
    setResources(existing.resources ?? "");
    setPracticePrompt(existing.practicePrompt ?? "");
    setFreePreview(Boolean(existing.isFreePreview));
    setActive(Boolean(existing.isActive));
    setContentHash(existing.contentHash ?? "");
    setStorageRef(existing.storageRef ?? "");
    setKeyId(existing.keyId ?? "");
    setClearOfficial(false);
  }, [existing]);

  function officialFields(): Pick<LessonInput, "contentHash" | "storageRef" | "keyId"> | Record<string, never> {
    if (!ownerPin) return {};
    if (clearOfficial) {
      return { contentHash: "", storageRef: "", keyId: "" };
    }
    const hash = contentHash.trim();
    const ref = storageRef.trim();
    const kid = keyId.trim();
    if (!hash && !ref && !kid) return {};
    return { contentHash: hash, storageRef: ref, keyId: kid };
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (duration.trim() && seconds === null) {
      setMsg("Write the length as minutes:seconds, for example 8:30.");
      return;
    }
    if (ownerPin && !clearOfficial) {
      const hash = contentHash.trim();
      const ref = storageRef.trim();
      const kid = keyId.trim();
      const any = Boolean(hash || ref || kid);
      if (any && !(hash && ref && kid)) {
        setMsg("Official media needs contentHash, storageRef and keyId together (or leave all three empty).");
        return;
      }
      if (hash && !HASH_RE.test(hash)) {
        setMsg("contentHash must be a SHA-256 hex string (64 characters).");
        return;
      }
    }
    setBusy(true);
    setMsg(null);
    const input: LessonInput = {
      title: title.trim(),
      videoUrl: videoUrl.trim(),
      durationSec: seconds,
      description: description.trim() || null,
      resources: resources.trim() || null,
      practicePrompt: practicePrompt.trim() || null,
      isFreePreview: freePreview ? 1 : 0,
      ...officialFields(),
    };
    try {
      if (editing) await actions.updateLesson(Number(lessonId), { ...input, isActive: active ? 1 : 0 });
      else await actions.createLesson(Number(sectionId), input);
      navigate(`${root}/${courseId}`);
    } catch (err) {
      setMsg(authoringError(err, "Could not save the lesson.", scope));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">
          <Link to={`${root}/${courseId}`}>{outline?.course.title ?? "Course"}</Link>
          {section ? ` · ${section.title}` : ""}
        </p>
        <h1>{editing ? "Edit lesson" : "New lesson"}</h1>
        <p className="eh8s-lead">
          {ownerPin
            ? "Paste a public or free-preview video link, or pin official encrypted media for paid playback."
            : "Upload the video to YouTube (unlisted), Vimeo, Bunny Stream or Cloudflare Stream, then paste its link."}
        </p>
      </header>
      {loading ? <p className="eh8s-muted-line">Loading…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      {!loading && outline && !section ? <div className="eh8s-banner bad">That lesson or section is not in this course.</div> : null}
      {msg ? <div className="eh8s-banner bad">{msg}</div> : null}
      {editing && existing && isSafeEmbed(existing.embedUrl) && !wasPinned ? (
        <div className="eh8s-player">
          <iframe
            src={existing.embedUrl}
            title={existing.title}
            allow="accelerometer; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
            allowFullScreen
            referrerPolicy="strict-origin-when-cross-origin"
          />
        </div>
      ) : null}
      {editing && wasPinned ? (
        <p className="eh8s-muted-line">Official media is pinned. Students play via a temporary watch link, not a public embed.</p>
      ) : null}
      {section ? (
        <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
          <label>
            Title
            <input required maxLength={160} value={title} onChange={(ev) => setTitle(ev.target.value)} />
          </label>
          <label>
            Video link
            <input
              required
              type="url"
              maxLength={600}
              placeholder="https://youtu.be/…"
              value={videoUrl}
              onChange={(ev) => setVideoUrl(ev.target.value)}
            />
          </label>
          <p className="eh8s-muted-line">
            {videoUrl.trim()
              ? provider
                ? `Recognized: ${provider}.`
                : "Not a supported link yet. Use an https YouTube, Vimeo, Bunny Stream or Cloudflare Stream link."
              : "Supported: YouTube, Vimeo, Bunny Stream, Cloudflare Stream."}
          </p>
          <label>
            Length (optional, minutes:seconds)
            <input placeholder="8:30" value={duration} onChange={(ev) => setDuration(ev.target.value)} />
          </label>
          <label>
            What this lesson covers (optional)
            <textarea maxLength={2000} value={description} onChange={(ev) => setDescription(ev.target.value)} />
          </label>
          <label>
            Practice notes and resources (optional)
            <textarea maxLength={2000} value={resources} onChange={(ev) => setResources(ev.target.value)} />
          </label>
          <label>
            Practice exercise for NEXUS (optional)
            <textarea
              maxLength={1000}
              placeholder="e.g. Record the ghost-note groove at 80 bpm for one minute and describe where you rushed."
              value={practicePrompt}
              onChange={(ev) => setPracticePrompt(ev.target.value)}
            />
          </label>
          <label className="eh8s-check">
            <input type="checkbox" checked={freePreview} onChange={(ev) => setFreePreview(ev.target.checked)} />
            Free preview (anyone signed in can watch)
          </label>
          {editing ? (
            <label className="eh8s-check">
              <input type="checkbox" checked={active} onChange={(ev) => setActive(ev.target.checked)} />
              Visible to students
            </label>
          ) : null}
          {ownerPin ? (
            <fieldset className="eh8s-fieldset" disabled={clearOfficial}>
              <legend>Official media (optional)</legend>
              <p className="eh8s-muted-line">
                Pin all three to serve a short-lived signed watch URL. Leave empty to keep YouTube/Vimeo embeds. Clear
                removes the pin.
              </p>
              <label>
                Content hash (SHA-256 hex)
                <input
                  maxLength={64}
                  spellCheck={false}
                  placeholder="64 hex characters"
                  value={contentHash}
                  onChange={(ev) => setContentHash(ev.target.value)}
                />
              </label>
              <label>
                Storage ref
                <input
                  maxLength={600}
                  spellCheck={false}
                  placeholder="official/lesson-1.enc"
                  value={storageRef}
                  onChange={(ev) => setStorageRef(ev.target.value)}
                />
              </label>
              <label>
                Key id
                <input
                  maxLength={64}
                  spellCheck={false}
                  placeholder="k-2026-10"
                  value={keyId}
                  onChange={(ev) => setKeyId(ev.target.value)}
                />
              </label>
            </fieldset>
          ) : null}
          {ownerPin && wasPinned ? (
            <label className="eh8s-check">
              <input type="checkbox" checked={clearOfficial} onChange={(ev) => setClearOfficial(ev.target.checked)} />
              Clear official pin (students fall back to the video link embed)
            </label>
          ) : null}
          <button type="submit" className="eh8s-btn primary" disabled={busy || !provider}>
            {busy ? "Saving…" : editing ? "Save" : "Add lesson"}
          </button>
        </form>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to={`${root}/${courseId}`}>
          Back to course
        </Link>
      </div>
    </section>
  );
}
