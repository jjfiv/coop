package edu.umass.cs.jfoley.coop.querying;

/**
 * Store a corpus-global positional hit.
 */
public class DocumentAndPosition {
  public final int documentId;
  public final int matchPosition;

  public DocumentAndPosition(int documentId, int matchPosition) {
    this.documentId = documentId;
    this.matchPosition = matchPosition;
  }

  public TermSlice asSlice(int width) {
    if(width < 0) {
      throw new IllegalArgumentException("Can't make a slice of negative width.");
    }
    return new TermSlice(documentId, matchPosition - width, matchPosition + width);
  }
}
