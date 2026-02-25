package de.schliweb.makeacopy.framing;

import lombok.AllArgsConstructor;
import lombok.ToString;

/**
 * Represents the result of a framing or alignment process. This class provides detailed information
 * about the framing status, including quality, positional adjustments, scale ratio, tilt angles,
 * guidance hints, and the presence of a document.
 */
@AllArgsConstructor
@ToString
public class FramingResult {
  public final float quality;
  public final float dxNorm;
  public final float dyNorm;
  public final float scaleRatio;
  public final float tiltHorizontal;
  public final float tiltVertical;
  public final GuidanceHint hint;
  public final boolean hasDocument;
}
