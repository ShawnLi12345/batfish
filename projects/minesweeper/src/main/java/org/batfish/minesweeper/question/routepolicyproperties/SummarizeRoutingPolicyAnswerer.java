package org.batfish.minesweeper.question.routepolicyproperties;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.List;
import java.util.AbstractMap;
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
import org.batfish.datamodel.routing_policy.expr.CallExpr;
import org.batfish.datamodel.routing_policy.expr.Conjunction;
import org.batfish.datamodel.routing_policy.expr.Disjunction;
import org.batfish.datamodel.routing_policy.expr.Not;
import org.batfish.datamodel.routing_policy.statement.CallStatement;
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
  public static final String COL_VENDOR = "Vendor";
  public static final String COL_POLICY = "Policy";
  public static final String COL_PATH_COUNT = "Path_Count";
  public static final String COL_IF_COUNT = "If_Count";
  public static final String COL_TRACEABLE_STATEMENT_COUNT = "TraceableStatement_Count";
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

  private static PolicySummary createPolicySummary(Configuration config, RoutingPolicy policy) {
    PolicySummary cnt = new PolicySummary();
    Set<String> onPath = new HashSet<>();
    onPath.add(policy.getName());
    walkStanzas(policy.getStatements(), config.getRoutingPolicies(), onPath, cnt);
    ConfigAtomicPredicates configAPs =
            new ConfigAtomicPredicates(
                    ImmutableList.of(
                            new AbstractMap.SimpleImmutableEntry<>(
                                    config, ImmutableList.of(policy))),
                    ImmutableSet.of(),
                    ImmutableSet.of());
    TransferBDD tBDD = new TransferBDD(configAPs);
    cnt.paths = countPath(tBDD,policy);
    return cnt;
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

      for (RoutingPolicy policy : config.getRoutingPolicies().values()) {
        if(policy.getName().startsWith("~"))continue;

        PolicySummary cnt = createPolicySummary(config, policy);
        rows.add(Row.builder(columnMap)
                .put(COL_NODE, node)
                .put(COL_VENDOR, config.getConfigurationFormat().name().toLowerCase().replace('_', '-'))
                .put(COL_POLICY, policy.getName())
                .put(COL_PATH_COUNT, cnt.paths)
                .put(COL_IF_COUNT, cnt.ifCount)
                .put(COL_TRACEABLE_STATEMENT_COUNT, cnt.traceableStatementCount)
                .put(COL_CONDITIONS, cnt.Conditions.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", ")))
                .put(COL_Attributes, cnt.Attributes.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", ")))
                .build());
      }
    }
    return rows.build();
  }

  private static int countPath(TransferBDD tBDD, RoutingPolicy policy) {
    List<TransferReturn> paths = tBDD.computePaths(policy,false);
    return paths.size();
  }

  static class PolicySummary{
    int ifCount;
    int traceableStatementCount;
    int paths;
    Map<String, Integer> Attributes = new TreeMap<>();
    Map<String, Integer> Conditions = new TreeMap<>();
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

  private static void walkStanzas(
      @Nonnull List<Statement> sts,
      Map<String, RoutingPolicy> policies,
      Set<String> onPath,
      PolicySummary count) {
    for (Statement s : sts) {
      if (s instanceof If ifs) {
        BooleanExpr g = ifs.getGuard();
        boolean syntheticContext =
            BooleanExprs.CALL_EXPR_CONTEXT.equals(g)
                || BooleanExprs.CALL_STATEMENT_CONTEXT.equals(g);
        if (!syntheticContext) {
          count.Conditions.merge(getCondition(g),1,Integer::sum);
          count.ifCount++;
        }
        walkGuard(g, policies, onPath, count);
        walkStanzas(ifs.getTrueStatements(), policies, onPath, count);
        walkStanzas(ifs.getFalseStatements(), policies, onPath, count);
      } else if (s instanceof TraceableStatement ts) {
        count.traceableStatementCount++;
        walkStanzas(ts.getInnerStatements(), policies, onPath, count);
      } else if (s instanceof CallStatement cs) {
        walkCalledPolicy(cs.getCalledPolicyName(), policies, onPath, count);
      } else if (isAttributeModifier(s)) {
        count.Attributes.merge(s.getClass().getSimpleName(),1,Integer::sum);
      }
    }
  }

  private static void walkCalledPolicy(
      String name,
      Map<String, RoutingPolicy> policies,
      Set<String> onPath,
      PolicySummary count) {
    RoutingPolicy called = policies.get(name);
    if (called == null || !onPath.add(name)) return;
    walkStanzas(called.getStatements(), policies, onPath, count);
    onPath.remove(name);
  }

  private static void walkGuard(
      BooleanExpr g,
      Map<String, RoutingPolicy> policies,
      Set<String> onPath,
      PolicySummary count) {
    if (g instanceof CallExpr ce) {
      walkCalledPolicy(ce.getCalledPolicyName(), policies, onPath, count);
    } else if (g instanceof Conjunction c) {
      c.getConjuncts().forEach(e -> walkGuard(e, policies, onPath, count));
    } else if (g instanceof Disjunction d) {
      d.getDisjuncts().forEach(e -> walkGuard(e, policies, onPath, count));
    } else if (g instanceof Not n) {
      walkGuard(n.getExpr(), policies, onPath, count);
    }
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(
                            COL_NODE, Schema.STRING, "Node name", true, false),
                    new ColumnMetadata(
                            COL_VENDOR, Schema.STRING, "Router vendor", false, false),
                    new ColumnMetadata(
                            COL_POLICY, Schema.STRING, "Routing policy name", true, false),
                    new ColumnMetadata(
                            COL_PATH_COUNT, Schema.INTEGER, "Count of possible paths", false, true),
                    new ColumnMetadata(
                            COL_IF_COUNT, Schema.INTEGER, "Count of If statements", false, true),
                    new ColumnMetadata(
                            COL_TRACEABLE_STATEMENT_COUNT, Schema.INTEGER, "Count of TraceableStatements", false, true),
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