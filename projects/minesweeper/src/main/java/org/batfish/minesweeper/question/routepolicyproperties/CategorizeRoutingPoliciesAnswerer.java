package org.batfish.minesweeper.question.routepolicyproperties;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.util.stream.Collectors;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.AbstractMap;

import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.BgpPeerConfig;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.bgp.Ipv4UnicastAddressFamily;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.communities.SetCommunities;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.BooleanExprs;
import org.batfish.datamodel.routing_policy.expr.Conjunction;
import org.batfish.datamodel.routing_policy.expr.Disjunction;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;
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


public class CategorizeRoutingPoliciesAnswerer extends Answerer {

  public static final String COL_NODE = "Node";
  public static final String COL_ROW_KIND = "Row_Kind";
  public static final String ROW_KIND_COUNTS = "Counts";
  public static final String ROW_KIND_KINDS = "Kinds";
  public static final String COL_IMPORT_TAG = "Import_Tag";
  public static final String COL_IMPORT_ACCEPT_ALL = "Import_AcceptAll";
  public static final String COL_IMPORT_DENY_ALL = "Import_DenyAll";
  public static final String COL_IMPORT_ACCEPT_PART = "Import_AcceptPart";
  public static final String COL_IMPORT_DENY_PART = "Import_DenyPart";
  public static final String COL_EXPORT_TAG = "Export_Tag";
  public static final String COL_EXPORT_ACCEPT_ALL = "Export_AcceptAll";
  public static final String COL_EXPORT_DENY_ALL = "Export_DenyAll";
  public static final String COL_EXPORT_ACCEPT_PART = "Export_AcceptPart";
  public static final String COL_EXPORT_DENY_PART = "Export_DenyPart";
  public static final String COL_OTHER = "Other";

  public CategorizeRoutingPoliciesAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    CategorizeRoutingPoliciesQuestion question = (CategorizeRoutingPoliciesQuestion) _question;
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

  public enum Category {
    Tag,
    AcceptPart,
    DenyPart,
    AcceptAll,
    DenyAll,
    Other
  }

  static class StanzaCategoryCount{
    int Tag;
    int AcceptPart;
    int DenyPart;
    int AcceptAll;
    int DenyAll;
    int other;
    Set<String> TagAttrs = new TreeSet<>();
    Set<String> AcceptPartAttrs = new TreeSet<>();
    Set<String> DenyPartAttrs = new TreeSet<>();
    Set<String> AcceptAllAttrs = new TreeSet<>();
    Set<String> DenyAllAttrs = new TreeSet<>();
    Set<String> otherAttrs = new TreeSet<>();
  }
  static class Flags{
    boolean setCommunity;
    boolean hasAccept;
    boolean hasReject;
  }

  private static boolean isAcceptType(Statements t) {
    return t == Statements.ExitAccept
        || t == Statements.ReturnTrue
        || t == Statements.SetDefaultActionAccept
        || t == Statements.SetLocalDefaultActionAccept;
  }

  private static boolean isRejectType(Statements t) {
    return t == Statements.ExitReject
        || t == Statements.ReturnFalse
        || t == Statements.SetDefaultActionReject
        || t == Statements.SetLocalDefaultActionReject;
  }

  private static void scan(@Nonnull List<Statement> sts, Flags f) {
    for (Statement s : sts) {
      if (s instanceof SetCommunities) {
        f.setCommunity = true;
      }
      else if (s instanceof Statements.StaticStatement ss) {
        Statements t = ss.getType();
        if (isAcceptType(t)) f.hasAccept = true;
        if (isRejectType(t)) f.hasReject = true;
      }
      else if (s instanceof If ifs) {
        scan(ifs.getTrueStatements(), f);
        scan(ifs.getFalseStatements(), f);
      }
      else if (s instanceof TraceableStatement ts) {
        scan(ts.getInnerStatements(), f);
      }
    }
  }

  private static boolean mayExit(@Nonnull List<Statement> sts) {
    for (Statement s : sts) {
      if (s instanceof Statements.StaticStatement ss) {
        Statements t = ss.getType();
        if (isAcceptType(t) || isRejectType(t)) return true;
      }
      if (s instanceof If ifs) {
        if (mayExit(ifs.getTrueStatements()) || mayExit(ifs.getFalseStatements())) return true;
      }
      if (s instanceof TraceableStatement ts) {
        if (mayExit(ts.getInnerStatements())) return true;
      }
    }
    return false;
  }

  private static boolean terminates(@Nonnull List<Statement> sts, boolean ifAccept) {
    for (Statement s : sts) {
      if (s instanceof Statements.StaticStatement ss){
        Statements t = ss.getType();
        boolean isAcc = isAcceptType(t);
        boolean isRej = isRejectType(t);
        if ( ifAccept && isAcc) return true;
        if (!ifAccept && isRej) return true;
        if ( ifAccept && isRej) return false;
        if (!ifAccept && isAcc) return false;
      }
      else if (s instanceof If ifs) {
        boolean guardTrue = BooleanExprs.TRUE.equals(ifs.getGuard());
        if (guardTrue && terminates(ifs.getTrueStatements(), ifAccept)) return true;
        if (terminates(ifs.getTrueStatements(), ifAccept) && terminates(ifs.getFalseStatements(), ifAccept)) return true;
        if (mayExit(ifs.getTrueStatements()) || mayExit(ifs.getFalseStatements()))return false;
      }
      else if (s instanceof TraceableStatement ts) {
        if (terminates(ts.getInnerStatements(), ifAccept)) return true;
        if (mayExit(ts.getInnerStatements())) return false;
      }
    }
    return false;
  }
  @Nonnull
  private static StanzaCategoryCount analyzeRouteMap(@Nonnull RoutingPolicy policy) {
    StanzaCategoryCount count = new StanzaCategoryCount();
    walkStanzas(policy.getStatements(), count);
    return count;
  }

  private static void walkStanzas(@Nonnull List<Statement> sts, StanzaCategoryCount count) {
    for (Statement s : sts) {
      if (s instanceof If ifs) {
        BooleanExpr g = ifs.getGuard();
        boolean syntheticContext =
                BooleanExprs.CALL_EXPR_CONTEXT.equals(g)
                || BooleanExprs.CALL_STATEMENT_CONTEXT.equals(g);
        if (!syntheticContext) {
          Category cat = getCategory(g, ifs.getTrueStatements());
          String attr = g == null ? "null" : g.getClass().getSimpleName();
          if(g instanceof Conjunction c){
            attr+="("+ c.getConjuncts().stream()
                    .map(e->e.getClass().getSimpleName())
                    .collect(Collectors.joining(", ")) + ")";
          }
          if(g instanceof Disjunction c){
            attr+="("+ c.getDisjuncts().stream()
                    .map(e->e.getClass().getSimpleName())
                    .collect(Collectors.joining(", ")) + ")";
          }
          increment(count, cat, attr);
        }
        walkStanzas(ifs.getFalseStatements(), count);
      } else if (s instanceof TraceableStatement ts) {
        Category cat = getCategory(BooleanExprs.TRUE, ts.getInnerStatements());
        increment(count, cat, BooleanExprs.TRUE.getClass().getSimpleName());
      }
    }
  }

  private static void increment(StanzaCategoryCount count, @Nonnull Category cat, @Nonnull String attr) {
    switch (cat) {
      case Tag         -> { count.Tag++;        count.TagAttrs.add(attr); }
      case AcceptPart  -> { count.AcceptPart++; count.AcceptPartAttrs.add(attr); }
      case DenyPart    -> { count.DenyPart++;   count.DenyPartAttrs.add(attr); }
      case AcceptAll   -> { count.AcceptAll++;  count.AcceptAllAttrs.add(attr); }
      case DenyAll     -> { count.DenyAll++;    count.DenyAllAttrs.add(attr); }
      case Other       -> { count.other++;      count.otherAttrs.add(attr); }
    }
  }


  private static Category getCategory(BooleanExpr guard, List<Statement> body) {
    boolean absolute = BooleanExprs.TRUE.equals(guard);

    Flags f = new Flags();
    scan(body, f);
    if(f.setCommunity) return Category.Tag;

    boolean termAccept = terminates(body, true);
    boolean termReject = terminates(body, false);
    if(absolute) {
      if(termAccept) return Category.AcceptAll;
      if(termReject) return Category.DenyAll;
    } else {
      if(termAccept) return Category.AcceptPart;
      if(termReject) return Category.DenyPart;
    }
    if(f.hasReject) return Category.DenyPart;
    if(f.hasAccept) return Category.AcceptPart;
    return Category.Other;
  }

  @VisibleForTesting
  static List<Row> getAnswerRows(
          SpecifierContext ctxt,
          NodeSpecifier nodeSpecifier,
          Map<String, ColumnMetadata> columnMap) {
    ImmutableList.Builder<Row> rows = ImmutableList.builder();

    int totImportTag=0, totImportAll=0, totImportNone=0, totImportAcceptPart=0, totImportDenyPart=0;
    int totExportTag=0, totExportAll=0, totExportNone=0, totExportAcceptPart=0, totExportDenyPart=0, totOther=0;

    Set<String> totImportTagAttrs = new TreeSet<>();
    Set<String> totImportAllAttrs = new TreeSet<>();
    Set<String> totImportNoneAttrs = new TreeSet<>();
    Set<String> totImportAcceptPartAttrs = new TreeSet<>();
    Set<String> totImportDenyPartAttrs = new TreeSet<>();
    Set<String> totExportTagAttrs = new TreeSet<>();
    Set<String> totExportAllAttrs = new TreeSet<>();
    Set<String> totExportNoneAttrs = new TreeSet<>();
    Set<String> totExportAcceptPartAttrs = new TreeSet<>();
    Set<String> totExportDenyPartAttrs = new TreeSet<>();
    Set<String> totOtherAttrs = new TreeSet<>();

    Map<String, StanzaCategoryCount> classified = new HashMap<>();

    for(String node : nodeSpecifier.resolve(ctxt)){
      classified.clear();
      Configuration config = ctxt.getConfigs().get(node);

      ConfigAtomicPredicates configAPs =
              new ConfigAtomicPredicates(
                      ImmutableList.of(
                              new AbstractMap.SimpleImmutableEntry<>(
                                      config, config.getRoutingPolicies().values())),
                      ImmutableSet.of(),   // extraCommunities
                      ImmutableSet.of());
      TransferBDD tBDD = new TransferBDD(configAPs);

      if (config.getDefaultVrf() == null) continue;
      BgpProcess bProcess = config.getDefaultVrf().getBgpProcess();
      if(bProcess == null)continue;

      int importTag=0, importAll=0, importNone=0, importAcceptPart=0, importDenyPart=0;
      int exportTag=0, exportAll=0, exportNone=0, exportAcceptPart=0, exportDenyPart=0, other=0;

      Set<String> importTagAttrs = new TreeSet<>();
      Set<String> importAllAttrs = new TreeSet<>();
      Set<String> importNoneAttrs = new TreeSet<>();
      Set<String> importAcceptPartAttrs = new TreeSet<>();
      Set<String> importDenyPartAttrs = new TreeSet<>();
      Set<String> exportTagAttrs = new TreeSet<>();
      Set<String> exportAllAttrs = new TreeSet<>();
      Set<String> exportNoneAttrs = new TreeSet<>();
      Set<String> exportAcceptPartAttrs = new TreeSet<>();
      Set<String> exportDenyPartAttrs = new TreeSet<>();
      Set<String> otherAttrs = new TreeSet<>();

      for(BgpPeerConfig peer : Iterables.concat(
              bProcess.getActiveNeighbors().values(),
              bProcess.getPassiveNeighbors().values())){
        Ipv4UnicastAddressFamily af = peer.getIpv4UnicastAddressFamily();
        if(af==null)continue;
        for(String im : af.getImportPolicySources()){
          RoutingPolicy policy = config.getRoutingPolicies().get(im);
          StanzaCategoryCount c = classified.computeIfAbsent(im, k -> analyzeRouteMap(policy));
          importTag        += c.Tag;
          importAll        += c.AcceptAll;
          importNone       += c.DenyAll;
          importAcceptPart += c.AcceptPart;
          importDenyPart   += c.DenyPart;
          other            += c.other;
          importTagAttrs.addAll(c.TagAttrs);
          importAllAttrs.addAll(c.AcceptAllAttrs);
          importNoneAttrs.addAll(c.DenyAllAttrs);
          importAcceptPartAttrs.addAll(c.AcceptPartAttrs);
          importDenyPartAttrs.addAll(c.DenyPartAttrs);
          otherAttrs.addAll(c.otherAttrs);
        }
        for(String ex : af.getExportPolicySources()){
          RoutingPolicy policy = config.getRoutingPolicies().get(ex);
          StanzaCategoryCount c = classified.computeIfAbsent(ex, k -> analyzeRouteMap(policy));
          List<TransferReturn> paths = tBDD.computePaths(policy,true);
          System.out.println(paths);

          exportTag        += c.Tag;
          exportAll        += c.AcceptAll;
          exportNone       += c.DenyAll;
          exportAcceptPart += c.AcceptPart;
          exportDenyPart   += c.DenyPart;
          other            += c.other;
          exportTagAttrs.addAll(c.TagAttrs);
          exportAllAttrs.addAll(c.AcceptAllAttrs);
          exportNoneAttrs.addAll(c.DenyAllAttrs);
          exportAcceptPartAttrs.addAll(c.AcceptPartAttrs);
          exportDenyPartAttrs.addAll(c.DenyPartAttrs);
          otherAttrs.addAll(c.otherAttrs);
        }
      }

      rows.add(Row.builder(columnMap)
              .put(COL_NODE, node)
              .put(COL_ROW_KIND, ROW_KIND_COUNTS)
              .put(COL_IMPORT_TAG, Integer.toString(importTag))
              .put(COL_IMPORT_ACCEPT_ALL, Integer.toString(importAll))
              .put(COL_IMPORT_DENY_ALL, Integer.toString(importNone))
              .put(COL_IMPORT_ACCEPT_PART, Integer.toString(importAcceptPart))
              .put(COL_IMPORT_DENY_PART, Integer.toString(importDenyPart))
              .put(COL_EXPORT_TAG, Integer.toString(exportTag))
              .put(COL_EXPORT_ACCEPT_ALL, Integer.toString(exportAll))
              .put(COL_EXPORT_DENY_ALL, Integer.toString(exportNone))
              .put(COL_EXPORT_ACCEPT_PART, Integer.toString(exportAcceptPart))
              .put(COL_EXPORT_DENY_PART, Integer.toString(exportDenyPart))
              .put(COL_OTHER, Integer.toString(other))
              .build());

      rows.add(Row.builder(columnMap)
              .put(COL_NODE, node)
              .put(COL_ROW_KIND, ROW_KIND_KINDS)
              .put(COL_IMPORT_TAG, String.join(", ", importTagAttrs))
              .put(COL_IMPORT_ACCEPT_ALL, String.join(", ", importAllAttrs))
              .put(COL_IMPORT_DENY_ALL, String.join(", ", importNoneAttrs))
              .put(COL_IMPORT_ACCEPT_PART, String.join(", ", importAcceptPartAttrs))
              .put(COL_IMPORT_DENY_PART, String.join(", ", importDenyPartAttrs))
              .put(COL_EXPORT_TAG, String.join(", ", exportTagAttrs))
              .put(COL_EXPORT_ACCEPT_ALL, String.join(", ", exportAllAttrs))
              .put(COL_EXPORT_DENY_ALL, String.join(", ", exportNoneAttrs))
              .put(COL_EXPORT_ACCEPT_PART, String.join(", ", exportAcceptPartAttrs))
              .put(COL_EXPORT_DENY_PART, String.join(", ", exportDenyPartAttrs))
              .put(COL_OTHER, String.join(", ", otherAttrs))
              .build());

      totImportTag        += importTag;
      totImportAll        += importAll;
      totImportNone       += importNone;
      totImportAcceptPart += importAcceptPart;
      totImportDenyPart   += importDenyPart;
      totExportTag        += exportTag;
      totExportAll        += exportAll;
      totExportNone       += exportNone;
      totExportAcceptPart += exportAcceptPart;
      totExportDenyPart   += exportDenyPart;
      totOther            += other;
      totImportTagAttrs.addAll(importTagAttrs);
      totImportAllAttrs.addAll(importAllAttrs);
      totImportNoneAttrs.addAll(importNoneAttrs);
      totImportAcceptPartAttrs.addAll(importAcceptPartAttrs);
      totImportDenyPartAttrs.addAll(importDenyPartAttrs);
      totExportTagAttrs.addAll(exportTagAttrs);
      totExportAllAttrs.addAll(exportAllAttrs);
      totExportNoneAttrs.addAll(exportNoneAttrs);
      totExportAcceptPartAttrs.addAll(exportAcceptPartAttrs);
      totExportDenyPartAttrs.addAll(exportDenyPartAttrs);
      totOtherAttrs.addAll(otherAttrs);
    }

    rows.add(Row.builder(columnMap)
            .put(COL_NODE, "TOTAL")
            .put(COL_ROW_KIND, ROW_KIND_COUNTS)
            .put(COL_IMPORT_TAG, Integer.toString(totImportTag))
            .put(COL_IMPORT_ACCEPT_ALL, Integer.toString(totImportAll))
            .put(COL_IMPORT_DENY_ALL, Integer.toString(totImportNone))
            .put(COL_IMPORT_ACCEPT_PART, Integer.toString(totImportAcceptPart))
            .put(COL_IMPORT_DENY_PART, Integer.toString(totImportDenyPart))
            .put(COL_EXPORT_TAG, Integer.toString(totExportTag))
            .put(COL_EXPORT_ACCEPT_ALL, Integer.toString(totExportAll))
            .put(COL_EXPORT_DENY_ALL, Integer.toString(totExportNone))
            .put(COL_EXPORT_ACCEPT_PART, Integer.toString(totExportAcceptPart))
            .put(COL_EXPORT_DENY_PART, Integer.toString(totExportDenyPart))
            .put(COL_OTHER, Integer.toString(totOther))
            .build());

    rows.add(Row.builder(columnMap)
            .put(COL_NODE, "TOTAL")
            .put(COL_ROW_KIND, ROW_KIND_KINDS)
            .put(COL_IMPORT_TAG, String.join(", ", totImportTagAttrs))
            .put(COL_IMPORT_ACCEPT_ALL, String.join(", ", totImportAllAttrs))
            .put(COL_IMPORT_DENY_ALL, String.join(", ", totImportNoneAttrs))
            .put(COL_IMPORT_ACCEPT_PART, String.join(", ", totImportAcceptPartAttrs))
            .put(COL_IMPORT_DENY_PART, String.join(", ", totImportDenyPartAttrs))
            .put(COL_EXPORT_TAG, String.join(", ", totExportTagAttrs))
            .put(COL_EXPORT_ACCEPT_ALL, String.join(", ", totExportAllAttrs))
            .put(COL_EXPORT_DENY_ALL, String.join(", ", totExportNoneAttrs))
            .put(COL_EXPORT_ACCEPT_PART, String.join(", ", totExportAcceptPartAttrs))
            .put(COL_EXPORT_DENY_PART, String.join(", ", totExportDenyPartAttrs))
            .put(COL_OTHER, String.join(", ", totOtherAttrs))
            .build());

    return rows.build();
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(
                            COL_NODE, Schema.STRING, "Node name", true, false),
                    new ColumnMetadata(
                            COL_ROW_KIND, Schema.STRING,
                            "Row kind: Counts or Kinds (guard expression class names)", true, false),
                    new ColumnMetadata(
                            COL_IMPORT_TAG, Schema.STRING,
                            "Import tag policies", false, true),
                    new ColumnMetadata(
                            COL_IMPORT_ACCEPT_ALL, Schema.STRING,
                            "Import accept-all policies", false, true),
                    new ColumnMetadata(
                            COL_IMPORT_DENY_ALL, Schema.STRING,
                            "Import deny-all policies", false, true),
                    new ColumnMetadata(
                            COL_IMPORT_ACCEPT_PART, Schema.STRING,
                            "Import partial-accept policies", false, true),
                    new ColumnMetadata(
                            COL_IMPORT_DENY_PART, Schema.STRING,
                            "Import partial-deny policies", false, true),
                    new ColumnMetadata(
                            COL_EXPORT_TAG, Schema.STRING,
                            "Export tag policies", false, true),
                    new ColumnMetadata(
                            COL_EXPORT_ACCEPT_ALL, Schema.STRING,
                            "Export accept-all policies", false, true),
                    new ColumnMetadata(
                            COL_EXPORT_DENY_ALL, Schema.STRING,
                            "Export deny-all policies", false, true),
                    new ColumnMetadata(
                            COL_EXPORT_ACCEPT_PART, Schema.STRING,
                            "Export partial-accept policies", false, true),
                    new ColumnMetadata(
                            COL_EXPORT_DENY_PART, Schema.STRING,
                            "Export partial-deny policies", false, true),
                    new ColumnMetadata(
                            COL_OTHER, Schema.STRING,
                            "Other policies", false, true));
    return new TableMetadata(
            columns,
            String.format(
                    "Counts of routing policies categorized by content and direction on ${%s}.",
                    COL_NODE));
  }
}