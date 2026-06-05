package org.batfish.minesweeper.question.routepolicyproperties;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableSet;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.minesweeper.ConfigAtomicPredicates;
import org.batfish.minesweeper.bdd.TransferBDD;
import org.batfish.minesweeper.bdd.TransferReturn;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;


public class SummarizeRoutingPolicyAnswerer extends Answerer {

  public static final String COL_NODE = "Node";
  public static final String COL_POLICY = "Policy";
  public static final String COL_SUMMARY = "Summary";

  public SummarizeRoutingPolicyAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    SummarizeRoutingPolicyQuestion question = (SummarizeRoutingPolicyQuestion) _question;
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

    for (String node : nodeSpecifier.resolve(ctxt)) {
      Configuration config = ctxt.getConfigs().get(node);
      if (config == null) continue;

      ConfigAtomicPredicates configAPs =
              new ConfigAtomicPredicates(
                      ImmutableList.of(
                              new AbstractMap.SimpleImmutableEntry<>(
                                      config, config.getRoutingPolicies().values())),
                      ImmutableSet.of(),   // extraCommunities
                      ImmutableSet.of());
      TransferBDD tBDD = new TransferBDD(configAPs);
      for (RoutingPolicy policy : config.getRoutingPolicies().values()) {
        rows.add(Row.builder(columnMap)
                .put(COL_NODE, node)
                .put(COL_POLICY, policy.getName())
                .put(COL_SUMMARY, summarize(tBDD, policy))
                .build());
      }
    }

    return rows.build();
  }

  private static int summarize(TransferBDD tBDD, RoutingPolicy policy) {
    List<TransferReturn> paths = tBDD.computePaths(policy,false);
    if(paths.size()!=1){
      for(TransferReturn tr : paths){
        System.out.println(tr);
      }
    }
    return paths.size();
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(
                            COL_NODE, Schema.STRING, "Node name", true, false),
                    new ColumnMetadata(
                            COL_POLICY, Schema.STRING, "Routing policy name", true, false),
                    new ColumnMetadata(
                            COL_SUMMARY, Schema.INTEGER, "Summary of the routing policy", false, true));
    return new TableMetadata(
            columns,
            String.format(
                    "Summary of routing policies on ${%s}.",
                    COL_NODE));
  }
}