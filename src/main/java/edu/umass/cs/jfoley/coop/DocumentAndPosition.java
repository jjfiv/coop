package edu.umass.cs.jfoley.coop;

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
}
