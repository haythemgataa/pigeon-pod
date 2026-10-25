package top.asimov.pigeon.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.enums.EpisodeCoverFill;
import top.asimov.pigeon.model.enums.EpisodeCoverMode;

/**
 * How a feed presents episode artwork in its RSS feed.
 *
 * <p>Stored as JSON on the feed. {@code blur}, {@code fade}, {@code tint} and {@code position} are
 * percentages (0-100); fields that don't apply to the selected mode/fill are dropped by
 * {@link #normalized()}. {@code position} places the crop window (CROP) or the image inside the
 * square (PAD), from top/left (0) to bottom/right (100). {@code tint} washes image-based
 * backgrounds with a deepened color taken from the image.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EpisodeCoverStyle(EpisodeCoverMode mode, EpisodeCoverFill fill, String color,
                                Integer blur, Integer fade, Integer tint, Integer position) {

  public static final EpisodeCoverStyle ORIGINAL =
      new EpisodeCoverStyle(EpisodeCoverMode.ORIGINAL, null, null, null, null, null, null);

  public static final String DEFAULT_COLOR = "#000000";
  public static final int DEFAULT_BLUR = 70;
  public static final int DEFAULT_FADE = 30;
  public static final int DEFAULT_TINT = 50;
  public static final int DEFAULT_POSITION = 50;

  private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-f]{6}$");
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

  /**
   * Parses a stored style. Blank or unreadable values fall back to {@link #ORIGINAL} so a bad row
   * never breaks RSS generation.
   */
  public static EpisodeCoverStyle parse(String json) {
    if (!StringUtils.hasText(json)) {
      return ORIGINAL;
    }
    try {
      EpisodeCoverStyle style = MAPPER.readValue(json, EpisodeCoverStyle.class);
      return style == null ? ORIGINAL : style.normalized();
    } catch (JsonProcessingException e) {
      return ORIGINAL;
    }
  }

  /**
   * Validates user input and returns the JSON to store, or {@code null} when artwork is left as
   * is.
   */
  public static String normalizeForStorage(String json) {
    if (!StringUtils.hasText(json)) {
      return null;
    }
    EpisodeCoverStyle style;
    try {
      style = MAPPER.readValue(json, EpisodeCoverStyle.class);
    } catch (JsonProcessingException e) {
      throw new BusinessException("Invalid episode artwork settings");
    }
    if (style == null) {
      return null;
    }
    EpisodeCoverStyle normalized = style.normalized();
    return normalized.mode() == EpisodeCoverMode.ORIGINAL ? null : normalized.toJson();
  }

  public EpisodeCoverStyle normalized() {
    EpisodeCoverMode safeMode = mode == null ? EpisodeCoverMode.ORIGINAL : mode;
    return switch (safeMode) {
      case ORIGINAL, FEED_COVER ->
          new EpisodeCoverStyle(safeMode, null, null, null, null, null, null);
      case CROP -> new EpisodeCoverStyle(safeMode, null, null, null, null, null,
          clampPercent(position, DEFAULT_POSITION));
      case PAD -> {
        EpisodeCoverFill safeFill = fill == null ? EpisodeCoverFill.PROGRESSIVE : fill;
        boolean isColorFill = safeFill == EpisodeCoverFill.COLOR;
        yield new EpisodeCoverStyle(safeMode, safeFill,
            isColorFill ? normalizeColor(color) : null,
            isColorFill ? null : clampPercent(blur, DEFAULT_BLUR),
            clampPercent(fade, DEFAULT_FADE),
            isColorFill ? null : clampPercent(tint, DEFAULT_TINT),
            clampPercent(position, DEFAULT_POSITION));
      }
    };
  }

  /** Whether episode covers have to be re-drawn (as opposed to linked as they are). */
  public boolean requiresRendering() {
    return mode == EpisodeCoverMode.PAD || mode == EpisodeCoverMode.CROP;
  }

  /** Short, stable hash used to version cover URLs and cache files. */
  public String fingerprint() {
    return DigestUtils.md5DigestAsHex(normalized().toJson().getBytes(StandardCharsets.UTF_8))
        .substring(0, 10);
  }

  public String toJson() {
    try {
      return MAPPER.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize episode cover style", e);
    }
  }

  private static int clampPercent(Integer value, int fallback) {
    if (value == null) {
      return fallback;
    }
    return Math.max(0, Math.min(100, value));
  }

  private static String normalizeColor(String value) {
    if (!StringUtils.hasText(value)) {
      return DEFAULT_COLOR;
    }
    String candidate = value.trim().toLowerCase(Locale.ROOT);
    return HEX_COLOR.matcher(candidate).matches() ? candidate : DEFAULT_COLOR;
  }
}
