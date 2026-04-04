package org.batfish.question.communitymatchusage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.pojo.Node;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchExpr;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class CommunityMatchUsageAnswerer extends Answerer {

  public static final String COL_NODE = "Node";
  public static final String COL_COMMUNITY_MATCH_COUNT = "Community_Match_Count";

  public CommunityMatchUsageAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    CommunityMatchUsageQuestion question = (CommunityMatchUsageQuestion) _question;
    TableMetadata tableMetadata = createTableMetadata();
    TableAnswerElement answer = new TableAnswerElement(tableMetadata);

    List<Row> rows =
        getAnswerRows(
            _batfish.specifierContext(snapshot),
            question.getNodeSpecifier(),
            tableMetadata.toColumnMap());

    answer.postProcessAnswer(question, rows);
    return answer;
  }

  @VisibleForTesting
  static List<Row> getAnswerRows(
      SpecifierContext ctxt,
      NodeSpecifier nodeSpecifier,
      Map<String, ColumnMetadata> columnMap) {
    ImmutableList.Builder<Row> rows = ImmutableList.builder();
    int totalNodes = 0;
    int matchingNodes = 0;

    for (String nodeName : nodeSpecifier.resolve(ctxt)) {
      totalNodes++;
      Configuration config = ctxt.getConfigs().get(nodeName);
      Map<String, CommunityMatchExpr> exprs = config.getCommunityMatchExprs();
      if (!exprs.isEmpty()) {
        matchingNodes++;
        rows.add(
            Row.builder(columnMap)
                .put(COL_NODE, new Node(nodeName))
                .put(COL_COMMUNITY_MATCH_COUNT, exprs.size())
                .build());
      }
    }
    return rows.build();
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
        ImmutableList.of(
            new ColumnMetadata(COL_NODE, Schema.NODE, "Node", true, false),
            new ColumnMetadata(
                COL_COMMUNITY_MATCH_COUNT,
                Schema.INTEGER,
                "Number of community match expressions",
                false,
                true));
    return new TableMetadata(
        columns, String.format("Node ${%s} has ${%s} community match expressions", COL_NODE, COL_COMMUNITY_MATCH_COUNT));
  }
}