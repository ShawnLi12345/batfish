package org.batfish.question.routepolicyproperties;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;

import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.util.BatfishObjectMapper;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class BgpPropertiesCountAnswerer extends Answerer {

  public static final String COL_NODE_STRUCT = "Node_Structure_Name";
  public static final String COL_COMMUNITY_SET_VALS = "Community_Set_Vals";
  public static final String COL_SET_METRIC = "Set_Metric";
  public static final String COL_MATCH_COMMUNITY_VALS = "Match_Community_Vals";
  public static final String COL_LOCAL_PREF = "Local_Preference";

  public BgpPropertiesCountAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    BgpPropertiesCountQuestion question = (BgpPropertiesCountQuestion) _question;
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

    for(String node : nodeSpecifier.resolve(ctxt)){
      Map<String, RoutingPolicy> rPolicies = ctxt.getConfigs().get(node).getRoutingPolicies();
      for(Map.Entry<String, RoutingPolicy> entry : rPolicies.entrySet()){
        RoutingPolicy policy = entry.getValue();
        if(policy.getName().startsWith("~"))continue;
        String json = BatfishObjectMapper.writeStringRuntimeError(policy);
        rows.add(
                Row.builder(columnMap)
                        .put(COL_NODE_STRUCT, node + " " + policy.getName())
                        .put(COL_COMMUNITY_SET_VALS, countOccurrences(json, "LiteralCommunitySet"))
                        .put(COL_SET_METRIC, countOccurrences(json, "SetMetric"))
                        .put(COL_MATCH_COMMUNITY_VALS, countOccurrences(json, "communitySetMatchExpr"))
                        .put(COL_LOCAL_PREF, countOccurrences(json, "SetLocalPreference"))
                        .build());
      }
    }
    return rows.build();
  }

  private static int countOccurrences(String s, String targ) {
    int i = s.indexOf(targ), occurrences = 0;
    while (i != -1) {
      occurrences++;
      i = s.indexOf(targ, i + targ.length());
    }
    return occurrences;
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(
                            COL_NODE_STRUCT, Schema.STRING, "Node and structure name", true, false),
                    new ColumnMetadata(
                            COL_COMMUNITY_SET_VALS,
                            Schema.INTEGER,
                            "Number of community set expressions",
                            false,
                            true),
                    new ColumnMetadata(
                            COL_SET_METRIC,
                            Schema.INTEGER,
                            "Number of set metric statements",
                            false,
                            true),
                    new ColumnMetadata(
                            COL_MATCH_COMMUNITY_VALS,
                            Schema.INTEGER,
                            "Number of community match expressions",
                            false,
                            true),
                    new ColumnMetadata(
                            COL_LOCAL_PREF,
                            Schema.INTEGER,
                            "Number of local preference statements",
                            false,
                            true));
    return new TableMetadata(
            columns,
            String.format("BGP property counts for ${%s}.", COL_NODE_STRUCT));
  }
}