package org.batfish.question.routepolicyproperties;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.util.BatfishObjectMapper;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchExpr;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class CommunitySetRegexAnswerer extends Answerer {

  public static final String COL_STRUCT_NAME = "Structure_Name";
  public static final String COL_REGEX = "Regex";

  public CommunitySetRegexAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    CommunitySetRegexQuestion question = (CommunitySetRegexQuestion) _question;
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
      SpecifierContext ctxt, NodeSpecifier nodeSpecifier, Map<String, ColumnMetadata> columnMap) {
    ImmutableList.Builder<Row> rows = ImmutableList.builder();

    Map<String, String> structToRegex = new TreeMap<>();
    for (String node : nodeSpecifier.resolve(ctxt)) {
      Map<String, CommunitySetMatchExpr> matchExprMap =
          ctxt.getConfigs().get(node).getCommunitySetMatchExprs();
      for (Map.Entry<String, CommunitySetMatchExpr> entry : matchExprMap.entrySet()) {
        if (entry.getKey().startsWith("~")||structToRegex.containsKey(entry.getKey())) continue;
        CommunitySetMatchExpr matchExpr = entry.getValue();
        String json = BatfishObjectMapper.writeStringRuntimeError(matchExpr);
        int index = json.indexOf("\"regex\"");
        if (index == -1) continue;
        String regex = json.substring(index + 9);
        regex = regex.substring(0, regex.indexOf("\"")).replace("\\", "");
        structToRegex.put(entry.getKey(), regex);
      }
    }
    for (Map.Entry<String, String> entry : structToRegex.entrySet()) {
      rows.add(
              Row.builder(columnMap)
                      .put(COL_STRUCT_NAME, entry.getKey())
                      .put(COL_REGEX, entry.getValue())
                      .build());
    }
    return rows.build();
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(
                            COL_STRUCT_NAME, Schema.STRING, "Node Structure name", true, false),
                    new ColumnMetadata(
                            COL_REGEX, Schema.STRING, "Community set regex", false, true));
    return new TableMetadata(
            columns,
            String.format("Community set regex for ${%s}.", COL_STRUCT_NAME));
  }
}
