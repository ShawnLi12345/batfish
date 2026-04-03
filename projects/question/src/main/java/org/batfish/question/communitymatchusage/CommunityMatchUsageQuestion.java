package org.batfish.question.communitymatchusage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.batfish.datamodel.questions.NodePropertySpecifier;
import org.batfish.datamodel.questions.Question;
import org.batfish.question.nodeproperties.NodePropertiesQuestion;
import org.batfish.specifier.AllNodesNodeSpecifier;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierFactories;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class CommunityMatchUsageQuestion extends Question {
  private static final String PROP_NODES = "nodes";

  private @Nullable String _nodes;
  private @Nonnull NodeSpecifier _nodeSpecifier;

  public CommunityMatchUsageQuestion(@Nullable String nodes){
    _nodes = nodes;
    _nodeSpecifier = SpecifierFactories.getNodeSpecifierOrDefault(nodes, AllNodesNodeSpecifier.INSTANCE);
  }


  @Override
  public boolean getDataPlane() {return false;}
  @Override
  public String getName() {
      return "communityMatchUsage";
  }

  @JsonProperty(PROP_NODES)
  public @Nullable String getNodes() {
      return _nodes;
  }

  @JsonIgnore
  public @Nonnull NodeSpecifier getNodeSpecifier() {
      return _nodeSpecifier;
  }

  @Override
  public boolean equals(@Nullable Object o) {
      if (!(o instanceof CommunityMatchUsageQuestion)) {
          return false;
      }
      CommunityMatchUsageQuestion that = (CommunityMatchUsageQuestion) o;
      return Objects.equals(_nodes, that._nodes)
              && Objects.equals(_nodeSpecifier, that._nodeSpecifier);
  }

  @Override
  public int hashCode() {
      return Objects.hash(_nodes, _nodeSpecifier);
  }
}
