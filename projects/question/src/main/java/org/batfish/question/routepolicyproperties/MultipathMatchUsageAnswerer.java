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

public class MultipathMatchUsageAnswerer extends Answerer {

  public static final String COL_MATCH_MODE = "Match_Mode";
  public static final String COL_NODE_LIST = "Node_List";
  public static final String COL_NODE_COUNT = "Node_Count";

  public MultipathMatchUsageAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    MultipathMatchUsageQuestion question = (MultipathMatchUsageQuestion) _question;
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
    Map<String, Set<String>> matchModeToNodes = new TreeMap<>();
    for(String node : nodeSpecifier.resolve(ctxt)){
      Configuration config = ctxt.getConfigs().get(node);
      for(Vrf vrf : config.getVrfs().values()){
        BgpProcess bProcess = vrf.getBgpProcess();
        if(bProcess != null){
          String mode;
          if(bProcess.getMultipathEbgp() && bProcess.getMultipathIbgp()){
            mode = bProcess.getMultipathEquivalentAsPathMatchMode().toString();
          }
          else {
            mode = "N/A";
          }
          if(!matchModeToNodes.containsKey(mode)){
            matchModeToNodes.put(mode, new TreeSet<>());
          }
          matchModeToNodes.get(mode).add(node);
        }
      }
    }
    ImmutableList.Builder<Row> rows = ImmutableList.builder();

    for (Map.Entry<String, Set<String>> entry : matchModeToNodes.entrySet()) {
      List<Node> nodes = entry.getValue().stream()
              .map(Node::new)
              .collect(ImmutableList.toImmutableList());
      rows.add(
              Row.builder(columnMap)
                      .put(COL_MATCH_MODE, entry.getKey())
                      .put(COL_NODE_LIST, nodes)
                      .put(COL_NODE_COUNT, nodes.size())
                      .build());
    }
    return rows.build();
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(COL_MATCH_MODE, Schema.STRING, "Multipath match mode", true, false),
                    new ColumnMetadata(
                            COL_NODE_LIST,
                            Schema.list(Schema.NODE),
                            "List of nodes using this match mode",
                            false,
                            true),
                    new ColumnMetadata(
                            COL_NODE_COUNT,
                            Schema.INTEGER,
                            "Number of nodes using this match mode",
                            false,
                            true));
    return new TableMetadata(
            columns, String.format("Match mode ${%s} is used by ${%s} nodes.", COL_MATCH_MODE, COL_NODE_COUNT));
  }
}