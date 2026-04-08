package org.batfish.question.routepolicyproperties;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.questions.Question;
import org.batfish.specifier.AllNodesNodeSpecifier;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierFactories;

public class CommunitySetRegexQuestion extends Question {
  private static final String PROP_NODES = "nodes";

  private final @Nullable String _nodes;
  private final @Nonnull NodeSpecifier _nodeSpecifier;

  @JsonCreator
  private static CommunitySetRegexQuestion create(
          @JsonProperty(PROP_NODES) @Nullable String nodes) {
    return new CommunitySetRegexQuestion(nodes);
  }

  public CommunitySetRegexQuestion(@Nullable String nodes) {
    _nodes = nodes;
    _nodeSpecifier =
            SpecifierFactories.getNodeSpecifierOrDefault(nodes, AllNodesNodeSpecifier.INSTANCE);
  }

  @Override
  public boolean getDataPlane() {return false;}
  @Override
  public String getName() {
    return "communitySetRegex";
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
    if (!(o instanceof CommunitySetRegexQuestion)) {
      return false;
    }
    CommunitySetRegexQuestion that = (CommunitySetRegexQuestion) o;
    return Objects.equals(_nodes, that._nodes)
            && Objects.equals(_nodeSpecifier, that._nodeSpecifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_nodes, _nodeSpecifier);
  }
}
