package org.batfish.question.routepolicyproperties;
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
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Vrf;
import org.batfish.datamodel.pojo.Node;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class TiebreakerUsageAnswerer extends Answerer {

  public static final String COL_TIEBREAKER = "Tiebreaker";
  public static final String COL_NODE_LIST = "Node_List";
  public static final String COL_NODE_COUNT = "Node_Count";

  public TiebreakerUsageAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    TiebreakerUsageQuestion question = (TiebreakerUsageQuestion) _question;
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
    Map<String, Set<String>> tbkToNodes = new TreeMap<>();
    for(String node : nodeSpecifier.resolve(ctxt)){
      Configuration config = ctxt.getConfigs().get(node);
      for(Vrf vrf : config.getVrfs().values()){
        BgpProcess bProcess = vrf.getBgpProcess();
        if(bProcess != null){
          String tiebreaker = bProcess.getTieBreaker().toString();
          if(!tbkToNodes.containsKey(tiebreaker)){
            tbkToNodes.put(tiebreaker, new TreeSet<>());
          }
          tbkToNodes.get(tiebreaker).add(node);
        }
      }
    }
    ImmutableList.Builder<Row> rows = ImmutableList.builder();


    for (Map.Entry<String, Set<String>> entry : tbkToNodes.entrySet()) {
      List<Node> nodes = entry.getValue().stream()
              .map(Node::new)
              .collect(ImmutableList.toImmutableList());
      rows.add(
              Row.builder(columnMap)
                      .put(COL_TIEBREAKER, entry.getKey())
                      .put(COL_NODE_LIST, nodes)
                      .put(COL_NODE_COUNT, nodes.size())
                      .build());
    }
    return rows.build();
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(COL_TIEBREAKER, Schema.STRING, "Tiebreaker", true, false),
                    new ColumnMetadata(
                            COL_NODE_LIST,
                            Schema.list(Schema.NODE),
                            "List of nodes using this Tiebreaker",
                            false,
                            true),
                    new ColumnMetadata(
                            COL_NODE_COUNT,
                            Schema.INTEGER,
                            "Number of nodes using this tiebreaker",
                            false,
                            true));
    return new TableMetadata(
            columns, String.format("Tiebreaker ${%s} is used by ${%s} nodes.", COL_TIEBREAKER, COL_NODE_COUNT));
  }
}