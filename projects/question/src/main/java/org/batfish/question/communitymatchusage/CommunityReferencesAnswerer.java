package org.batfish.question.communitymatchusage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.pojo.Node;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchExpr;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class CommunityReferencesAnswerer extends Answerer {

  public static final String COL_COMMUNITY = "Community";
  public static final String COL_NODE_LIST = "Node_List";

  public CommunityReferencesAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    CommunityReferencesQuestion question = (CommunityReferencesQuestion) _question;
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
    Map<String, Set<String>> exprToNodes = new TreeMap<>();
    for(String node : nodeSpecifier.resolve(ctxt)){
      Configuration config = ctxt.getConfigs().get(node);
      for(Map.Entry<String, CommunityMatchExpr> entry : config.getCommunityMatchExprs().entrySet()){
        if(!exprToNodes.containsKey(entry.getKey())){
          exprToNodes.put(entry.getKey(),new TreeSet<>());
        }
        exprToNodes.get(entry.getKey()).add(node);
      }
    }
    ImmutableList.Builder<Row> rows = ImmutableList.builder();


    for (Map.Entry<String, Set<String>> entry : exprToNodes.entrySet()) {
      List<Node> nodes = entry.getValue().stream()
              .map(Node::new)
              .collect(ImmutableList.toImmutableList());
      rows.add(
              Row.builder(columnMap)
                      .put(COL_COMMUNITY, entry.getKey())
                      .put(COL_NODE_LIST, nodes)
                      .build());
    }
    return rows.build();
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(COL_COMMUNITY, Schema.STRING, "Community", true, false),
                    new ColumnMetadata(
                            COL_NODE_LIST,
                            Schema.list(Schema.NODE),
                            "List of nodes referencing this community",
                            false,
                            true));
    return new TableMetadata(
            columns, String.format("Community ${%s} is referenced by ${%s}", COL_COMMUNITY, COL_NODE_LIST));
  }
}