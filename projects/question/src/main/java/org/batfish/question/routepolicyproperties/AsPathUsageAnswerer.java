package org.batfish.question.routepolicyproperties;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.ArrayList;

import com.google.common.collect.Table;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Bgpv4Route;
import org.batfish.datamodel.DataPlane;
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

public class AsPathUsageAnswerer extends Answerer {

  public static final String COL_AS_PATH = "AS_Path";
  public static final String COL_NODE_LIST = "Node_List";
  public static final String COL_NODE_COUNT = "Node_Count";

  public AsPathUsageAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    AsPathUsageQuestion question = (AsPathUsageQuestion) _question;
    TableMetadata tableMetadata = createTableMetadata();
    TableAnswerElement answer = new TableAnswerElement(tableMetadata);

    DataPlane dp = _batfish.loadDataPlane(snapshot);

    List<Row> rows =
            getAnswerRows(
                    dp,
                    _batfish.specifierContext(snapshot),
                    question.getNodeSpecifier(),
                    tableMetadata.toColumnMap());

    answer.postProcessAnswer(question, rows);
    return answer;
  }

  @VisibleForTesting
  static List<Row> getAnswerRows(
          DataPlane dp,
          SpecifierContext ctxt,
          NodeSpecifier nodeSpecifier,
          Map<String, ColumnMetadata> columnMap) {
    Map<String, List<String>> PathToNodes = new TreeMap<>();
    Table<String, String, Set<Bgpv4Route>> bgpRoutes = dp.getBgpRoutes();

    for (String node : nodeSpecifier.resolve(ctxt)) {
      Map<String, Set<Bgpv4Route>> vrfRoutes = bgpRoutes.row(node);
      for (Set<Bgpv4Route> routes : vrfRoutes.values()) {
        for (Bgpv4Route route : routes) {
          String asPath = route.getAsPath().toString();
          if(asPath.isEmpty())asPath = "None";
          if(!PathToNodes.containsKey(asPath)){
            PathToNodes.put(asPath, new ArrayList<>());
          }
          PathToNodes.get(asPath).add(node);
        }
      }
    }

    ImmutableList.Builder<Row> rows = ImmutableList.builder();
    for (Map.Entry<String, List<String>> entry : PathToNodes.entrySet()) {
      List<Node> nodes = entry.getValue().stream()
              .map(Node::new)
              .collect(ImmutableList.toImmutableList());
      rows.add(
              Row.builder(columnMap)
                      .put(COL_AS_PATH, entry.getKey())
                      .put(COL_NODE_LIST, nodes)
                      .put(COL_NODE_COUNT, nodes.size())
                      .build());
    }
    return rows.build();
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(COL_AS_PATH, Schema.STRING, "AS Path", true, false),
                    new ColumnMetadata(
                            COL_NODE_LIST,
                            Schema.list(Schema.NODE),
                            "List of nodes with this AS path",
                            false,
                            true),
                    new ColumnMetadata(
                            COL_NODE_COUNT,
                            Schema.INTEGER,
                            "Number of nodes with this AS path",
                            false,
                            true));
    return new TableMetadata(
            columns, String.format("AS path ${%s} is used by ${%s} nodes.", COL_AS_PATH, COL_NODE_COUNT));
  }
}