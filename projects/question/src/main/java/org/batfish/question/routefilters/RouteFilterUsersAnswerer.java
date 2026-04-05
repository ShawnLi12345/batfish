package org.batfish.question.routefilters;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.pojo.Node;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class RouteFilterUsersAnswerer extends Answerer {

  public static final String COL_NODE = "Node";
  public static final String COL_ROUTE_FILTER_COUNT = "Route_Filter_Count";

  public RouteFilterUsersAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    RouteFilterUsersQuestion question = (RouteFilterUsersQuestion) _question;
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

    for (String nodeName : nodeSpecifier.resolve(ctxt)) {
      Configuration config = ctxt.getConfigs().get(nodeName);
      Map<String, RouteFilterList> exprs = config.getRouteFilterLists();
      if (!exprs.isEmpty()) {
        rows.add(
                Row.builder(columnMap)
                        .put(COL_NODE, new Node(nodeName))
                        .put(COL_ROUTE_FILTER_COUNT, exprs.size())
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
                            COL_ROUTE_FILTER_COUNT,
                            Schema.INTEGER,
                            "Number of route filters",
                            false,
                            true));
    return new TableMetadata(
            columns, String.format("Node ${%s} has ${%s} route filters", COL_NODE, COL_ROUTE_FILTER_COUNT));
  }
}