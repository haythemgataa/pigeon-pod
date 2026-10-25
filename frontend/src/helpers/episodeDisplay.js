/** Title shown for an episode, after the user's per-episode edits. */
export function episodeTitle(episode) {
  return episode?.customTitle || episode?.title || '';
}

/** Description shown for an episode, after the user's per-episode edits. */
export function episodeDescription(episode) {
  return episode?.customDescription || episode?.description || '';
}

/** Thumbnail URL for an episode: the uploaded one if any, else the source thumbnail. */
export function episodeCoverSrc(episode) {
  if (!episode) return '';
  if (episode.customCoverExt) {
    const version = episode.customizedAt ? `?v=${encodeURIComponent(episode.customizedAt)}` : '';
    return `/media/${encodeURIComponent(episode.id)}/cover${version}`;
  }
  return episode.maxCoverUrl || episode.defaultCoverUrl || '';
}

export function isEpisodeCustomized(episode) {
  return Boolean(episode?.customTitle || episode?.customDescription || episode?.customCoverExt);
}

const LINK_PATTERN = /<a href="([^"]+)">([^<]*)<\/a>/g;

/**
 * Splits RSS description text into plain-text and link parts, so it can be shown without
 * rendering any HTML from the source.
 */
export function splitDescriptionLinks(text) {
  const parts = [];
  let lastIndex = 0;
  for (const match of (text || '').matchAll(LINK_PATTERN)) {
    if (match.index > lastIndex) parts.push({ text: text.slice(lastIndex, match.index) });
    parts.push({ text: decodeEntities(match[2]), href: decodeEntities(match[1]) });
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < (text || '').length) parts.push({ text: text.slice(lastIndex) });
  return parts;
}

function decodeEntities(value) {
  return value
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&amp;', '&');
}
