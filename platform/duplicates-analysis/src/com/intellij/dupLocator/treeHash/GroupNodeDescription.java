package com.intellij.dupLocator.treeHash;

/**
* Created by IntelliJ IDEA.
* User: Eugene.Kudelevsky
* Date: 15.05.2009
* Time: 16:24:08
* To change this template use File | Settings | File Templates.
*/
public class GroupNodeDescription {
  private final int myFilesCount;
  private final String myTitle;
  private final String myComment;


  public GroupNodeDescription(final int filesCount, final String title, final String comment) {
    myFilesCount = filesCount;
    myTitle = title;
    myComment = comment;
  }


  public int getFilesCount() {
    return myFilesCount;
  }

  public String getTitle() {
    return myTitle;
  }

  public String getComment() {
    return myComment;
  }
}
