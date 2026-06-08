package org.batfish.minesweeper.question.routepolicyproperties;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.BooleanExprs;
import org.batfish.datamodel.routing_policy.expr.Conjunction;
import org.batfish.datamodel.routing_policy.expr.Disjunction;
import org.batfish.datamodel.routing_policy.statement.ExcludeAsPath;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.PrependAsPath;
import org.batfish.datamodel.routing_policy.statement.RemoveTunnelEncapsulationAttribute;
import org.batfish.datamodel.routing_policy.statement.ReplaceAsesInAsSequence;
import org.batfish.datamodel.routing_policy.statement.SetAdministrativeCost;
import org.batfish.datamodel.routing_policy.statement.SetDefaultPolicy;
import org.batfish.datamodel.routing_policy.statement.SetDefaultTag;
import org.batfish.datamodel.routing_policy.statement.SetEigrpMetric;
import org.batfish.datamodel.routing_policy.statement.SetIsisLevel;
import org.batfish.datamodel.routing_policy.statement.SetIsisMetricType;
import org.batfish.datamodel.routing_policy.statement.SetLocalPreference;
import org.batfish.datamodel.routing_policy.statement.SetMetric;
import org.batfish.datamodel.routing_policy.statement.SetNextHop;
import org.batfish.datamodel.routing_policy.statement.SetOrigin;
import org.batfish.datamodel.routing_policy.statement.SetOriginatorIp;
import org.batfish.datamodel.routing_policy.statement.SetOspfMetricType;
import org.batfish.datamodel.routing_policy.statement.SetTag;
import org.batfish.datamodel.routing_policy.statement.SetTunnelEncapsulationAttribute;
import org.batfish.datamodel.routing_policy.statement.SetVarMetricType;
import org.batfish.datamodel.routing_policy.statement.SetWeight;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.TraceableStatement;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.minesweeper.ConfigAtomicPredicates;
import org.batfish.minesweeper.bdd.TransferBDD;
import org.batfish.minesweeper.bdd.TransferReturn;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

import javax.annotation.Nonnull;

import org.batfish.datamodel.routing_policy.communities.SetCommunities;


public class SummarizeRoutingPolicyAnswerer extends Answerer {

  public static final String COL_NODE = "Node";
  public static final String COL_POLICY = "Policy";
  public static final String COL_PATH_COUNT = "Path_Count";
  public static final String COL_STANZA_COUNT = "Stanza_Count";
  public static final String COL_CONDITIONS = "Conditions";
  public static final String COL_Attributes = "Attributes";

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
                      ImmutableSet.of(),
                      ImmutableSet.of());
      TransferBDD tBDD = new TransferBDD(configAPs);
      for (RoutingPolicy policy : config.getRoutingPolicies().values()) {
        if(policy.getName().startsWith("~"))continue;
        StanzaCategoryCount cnt = new StanzaCategoryCount();
/*
        System.out.println(config.getVendorFamily()+"   "+node+"   "+policy.getName());
        for(Statement st : policy.getStatements()) {
          System.out.println(st.toString()+"\n");
        }
        System.out.println("\n");
*/
        walkStanzas(policy.getStatements(), cnt);
        rows.add(Row.builder(columnMap)
                .put(COL_NODE, node)
                .put(COL_POLICY, policy.getName())
                .put(COL_PATH_COUNT, countPath(tBDD, policy))
                .put(COL_STANZA_COUNT, cnt.stanzaCount)
                .put(COL_CONDITIONS, String.join(", ",cnt.Conditions))
                .put(COL_Attributes, String.join(", ",cnt.Attributes))
                .build());
      }
    }
    return rows.build();
  }

  private static int countPath(TransferBDD tBDD, RoutingPolicy policy) {
    List<TransferReturn> paths = tBDD.computePaths(policy,false);
    return paths.size();
  }

  static class StanzaCategoryCount{
    int stanzaCount;
    Set<String> Attributes = new TreeSet<>();
    Set<String> Conditions = new TreeSet<>();
  }

  static boolean isAttributeModifier(Statement s) {
    return s instanceof SetMetric
            || s instanceof SetLocalPreference
            || s instanceof SetTag
            || s instanceof SetDefaultTag
            || s instanceof SetWeight
            || s instanceof SetOrigin
            || s instanceof SetOriginatorIp
            || s instanceof SetNextHop
            || s instanceof SetAdministrativeCost
            || s instanceof SetDefaultPolicy
            || s instanceof SetOspfMetricType
            || s instanceof SetIsisLevel
            || s instanceof SetIsisMetricType
            || s instanceof SetEigrpMetric
            || s instanceof SetVarMetricType
            || s instanceof SetTunnelEncapsulationAttribute
            || s instanceof RemoveTunnelEncapsulationAttribute
            || s instanceof PrependAsPath
            || s instanceof ExcludeAsPath
            || s instanceof ReplaceAsesInAsSequence
            || s instanceof SetCommunities;
  }

  private static String getCondition(BooleanExpr g){
    String attr = g == null ? "null" : g.getClass().getSimpleName();
    if (g instanceof Conjunction c) {
      attr +=
              "("
                      + c.getConjuncts().stream()
                      .map(e -> e.getClass().getSimpleName())
                      .collect(Collectors.joining(", "))
                      + ")";
    }
    else if (g instanceof Disjunction c) {
      attr +=
              "("
                      + c.getDisjuncts().stream()
                      .map(e -> e.getClass().getSimpleName())
                      .collect(Collectors.joining(", "))
                      + ")";
    }
    return attr;
  }

  private static void walkStanzas(@Nonnull List<Statement> sts, StanzaCategoryCount count) {
    for (Statement s : sts) {
      if (s instanceof If ifs) {
        count.stanzaCount++;
        BooleanExpr g = ifs.getGuard();
        boolean syntheticContext =
            BooleanExprs.CALL_EXPR_CONTEXT.equals(g)
                || BooleanExprs.CALL_STATEMENT_CONTEXT.equals(g);
        if (!syntheticContext) {
          count.Conditions.add(getCondition(g));
        }
        walkStanzas(ifs.getTrueStatements(),count);
        walkStanzas(ifs.getFalseStatements(), count);
      } else if (s instanceof TraceableStatement ts) {
        walkStanzas(ts.getInnerStatements(), count);
      }
      else if(isAttributeModifier(s)) {
        count.Attributes.add(s.getClass().getSimpleName());
      }
    }
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(
                            COL_NODE, Schema.STRING, "Node name", true, false),
                    new ColumnMetadata(
                            COL_POLICY, Schema.STRING, "Routing policy name", true, false),
                    new ColumnMetadata(
                            COL_PATH_COUNT, Schema.INTEGER, "Count of possible paths", false, true),
                    new ColumnMetadata(
                            COL_STANZA_COUNT, Schema.INTEGER, "Stanza Count", false, true),
                    new ColumnMetadata(
                            COL_CONDITIONS, Schema.STRING, "List of Routing Policy Conditions", false, true),
                    new ColumnMetadata(
                            COL_Attributes, Schema.STRING, "List of Attributes", false, false)
            );
    return new TableMetadata(
            columns,
            String.format(
                    "Summary of routing policies on ${%s}.",
                    COL_NODE));
  }
}