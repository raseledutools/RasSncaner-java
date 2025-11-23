package de.schliweb.makeacopy.framing;

/**
 * Represents the result of a framing or alignment process.
 * This class provides detailed information about the framing status,
 * including quality, positional adjustments, scale ratio, tilt angles,
 * guidance hints, and the presence of a document.
 */
public class FramingResult {
    public final float quality;
    public final float dxNorm;
    public final float dyNorm;
    public final float scaleRatio;
    public final float tiltHorizontal;
    public final float tiltVertical;
    public final GuidanceHint hint;
    public final boolean hasDocument;

    public FramingResult(float quality,
                         float dxNorm,
                         float dyNorm,
                         float scaleRatio,
                         float tiltHorizontal,
                         float tiltVertical,
                         GuidanceHint hint,
                         boolean hasDocument) {
        this.quality = quality;
        this.dxNorm = dxNorm;
        this.dyNorm = dyNorm;
        this.scaleRatio = scaleRatio;
        this.tiltHorizontal = tiltHorizontal;
        this.tiltVertical = tiltVertical;
        this.hint = hint;
        this.hasDocument = hasDocument;
    }

    @Override
    public String toString() {
        return "FramingResult{" +
                "quality=" + quality +
                ", dxNorm=" + dxNorm +
                ", dyNorm=" + dyNorm +
                ", scaleRatio=" + scaleRatio +
                ", tiltHorizontal=" + tiltHorizontal +
                ", tiltVertical=" + tiltVertical +
                ", hint=" + hint +
                ", hasDocument=" + hasDocument +
                '}';
    }
}
